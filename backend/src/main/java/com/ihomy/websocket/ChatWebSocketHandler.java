package com.ihomy.websocket;

import tools.jackson.databind.ObjectMapper;
import com.ihomy.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 聊天室 WebSocket 处理器(协议 v2):
 * - 上行统一 {"type","data"}:ping(心跳,回 pong)/ message(聊天消息);未知 type 回 error 不断连。
 * - 下行 envelope:{"type":"message|pong|error","data":{…}};聊天消息视图与 REST 历史同构(含 senderName/msgType/payload)。
 * - 按 familyId 分房间广播;另有 userId→sessions 索引供定向投递(通话/对战信令,P3/P4 启用),
 *   目标不在线不排队补发,由上层走离线落库逻辑。
 * 低并发家庭场景内存房间表足够,不引入 Redis 广播。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ChatService chatService;
    private final com.ihomy.service.GameMatchService matchService;
    private final ObjectMapper objectMapper;

    /** 房间表:familyId -> 在线会话集合(会话自带 userId/familyId 属性) */
    private final Map<Long, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();

    /** 定向投递索引:userId -> 在线会话集合(同人多标签各一条) */
    private final Map<Long, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long familyId = familyOf(session);
        if (familyId == null) {
            closeSilently(session);
            return;
        }
        rooms.computeIfAbsent(familyId, k -> ConcurrentHashMap.newKeySet()).add(session);
        Long userId = userIdOf(session);
        if (userId != null) {
            userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
        }
        log.info("聊天连接建立 family={} sessions={} user={}", familyId, rooms.get(familyId).size(), userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long familyId = familyOf(session);
        Long userId = userIdOf(session);
        if (familyId == null || userId == null) {
            return;
        }
        // 每条 WS 消息一个独立 tid:握手 tid 只代表建连,消息级操作单独成链路
        String tid = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        org.slf4j.MDC.put(com.ihomy.filter.TraceIdFilter.TRACE_ID, tid);
        try {
            com.ihomy.common.Loggers.access().info(
                    "WS /ws/chat user={}#{} family={} bytes={} content={}",
                    session.getAttributes().get("username"), userId, familyId,
                    message.getPayloadLength(), abbreviate(message.getPayload()));
            Map<?, ?> pkt = objectMapper.readValue(message.getPayload(), Map.class);
            String type = str(pkt.get("type"));
            Map<?, ?> data = pkt.get("data") instanceof Map<?, ?> d ? d : Map.of();
            switch (type) {
                case "ping" -> reply(session, Map.of("type", "pong"));
                case "message" -> onChatMessage(session, familyId, userId, data);
                case "call-signal" -> onCallSignal(session, familyId, userId, data);
                case "game-signal" -> onGameSignal(session, familyId, userId, data);
                default -> replyError(session, "BAD_TYPE");
            }
        } catch (Exception e) {
            // 载荷不是合法 JSON 等协议错误:回 error envelope,不断连(心跳错配曾在这里关连接)
            log.warn("WS 载荷处理失败 user={} bytes={}", userId, message.getPayloadLength(), e);
            replyError(session, "BAD_FORMAT");
        } finally {
            org.slf4j.MDC.remove(com.ihomy.filter.TraceIdFilter.TRACE_ID);
        }
    }

    /** 聊天消息:校验+落库+广播(校验失败回 error envelope,连接保留) */
    private void onChatMessage(WebSocketSession session, Long familyId, Long userId, Map<?, ?> data) {
        try {
            String payload = data.get("payload") == null ? null
                    : data.get("payload") instanceof String s ? s : objectMapper.writeValueAsString(data.get("payload"));
            Map<String, Object> view = chatService.sendMessage(familyId, userId, str(data.get("msgType")),
                    str(data.get("content")), payload);
            broadcast(familyId, envelope("message", view));
        } catch (IllegalArgumentException e) {
            replyError(session, e.getMessage());
        }
    }

    /** 通话信令事件集:end 由服务端代落 CALL 记录,其余按 to 定向转发(不排队补发,离线由主叫超时逻辑兜底) */
    private static final java.util.Set<String> CALL_EVENTS =
            java.util.Set.of("invite", "accept", "reject", "busy", "offer", "answer", "ice");

    private void onCallSignal(WebSocketSession session, Long familyId, Long userId, Map<?, ?> data) {
        String event = str(data.get("event"));
        Long to = data.get("to") instanceof Number n ? n.longValue() : null;
        String callId = str(data.get("callId"));
        if (event == null || to == null || callId == null || callId.isBlank() || callId.length() > 64) {
            replyError(session, "BAD_SIGNAL");
            return;
        }
        // 家庭隔离:定向目标必须与发送人同家庭(跨家庭一律拒绝)
        if (!chatService.sameFamily(userId, to)) {
            replyError(session, "BAD_SIGNAL");
            return;
        }
        if (CALL_EVENTS.contains(event)) {
            Map<String, Object> out = new java.util.LinkedHashMap<>();
            data.forEach((k, v) -> out.put(String.valueOf(k), v));
            out.put("from", userId);
            sendToUser(to, "call-signal", out);
            return;
        }
        if ("end".equals(event)) {
            // 通话结束:服务端统一落 CALL 记录并广播(挂断/拒绝/超时/取消/忙线都收敛到这里)
            String media = "VIDEO".equals(str(data.get("media"))) ? "VIDEO" : "AUDIO";
            String result = switch (str(data.get("reason"))) {
                case "completed" -> "ANSWERED";
                case "rejected" -> "REJECTED";
                case "timeout" -> "MISSED";
                default -> "CANCELLED";
            };
            Integer duration = data.get("duration") instanceof Number n ? n.intValue() : 0;
            Map<String, Object> view = chatService.writeCallRecord(familyId, userId, media, result, duration, callId);
            if (view != null) {
                broadcast(familyId, envelope("message", view));
            }
            return;
        }
        replyError(session, "BAD_SIGNAL");
    }

    /**
     * 对战信令(实时对战输入级同步):1v1 即「转发给对端」——按 matchId 找对局,
     * 发给除发送者外的另一方(host/guest);不在线不补发,由双方本地确定性模拟兜底。
     */
    private void onGameSignal(WebSocketSession session, Long familyId, Long userId, Map<?, ?> data) {
        Long matchId = data.get("matchId") instanceof Number n ? n.longValue() : null;
        String event = str(data.get("event"));
        if (matchId == null || event == null || event.isBlank()) {
            replyError(session, "BAD_SIGNAL");
            return;
        }
        com.ihomy.entity.GameMatch m = matchService.participantMatch(userId, familyId, matchId);
        if (m == null) {
            replyError(session, "BAD_SIGNAL");
            return;
        }
        Long other = userId.equals(m.getHostId()) ? m.getGuestId() : m.getHostId();
        if (other == null) {
            return;
        }
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        data.forEach((k, v) -> out.put(String.valueOf(k), v));
        out.put("from", userId);
        sendToUser(other, "game-signal", out);
    }

    /** 广播给同房间全部在线会话 */
    private void broadcast(Long familyId, Map<String, Object> envelope) {
        Set<WebSocketSession> room = rooms.get(familyId);
        if (room == null) {
            return;
        }
        TextMessage msg = new TextMessage(toJson(envelope));
        for (WebSocketSession s : room) {
            if (s.isOpen()) {
                sendQuietly(s, msg);
            }
        }
    }

    /** REST 侧(对战邀请卡/结果卡等)复用同一 envelope 推送同房间在线成员 */
    public void broadcastMessage(Long familyId, Map<String, Object> view) {
        broadcast(familyId, envelope("message", view));
    }

    /** 单会话投递:IO 失败只记日志不中断循环(广播/定向共用,死会话由容器异步关闭回调摘除) */
    private void sendQuietly(WebSocketSession s, TextMessage msg) {
        try {
            s.sendMessage(msg);
        } catch (Exception e) {
            log.warn("WS 投递失败 session={}", s.getId(), e);
        }
    }

    /**
     * 定向投递:按 userId 找全部在线会话(P3 通话/P4 对战信令用),返回送达的会话数。
     * 返回 0 表示目标不在线 —— 上层据此走离线逻辑(如落 CALL MISSED 消息),不排队补发。
     */
    public int sendToUser(Long userId, String type, Map<String, Object> data) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return 0;
        }
        TextMessage msg = new TextMessage(toJson(envelope(type, data)));
        int sent = 0;
        for (WebSocketSession s : sessions) {
            if (s.isOpen()) {
                sendQuietly(s, msg);
                sent++;
            }
        }
        return sent;
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        removeFrom(rooms.get(familyOf(session)), session);
        removeFrom(userSessions.get(userIdOf(session)), session);
    }

    /** 从房间/定向索引摘除会话;集合允许留空不删键 —— 删键会与 computeIfAbsent 竞争,可能把并发中的新会话整组孤儿化(空集合内存可忽略) */
    private void removeFrom(Set<WebSocketSession> set, WebSocketSession session) {
        if (set != null) {
            set.remove(session);
        }
    }

    private Map<String, Object> envelope(String type, Map<String, Object> data) {
        Map<String, Object> e = new java.util.LinkedHashMap<>();
        e.put("type", type);
        e.put("data", data);
        return e;
    }

    private void reply(WebSocketSession session, Map<String, Object> envelope) {
        try {
            session.sendMessage(new TextMessage(toJson(envelope)));
        } catch (Exception e) {
            log.warn("WS 回包失败", e);
        }
    }

    /** 协议错误下行:{"type":"error","data":{"reason":"…"}};reason 为协议错误码,不写实现细节 */
    private void replyError(WebSocketSession session, String reason) {
        reply(session, envelope("error", Map.of("reason", reason == null ? "BAD_FORMAT" : reason)));
    }

    private String toJson(Map<String, Object> envelope) {
        return objectMapper.writeValueAsString(envelope);
    }

    /** WS 消息内容截断(access 日志防刷屏,完整内容在 family_chat_message 表) */
    private String abbreviate(String payload) {
        return payload != null && payload.length() > 200 ? payload.substring(0, 200) + "...(truncated)" : payload;
    }

    private Long familyOf(WebSocketSession s) {
        Object v = s.getAttributes().get("familyId");
        return v instanceof Number n ? n.longValue() : null;
    }

    private Long userIdOf(WebSocketSession s) {
        Object v = s.getAttributes().get("userId");
        return v instanceof Number n ? n.longValue() : null;
    }

    private String str(Object v) {
        return v == null ? null : v.toString();
    }

    private void closeSilently(WebSocketSession s) {
        try {
            s.close(CloseStatus.POLICY_VIOLATION);
        } catch (Exception ignored) {
        }
    }
}
