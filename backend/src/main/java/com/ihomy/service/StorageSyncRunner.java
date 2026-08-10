package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.entity.Album;
import com.ihomy.entity.Photo;
import com.ihomy.entity.StorageDevice;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.AlbumMapper;
import com.ihomy.mapper.PhotoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 存储同步异步执行器:按设备顶层目录建相册(相册名=目录名),
 * 图片复制进系统存储 upload/yyyyMM/相册ID_文件名,source_path 去重。
 * 进度在内存 map,重启丢失(低频后台任务,可接受)。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StorageSyncRunner {

    private static final String[] IMAGE_EXTS = {".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp"};

    private final AlbumMapper albumMapper;
    private final PhotoMapper photoMapper;
    private final NotificationService notificationService;

    private final Map<Long, Map<String, Object>> syncProgress = new ConcurrentHashMap<>();

    @Value("${file.upload-dir}")
    private String uploadDir;

    public Map<String, Object> progress(Long taskId) {
        Map<String, Object> p = syncProgress.get(taskId);
        if (p == null) throw new BizException(ResultCode.NOT_FOUND, "任务不存在或已过期");
        return p;
    }

    @Async
    public void run(StorageDevice device, boolean includeEmpty, SysUser user, Long taskId) {
        Map<String, Object> p = syncProgress.computeIfAbsent(taskId, k -> new ConcurrentHashMap<>());
        Path root = Paths.get(device == null ? uploadDir : device.getRootPath()).toAbsolutePath().normalize();
        try {
            List<Path> dirs = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
                for (Path d : stream) {
                    if (Files.isDirectory(d) && !d.getFileName().toString().startsWith(".")) dirs.add(d);
                }
            }
            p.put("totalDirs", dirs.size());
            int doneDir = 0, albumsCreated = 0, photos = 0, skippedEmpty = 0, skippedDup = 0;
            for (Path dir : dirs) {
                List<Path> images = listImages(dir);
                if (images.isEmpty()) {
                    if (!includeEmpty) skippedEmpty++;
                    else albumsCreated += syncAlbum(device, user, dir, images, p);
                    doneDir++;
                    p.put("doneDirs", doneDir);
                    continue;
                }
                int[] r = syncAlbumCounts(device, user, dir, images, p);
                albumsCreated += r[0];
                photos += r[1];
                skippedDup += r[2];
                doneDir++;
                p.put("doneDirs", doneDir);
            }
            p.put("status", "DONE");
            p.put("message", "同步完成");
            p.put("albums", albumsCreated);
            p.put("photos", photos);
            p.put("skippedEmpty", skippedEmpty);
            p.put("skippedDup", skippedDup);
            notificationService.create(user.getId(), "system",
                    "存储同步完成: 相册 " + albumsCreated + " 个, 照片 " + photos + " 张", null, "storage", null);
        } catch (Exception e) {
            log.error("存储同步任务失败", e);
            p.put("status", "FAILED");
            p.put("message", "同步失败: " + e.getMessage());
            notificationService.create(user.getId(), "system",
                    "存储同步失败: " + e.getMessage(), null, "storage", null);
        } finally {
            p.put("finishedAt", System.currentTimeMillis());
        }
    }

    /** 空目录也建相册(includeEmpty=true 时) */
    private int syncAlbum(StorageDevice device, SysUser user, Path dir, List<Path> images, Map<String, Object> p) {
        return syncAlbumCounts(device, user, dir, images, p)[0];
    }

    /** 建/复用同名相册并复制图片,返回 [相册新建数, 新增照片数, 去重跳过数] */
    private int[] syncAlbumCounts(StorageDevice device, SysUser user, Path dir, List<Path> images, Map<String, Object> p) {
        String albumName = dir.getFileName().toString();
        Album album = albumMapper.selectOne(new LambdaQueryWrapper<Album>()
                .eq(Album::getFamilyId, user.getFamilyId()).eq(Album::getName, albumName).last("LIMIT 1"));
        int created = 0;
        if (album == null) {
            album = new Album();
            album.setName(albumName);
            album.setType("public");
            album.setFamilyId(user.getFamilyId());
            album.setCreatedBy(user.getId());
            album.setCreatedAt(LocalDateTime.now());
            albumMapper.insert(album);
            created = 1;
        }
        int dup = 0, added = 0;
        for (Path f : images) {
            String rel = rootRelPath(device, f);
            if (photoMapper.selectCount(new LambdaQueryWrapper<Photo>()
                    .eq(Photo::getAlbumId, album.getId()).eq(Photo::getSourcePath, rel)) > 0) {
                dup++;
                continue;
            }
            try {
                String url = copyToSystemStorage(f, album.getId(), albumName);
                Photo photo = new Photo();
                photo.setAlbumId(album.getId());
                photo.setUrl(url);
                photo.setAuthorId(user.getId());
                photo.setFamilyId(user.getFamilyId());
                photo.setVisibility("FAMILY");
                photo.setSourcePath(rel);
                photoMapper.insert(photo);
                added++;
            } catch (IOException e) {
                log.warn("同步复制失败: {}", f, e);
            }
        }
        if (album.getCoverPhotoUrl() == null || album.getCoverPhotoUrl().isBlank()) {
            Photo first = photoMapper.selectOne(new LambdaQueryWrapper<Photo>()
                    .eq(Photo::getAlbumId, album.getId()).orderByAsc(Photo::getId).last("LIMIT 1"));
            if (first != null) {
                album.setCoverPhotoUrl(first.getUrl());
                albumMapper.updateById(album);
            }
        }
        p.put("lastAlbum", albumName);
        return new int[]{created, added, dup};
    }

    private List<Path> listImages(Path dir) {
        List<Path> images = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path f : stream) {
                if (Files.isRegularFile(f) && isImage(f.getFileName().toString())) images.add(f);
            }
        } catch (IOException ignored) {
        }
        return images;
    }

    private boolean isImage(String name) {
        String n = name.toLowerCase();
        for (String ext : IMAGE_EXTS) {
            if (n.endsWith(ext)) return true;
        }
        return false;
    }

    private String rootRelPath(StorageDevice device, Path f) {
        Path base = Paths.get(device == null ? uploadDir : device.getRootPath()).toAbsolutePath().normalize();
        String prefix = device == null ? "SYSTEM" : device.getName();
        return prefix + ":" + base.relativize(f.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private String copyToSystemStorage(Path src, Long albumId, String albumName) throws IOException {
        String cleanName = albumName == null ? null : albumName.replaceAll("[^\\w.\\-\\u4e00-\\u9fa5]", "_");
        Path dir = cleanName == null ? Paths.get(uploadDir, "pictures")
                : Paths.get(uploadDir, "pictures", cleanName);
        Files.createDirectories(dir);
        String fileName = albumId + "_" + System.currentTimeMillis() + "_" + src.getFileName().toString();
        Files.copy(src, dir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        return "/files/pictures/" + (cleanName == null ? "" : cleanName + "/") + fileName;
    }
}