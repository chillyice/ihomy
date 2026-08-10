package com.ihomy.service;

import com.ihomy.common.UserNames;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 家庭动态流:聚合博客/日记/照片三类内容,按时间倒序合并,
 * 支持游客模式(仅公开内容)与条数限制。
 */
@Service
@RequiredArgsConstructor
public class ActivityFeedService {

    private final com.ihomy.mapper.BlogMapper blogMapper;
    private final com.ihomy.mapper.DiaryMapper diaryMapper;
    private final com.ihomy.mapper.PhotoMapper photoMapper;
    private final com.ihomy.mapper.CommentMapper commentMapper;
    private final com.ihomy.mapper.SysUserMapper sysUserMapper;

    /** 组装动态:照片按上传者分组聚合为一条(带数量/前 5 张预览),最后统一按时间倒序取前 limit 条 */
    public List<Map<String, Object>> getFeed(Long familyId, int limit, boolean publicOnly) {
        List<Map<String, Object>> items = new ArrayList<>();
        List<Map<String, Object>> blogItems = new ArrayList<>();
        List<Map<String, Object>> diaryItems = new ArrayList<>();
        List<Long> blogIds = new ArrayList<>();
        List<Long> diaryIds = new ArrayList<>();

        if (familyId != null) {
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.ihomy.entity.Blog> bq =
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            bq.eq(com.ihomy.entity.Blog::getFamilyId, familyId)
              .eq(com.ihomy.entity.Blog::getStatus, com.ihomy.common.DictConst.BLOG_PUBLISHED);
            if (publicOnly) {
                bq.eq(com.ihomy.entity.Blog::getVisibility, com.ihomy.common.DictConst.VIS_PUBLIC);
            }
            bq.orderByDesc(com.ihomy.entity.Blog::getCreatedAt).last("LIMIT " + limit);
            for (com.ihomy.entity.Blog b : blogMapper.selectList(bq)) {
                Map<String, Object> m = base("blog", b.getId(), b.getAuthorId(), b.getFamilyId(), b.getCreatedAt());
                m.put("title", b.getTitle());
                m.put("summary", truncate(stripMarkdown(b.getContent()), 120));
                m.put("coverImage", b.getCoverImage());
                m.put("tags", b.getTags());
                m.put("viewCount", b.getViewCount());
                m.put("likeCount", b.getLikeCount() == null ? 0 : b.getLikeCount());
                items.add(m);
                blogItems.add(m);
                blogIds.add(b.getId());
            }

            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.ihomy.entity.Diary> dq =
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            dq.eq(com.ihomy.entity.Diary::getFamilyId, familyId);
            if (publicOnly) {
                dq.eq(com.ihomy.entity.Diary::getVisibility, com.ihomy.common.DictConst.VIS_PUBLIC);
            }
            dq.orderByDesc(com.ihomy.entity.Diary::getCreatedAt).last("LIMIT " + limit);
            for (com.ihomy.entity.Diary d : diaryMapper.selectList(dq)) {
                Map<String, Object> m = base("diary", d.getId(), d.getAuthorId(), d.getFamilyId(), d.getCreatedAt());
                m.put("content", truncate(d.getContent(), 150));
                m.put("mood", d.getMood());
                m.put("weather", d.getWeather());
                m.put("images", d.getImages());
                m.put("likeCount", d.getLikeCount() == null ? 0 : d.getLikeCount());
                items.add(m);
                diaryItems.add(m);
                diaryIds.add(d.getId());
            }

            List<com.ihomy.entity.Photo> photos = publicOnly
                    ? photoMapper.selectLatestPublicByFamily(familyId, limit * 3)
                    : photoMapper.selectLatestByFamily(familyId, limit * 3);
            Map<Long, List<com.ihomy.entity.Photo>> grouped = new HashMap<>();
            for (com.ihomy.entity.Photo p : photos) {
                grouped.computeIfAbsent(p.getAuthorId(), k -> new ArrayList<>()).add(p);
            }
            for (Map.Entry<Long, List<com.ihomy.entity.Photo>> e : grouped.entrySet()) {
                List<com.ihomy.entity.Photo> ps = e.getValue();
                if (ps.isEmpty()) continue;
                com.ihomy.entity.Photo first = ps.get(0);
                Map<String, Object> m = base("photo", first.getAuthorId(), first.getAuthorId(), first.getFamilyId(), first.getCreatedAt());
                m.put("count", ps.size());
                m.put("urls", ps.stream().map(com.ihomy.entity.Photo::getUrl).limit(5).toList());
                m.put("descriptions", ps.stream().map(com.ihomy.entity.Photo::getDescription).limit(5).toList());
                m.put("likeCount", ps.stream().mapToInt(p -> p.getLikeCount() == null ? 0 : p.getLikeCount()).sum());
                m.put("commentCount", commentCount("photo", ps.stream().map(com.ihomy.entity.Photo::getId).toList()));
                items.add(m);
            }

            applyCommentCounts(blogItems, blogIds, "blog");
            applyCommentCounts(diaryItems, diaryIds, "diary");
        }

        items.sort(Comparator.comparing(m -> (java.time.LocalDateTime) m.get("createdAt"), Comparator.reverseOrder()));
        if (items.size() > limit) {
            return items.subList(0, limit);
        }
        return items;
    }

