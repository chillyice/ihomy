package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.common.UserNames;
import com.ihomy.dto.CommentDTO;
import com.ihomy.entity.Blog;
import com.ihomy.entity.Comment;
import com.ihomy.entity.Diary;
import com.ihomy.entity.Photo;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.BlogMapper;
import com.ihomy.mapper.CommentMapper;
import com.ihomy.mapper.DiaryMapper;
import com.ihomy.mapper.PhotoMapper;
import com.ihomy.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 评论业务:博客/日记/照片统一评论,支持 parentId 回复树;
 * 严格同家庭校验,评论/回复自动产生站内通知。
 */
@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentMapper commentMapper;
    private final SysUserMapper sysUserMapper;
    private final BlogMapper blogMapper;
    private final DiaryMapper diaryMapper;
    private final PhotoMapper photoMapper;
    private final NotificationService notificationService;

    /** 评论列表:先按时间排好,再按 parentId 组装成 根评论→回复 的树 */
    public List<Map<String, Object>> list(String contentType, Long contentId) {
        LambdaQueryWrapper<Comment> qw = new LambdaQueryWrapper<>();
        qw.eq(Comment::getContentType, contentType)
          .eq(Comment::getContentId, contentId)
          .orderByAsc(Comment::getCreatedAt);
        List<Comment> all = commentMapper.selectList(qw);
        Map<Long, Map<String, Object>> byId = new LinkedHashMap<>();
        List<Map<String, Object>> roots = new ArrayList<>();
        for (Comment c : all) {
            Map<String, Object> m = toView(c);
            byId.put(c.getId(), m);
            if (c.getParentId() == null) {
                roots.add(m);
            }
        }
        for (Comment c : all) {
            if (c.getParentId() == null) continue;
            Map<String, Object> m = byId.get(c.getId());
            Map<String, Object> parent = byId.get(c.getParentId());
            if (parent == null) {
                roots.add(m);
            } else {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children = (List<Map<String, Object>>) parent.computeIfAbsent("replies", k -> new ArrayList<Map<String, Object>>());
                children.add(m);
            }
        }
        return roots;
    }

    /** 发表评论:校验目标内容同家庭后落库,并通知被回复者/内容作者 */
    public Comment create(SysUser user, CommentDTO dto) {
        if (dto.getContent() == null || dto.getContent().isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST);
        }
        Long targetAuthorId = validateTarget(dto.getContentType(), dto.getContentId(), user.getFamilyId());
        Comment comment = new Comment();
        comment.setContentType(dto.getContentType());
        comment.setContentId(dto.getContentId());
        comment.setParentId(dto.getParentId());
        comment.setReplyToUserId(dto.getReplyToUserId());
        comment.setContent(dto.getContent());
        comment.setAuthorId(user.getId());
        comment.setFamilyId(user.getFamilyId());
        commentMapper.insert(comment);
        notifyTo(dto, user.getId(), comment.getId(), targetAuthorId);
        return comment;
    }

    /** 删除评论:作者本人或家长;父评论删除后子回复会独立为根 */
    public void delete(Long id, SysUser user, boolean isOwner) {
        Comment c = commentMapper.selectById(id);
        if (c == null) throw new BizException(ResultCode.NOT_FOUND);
        if (!isOwner && !c.getAuthorId().equals(user.getId())) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        commentMapper.deleteById(id);
    }

    /** 校验目标内容存在且与评论者同家庭,返回内容作者 ID(跨家庭一律 404) */
    private Long validateTarget(String contentType, Long contentId, Long familyId) {
        if (contentId == null) return null;
        switch (contentType == null ? "" : contentType) {
            case "blog" -> {
                Blog b = blogMapper.selectById(contentId);
                if (b == null || !familyId.equals(b.getFamilyId())) throw new BizException(ResultCode.NOT_FOUND);
                return b.getAuthorId();
            }
            case "diary" -> {
                Diary d = diaryMapper.selectById(contentId);
                if (d == null || !familyId.equals(d.getFamilyId())) throw new BizException(ResultCode.NOT_FOUND);
                return d.getAuthorId();
            }
            case "photo" -> {
                Photo p = photoMapper.selectById(contentId);
                if (p == null || !familyId.equals(p.getFamilyId())) throw new BizException(ResultCode.NOT_FOUND);
                return p.getAuthorId();
            }
            default -> throw new BizException(ResultCode.BAD_REQUEST);
        }
    }

    /** 发送站内通知:回复优先通知被回复人,否则通知内容作者(自己评论自己不通知) */
    private void notifyTo(CommentDTO dto, Long currentUserId, Long commentId, Long targetAuthorId) {
        if (targetAuthorId != null && targetAuthorId.equals(currentUserId)) {
            return;
        }
        String type = dto.getParentId() != null ? "reply" : "comment";
        String snippet = truncate(dto.getContent(), 50);
        if (dto.getReplyToUserId() != null && !dto.getReplyToUserId().equals(currentUserId)) {
            notificationService.create(dto.getReplyToUserId(), "reply", "回复了你: " + snippet, commentId, dto.getContentType(), dto.getContentId());
        } else if (targetAuthorId != null) {
            notificationService.create(targetAuthorId, type, (dto.getParentId() != null ? "回复了你的内容: " : "评论了你的内容: ") + snippet,
                    commentId, dto.getContentType(), dto.getContentId());
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    private String resolveName(Long userId) {
        if (userId == null) return null;
        return UserNames.of(sysUserMapper.selectById(userId));
    }

    /** 评论转展示结构:补作者昵称与被回复人昵称 */
    private Map<String, Object> toView(Comment c) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", c.getId());
        m.put("content", c.getContent());
        m.put("authorId", c.getAuthorId());
        m.put("authorName", resolveName(c.getAuthorId()));
        m.put("replyToUserId", c.getReplyToUserId());
        m.put("replyToName", c.getReplyToUserId() == null ? null : resolveName(c.getReplyToUserId()));
        m.put("createdAt", c.getCreatedAt());
        return m;
    }
}