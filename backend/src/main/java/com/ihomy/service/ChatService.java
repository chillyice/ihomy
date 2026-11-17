package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.entity.ChatDmMessage;
import com.ihomy.entity.ChatMessage;
import com.ihomy.entity.ChatRead;
import com.ihomy.entity.SysUser;
import com.ihomy.entity.SysUserRole;
import com.ihomy.mapper.ChatDmMessageMapper;
import com.ihomy.mapper.ChatMessageMapper;
import com.ihomy.mapper.ChatReadMapper;
import com.ihomy.mapper.FamilyMapper;
import com.ihomy.mapper.SysUserMapper;
import com.ihomy.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 聊天室业务:历史消息分页(含发送人昵称)、未读数、已读游标、消息发送(分类型负载校验)、通话记录落库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageMapper messageMapper;
    private final ChatDmMessageMapper dmMapper;
    private final ChatReadMapper readMapper;
    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final FamilyMapper familyMapper;
    private final CircleService circleService;
    private final tools.jackson.databind.ObjectMapper objectMapper;
    private final org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    /** 站内对象卡可引用的对象类型(P1 口径,与前端 utils/chatMsg.js 一致) */
    private static final java.util.Set<String> CARD_OBJ_TYPES =
            java.util.Set.of("ALBUM", "PHOTO", "VIDEO", "BLOG", "DIARY", "BOOK", "WISH", "MATCH");

    /** 取某家庭最近 limit 条消息(倒序取再翻正),附带发送人昵称 */
    public List<Map<String, Object>> history(Long familyId, Long beforeId, int limit) {
        List<ChatMessage> list = messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getFamilyId, familyId)
                .lt(beforeId != null && beforeId > 0, ChatMessage::getId, beforeId)
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 200)));
        // 倒序查出,翻转为时间正序返回
        java.util.Collections.reverse(list);

        Map<Long, Sender> senders = messageSenders(list);
        Map<Long, String> familyNames = crossFamilyNames(list, senders);
        List<Map<String, Object>> result = new ArrayList<>();
        for (ChatMessage m : list) {
            result.add(toView(m, senders, familyNames));
        }
        return result;
    }

    /** 当前用户在该家庭的未读数与最新消息摘要 */
    public Map<String, Object> unread(Long userId, Long familyId) {
        ChatMessage last = messageMapper.selectOne(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getFamilyId, familyId)
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT 1"));
        long lastId = last == null ? 0 : last.getId();
        ChatRead read = readMapper.selectOne(new LambdaQueryWrapper<ChatRead>()
                .eq(ChatRead::getUserId, userId).eq(ChatRead::getFamilyId, familyId));
        long readId = read == null ? 0 : read.getLastReadMsgId();
        // 未读数按 ID 差值估算(依赖消息 ID 递增且不被清理);后退到 0,避免已读位点超前时出现负数
        long count = Math.max(0, lastId - readId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("unread", count);
        result.put("lastMsgId", lastId);
        result.put("lastMsg", last == null ? null : view(last));
        return result;
    }

    /** 记录已读游标:仅当消息 ID 大于当前值才推进(防止读旧消息倒灌) */
    public void markRead(Long userId, Long familyId, Long msgId) {
        ChatRead read = readMapper.selectOne(new LambdaQueryWrapper<ChatRead>()
                .eq(ChatRead::getUserId, userId).eq(ChatRead::getFamilyId, familyId));
        long target = msgId == null ? (lastMsgId(familyId)) : msgId;
        if (read == null) {
            read = new ChatRead();
            read.setUserId(userId);
            read.setFamilyId(familyId);
            read.setLastReadMsgId(target);
            readMapper.insert(read);
        } else if (target > read.getLastReadMsgId()) {
            read.setLastReadMsgId(target);
            readMapper.updateById(read);
        }
    }

    // ---------- 亲友圈家庭房间(2026-11:圈内家庭可读可发言) ----------

    /**
     * 房间准入:目标为空=本家;其他家庭必须与当前家庭在同一个活跃亲友圈里。
     * 违规一律 NOT_FOUND(不泄露家庭是否存在),返回最终要读写的家庭 id。
     */
    public Long requireRoomAccess(Long userId, Long currentFamilyId, Long requestedFamilyId) {
        if (currentFamilyId == null || userId == null || !userFamilyIds(userId).contains(currentFamilyId)) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        if (requestedFamilyId == null || requestedFamilyId.equals(currentFamilyId)) {
            return currentFamilyId;
        }
        if (!circleService.connectedFamilyIds(currentFamilyId).contains(requestedFamilyId)) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return requestedFamilyId;
    }

    /** 会话所在家庭能否读目标家庭房间(本家或同圈);WS 广播按此筛在线会话 */
    public boolean canAccessRoom(Long familyId, Long targetFamilyId) {
        if (familyId == null || targetFamilyId == null) return false;
        return familyId.equals(targetFamilyId) || circleService.connectedFamilyIds(familyId).contains(targetFamilyId);
    }

    /** 用户有角色绑定的全部家庭(多家庭成员口径,替代只看主家庭) */
    private Set<Long> userFamilyIds(Long userId) {
        return sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId)).stream()
                .map(SysUserRole::getFamilyId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 我可见的聊天室(本家 + 活跃亲友圈里其他家庭),带各自未读数与最新消息 id。
     * 三次批量查询(家庭概要 / 各房最新 id / 本人已读游标),不做逐房循环。
     */
    public List<Map<String, Object>> rooms(Long userId, Long currentFamilyId) {
        List<Map<String, Object>> families = circleService.connectedFamilies(currentFamilyId);
        if (families.isEmpty()) return new ArrayList<>();
        List<Long> ids = families.stream().map(m -> ((Number) m.get("familyId")).longValue()).toList();
        Map<Long, Long> lastIds = new LinkedHashMap<>();
        for (Map<String, Object> row : messageMapper.selectLastIds(ids)) {
            Object fid = row.get("familyId");
            Object lid = row.get("lastMsgId");
            if (fid instanceof Number f && lid instanceof Number l) {
                lastIds.put(f.longValue(), l.longValue());
            }
        }
        Map<Long, Long> readIds = new LinkedHashMap<>();
        for (ChatRead r : readMapper.selectList(new LambdaQueryWrapper<ChatRead>()
                .eq(ChatRead::getUserId, userId)
                .in(ChatRead::getFamilyId, ids))) {
            readIds.put(r.getFamilyId(), r.getLastReadMsgId() == null ? 0L : r.getLastReadMsgId());
        }
        List<Map<String, Object>> out = new ArrayList<>(families.size());
        for (Map<String, Object> f : families) {
            long familyId = ((Number) f.get("familyId")).longValue();
            long lastId = lastIds.getOrDefault(familyId, 0L);
            long readId = readIds.getOrDefault(familyId, 0L);
            Map<String, Object> v = new LinkedHashMap<>(f);
            v.put("unread", Math.max(0, lastId - readId));
            v.put("lastMsgId", lastId);
            out.add(v);
        }
        return out;
    }

    /** payload 长度上限:发送侧与 WS 侧同一口径,超限拒绝 */
    private static final int PAYLOAD_MAX_BYTES = 4096;

    /**
     * 发送一条消息(WS 实时链路):校验类型与负载 → 落库 → 返回统一视图。
     * msgType 空 = TEXT;非法类型/负载抛 IllegalArgumentException,文案为协议错误码
     * (WS 层转 error envelope,不走 BizException:GlobalExceptionHandler 不覆盖 WS 线程)。
     */
    public Map<String, Object> sendMessage(Long familyId, Long senderId, String msgType, String content, String payload) {
        String type = msgType == null || msgType.isBlank() ? com.ihomy.common.ChatMsgType.TEXT : msgType;
        // CALL 只由通话流程(挂断/超时等)落库,用户不能直接发
        if (!com.ihomy.common.ChatMsgType.isKnown(type) || com.ihomy.common.ChatMsgType.CALL.equals(type)) {
            throw new IllegalArgumentException("BAD_TYPE");
        }
        if (payload != null && payload.length() > PAYLOAD_MAX_BYTES) {
            throw new IllegalArgumentException("BAD_PAYLOAD");
        }
        boolean text = com.ihomy.common.ChatMsgType.TEXT.equals(type);
        if (text) {
            // 文本消息:内容必填、不带结构化负载
            if (content == null || content.isBlank() || content.length() > 2000) {
                throw new IllegalArgumentException("BAD_CONTENT");
            }
            if (payload != null && !payload.isBlank()) {
                throw new IllegalArgumentException("BAD_PAYLOAD");
            }
        } else {
            validateRichPayload(type, content, payload);
        }
        ChatMessage m = new ChatMessage();
        m.setFamilyId(familyId);
        m.setSenderId(senderId);
        m.setContent(content == null ? "" : content);
        m.setMsgType(type);
        m.setPayload(payload);
        // 应用侧写入发送时间:insert 后实体直接可广播(依赖 DB DEFAULT 回填的话实体里恒为 null)
        m.setCreatedAt(java.time.LocalDateTime.now());
        messageMapper.insert(m);
        return view(m);
    }

    /**
     * 富内容消息的负载校验(P1 起按类型放开):
     * payload 必须是 JSON 对象且字段符合该类型的 schema,违规一律 BAD_PAYLOAD。
     * 各类型 schema(与前端 utils/chatMsg.js 同一口径):
     * IMAGE  {url 必填 /files/ 开头, width?, height?};content=附言可空
     * LINK   {title?, description?, image?, siteName?};content=URL 必填 http(s) 开头
     * CARD   {objType 必填 ALBUM/PHOTO/VIDEO/BLOG/DIARY/BOOK/WISH, objId 数字, url 必填 / 开头, sub?, cover?};content=标题必填
     * GAME_INVITE {gameCode 必填且属于 H5 小游戏全集};content=游戏名必填
     */
    private void validateRichPayload(String type, String content, String payload) {
        boolean hasPayload = payload != null && !payload.isBlank();
        Map<?, ?> p = Map.of();
        if (hasPayload) {
            try {
                Object node = objectMapper.readValue(payload, Object.class);
                if (!(node instanceof Map)) {
                    throw new IllegalArgumentException("BAD_PAYLOAD");
                }
                p = (Map<?, ?>) node;
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalArgumentException("BAD_PAYLOAD");
            }
        }
        switch (type) {
            case com.ihomy.common.ChatMsgType.IMAGE -> {
                String url = strField(p.get("url"));
                if (url == null || !url.startsWith("/files/") || url.length() > 1000) {
                    throw new IllegalArgumentException("BAD_PAYLOAD");
                }
                if (p.get("width") != null && !(p.get("width") instanceof Number)) {
                    throw new IllegalArgumentException("BAD_PAYLOAD");
                }
                if (p.get("height") != null && !(p.get("height") instanceof Number)) {
                    throw new IllegalArgumentException("BAD_PAYLOAD");
                }
                if (content != null && content.length() > 2000) {
                    throw new IllegalArgumentException("BAD_CONTENT");
                }
            }
            case com.ihomy.common.ChatMsgType.LINK -> {
                if (content == null || content.isBlank() || content.length() > 2000
                        || !(content.startsWith("http://") || content.startsWith("https://"))) {
                    throw new IllegalArgumentException("BAD_CONTENT");
                }
                if (hasPayload) {
                    // 预览四字段全可缺(抓取失败就是纯链接),存在则限长
                    if (overLen(strField(p.get("title")), 200) || overLen(strField(p.get("description")), 500)
                            || overLen(strField(p.get("image")), 1000) || overLen(strField(p.get("siteName")), 100)) {
                        throw new IllegalArgumentException("BAD_PAYLOAD");
                    }
                }
            }
            case com.ihomy.common.ChatMsgType.CARD -> {
                String objType = strField(p.get("objType"));
                String url = strField(p.get("url"));
                // objId 放宽为数字或短字符串(放映厅条目是 Jellyfin 十六进制 id),只作展示/追溯,url 才是导航
                boolean objIdOk = p.get("objId") instanceof Number n && n.longValue() >= 0
                        || p.get("objId") instanceof String s && !s.isBlank() && s.length() <= 64;
                if (objType == null || !CARD_OBJ_TYPES.contains(objType) || !objIdOk
                        || url == null || !url.startsWith("/") || url.length() > 200
                        || content == null || content.isBlank() || content.length() > 200
                        || overLen(strField(p.get("sub")), 200) || overLen(strField(p.get("cover")), 1000)) {
                    throw new IllegalArgumentException("BAD_PAYLOAD");
                }
            }
            case com.ihomy.common.ChatMsgType.GAME_INVITE -> {
                String gameCode = strField(p.get("gameCode"));
                if (gameCode == null || !com.ihomy.common.GameConst.H5_GAME_CODES.contains(gameCode)
                        || content == null || content.isBlank() || content.length() > 100) {
                    throw new IllegalArgumentException("BAD_PAYLOAD");
                }
            }
            default -> throw new IllegalArgumentException("BAD_TYPE");
        }
    }

    private boolean overLen(String v, int max) {
        return v != null && v.length() > max;
    }

    private String strField(Object v) {
        return v == null ? null : v.toString();
    }

    /** 通话/对战的家庭隔离:双方必须有共同的家庭角色绑定(OPS/无家庭用户不参与) */
    public boolean sameFamily(Long userId, Long targetUserId) {
        if (userId == null || targetUserId == null || userId.equals(targetUserId)) {
            return false;
        }
        Set<Long> mine = userFamilyIds(userId);
        if (mine.isEmpty()) return false;
        Set<Long> peers = userFamilyIds(targetUserId);
        for (Long id : mine) {
            if (peers.contains(id)) return true;
        }
        return false;
    }

    /** 单条消息的统一视图(WS 广播与 REST 历史同构,带发送人昵称) */
    public Map<String, Object> view(ChatMessage m) {
        Map<Long, Sender> senders = messageSenders(List.of(m));
        return toView(m, senders, crossFamilyNames(List.of(m), senders));
    }

    /**
     * 通话结束落 CALL 记录(聊天流内一张卡,不另建表;计划 §4.1):
     * 同一 callId 只落一条(Redis SETNX 48h 兜底防信令重发重复落卡)。
     * result 由通话事件映射:completed→ANSWERED / rejected→REJECTED / timeout→MISSED / 其余→CANCELLED。
     */
    public Map<String, Object> writeCallRecord(Long familyId, Long callerId, String media, String result,
                                               Integer durationSec, String callId) {
        try {
            Boolean first = stringRedisTemplate.opsForValue()
                    .setIfAbsent("ihomy:chat:call:" + callId, "1", java.time.Duration.ofHours(48));
            if (!Boolean.TRUE.equals(first)) {
                return null;
            }
        } catch (Exception e) {
            log.warn("通话记录防重检查失败,按首次处理 callId={}", callId, e);
        }
        ChatMessage m = new ChatMessage();
        m.setFamilyId(familyId);
        m.setSenderId(callerId);
        m.setContent("");
        m.setMsgType(com.ihomy.common.ChatMsgType.CALL);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("media", media);
        payload.put("result", result);
        payload.put("duration", durationSec == null || durationSec < 0 ? 0 : durationSec);
        try {
            m.setPayload(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            m.setPayload(null);
        }
        m.setCreatedAt(java.time.LocalDateTime.now());
        messageMapper.insert(m);
        return view(m);
    }

    // ---------- 私聊(DM) ----------

    /**
     * 私聊关系校验:双方有共同的家庭角色绑定(同家庭),或双方家庭在同一活跃亲友圈里连通。
     * 不满足抛 IllegalArgumentException("BAD_TARGET"),WS 层转 error envelope,REST 层转友好提示。
     */
    public void requireCanChat(Long userId, Long peerId) {
        if (userId == null || peerId == null || userId.equals(peerId)) {
            throw new IllegalArgumentException("BAD_TARGET");
        }
        Set<Long> mine = userFamilyIds(userId);
        Set<Long> peers = userFamilyIds(peerId);
        if (mine.isEmpty() || peers.isEmpty()) {
            throw new IllegalArgumentException("BAD_TARGET");
        }
        for (Long f : mine) {
            if (peers.contains(f)) return;
        }
        // 跨家庭:任一边的家庭与本边家庭在同一亲友圈即可(同圈可见成员名单、可私聊)
        for (Long f : mine) {
            Set<Long> connected = circleService.connectedFamilyIds(f);
            for (Long p : peers) {
                if (connected.contains(p)) return;
            }
        }
        throw new IllegalArgumentException("BAD_TARGET");
    }

    /** 私聊关系是否成立(requireCanChat 的布尔版,批量列表过滤用) */
    public boolean canChat(Long userId, Long peerId) {
        try {
            requireCanChat(userId, peerId);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 发送私聊消息:类型/负载校验与家庭消息同一套口径,另校验私聊关系。
     * WS 与 REST 共用;校验失败抛 IllegalArgumentException(协议错误码),由调用方转换。
     */
    public Map<String, Object> sendDm(Long senderId, Long recipientId, String msgType, String content, String payload) {
        requireCanChat(senderId, recipientId);
        String type = msgType == null || msgType.isBlank() ? com.ihomy.common.ChatMsgType.TEXT : msgType;
        if (!com.ihomy.common.ChatMsgType.isKnown(type) || com.ihomy.common.ChatMsgType.CALL.equals(type)) {
            throw new IllegalArgumentException("BAD_TYPE");
        }
        if (payload != null && payload.length() > PAYLOAD_MAX_BYTES) {
            throw new IllegalArgumentException("BAD_PAYLOAD");
        }
        if (com.ihomy.common.ChatMsgType.TEXT.equals(type)) {
            if (content == null || content.isBlank() || content.length() > 2000) {
                throw new IllegalArgumentException("BAD_CONTENT");
            }
            if (payload != null && !payload.isBlank()) {
                throw new IllegalArgumentException("BAD_PAYLOAD");
            }
        } else {
            validateRichPayload(type, content, payload);
        }
        ChatDmMessage m = new ChatDmMessage();
        m.setSenderId(senderId);
        m.setRecipientId(recipientId);
        m.setContent(content == null ? "" : content);
        m.setMsgType(type);
        m.setPayload(payload);
        m.setCreatedAt(java.time.LocalDateTime.now());
        dmMapper.insert(m);
        return dmView(m);
    }

    /** 私聊历史(与某人,双向前翻页),视图附 peerId=对方 id 便于前端分桶 */
    public List<Map<String, Object>> dmHistory(Long userId, Long peerId, Long beforeId, int limit) {
        requireCanChat(userId, peerId);
        List<ChatDmMessage> list = dmMapper.selectList(new LambdaQueryWrapper<ChatDmMessage>()
                .and(w -> w.eq(ChatDmMessage::getSenderId, userId).eq(ChatDmMessage::getRecipientId, peerId))
                .or(w -> w.eq(ChatDmMessage::getSenderId, peerId).eq(ChatDmMessage::getRecipientId, userId))
                .lt(beforeId != null && beforeId > 0, ChatDmMessage::getId, beforeId)
                .orderByDesc(ChatDmMessage::getId)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 200)));
        java.util.Collections.reverse(list);
        Map<Long, Sender> senders = dmSenders(list);
        List<Map<String, Object>> out = new ArrayList<>();
        for (ChatDmMessage m : list) {
            Map<String, Object> v = dmView(m, senders);
            v.put("peerId", userId.equals(m.getSenderId()) ? m.getRecipientId() : m.getSenderId());
            out.add(v);
        }
        return out;
    }

    /** 私聊未读:按发送人分组 [{userId,name,unread,lastMsg}](按 read_at 精确计数,单次最多聚合 500 条) */
    public List<Map<String, Object>> dmUnread(Long userId) {
        List<ChatDmMessage> list = dmMapper.selectList(new LambdaQueryWrapper<ChatDmMessage>()
                .eq(ChatDmMessage::getRecipientId, userId)
                .isNull(ChatDmMessage::getReadAt)
                .orderByAsc(ChatDmMessage::getId)
                .last("LIMIT 500"));
        Map<Long, List<ChatDmMessage>> bySender = list.stream()
                .collect(Collectors.groupingBy(ChatDmMessage::getSenderId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, Sender> senders = dmSenders(list);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<Long, List<ChatDmMessage>> e : bySender.entrySet()) {
            List<ChatDmMessage> rows = e.getValue();
            Sender sender = senders.get(e.getKey());
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("userId", e.getKey());
            v.put("name", sender == null || sender.name() == null ? "成员" : sender.name());
            v.put("avatar", sender == null ? null : sender.avatar());
            v.put("unread", rows.size());
            v.put("lastMsg", dmView(rows.get(rows.size() - 1), senders));
            out.add(v);
        }
        return out;
    }

    /** 私聊已读:把某人发给我的未读全部置已读,返回置读条数 */
    public int dmMarkRead(Long userId, Long peerId) {
        return dmMapper.update(null, new LambdaUpdateWrapper<ChatDmMessage>()
                .eq(ChatDmMessage::getRecipientId, userId)
                .eq(ChatDmMessage::getSenderId, peerId)
                .isNull(ChatDmMessage::getReadAt)
                .set(ChatDmMessage::getReadAt, java.time.LocalDateTime.now()));
    }

    /**
     * 「一起玩」房间邀请批量发送:成员走私聊,家庭走对方家庭聊天流(含本家)。
     * 目标必须是同家庭或同亲友圈;没有任何有效目标抛 BizException。
     * 返回 {dmSent, familySent, familyViews(各家庭聊天流已落库的邀请卡视图,调用方负责 WS 下发)}。
     */
    public Map<String, Object> sendRoomInvites(Long senderId, Long senderFamilyId, Map<String, Object> room,
                                               List<Long> memberIds, List<Long> familyIds) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("gameCode", room.get("gameCode"));
        payload.put("roomId", room.get("id"));
        payload.put("mode", room.get("mode"));
        String content = String.valueOf(room.get("gameName"));
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new BizException(ResultCode.BAD_REQUEST, "邀请没发出去,再试一次");
        }
        int dmSent = 0;
        int familySent = 0;
        List<Map<String, Object>> familyViews = new ArrayList<>();
        if (memberIds != null) {
            for (Long peerId : memberIds.stream().filter(java.util.Objects::nonNull).distinct().toList()) {
                sendDm(senderId, peerId, com.ihomy.common.ChatMsgType.GAME_INVITE, content, json);
                dmSent++;
            }
        }
        if (familyIds != null) {
            java.util.Set<Long> connected = circleService.connectedFamilyIds(senderFamilyId);
            for (Long familyId : familyIds.stream().filter(java.util.Objects::nonNull).distinct().toList()) {
                if (!connected.contains(familyId)) {
                    throw new BizException(ResultCode.BAD_REQUEST, "只能发给自己家庭或亲友圈里的家庭");
                }
                Map<String, Object> view = sendMessage(familyId, senderId, com.ihomy.common.ChatMsgType.GAME_INVITE, content, json);
                familyViews.add(Map.of("familyId", familyId, "view", view));
                familySent++;
            }
        }
        if (dmSent + familySent == 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "先选择要邀请的成员或家庭");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dmSent", dmSent);
        out.put("familySent", familySent);
        out.put("familyViews", familyViews);
        return out;
    }

    private long lastMsgId(Long familyId) {
        ChatMessage last = messageMapper.selectOne(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getFamilyId, familyId)
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT 1"));
        return last == null ? 0 : last.getId();
    }

    /** 发送人展示信息(主家庭 + 昵称 + 头像):一次批查回填,避免 N+1 */
    private record Sender(Long familyId, String name, String avatar) {}

    /** 按发送人 id 批查展示信息;昵称缺失回退用户名,再缺回退占位名 */
    private Map<Long, Sender> senders(List<Long> ids) {
        List<Long> distinct = ids.stream().filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        if (distinct.isEmpty()) {
            return Map.of();
        }
        Map<Long, Sender> out = new LinkedHashMap<>();
        for (SysUser u : sysUserMapper.selectBatchIds(distinct)) {
            String name = u.getNickname() != null && !u.getNickname().isBlank() ? u.getNickname() : u.getUsername();
            out.put(u.getId(), new Sender(u.getFamilyId(), name, u.getAvatar()));
        }
        return out;
    }

    /** 跨家庭发言的发送人所属家庭名:只有发送人主家庭≠房间家庭时才下发,前端好标「来自某家」 */
    private Map<Long, String> crossFamilyNames(List<ChatMessage> messages, Map<Long, Sender> senders) {
        Set<Long> ids = new LinkedHashSet<>();
        for (ChatMessage m : messages) {
            Sender s = senders.get(m.getSenderId());
            if (s != null && s.familyId() != null && !s.familyId().equals(m.getFamilyId())) {
                ids.add(s.familyId());
            }
        }
        if (ids.isEmpty()) return Map.of();
        Map<Long, String> names = new LinkedHashMap<>();
        for (com.ihomy.entity.Family f : familyMapper.selectBatchIds(ids)) {
            names.put(f.getId(), f.getName());
        }
        return names;
    }

    /** 消息列表里出现过的发送人 */
    private Map<Long, Sender> messageSenders(List<ChatMessage> messages) {
        return senders(messages.stream().map(ChatMessage::getSenderId).collect(Collectors.toList()));
    }

    /** 私聊消息里出现过的发送人 */
    private Map<Long, Sender> dmSenders(List<ChatDmMessage> messages) {
        return senders(messages.stream().map(ChatDmMessage::getSenderId).collect(Collectors.toList()));
    }

    private Map<String, Object> toView(ChatMessage m, Map<Long, Sender> senders) {
        return toView(m, senders, Map.of());
    }

    private Map<String, Object> toView(ChatMessage m, Map<Long, Sender> senders, Map<Long, String> familyNames) {
        Sender sender = senders.get(m.getSenderId());
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", m.getId());
        v.put("familyId", m.getFamilyId());
        v.put("senderId", m.getSenderId());
        v.put("senderName", sender == null || sender.name() == null ? "未知成员" : sender.name());
        v.put("senderAvatar", sender == null ? null : sender.avatar());
        if (sender != null && sender.familyId() != null && !sender.familyId().equals(m.getFamilyId())) {
            String familyName = familyNames.get(sender.familyId());
            if (familyName != null) v.put("senderFamilyName", familyName);
        }
        v.put("content", m.getContent());
        v.put("msgType", m.getMsgType());
        v.put("payload", m.getPayload());
        v.put("createdAt", m.getCreatedAt());
        return v;
    }

    /** 单条私聊消息的统一视图(WS 下发与 REST 历史同构,带发送人昵称与头像) */
    private Map<String, Object> dmView(ChatDmMessage m) {
        return dmView(m, dmSenders(List.of(m)));
    }

    private Map<String, Object> dmView(ChatDmMessage m, Map<Long, Sender> senders) {
        Sender sender = senders.get(m.getSenderId());
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", m.getId());
        v.put("senderId", m.getSenderId());
        v.put("recipientId", m.getRecipientId());
        v.put("senderName", sender == null || sender.name() == null ? "未知成员" : sender.name());
        v.put("senderAvatar", sender == null ? null : sender.avatar());
        v.put("content", m.getContent());
        v.put("msgType", m.getMsgType());
        v.put("payload", m.getPayload());
        v.put("createdAt", m.getCreatedAt());
        return v;
    }

}