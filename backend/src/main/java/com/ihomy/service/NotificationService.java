package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ihomy.entity.Notification;
import com.ihomy.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 站内通知业务:评论/回复/家庭申请等场景写入通知,
 * 支持列表/未读数/单条与全部已读。
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;

    /** 创建通知,receiverId 为空(如无作者)时忽略 */
    public void create(Long receiverId, String type, String content, Long sourceId, String contentType, Long contentId) {
        if (receiverId == null) return;
        Notification n = new Notification();
        n.setReceiverId(receiverId);
        n.setType(type);
        n.setContent(content);
        n.setSourceId(sourceId);
        n.setContentType(contentType);
        n.setContentId(contentId);
        n.setIsRead(0);
        notificationMapper.insert(n);
    }

    /** 最近 50 条通知(倒序) */
    public List<Notification> list(Long receiverId) {
        LambdaQueryWrapper<Notification> qw = new LambdaQueryWrapper<>();
        qw.eq(Notification::getReceiverId, receiverId)
          .orderByDesc(Notification::getCreatedAt).last("LIMIT 50");
        return notificationMapper.selectList(qw);
    }

    /** 未读条数(铃铛角标) */
    public long unreadCount(Long receiverId) {
        LambdaQueryWrapper<Notification> qw = new LambdaQueryWrapper<>();
        qw.eq(Notification::getReceiverId, receiverId).eq(Notification::getIsRead, 0);
        return notificationMapper.selectCount(qw);
    }

    /** 单条标记已读(校验归属) */
    public void markRead(Long id, Long receiverId) {
        LambdaUpdateWrapper<Notification> uw = new LambdaUpdateWrapper<>();
        uw.eq(Notification::getId, id).eq(Notification::getReceiverId, receiverId)
          .set(Notification::getIsRead, 1);
        notificationMapper.update(null, uw);
    }

    /** 全部标记已读 */
    public void markAllRead(Long receiverId) {
        LambdaUpdateWrapper<Notification> uw = new LambdaUpdateWrapper<>();
        uw.eq(Notification::getReceiverId, receiverId)
          .set(Notification::getIsRead, 1);
        notificationMapper.update(null, uw);
    }

    /** 实体转前端视图结构 */
    public Map<String, Object> toView(Notification n) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", n.getId());
        m.put("type", n.getType());
        m.put("content", n.getContent());
        m.put("contentType", n.getContentType());
        m.put("contentId", n.getContentId());
        m.put("isRead", n.getIsRead());
        m.put("createdAt", n.getCreatedAt());
        return m;
    }
}