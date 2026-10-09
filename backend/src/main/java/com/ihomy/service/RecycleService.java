package com.ihomy.service;

import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.entity.Album;
import com.ihomy.entity.ContentBook;
import com.ihomy.entity.Photo;
import com.ihomy.entity.Video;
import com.ihomy.mapper.AlbumMapper;
import com.ihomy.mapper.ContentBookMapper;
import com.ihomy.mapper.PhotoMapper;
import com.ihomy.mapper.VideoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 回收站业务:照片/相册/视频/图书的逻辑删内容可恢复或彻底删除。
 * 删除仍走各业务 Service(置 deleted=1 保留磁盘文件),本服务负责聚合展示、恢复、
 * 彻底删除(物理删 DB + 磁盘文件)与 7 天定时清理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecycleService {

    /** 回收站保留天数,超期定时物理清理 */
    public static final int RETENTION_DAYS = 7;
    private static final String TYPE_PHOTO = "photo";
    private static final String TYPE_ALBUM = "album";
    private static final String TYPE_VIDEO = "video";
    private static final String TYPE_BOOK = "book";

    private final PhotoMapper photoMapper;
    private final AlbumMapper albumMapper;
    private final VideoMapper videoMapper;
    private final ContentBookMapper bookMapper;
    private final FileService fileService;
    private final SignedUrlService signedUrlService;
    private final ThumbnailService thumbnailService;

    /** 回收站列表:type=photo/album/video/book */
    public List<Map<String, Object>> list(Long familyId, String type) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (familyId == null) return result;
        switch (normalize(type)) {
            case TYPE_PHOTO -> {
                for (Photo p : photoMapper.selectTrashByFamily(familyId)) {
                    Map<String, Object> m = base(p.getId(), p.getDeletedAt());
                    m.put("title", (p.getDescription() == null || p.getDescription().isBlank()) ? "照片" : p.getDescription());
                    m.put("url", signedUrlService.resolve(p.getUrl()));
                    result.add(m);
                }
            }
            case TYPE_ALBUM -> {
                for (Album a : albumMapper.selectTrashRoots(familyId)) {
                    Map<String, Object> m = base(a.getId(), a.getDeletedAt());
                    m.put("title", a.getName());
                    String cover = (a.getCoverUrl() != null && !a.getCoverUrl().isBlank()) ? a.getCoverUrl() : a.getCoverPhotoUrl();
                    m.put("url", cover == null ? null : signedUrlService.resolve(cover));
                    result.add(m);
                }
            }
            case TYPE_VIDEO -> {
                for (Video v : videoMapper.selectTrashByFamily(familyId)) {
                    Map<String, Object> m = base(v.getId(), v.getDeletedAt());
                    m.put("title", v.getTitle());
                    m.put("url", v.getPoster() == null ? null : signedUrlService.resolve(v.getPoster()));
                    result.add(m);
                }
            }
            case TYPE_BOOK -> {
                for (ContentBook b : bookMapper.selectTrashByFamily(familyId)) {
                    Map<String, Object> m = base(b.getId(), b.getDeletedAt());
                    m.put("title", b.getTitle());
                    m.put("url", b.getCoverUrl() == null ? null : signedUrlService.resolve(b.getCoverUrl()));
                    result.add(m);
                }
            }
            default -> throw new BizException(ResultCode.BAD_REQUEST);
        }
        return result;
    }

    /** 恢复:相册恢复整棵子树(含其内照片),其余按单条恢复 */
    @Transactional
    public void restore(Long familyId, String type, Long id) {
        switch (normalize(type)) {
            case TYPE_PHOTO -> {
                requireDeleted(photoMapper.selectDeletedById(id), familyId);
                photoMapper.restoreById(id);
            }
            case TYPE_ALBUM -> {
                requireDeleted(albumMapper.selectDeletedById(id), familyId);
                List<Long> ids = subtreeIds(familyId, id);
                albumMapper.restoreByIds(ids);
                photoMapper.restoreByAlbumIds(ids);
            }
            case TYPE_VIDEO -> {
                requireDeleted(videoMapper.selectDeletedById(id), familyId);
                videoMapper.restoreById(id);
            }
            case TYPE_BOOK -> {
                requireDeleted(bookMapper.selectDeletedById(id), familyId);
                bookMapper.restoreById(id);
            }
            default -> throw new BizException(ResultCode.BAD_REQUEST);
        }
    }

    /** 彻底删除:物理删 DB 记录 + 磁盘文件;相册连同子树与其内照片一并清理 */
    @Transactional
    public void purge(Long familyId, String type, Long id) {
        switch (normalize(type)) {
            case TYPE_PHOTO -> purgePhoto(requireDeleted(photoMapper.selectDeletedById(id), familyId));
            case TYPE_ALBUM -> purgeAlbum(familyId, requireDeleted(albumMapper.selectDeletedById(id), familyId));
            case TYPE_VIDEO -> purgeVideo(requireDeleted(videoMapper.selectDeletedById(id), familyId));
            case TYPE_BOOK -> purgeBook(requireDeleted(bookMapper.selectDeletedById(id), familyId));
            default -> throw new BizException(ResultCode.BAD_REQUEST);
        }
    }

    /** 清空某类回收站:逐条彻底删除 */
    @Transactional
    public int empty(Long familyId, String type) {
        List<Map<String, Object>> items = list(familyId, type);
        for (Map<String, Object> item : items) {
            purge(familyId, type, Long.valueOf(item.get("id").toString()));
        }
        return items.size();
    }

    /** 每日 03:00 物理清理超过保留期的回收站项(磁盘文件一并删除) */
    @Scheduled(cron = "0 0 3 * * ?")
    public void autoCleanExpired() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
        int n = 0;
        for (Long id : safe(photoMapper.selectExpiredTrashIds(cutoff))) {
            Photo p = photoMapper.selectDeletedById(id);
            if (p != null) { purgePhoto(p); n++; }
        }
        for (Long id : safe(albumMapper.selectExpiredTrashIds(cutoff))) {
            Album a = albumMapper.selectDeletedById(id);
            if (a != null) { purgeAlbum(a.getFamilyId(), a); n++; }
        }
        for (Long id : safe(videoMapper.selectExpiredTrashIds(cutoff))) {
            Video v = videoMapper.selectDeletedById(id);
            if (v != null) { purgeVideo(v); n++; }
        }
        for (Long id : safe(bookMapper.selectExpiredTrashIds(cutoff))) {
            ContentBook b = bookMapper.selectDeletedById(id);
            if (b != null) { purgeBook(b); n++; }
        }
        if (n > 0) log.info("回收站定时清理:已物理删除 {} 项(超过 {} 天)", n, RETENTION_DAYS);
    }

    /* ---------- 内部工具 ---------- */

    private Map<String, Object> base(Long id, LocalDateTime deletedAt) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("deletedAt", deletedAt);
        return m;
    }

    private String normalize(String type) {
        return type == null ? "" : type.trim().toLowerCase();
    }

    private <T> T requireDeleted(T entity, Long familyId) {
        if (entity == null) throw new BizException(ResultCode.NOT_FOUND);
        Long entityFamily = null;
        if (entity instanceof Photo p) entityFamily = p.getFamilyId();
        else if (entity instanceof Album a) entityFamily = a.getFamilyId();
        else if (entity instanceof Video v) entityFamily = v.getFamilyId();
        else if (entity instanceof ContentBook b) entityFamily = b.getFamilyId();
        if (familyId != null && !familyId.equals(entityFamily)) throw new BizException(ResultCode.NOT_FOUND);
        return entity;
    }

    private <T> List<T> safe(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }

    /** 收集相册子树全部 id(含自身),遍历含已删行 */
    private List<Long> subtreeIds(Long familyId, Long rootId) {
        List<Album> all = albumMapper.selectAllByFamily(familyId);
        List<Long> ids = new ArrayList<>();
        collect(rootId, all, ids);
        return ids;
    }

    private void collect(Long rootId, List<Album> all, List<Long> out) {
        out.add(rootId);
        for (Album a : all) {
            if (rootId.equals(a.getParentId())) collect(a.getId(), all, out);
        }
    }

    private void purgePhoto(Photo p) {
        photoMapper.deletePhysicalById(p.getId());
        fileService.deleteByUrl(p.getUrl());
        fileService.deleteByUrl(p.getLiveVideoUrl());
        thumbnailService.evictByUrl(p.getUrl());
    }

    private void purgeAlbum(Long familyId, Album root) {
        List<Long> ids = subtreeIds(familyId, root.getId());
        List<Photo> photos = photoMapper.selectByAlbumIdsAny(ids);
        for (Photo p : photos) {
            fileService.deleteByUrl(p.getUrl());
            fileService.deleteByUrl(p.getLiveVideoUrl());
            thumbnailService.evictByUrl(p.getUrl());
        }
        List<Album> all = albumMapper.selectAllByFamily(familyId);
        for (Album a : all) {
            if (ids.contains(a.getId())) {
                fileService.deleteByUrl(a.getCoverUrl());
                fileService.deleteByUrl(a.getCoverPhotoUrl());
                photoMapper.deletePhysicalByAlbumId(a.getId());
                albumMapper.deletePhysicalById(a.getId());
            }
        }
    }

    private void purgeVideo(Video v) {
        videoMapper.deletePhysicalById(v.getId());
        fileService.deleteByUrl(v.getVideoUrl());
        fileService.deleteByUrl(v.getPoster());
        thumbnailService.evictByUrl(v.getPoster());
    }

    private void purgeBook(ContentBook b) {
        bookMapper.deleteRelByBookId(b.getId());
        bookMapper.deletePhysicalById(b.getId());
        fileService.deleteByUrl(b.getFileUrl());
        fileService.deleteByUrl(b.getCoverUrl());
    }
}
