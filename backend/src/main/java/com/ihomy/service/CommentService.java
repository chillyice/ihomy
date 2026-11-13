package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
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
import com.ihomy.service.access.ContentKind;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
    private final AudienceService audienceService;

    /** 评论列表:先校验内容可访问性(同家庭或公开),再按时间排好组装回复树 */
    public List<Map<String, Object>> list(String contentType, Long contentId, Long familyId) {
        checkContentAccessible(contentType, contentId, familyId);
        LambdaQueryWrapper<Comment> qw = new LambdaQueryWrapper<>();
        qw.eq(Comment::getContentType, contentType)
          .eq(Comment::getContentId, contentId)
          .orderByDesc(Comment::getCreatedAt)
          .last("LIMIT 500");
        // ponytail: 单条内容最多载入最近 500 条评论防内存膨胀;取最新再翻回升序组树。
        // 超出上限的旧评论被丢弃,其后续回复因父节点缺失会自动升为根节点
        List<Comment> all = commentMapper.selectList(qw);
        java.util.Collections.reverse(all);

        // 收集所有 userIds(authorId + replyToUserId)批量查用户,避免 N+1
        java.util.Set<Long> userIds = new java.util.HashSet<>();
        for (Comment c : all) {
            if (c.getAuthorId() != null) userIds.add(c.getAuthorId());
            if (c.getReplyToUserId() != null) userIds.add(c.getReplyToUserId());
        }
        Map<Long, SysUser> userMap = batchUsers(userIds);

        Map<Long, Map<String, Object>> byId = new LinkedHashMap<>();
        List<Map<String, Object>> roots = new ArrayList<>();
        for (Comment c : all) {
            Map<String, Object> m = toView(c, userMap);
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

    /** 批量取用户,返回 id→SysUser 映射(空集返空 Map,避免一次全表查) */
    private Map<Long, SysUser> batchUsers(java.util.Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        List<SysUser> users = sysUserMapper.selectBatchIds(ids);
        Map<Long, SysUser> map = new HashMap<>(users.size() * 2);
        for (SysUser u : users) {
            map.put(u.getId(), u);
        }
        return map;
    }

    /** 发表评论:校验目标内容同家庭后落库,并通知被回复者/内容作者 */
    public Comment create(SysUser user, CommentDTO dto) {
        if (dto.getContent() == null || dto.getContent().isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST);
        }
        TargetContent target = validateTarget(dto.getContentType(), dto.getContentId(), user.getFamilyId());
        Comment comment = new Comment();
        comment.setContentType(dto.getContentType());
        comment.setContentId(dto.getContentId());
        comment.setParentId(dto.getParentId());
        comment.setReplyToUserId(dto.getReplyToUserId());
        comment.setContent(dto.getContent());
        comment.setAuthorId(user.getId());
        comment.setFamilyId(user.getFamilyId());
        commentMapper.insert(comment);
        notifyTo(dto, user.getId(), comment.getId(), target);
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

    /** 校验内容可被当前调用方访问(同家庭、公开,或已分享给本家庭),否则 404 */
    private void checkContentAccessible(String contentType, Long contentId, Long familyId) {
        ContentInfo info = loadContent(contentType, contentId);
        if (Objects.equals(familyId, info.familyId())) return;
        if (DictConst.VIS_PUBLIC.equals(info.visibility())) return;
        if (DictConst.VIS_PRIVATE.equals(info.visibility())) throw new BizException(ResultCode.NOT_FOUND);
        if (!audienceService.sharedTo(info.kind(), contentId, familyId)) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
    }

    /** 目标内容摘要:作者与所属家庭(通知前校验接收者确实在这个家庭) */
    private record TargetContent(Long authorId, Long familyId) {
    }

    /** 内容基本信息(kind 用于查受众) */
    private record ContentInfo(ContentKind kind, Long authorId, Long familyId, String visibility) {
    }

    /** 载入评论目标内容;跨家庭能否互动看下面两处规则 */
    private ContentInfo loadContent(String contentType, Long contentId) {
        if (contentId == null) throw new BizException(ResultCode.BAD_REQUEST);
        switch (contentType == null ? "" : contentType) {
            case "blog" -> {
                Blog b = blogMapper.selectById(contentId);
                if (b == null) throw new BizException(ResultCode.NOT_FOUND);
                return new ContentInfo(ContentKind.BLOG, b.getAuthorId(), b.getFamilyId(), b.getVisibility());
            }
            case "diary" -> {
                Diary d = diaryMapper.selectById(contentId);
                if (d == null) throw new BizException(ResultCode.NOT_FOUND);
                return new ContentInfo(ContentKind.DIARY, d.getAuthorId(), d.getFamilyId(), d.getVisibility());
            }
            case "photo" -> {
                Photo p = photoMapper.selectById(contentId);
                if (p == null) throw new BizException(ResultCode.NOT_FOUND);
                return new ContentInfo(ContentKind.PHOTO, p.getAuthorId(), p.getFamilyId(), p.getVisibility());
            }
            default -> throw new BizException(ResultCode.BAD_REQUEST);
        }
    }

    /** 校验目标内容存在且可评论(同家庭 or 已分享给本家庭),返回作者与家庭 */
    private TargetContent validateTarget(String contentType, Long contentId, Long familyId) {
        if (contentId == null) return new TargetContent(null, null);
        ContentInfo info = loadContent(contentType, contentId);
        if (!Objects.equals(familyId, info.familyId())) {
            boolean shared = !DictConst.VIS_PRIVATE.equals(info.visibility())
                    && audienceService.sharedTo(info.kind(), contentId, familyId);
            if (!shared) throw new BizException(ResultCode.NOT_FOUND);
        }
        return new TargetContent(info.authorId(), info.familyId());
    }

    /** 发送站内通知:回复优先通知被回复人,否则通知内容作者(自己评论自己不通知) */
    private void notifyTo(CommentDTO dto, Long currentUserId, Long commentId, TargetContent target) {
        if (target.authorId() != null && target.authorId().equals(currentUserId)) {
            return;
        }
        String type = dto.getParentId() != null ? "reply" : "comment";
        String snippet = truncate(dto.getContent(), 50);
        if (dto.getReplyToUserId() != null && !dto.getReplyToUserId().equals(currentUserId)) {
            notificationService.createInFamily(dto.getReplyToUserId(), target.familyId(), "reply", "回复了你: " + snippet,
                    commentId, dto.getContentType(), dto.getContentId());
        } else if (target.authorId() != null) {
            notificationService.createInFamily(target.authorId(), target.familyId(), type,
                    (dto.getParentId() != null ? "回复了你的内容: " : "评论了你的内容: ") + snippet,
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

    /** 评论转展示结构:补作者昵称与被回复人昵称(从批量预取的 userMap 取,无则回退单查) */
    private Map<String, Object> toView(Comment c, Map<Long, SysUser> userMap) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", c.getId());
        m.put("content", c.getContent());
        m.put("authorId", c.getAuthorId());
        SysUser author = c.getAuthorId() == null ? null : userMap.get(c.getAuthorId());
        m.put("authorName", UserNames.of(author));
        m.put("replyToUserId", c.getReplyToUserId());
        SysUser replyTo = c.getReplyToUserId() == null ? null : userMap.get(c.getReplyToUserId());
        m.put("replyToName", c.getReplyToUserId() == null ? null : UserNames.of(replyTo));
        m.put("createdAt", c.getCreatedAt());
        return m;
    }
}