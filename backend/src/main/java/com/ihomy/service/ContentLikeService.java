package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.entity.Blog;
import com.ihomy.entity.ContentLike;
import com.ihomy.entity.Diary;
import com.ihomy.entity.Anniversary;
import com.ihomy.entity.Photo;
import com.ihomy.mapper.AnniversaryMapper;
import com.ihomy.mapper.BlogMapper;
import com.ihomy.mapper.ContentLikeMapper;
import com.ihomy.mapper.DiaryMapper;
import com.ihomy.mapper.PhotoMapper;
import com.ihomy.service.access.ContentKind;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 统一点赞业务:博客/日记/照片共用一张 content_like 表,
 * 切换点赞状态并回写各内容的 likeCount 冗余字段;
 * 同家庭内容直接可赞,跨家庭只允许「已分享给本家庭」的内容。
 * 纪念日没有 like_count 列,只记 content_like 行(亲友圈里的「送祝福」,总数实时 COUNT)。
 */
@Service
@RequiredArgsConstructor
public class ContentLikeService {

    private final ContentLikeMapper likeMapper;
    private final BlogMapper blogMapper;
    private final DiaryMapper diaryMapper;
    private final PhotoMapper photoMapper;
    private final AnniversaryMapper anniversaryMapper;
    private final AudienceService audienceService;

    /** 切换点赞:已赞则取消,未赞则插入(表 UNIQUE 防重复),返回最新状态与总数 */
    @Transactional
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
        int delta;
        if (existing != null) {
            likeMapper.deleteById(existing.getId());
            liked = false;
            delta = -1;
        } else {
            ContentLike like = new ContentLike();
            like.setContentType(contentType);
            like.setContentId(contentId);
            like.setUserId(userId);
            like.setFamilyId(familyId);
            try {
                likeMapper.insert(like);
                liked = true;
                delta = 1;
            } catch (org.springframework.dao.DuplicateKeyException e) {
                ContentLike existing2 = likeMapper.selectOne(qw);
                if (existing2 != null) {
                    likeMapper.deleteById(existing2.getId());
                    liked = false;
                    delta = -1;
                } else {
                    liked = true;
                    delta = 0;
                }
            }
        }
        // 原子增量更新 like_count(并发交错不再互相覆盖),与 content_like 的增删同事务落库
        adjustCount(contentType, contentId, delta);
        Map<String, Object> result = new HashMap<>();
        result.put("liked", liked);
        result.put("likeCount", likeMapper.countByContent(contentType, contentId));
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

    /** 目标内容必须存在,且与操作者同家庭或已分享给本家庭,否则视为资源不存在 */
    private boolean validContent(String contentType, Long contentId, Long familyId) {
        if (contentId == null || familyId == null) return false;
        switch (contentType == null ? "" : contentType) {
            case "blog": {
                Blog b = blogMapper.selectById(contentId);
                if (b == null) return false;
                if (b.getFamilyId().equals(familyId)) return true;
                return !com.ihomy.common.DictConst.VIS_PRIVATE.equals(b.getVisibility())
                        && audienceService.sharedTo(ContentKind.BLOG, contentId, familyId);
            }
            case "diary": {
                Diary d = diaryMapper.selectById(contentId);
                if (d == null) return false;
                if (d.getFamilyId().equals(familyId)) return true;
                return !com.ihomy.common.DictConst.VIS_PRIVATE.equals(d.getVisibility())
                        && audienceService.sharedTo(ContentKind.DIARY, contentId, familyId);
            }
            case "photo": {
                Photo p = photoMapper.selectById(contentId);
                if (p == null) return false;
                if (p.getFamilyId().equals(familyId)) return true;
                return !com.ihomy.common.DictConst.VIS_PRIVATE.equals(p.getVisibility())
                        && audienceService.sharedTo(ContentKind.PHOTO, contentId, familyId);
            }
            // 纪念日没有 visibility 列:本家庭可送祝福;亲友圈分享给本家庭的也可(祝福不写对方数据)
            case "anniversary": {
                Anniversary a = anniversaryMapper.selectById(contentId);
                if (a == null) return false;
                if (a.getFamilyId().equals(familyId)) return true;
                return audienceService.sharedTo(ContentKind.ANNIVERSARY, contentId, familyId);
            }
            default:
                return false;
        }
    }

    /** 把点赞数增量原子回写到内容表的 like_count 字段。
     * 用 like_count = like_count + delta 而非「先 COUNT 再 SET」,并发交错时不会互相覆盖;
     * delta 为内部常量(±1/0),拼进 SQL 无注入风险。不存在的 id 自然不会受影响。
     */
    private void adjustCount(String contentType, Long contentId, int delta) {
        if (delta == 0) return;
        String expr = "like_count = like_count + " + delta;
        switch (contentType) {
            case "blog" -> {
                com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Blog> uw =
                        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
                uw.eq(Blog::getId, contentId).setSql(expr);
                blogMapper.update(null, uw);
            }
            case "diary" -> {
                com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Diary> uw =
                        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
                uw.eq(Diary::getId, contentId).setSql(expr);
                diaryMapper.update(null, uw);
            }
            case "photo" -> {
                com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Photo> uw =
                        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
                uw.eq(Photo::getId, contentId).setSql(expr);
                photoMapper.update(null, uw);
            }
            default -> {
            }
        }
    }
}
