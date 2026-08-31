package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.DictConst;
import com.ihomy.common.ResultCode;
import com.ihomy.entity.StorageDevice;
import com.ihomy.entity.SysUser;
import com.ihomy.entity.Video;
import com.ihomy.mapper.StorageDeviceMapper;
import com.ihomy.mapper.VideoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 设备目录→放映厅视频映射(平铺模式):勾选设备目录后递归扫描,视频文件以影子记录入库
 * (video_url 存 storage:// 逻辑地址,文件本体不拷贝),播放经 /storage/file-signed 签名中转。
 * 无层级容器(目录结构后续设计),来源经 source_device_id/source_dir 标记,列表页按来源筛选。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoMapService {

    private static final String[] VIDEO_EXTS = {".mp4", ".mkv", ".avi", ".mov", ".wmv",
            ".flv", ".webm", ".m4v", ".ts", ".rmvb", ".mpg", ".mpeg", ".3gp"};

    private final VideoMapper videoMapper;
    private final StorageDeviceMapper storageDeviceMapper;
    private final StorageService storageService;
    private final NotificationService notificationService;
    private final MapTaskRegistry taskRegistry;

    /** @Lazy 断开与 Runner 的构造器循环 */
    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private VideoMapRunner runner;

    public Map<String, Object> progress(Long taskId) {
        return taskRegistry.progress(taskId);
    }

    /** 创建映射:勾选的目录 → 递归扫描影子视频(异步任务) */
    public Long createMapping(SysUser user, Long familyId, Long deviceId, List<String> paths) {
        if (paths == null || paths.isEmpty()) throw new BizException(ResultCode.BAD_REQUEST, "请选择要同步的目录");
        StorageDevice device = storageService.getDevice(familyId, deviceId);
        Long taskId = System.nanoTime();
        runner.runMapping(taskId, user, familyId, device, paths);
        return taskId;
    }

    /** 刷新全部映射:按 source_dir 反查已映射目录,逐设备重扫(异步任务) */
    public Long refreshAll(SysUser user, Long familyId) {
        List<Video> rows = videoMapper.selectList(new LambdaQueryWrapper<Video>()
                .eq(Video::getFamilyId, familyId)
                .eq(Video::getDeleted, 0)
                .isNotNull(Video::getSourceDeviceId)
                .isNotNull(Video::getSourceDir)
                .select(Video::getSourceDeviceId, Video::getSourceDir));
        Map<Long, Set<String>> dirsByDevice = new LinkedHashMap<>();
        for (Video v : rows) {
            dirsByDevice.computeIfAbsent(v.getSourceDeviceId(), k -> new LinkedHashSet<>()).add(v.getSourceDir());
        }
        if (dirsByDevice.isEmpty()) throw new BizException(ResultCode.BAD_REQUEST, "没有设备映射的视频,请先从设备同步");
        Long taskId = System.nanoTime();
        runner.runRefresh(taskId, user, familyId, dirsByDevice);
        return taskId;
    }

    /* ---------- 任务体(由 VideoMapRunner @Async 调用) ---------- */

    public void executeMapping(Long taskId, SysUser user, Long familyId, StorageDevice device, List<String> paths) {
        Map<String, Object> p = taskRegistry.begin(taskId);
        p.put("status", "RUNNING");
        p.put("totalDirs", paths.size());
        int done = 0, videos = 0, failed = 0;
        try {
            for (String path : paths) {
                try {
                    videos += scanRoot(user, familyId, device, path);
                } catch (Exception e) {
                    failed++;
                    log.warn("映射目录失败: device={}, path={}", device.getId(), path, e);
                }
                done++;
                p.put("doneDirs", done);
            }
            p.put("status", "DONE");
            p.put("message", failed == 0 ? "视频同步完成: 新增 " + videos + " 个"
                    : "视频同步完成: 新增 " + videos + " 个, " + failed + " 个目录失败");
            p.put("videos", videos);
            notificationService.create(user.getId(), "system",
                    "视频同步完成: 新增 " + videos + " 个", null, "storage", null);
        } catch (Exception e) {
            log.error("视频映射任务失败", e);
            p.put("status", "FAILED");
            p.put("message", "同步失败: " + e.getMessage());
            notificationService.create(user.getId(), "system",
                    "视频同步失败: " + e.getMessage(), null, "storage", null);
        } finally {
            p.put("finishedAt", System.currentTimeMillis());
        }
    }

    public void executeRefresh(Long taskId, SysUser user, Long familyId, Map<Long, Set<String>> dirsByDevice) {
        Map<String, Object> p = taskRegistry.begin(taskId);
        p.put("status", "RUNNING");
        int total = dirsByDevice.values().stream().mapToInt(Set::size).sum();
        p.put("totalDirs", total);
        int done = 0, videos = 0;
        try {
            for (Map.Entry<Long, Set<String>> e : dirsByDevice.entrySet()) {
                StorageDevice device = storageDeviceMapper.selectById(e.getKey());
                if (device == null || !familyId.equals(device.getFamilyId())) {
                    done += e.getValue().size();
                    continue;
                }
                for (String dir : e.getValue()) {
                    try {
                        videos += scanRoot(user, familyId, device, dir);
                    } catch (Exception ex) {
                        // 目录扫不到:该目录下影子记录标 MISSING/OFFLINE,不清记录(设备可能临时离线)
                        markDirStatus(familyId, device, dir, classifyStatus(ex));
                        log.warn("刷新目录失败: device={}, dir={}", device.getId(), dir, ex);
                    }
                    done++;
                    p.put("doneDirs", done);
                }
            }
            p.put("status", "DONE");
            p.put("message", "刷新完成: 新增视频 " + videos + " 个");
            p.put("videos", videos);
            if (user != null) {
                notificationService.create(user.getId(), "system",
                        "视频库刷新完成: 新增 " + videos + " 个", null, "storage", null);
            }
        } catch (Exception e) {
            log.error("视频刷新任务失败", e);
            p.put("status", "FAILED");
            p.put("message", "刷新失败: " + e.getMessage());
        } finally {
            p.put("finishedAt", System.currentTimeMillis());
        }
    }

    /* ---------- 扫描核心 ---------- */

    /** 扫描一个映射根目录(递归子目录):upsert 影子视频 + prune 消失记录;返回新增数 */
    private int scanRoot(SysUser user, Long familyId, StorageDevice device, String rootDir) {
        if (basename(rootDir).isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "不能同步根目录,请选择具体目录");
        }
        Set<String> seen = new HashSet<>();
        int added = scanTree(user, familyId, device, rootDir, rootDir, seen);
        pruneVanished(familyId, device, rootDir, seen);
        markDirStatus(familyId, device, rootDir, "VALID");
        return added;
    }

    /** 递归扫描:目录下钻,视频文件 upsert(source_dir 记映射根目录);seen 收集整个子树供 prune */
    private int scanTree(SysUser user, Long familyId, StorageDevice device,
                         String rootDir, String dirPath, Set<String> seen) {
        List<Map<String, Object>> items = storageService.browse(device, dirPath);
        int added = 0;
        for (Map<String, Object> item : items) {
            String name = String.valueOf(item.get("name"));
            if (Boolean.TRUE.equals(item.get("isDir"))) {
                added += scanTree(user, familyId, device, rootDir, joinPath(device, dirPath, name), seen);
                continue;
            }
            if (!isVideo(name)) continue;
            String filePath = joinPath(device, dirPath, name);
            seen.add("dev:" + device.getId() + ":" + filePath);
            Long fsId = item.get("fsId") == null ? null : Long.valueOf(item.get("fsId").toString());
            added += upsertShadowVideo(user, familyId, device, rootDir, filePath, fsId);
        }
        return added;
    }

    /** 新增或更新影子视频,返回新增数;fs_id 变化时回写(百度免列目录加速) */
    private int upsertShadowVideo(SysUser user, Long familyId, StorageDevice device,
                                  String sourceDir, String filePath, Long fsId) {
        String key = "dev:" + device.getId() + ":" + filePath;
        Video exist = videoMapper.selectOne(new LambdaQueryWrapper<Video>()
                .eq(Video::getSourcePath, key).last("LIMIT 1"));
        if (exist != null) {
            if (fsId != null && !fsId.equals(exist.getSourceFsId())) {
                exist.setSourceFsId(fsId);
                videoMapper.updateById(exist);
            }
            return 0;
        }
        Video v = new Video();
        v.setTitle(stripExt(basename(filePath)));
        v.setMediaType("other");
        v.setVideoUrl("storage://" + device.getId() + "/" + filePath + (fsId == null ? "" : "?fsid=" + fsId));
        v.setUploaderId(user.getId());
        v.setFamilyId(familyId);
        v.setVisibility(DictConst.VIS_FAMILY);
        v.setSourceDeviceId(device.getId());
        v.setSourcePath(key);
        v.setSourceFsId(fsId);
        v.setSourceDir(sourceDir);
        v.setSyncStatus("VALID");
        v.setDeleted(0);
        videoMapper.insert(v);
        return 1;
    }

    /** 刷新时清理设备侧已消失的影子记录(仅本设备该目录子树下的,手动上传的视频不受影响) */
    private void pruneVanished(Long familyId, StorageDevice device, String rootDir, Set<String> seen) {
        String prefix = "dev:" + device.getId() + ":" + rootDir + "/";
        List<Video> rows = videoMapper.selectList(new LambdaQueryWrapper<Video>()
                .eq(Video::getFamilyId, familyId)
                .eq(Video::getDeleted, 0)
                .likeRight(Video::getSourcePath, prefix));
        List<Long> vanished = new ArrayList<>();
        for (Video v : rows) {
            if (!seen.contains(v.getSourcePath())) vanished.add(v.getId());
        }
        for (Long id : vanished) videoMapper.deletePhysicalById(id);
    }

    /** 整目录状态回写(VALID 扫描成功 / MISSING 目录没了 / OFFLINE 设备离线) */
    private void markDirStatus(Long familyId, StorageDevice device, String dir, String status) {
        videoMapper.update(null, new LambdaUpdateWrapper<Video>()
                .eq(Video::getFamilyId, familyId)
                .eq(Video::getDeleted, 0)
                .eq(Video::getSourceDeviceId, device.getId())
                .eq(Video::getSourceDir, dir)
                .set(Video::getSyncStatus, status));
    }

    /* ---------- 工具 ---------- */

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

    private String stripExt(String name) {
        int i = name.lastIndexOf('.');
        return i <= 0 ? name : name.substring(0, i);
    }

    private boolean isVideo(String name) {
        String n = name.toLowerCase();
        for (String ext : VIDEO_EXTS) {
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
}
