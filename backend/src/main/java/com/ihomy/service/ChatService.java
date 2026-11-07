package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.entity.ChatMessage;
import com.ihomy.entity.ChatRead;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.ChatMessageMapper;
import com.ihomy.mapper.ChatReadMapper;
import com.ihomy.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 聊天室业务:历史消息分页(含发送人昵称)、未读数、已读游标、消息发送(分类型负载校验)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageMapper messageMapper;
    private final ChatReadMapper readMapper;
    private final SysUserMapper sysUserMapper;
    private final tools.jackson.databind.ObjectMapper objectMapper;

    /** 站内对象卡可引用的对象类型(P1 口径,与前端 utils/chatMsg.js 一致) */
    private static final java.util.Set<String> CARD_OBJ_TYPES =
            java.util.Set.of("ALBUM", "PHOTO", "VIDEO", "BLOG", "DIARY", "BOOK", "WISH");

    /** 取某家庭最近 limit 条消息(倒序取再翻正),附带发送人昵称 */
    public List<Map<String, Object>> history(Long familyId, Long beforeId, int limit) {
        List<ChatMessage> list = messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getFamilyId, familyId)
                .lt(beforeId != null && beforeId > 0, ChatMessage::getId, beforeId)
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 200)));
        // 倒序查出,翻转为时间正序返回
        java.util.Collections.reverse(list);

        Map<Long, String> names = fuzzyNames(list);
        List<Map<String, Object>> result = new ArrayList<>();
        for (ChatMessage m : list) {
            result.add(toView(m, names));
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
        result.put("lastMsg", last == null ? null : toView(last, fuzzyNames(List.of(last))));
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

    /** 单条消息的统一视图(WS 广播与 REST 历史同构,带发送人昵称) */
    public Map<String, Object> view(ChatMessage m) {
        return toView(m, fuzzyNames(List.of(m)));
    }

    private long lastMsgId(Long familyId) {
        ChatMessage last = messageMapper.selectOne(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getFamilyId, familyId)
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT 1"));
        return last == null ? 0 : last.getId();
    }

    /** 批查发送人昵称(避免每条消息一次查询) */
    private Map<Long, String> fuzzyNames(List<ChatMessage> messages) {
        if (messages.isEmpty()) {
            return java.util.Map.of();
        }
        List<Long> ids = messages.stream().map(ChatMessage::getSenderId).distinct().collect(Collectors.toList());
        return sysUserMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getNickname));
    }

    private Map<String, Object> toView(ChatMessage m, Map<Long, String> names) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", m.getId());
        v.put("senderId", m.getSenderId());
        v.put("senderName", names.getOrDefault(m.getSenderId(), "未知成员"));
        v.put("content", m.getContent());
        v.put("msgType", m.getMsgType());
        v.put("payload", m.getPayload());
        v.put("createdAt", m.getCreatedAt());
        return v;
    }
}