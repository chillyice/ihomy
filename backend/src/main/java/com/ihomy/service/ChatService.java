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
 * 聊天室业务:历史消息分页(含发送人昵称)、未读数、已读游标。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageMapper messageMapper;
    private final ChatReadMapper readMapper;
    private final SysUserMapper sysUserMapper;

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
        if (!com.ihomy.common.ChatMsgType.isKnown(type)) {
            throw new IllegalArgumentException("BAD_TYPE");
        }
        if (content == null || content.isBlank() || content.length() > 2000) {
            throw new IllegalArgumentException("BAD_CONTENT");
        }
        if (com.ihomy.common.ChatMsgType.TEXT.equals(type)) {
            // 文本消息不带结构化负载
            if (payload != null && !payload.isBlank()) {
                throw new IllegalArgumentException("BAD_PAYLOAD");
            }
        } else {
            // 非文本类型 P0 暂未放开(P1 起按类型逐一启用 payload 校验)
            throw new IllegalArgumentException("TYPE_NOT_SUPPORTED");
        }
        if (payload != null && payload.length() > PAYLOAD_MAX_BYTES) {
            throw new IllegalArgumentException("BAD_PAYLOAD");
        }
        ChatMessage m = new ChatMessage();
        m.setFamilyId(familyId);
        m.setSenderId(senderId);
        m.setContent(content);
        m.setMsgType(type);
        m.setPayload(payload);
        // 应用侧写入发送时间:insert 后实体直接可广播(依赖 DB DEFAULT 回填的话实体里恒为 null)
        m.setCreatedAt(java.time.LocalDateTime.now());
        messageMapper.insert(m);
        return view(m);
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