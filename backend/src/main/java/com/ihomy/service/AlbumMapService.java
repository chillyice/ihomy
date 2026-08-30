package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.common.ResultCode;
import com.ihomy.entity.Album;
import com.ihomy.entity.Photo;
import com.ihomy.entity.StorageDevice;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.AlbumMapper;
import com.ihomy.mapper.PhotoMapper;
import com.ihomy.mapper.StorageDeviceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 设备目录→相册映射:勾选设备目录后建立同层级相册,照片以影子记录入库(url 存 storage:// 逻辑地址),
 * 文件本体不拷贝,浏览/预览经 /storage/file-signed 签名中转。
 * 状态:VALID 可访问 / OFFLINE 设备离线或授权失效 / MISSING 目录已不存在。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlbumMapService {

    private static final String[] IMAGE_EXTS = {".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".heic"};

    private final AlbumMapper albumMapper;
    private final PhotoMapper photoMapper;
    private final StorageDeviceMapper storageDeviceMapper;
    private final StorageService storageService;
    private final NotificationService notificationService;

    /** @Lazy 断开与 Runner 的构造器循环(Runner → Service → Runner) */
    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private AlbumMapRunner runner;

    /** 任务进度(内存 map,重启丢失,低频后台任务可接受) */
    private final Map<Long, Map<String, Object>> taskProgress = new ConcurrentHashMap<>();
    /** 静默刷新在飞去重(同一相册同时只跑一个刷新) */
    private final Set<Long> refreshing = ConcurrentHashMap.newKeySet();

    public Map<String, Object> progress(Long taskId) {
        Map<String, Object> p = taskProgress.get(taskId);
        if (p == null) throw new BizException(ResultCode.NOT_FOUND, "任务不存在或已过期");
        return p;
    }

    /** 创建映射:勾选的目录 → 同层级相册 + 影子照片(异步任务) */
    public Long createMapping(SysUser user, Long familyId, Long deviceId, List<String> paths) {
        if (paths == null || paths.isEmpty()) throw new BizException(ResultCode.BAD_REQUEST, "请选择要同步的目录");
        StorageDevice device = storageService.getDevice(familyId, deviceId);
        Long taskId = System.nanoTime();
        runner.runMapping(taskId, user, familyId, device, paths);
        return taskId;
    }

    /** 手动刷新映射相册(异步,递归整个子树) */
    public Long refreshAlbum(SysUser user, Album album) {
        StorageDevice device = requireSourceDevice(album);
        Long taskId = System.nanoTime();
        runner.runRefresh(taskId, user, album.getFamilyId(), device, album, true);
        return taskId;
    }

    /** 打开相册时静默刷新(仅当前层;2 分钟内刷过跳过;失败静默) */
    public void autoRefresh(Album album) {
        if (album.getSourceDeviceId() == null) return;
        if (album.getLastSyncedAt() != null
                && album.getLastSyncedAt().isAfter(LocalDateTime.now().minusMinutes(2))) return;
        if (!refreshing.add(album.getId())) return;
        try {
            StorageDevice device = storageDeviceMapper.selectById(album.getSourceDeviceId());
            if (device == null || !album.getFamilyId().equals(device.getFamilyId())) return;
            runner.runRefresh(System.nanoTime(), null, album.getFamilyId(), device, album, false);
        } catch (Exception e) {
            log.warn("静默刷新启动失败: albumId={}", album.getId(), e);
            refreshing.remove(album.getId());
        }
    }

    /* ---------- 任务体(由 AlbumMapRunner @Async 调用) ---------- */

    public void executeMapping(Long taskId, SysUser user, Long familyId, StorageDevice device, List<String> paths) {
        Map<String, Object> p = taskProgress.computeIfAbsent(taskId, k -> new ConcurrentHashMap<>());
        p.put("status", "RUNNING");
        p.put("totalDirs", paths.size());
        int done = 0, albums = 0, photos = 0, failed = 0;
        try {
            for (String path : paths) {
                try {
                    boolean existed = albumMapper.selectCount(new LambdaQueryWrapper<Album>()
                            .eq(Album::getFamilyId, familyId)
                            .eq(Album::getSourceDeviceId, device.getId())
                            .eq(Album::getSourcePath, path)) > 0;
                    Album root = ensureRootAlbum(user, familyId, device, path);
                    int[] r = syncAlbumTree(user, familyId, device, root, true, p);
                    albums += (existed ? 0 : 1) + r[0];
                    photos += r[1];
                } catch (Exception e) {
                    failed++;
                    log.warn("映射目录失败: device={}, path={}", device.getId(), path, e);
                }
                done++;
                p.put("doneDirs", done);
            }
            p.put("status", "DONE");
            p.put("message", failed == 0 ? "同步完成: 相册 " + albums + " 个, 照片 " + photos + " 张"
                    : "同步完成: 相册 " + albums + " 个, 照片 " + photos + " 张, " + failed + " 个目录失败");
            p.put("albums", albums);
            p.put("photos", photos);
            notificationService.create(user.getId(), "system",
                    "设备同步完成: 相册 " + albums + " 个, 照片 " + photos + " 张", null, "storage", null);
        } catch (Exception e) {
            log.error("设备映射任务失败", e);
            p.put("status", "FAILED");
            p.put("message", "同步失败: " + e.getMessage());
            notificationService.create(user.getId(), "system",
                    "设备同步失败: " + e.getMessage(), null, "storage", null);
        } finally {
            p.put("finishedAt", System.currentTimeMillis());
        }
    }

    public void executeRefresh(Long taskId, SysUser user, Long familyId, StorageDevice device,
                               Album album, boolean recursive) {
        Map<String, Object> p = taskProgress.computeIfAbsent(taskId, k -> new ConcurrentHashMap<>());
        p.put("status", "RUNNING");
        try {
            int[] r = syncAlbumTree(user, familyId, device, album, recursive, p);
            p.put("status", "DONE");
            p.put("message", "刷新完成: 新增照片 " + r[1] + " 张");
            p.put("photos", r[1]);
            if (user != null) {
                notificationService.create(user.getId(), "system",
                        "相册刷新完成: " + album.getName() + " 新增 " + r[1] + " 张照片", null, "storage", null);
            }
        } catch (Exception e) {
            log.warn("相册刷新失败: albumId={}", album.getId(), e);
            p.put("status", "FAILED");
            p.put("message", "刷新失败: " + e.getMessage());
        } finally {
            p.put("finishedAt", System.currentTimeMillis());
            refreshing.remove(album.getId());
        }
    }

    /* ---------- 同步核心 ---------- */

    /** 同步一棵相册子树:列目录→影子照片入库→(递归)子目录建子相册;返回 [新增相册数, 新增照片数] */
    private int[] syncAlbumTree(SysUser user, Long familyId, StorageDevice device,
                                Album album, boolean recursive, Map<String, Object> p) {
        List<Map<String, Object>> items;
        try {
            items = storageService.browse(device, album.getSourcePath());
            album.setSyncStatus("VALID");
            album.setLastSyncedAt(LocalDateTime.now());
            albumMapper.updateById(album);
        } catch (Exception e) {
            album.setSyncStatus(classifyStatus(e));
            albumMapper.updateById(album);
            throw e instanceof RuntimeException re ? re : new IllegalStateException(e);
        }

        int photos = 0;
        Set<String> seen = new HashSet<>();
        String prefix = "dev:" + device.getId() + ":";
        for (Map<String, Object> item : items) {
            String name = String.valueOf(item.get("name"));
            boolean isDir = Boolean.TRUE.equals(item.get("isDir"));
            if (isDir) {
                if (recursive) {
                    String childPath = joinPath(device, album.getSourcePath(), name);
                    Album child = ensureChildAlbum(user, familyId, device, album, name, childPath);
                    photos += syncAlbumTree(user, familyId, device, child, true, p)[1];
                }
                continue;
            }
            if (!isImage(name)) continue;
            String filePath = joinPath(device, album.getSourcePath(), name);
            String key = prefix + filePath;
            seen.add(key);
            Long fsId = item.get("fsId") == null ? null : Long.valueOf(item.get("fsId").toString());
            long modified = item.get("modified") == null ? 0L : Long.parseLong(item.get("modified").toString());
            photos += upsertShadowPhoto(user, familyId, device, album, filePath, key, fsId, modified);
        }
        pruneShadowPhotos(album, prefix, seen);
        p.put("lastAlbum", album.getName());
        return new int[]{0, photos};
    }

    /** 新增或更新影子照片,返回新增数;fs_id 变化时回写(百度免列目录加速) */
    private int upsertShadowPhoto(SysUser user, Long familyId, StorageDevice device, Album album,
                                  String filePath, String key, Long fsId, long modified) {
        Photo exist = photoMapper.selectOne(new LambdaQueryWrapper<Photo>()
                .eq(Photo::getAlbumId, album.getId()).eq(Photo::getSourcePath, key).last("LIMIT 1"));
        if (exist != null) {
            if (fsId != null && !fsId.equals(exist.getSourceFsId())) {
                exist.setSourceFsId(fsId);
                photoMapper.updateById(exist);
            }
            return 0;
        }
        Photo ph = new Photo();
        ph.setAlbumId(album.getId());
        ph.setUrl("storage://" + device.getId() + "/" + filePath + (fsId == null ? "" : "?fsid=" + fsId));
        ph.setAuthorId(user.getId());
        ph.setFamilyId(familyId);
        ph.setVisibility("public".equals(album.getType()) ? DictConst.VIS_PUBLIC : DictConst.VIS_FAMILY);
        ph.setSourcePath(key);
        ph.setSourceFsId(fsId);
        if (modified > 0) {
            ph.setTakenAt(LocalDateTime.ofInstant(Instant.ofEpochMilli(modified), ZoneId.systemDefault()));
        }
        photoMapper.insert(ph);
        if (album.getCoverPhotoUrl() == null || album.getCoverPhotoUrl().isBlank()) {
            album.setCoverPhotoUrl(ph.getUrl());
            albumMapper.updateById(album);
        }
        return 1;
    }

    /** 刷新时清理设备侧已消失的影子记录(仅本设备的记录,用户上传的照片不受影响) */
    private void pruneShadowPhotos(Album album, String prefix, Set<String> seen) {
        List<Photo> photos = photoMapper.selectList(new LambdaQueryWrapper<Photo>()
                .eq(Photo::getAlbumId, album.getId()).likeRight(Photo::getSourcePath, prefix));
        for (Photo ph : photos) {
            if (!seen.contains(ph.getSourcePath())) photoMapper.deletePhysicalById(ph.getId());
        }
    }

    /** 顶层映射相册:同设备同路径幂等复用;名称与普通相册冲突时加设备名后缀 */
    private Album ensureRootAlbum(SysUser user, Long familyId, StorageDevice device, String path) {
        String name = basename(path);
        if (name.isEmpty()) throw new BizException(ResultCode.BAD_REQUEST, "不能同步根目录,请选择具体目录");
        Album exists = albumMapper.selectOne(new LambdaQueryWrapper<Album>()
                .eq(Album::getFamilyId, familyId)
                .eq(Album::getSourceDeviceId, device.getId())
                .eq(Album::getSourcePath, path).last("LIMIT 1"));
        if (exists != null) return exists;
        String finalName = resolveTopName(familyId, device, name);
        return insertAlbum(user, familyId, device, null, finalName, path);
    }

    /** 子相册:同层级同名且属同一映射则幂等复用;撞上普通相册则加设备名后缀 */
    private Album ensureChildAlbum(SysUser user, Long familyId, StorageDevice device,
                                   Album parent, String name, String childPath) {
        Album exists = albumMapper.selectOne(new LambdaQueryWrapper<Album>()
                .eq(Album::getFamilyId, familyId)
                .eq(Album::getParentId, parent.getId())
                .eq(Album::getName, name).last("LIMIT 1"));
        if (exists != null) {
            if (device.getId().equals(exists.getSourceDeviceId()) && childPath.equals(exists.getSourcePath())) {
                return exists;
            }
            exists = albumMapper.selectOne(new LambdaQueryWrapper<Album>()
                    .eq(Album::getFamilyId, familyId)
                    .eq(Album::getParentId, parent.getId())
                    .eq(Album::getName, name + "(" + device.getName() + ")").last("LIMIT 1"));
            if (exists != null) {
                if (device.getId().equals(exists.getSourceDeviceId()) && childPath.equals(exists.getSourcePath())) {
                    return exists;
                }
                throw new BizException(ResultCode.BAD_REQUEST, "相册名称冲突: " + name);
            }
            return insertAlbum(user, familyId, device, parent, name + "(" + device.getName() + ")", childPath);
        }
        return insertAlbum(user, familyId, device, parent, name, childPath);
    }

    private Album insertAlbum(SysUser user, Long familyId, StorageDevice device,
                              Album parent, String name, String path) {
        Album a = new Album();
        a.setName(name);
        a.setType("public");
        a.setShareToken(java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        a.setFamilyId(familyId);
        a.setParentId(parent == null ? null : parent.getId());
        a.setSourceDeviceId(device.getId());
        a.setSourcePath(path);
        a.setSyncStatus("SYNCING");
        a.setCreatedBy(user.getId());
        albumMapper.insert(a);
        return a;
    }

    /** 顶层名称冲突处理:与任意现有顶层相册同名 → 追加设备名 */
    private String resolveTopName(Long familyId, StorageDevice device, String name) {
        boolean conflict = albumMapper.selectCount(new LambdaQueryWrapper<Album>()
                .eq(Album::getFamilyId, familyId)
                .isNull(Album::getParentId)
                .eq(Album::getName, name)) > 0;
        return conflict ? name + "(" + device.getName() + ")" : name;
    }

    /** 路径拼接:百度保留前导 /,本地设备相对路径拼接 */
    private String joinPath(StorageDevice device, String parent, String name) {
        String p = parent == null ? "" : parent;
        if ("BAIDU".equals(device.getDeviceType())) {
            String base = p.isEmpty() || "/".equals(p) ? "" : p;
            return base + "/" + name;
        }
        return p.isEmpty() ? name : p + "/" + name;
    }

    private String basename(String path) {
        if (path == null) return "";
        String p = path.trim().replace('\\', '/');
        while (p.endsWith("/") && p.length() > 1) p = p.substring(0, p.length() - 1);
        int i = p.lastIndexOf('/');
        return i < 0 ? p : p.substring(i + 1);
    }

    private boolean isImage(String name) {
        String n = name.toLowerCase();
        for (String ext : IMAGE_EXTS) {
            if (n.endsWith(ext)) return true;
        }
        return false;
    }

    /** 失败分类:目录不存在(本地 NOT_FOUND / 百度 errno=-9)→ MISSING,其余 → OFFLINE */
    private String classifyStatus(Exception e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if (e instanceof BizException be && be.getCode() == ResultCode.NOT_FOUND.getCode()) return "MISSING";
        return msg.contains("errno=-9") || msg.contains("不存在") ? "MISSING" : "OFFLINE";
    }

    private StorageDevice requireSourceDevice(Album album) {
        if (album.getSourceDeviceId() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "普通相册不支持刷新,仅设备映射相册需要刷新");
        }
        StorageDevice device = storageDeviceMapper.selectById(album.getSourceDeviceId());
        if (device == null || !album.getFamilyId().equals(device.getFamilyId())) {
            throw new BizException(ResultCode.NOT_FOUND, "映射设备不存在");
        }
        return device;
    }
}
