package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.entity.Blog;
import com.ihomy.entity.ContentLike;
import com.ihomy.entity.Diary;
import com.ihomy.entity.Photo;
import com.ihomy.mapper.BlogMapper;
import com.ihomy.mapper.ContentLikeMapper;
import com.ihomy.mapper.DiaryMapper;
import com.ihomy.mapper.PhotoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 统一点赞业务:博客/日记/照片共用一张 content_like 表,
 * 切换点赞状态并回写各内容的 likeCount 冗余字段。
 */
@Service
@RequiredArgsConstructor
public class ContentLikeService {

    private final ContentLikeMapper likeMapper;
    private final BlogMapper blogMapper;
    private final DiaryMapper diaryMapper;
    private final PhotoMapper photoMapper;

    /** 切换点赞:已赞则取消,未赞则插入(表 UNIQUE 防重复),返回最新状态与总数 */
    public Map<String, Object> toggle(Long userId, Long familyId, String contentType, Long contentId) {
        if (!validContent(contentType, contentId, familyId)) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        LambdaQueryWrapper<ContentLike> qw = new LambdaQueryWrapper<>();
        qw.eq(ContentLike::getContentType, contentType)
          .eq(ContentLike::getContentId, contentId)
          .eq(ContentLike::getUserId, userId);
        ContentLike existing = likeMapper.selectOne(qw);
        boolean liked;
        if (existing != null) {
            likeMapper.deleteById(existing.getId());
            liked = false;
        } else {
            ContentLike like = new ContentLike();
            like.setContentType(contentType);
            like.setContentId(contentId);
            like.setUserId(userId);
            like.setFamilyId(familyId);
            try {
                likeMapper.insert(like);
                liked = true;
            } catch (org.springframework.dao.DuplicateKeyException e) {
                ContentLike existing2 = likeMapper.selectOne(qw);
                if (existing2 != null) {
                    likeMapper.deleteById(existing2.getId());
                    liked = false;
                } else {
                    liked = true;
                }
            }
        }
        long count = likeMapper.countByContent(contentType, contentId);
        syncCount(contentType, contentId, (int) count);
        Map<String, Object> result = new HashMap<>();
        result.put("liked", liked);
        result.put("likeCount", count);
        return result;
    }

    /** 查询某用户对内容的点赞状态与总数(游客未点赞) */
    public Map<String, Object> state(Long userId, String contentType, Long contentId) {
        Map<String, Object> result = new HashMap<>();
        result.put("liked", isLiked(userId, contentType, contentId));
        result.put("likeCount", likeMapper.countByContent(contentType, contentId));
        return result;
    }

    public boolean isLiked(Long userId, String contentType, Long contentId) {
        if (userId == null) return false;
        LambdaQueryWrapper<ContentLike> qw = new LambdaQueryWrapper<>();
        qw.eq(ContentLike::getContentType, contentType)
          .eq(ContentLike::getContentId, contentId)
          .eq(ContentLike::getUserId, userId);
        return likeMapper.selectCount(qw) > 0;
    }

    /** 目标内容必须存在且与操作者同家庭,否则视为资源不存在 */
    private boolean validContent(String contentType, Long contentId, Long familyId) {
        if (contentId == null) return false;
        switch (contentType == null ? "" : contentType) {
            case "blog":
                Blog b = blogMapper.selectById(contentId);
                return b != null && b.getFamilyId().equals(familyId);
            case "diary":
                Diary d = diaryMapper.selectById(contentId);
                return d != null && d.getFamilyId().equals(familyId);
            case "photo":
                Photo p = photoMapper.selectById(contentId);
                return p != null && p.getFamilyId().equals(familyId);
            default:
                return false;
        }
    }

    /** 把最新点赞数回写到内容表的 likeCount 字段,便于列表直接展示。
     * 直接 UPDATE 不先 select,避免一次额外查询;不存在的 id 自然不会受影响。
     */
    private void syncCount(String contentType, Long contentId, int count) {
        switch (contentType) {
            case "blog" -> {
                com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Blog> uw =
                        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
                uw.eq(Blog::getId, contentId).set(Blog::getLikeCount, count);
                blogMapper.update(null, uw);
            }
            case "diary" -> {
                com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Diary> uw =
                        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
                uw.eq(Diary::getId, contentId).set(Diary::getLikeCount, count);
                diaryMapper.update(null, uw);
            }
            case "photo" -> {
                com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Photo> uw =
                        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
                uw.eq(Photo::getId, contentId).set(Photo::getLikeCount, count);
                photoMapper.update(null, uw);
            }
            default -> {
            }
        }
    }
}
