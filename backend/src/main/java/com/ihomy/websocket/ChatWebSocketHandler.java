package com.ihomy.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ihomy.entity.ChatMessage;
import com.ihomy.mapper.ChatMessageMapper;
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
 * 聊天室 WebSocket 处理器:
 * 按 family_id 分房间,收到文本即落库并向同房间所有在线会话广播。
 * 低并发家庭场景内存房间表足够,不引入 Redis 广播。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ChatMessageMapper messageMapper;
    private final ObjectMapper objectMapper;

    /** 房间表:familyId -> 在线会话集合(会话自带 userId/familyId 属性) */
    private final Map<Long, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long familyId = familyOf(session);
        if (familyId == null) {
            closeSilently(session);
            return;
        }
        rooms.computeIfAbsent(familyId, k -> ConcurrentHashMap.newKeySet()).add(session);
        log.info("聊天连接建立 family={} sessions={}", familyId, rooms.get(familyId).size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long familyId = familyOf(session);
        Long userId = userIdOf(session);
        if (familyId == null || userId == null) {
            return;
        }
        // 消息负载形如 {"content":"..."}:落库后广播给同房间全部在线会话
        Map<?, ?> payload = objectMapper.readValue(message.getPayload(), Map.class);
        String content = payload.get("content") == null ? "" : payload.get("content").toString();
        if (content.isBlank() || content.length() > 2000) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        ChatMessage m = new ChatMessage();
        m.setFamilyId(familyId);
        m.setSenderId(userId);
        m.setContent(content);
        messageMapper.insert(m);

        Map<String, Object> out = toJson(m, userId);
        Set<WebSocketSession> room = rooms.get(familyId);
        if (room != null) {
            TextMessage broadcast = new TextMessage(objectMapper.writeValueAsString(out));
            for (WebSocketSession s : room) {
                if (s.isOpen()) {
                    s.sendMessage(broadcast);
                }
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long familyId = familyOf(session);
        if (familyId == null) {
            return;
        }
        Set<WebSocketSession> room = rooms.get(familyId);
        if (room != null) {
            room.remove(session);
            if (room.isEmpty()) {
                rooms.remove(familyId);
            }
        }
    }

    /** 返回给客户端的消息结构(带发送人) */
    private Map<String, Object> toJson(ChatMessage m, Long senderId) {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("id", m.getId());
        data.put("familyId", m.getFamilyId());
        data.put("senderId", senderId);
        data.put("content", m.getContent());
        data.put("createdAt", m.getCreatedAt());
        Map<String, Object> envelope = new java.util.LinkedHashMap<>();
        envelope.put("type", "message");
        envelope.put("data", data);
        return envelope;
    }

    private Long familyOf(WebSocketSession s) {
        Object v = s.getAttributes().get("familyId");
        return v instanceof Number n ? n.longValue() : null;
    }

    private Long userIdOf(WebSocketSession s) {
        Object v = s.getAttributes().get("userId");
        return v instanceof Number n ? n.longValue() : null;
    }

    private void closeSilently(WebSocketSession s) {
        try {
            s.close(CloseStatus.POLICY_VIOLATION);
        } catch (Exception ignored) {
        }
    }
}