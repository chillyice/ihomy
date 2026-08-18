package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.common.ResultCode;
import com.ihomy.common.UserNames;
import com.ihomy.dto.VideoDTO;
import com.ihomy.entity.SysUser;
import com.ihomy.entity.Video;
import com.ihomy.entity.VideoWish;
import com.ihomy.mapper.SysUserMapper;
import com.ihomy.mapper.VideoMapper;
import com.ihomy.mapper.VideoWishMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 放映厅业务:家庭视频库(豆瓣式元数据,软删)与"想看"清单,
 * 改删视频校验上传者或家长,可见性固定为家庭可见。
 */
@Service
@RequiredArgsConstructor
public class VideoService {

    private final VideoMapper videoMapper;
    private final VideoWishMapper videoWishMapper;
    private final SysUserMapper sysUserMapper;
    private final PointsService pointsService;
    private final FileService fileService;

    /** 视频列表:按家庭过滤,支持关键字/类型搜索,附带上传者昵称 */
    public List<Map<String, Object>> list(Long familyId, String keyword, String mediaType) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (familyId == null) return result;
        LambdaQueryWrapper<Video> qw = new LambdaQueryWrapper<>();
        qw.eq(Video::getFamilyId, familyId)
          .eq(Video::getDeleted, 0)
          .eq(mediaType != null && !mediaType.isBlank(), Video::getMediaType, mediaType)
          .like(keyword != null && !keyword.isBlank(), Video::getTitle, keyword)
          .orderByDesc(Video::getCreatedAt);
        List<Video> videos = videoMapper.selectList(qw);
        Map<Long, SysUser> userMap = batchUsers(videos.stream().map(Video::getUploaderId));
        for (Video v : videos) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", v.getId());
            m.put("title", v.getTitle());
            m.put("originalTitle", v.getOriginalTitle());
            m.put("mediaType", v.getMediaType());
            m.put("genres", v.getGenres());
            m.put("region", v.getRegion());
            m.put("year", v.getYear());
            m.put("language", v.getLanguage());
            m.put("duration", v.getDuration());
            m.put("episodes", v.getEpisodes());
            m.put("director", v.getDirector());
            m.put("actors", v.getActors());
            m.put("rating", v.getRating());
            m.put("intro", v.getIntro());
            m.put("poster", v.getPoster());
            m.put("videoUrl", v.getVideoUrl());
            m.put("uploaderId", v.getUploaderId());
            SysUser u = v.getUploaderId() == null ? null : userMap.get(v.getUploaderId());
            m.put("uploaderName", UserNames.of(u));
            m.put("createdAt", v.getCreatedAt());
            result.add(m);
        }
        return result;
    }

    /** 入库视频:默认 movie 类型、家庭可见、未删除 */
    public Video create(Long userId, Long familyId, VideoDTO dto) {
        Video v = new Video();
        apply(v, dto);
        v.setUploaderId(userId);
        v.setFamilyId(familyId);
        v.setVisibility(DictConst.VIS_FAMILY);
        v.setDeleted(0);
        videoMapper.insert(v);
        pointsService.addRecord(userId, familyId, "REWARD", PointsService.REWARD_VIDEO, "发布视频");
        return v;
    }

    /** 更新视频:仅上传者或家长,且须同家庭 */
    public Video update(Long id, Long familyId, Long currentUserId, boolean isOwner, VideoDTO dto) {
        Video v = requireOwn(id, familyId, currentUserId, isOwner);
        apply(v, dto);
        videoMapper.updateById(v);
        return v;
    }

    /** 删除视频:硬删记录并删除视频文件与海报 */
    public void delete(Long id, Long familyId, Long currentUserId, boolean isOwner) {
        Video v = requireOwn(id, familyId, currentUserId, isOwner);
        videoMapper.deletePhysicalById(id);
        fileService.deleteByUrl(v.getVideoUrl());
        fileService.deleteByUrl(v.getPoster());
    }

    /** DTO 字段落库,mediaType 缺省补 movie */
    private void apply(Video v, VideoDTO dto) {
        v.setTitle(dto.getTitle());
        v.setOriginalTitle(dto.getOriginalTitle());
        v.setMediaType(dto.getMediaType() == null ? "movie" : dto.getMediaType());
        v.setGenres(dto.getGenres());
        v.setRegion(dto.getRegion());
        v.setYear(dto.getYear());
        v.setLanguage(dto.getLanguage());
        v.setDuration(dto.getDuration());
        v.setEpisodes(dto.getEpisodes());
        v.setDirector(dto.getDirector());
        v.setActors(dto.getActors());
        v.setRating(dto.getRating());
        v.setIntro(dto.getIntro());
        v.setPoster(dto.getPoster());
        v.setVideoUrl(dto.getVideoUrl());
    }

    /** 校验视频存在、未删除、同家庭,且操作者为上传者或家长 */
    private Video requireOwn(Long id, Long familyId, Long currentUserId, boolean isOwner) {
        Video v = videoMapper.selectById(id);
        if (v == null || (v.getDeleted() != null && v.getDeleted() == 1)) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        if (familyId != null && !familyId.equals(v.getFamilyId())) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        if (!isOwner && !v.getUploaderId().equals(currentUserId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        return v;
    }

    /** 提交想看:状态 0=待入库 */
    public VideoWish addWish(Long userId, Long familyId, VideoWish wish) {
        wish.setRequesterId(userId);
        wish.setFamilyId(familyId);
        wish.setStatus(DictConst.VWISH_PENDING);
        wish.setDeleted(0);
        videoWishMapper.insert(wish);
        return wish;
    }

    /** 想看列表,附带请求者昵称 */
    public List<Map<String, Object>> listWishes(Long familyId) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (familyId == null) return result;
        LambdaQueryWrapper<VideoWish> qw = new LambdaQueryWrapper<>();
        qw.eq(VideoWish::getFamilyId, familyId)
          .eq(VideoWish::getDeleted, 0)
          .orderByDesc(VideoWish::getCreatedAt);
        List<VideoWish> wishes = videoWishMapper.selectList(qw);
        Map<Long, SysUser> userMap = batchUsers(wishes.stream().map(VideoWish::getRequesterId));
        for (VideoWish w : wishes) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", w.getId());
            m.put("title", w.getTitle());
            m.put("genres", w.getGenres());
            m.put("reason", w.getReason());
            m.put("status", w.getStatus());
            m.put("requesterId", w.getRequesterId());
            SysUser u = w.getRequesterId() == null ? null : userMap.get(w.getRequesterId());
            m.put("requesterName", UserNames.of(u));
            m.put("createdAt", w.getCreatedAt());
            result.add(m);
        }
        return result;
    }

    /** 批量取用户,返回 id→SysUser 映射(空集返空 Map) */
    private Map<Long, SysUser> batchUsers(java.util.stream.Stream<Long> ids) {
        java.util.Set<Long> set = ids.filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        if (set.isEmpty()) return Map.of();
        List<SysUser> users = sysUserMapper.selectBatchIds(set);
        Map<Long, SysUser> map = new HashMap<>(users.size() * 2);
        for (SysUser u : users) {
            map.put(u.getId(), u);
        }
        return map;
    }

    /** 标记想看已入库(status=1),须同家庭 */
    public void markWishDone(Long id, Long familyId) {
        VideoWish w = videoWishMapper.selectById(id);
        if (w == null || (w.getDeleted() != null && w.getDeleted() == 1)) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        if (familyId != null && !familyId.equals(w.getFamilyId())) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        w.setStatus(DictConst.VWISH_IMPORTED);
        videoWishMapper.updateById(w);
    }

    /** 删除想看(软删),须同家庭 */
    public void deleteWish(Long id, Long familyId) {
        VideoWish w = videoWishMapper.selectById(id);
        if (w == null || (w.getDeleted() != null && w.getDeleted() == 1)) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        if (familyId != null && !familyId.equals(w.getFamilyId())) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        w.setDeleted(1);
        videoWishMapper.updateById(w);
    }

    private String resolveUserName(Long userId) {
        if (userId == null) return null;
        return UserNames.of(sysUserMapper.selectById(userId));
    }
}