    /** 批量补充各内容的评论数 */
    private void applyCommentCounts(List<Map<String, Object>> items, List<Long> ids, String type) {
        if (items.isEmpty()) return;
        Map<Long, Integer> counts = commentCounts(type, ids);
        for (Map<String, Object> m : items) {
            Long id = (Long) m.get("id");
            m.put("commentCount", counts.getOrDefault(id, 0));
        }
    }

    /** 一组内容的评论总数(用于照片分组聚合) */
    private int commentCount(String type, List<Long> ids) {
        Map<Long, Integer> counts = commentCounts(type, ids);
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** 按 content_id 批量查询评论数,返回 id→count 映射 */
    private Map<Long, Integer> commentCounts(String type, List<Long> ids) {
        Map<Long, Integer> map = new HashMap<>();
        if (ids == null || ids.isEmpty()) return map;
        for (Map<String, Object> row : commentMapper.countByContentIds(type, ids)) {
            Number cid = (Number) row.get("content_id");
            Number cnt = (Number) row.get("cnt");
            if (cid != null) map.put(cid.longValue(), cnt == null ? 0 : cnt.intValue());
        }
        return map;
    }

    /** 动态通用基础字段:类型/id/作者信息/时间 */
    private Map<String, Object> base(String type, Long id, Long authorId, Long familyId, java.time.LocalDateTime createdAt) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", type);
        m.put("id", id);
        m.put("authorId", authorId);
        m.put("authorName", resolveAuthorName(authorId));
        m.put("authorAvatar", resolveAuthorAvatar(authorId));
        m.put("familyId", familyId);
        m.put("createdAt", createdAt);
        return m;
    }

    private String resolveAuthorName(Long authorId) {
        if (authorId == null) return null;
        return UserNames.of(sysUserMapper.selectById(authorId));
    }

    private String resolveAuthorAvatar(Long authorId) {
        if (authorId == null) return null;
        com.ihomy.entity.SysUser u = sysUserMapper.selectById(authorId);
        return u != null ? u.getAvatar() : null;
    }

    /** 超长文本截断加省略号 */
    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    /** 去除常见 Markdown 语法,仅保留纯文本用于摘要 */
    private String stripMarkdown(String md) {
        if (md == null) return null;
        return md.replaceAll("#+ ", "")
                 .replaceAll("\\*\\*", "")
                 .replaceAll("`", "")
                 .replaceAll("!\\[.*?\\]\\(.*?\\)", "")
                 .replaceAll("\\[.*?\\]\\(.*?\\)", "")
                 .replaceAll("\\s+", " ")
                 .trim();
    }
}
