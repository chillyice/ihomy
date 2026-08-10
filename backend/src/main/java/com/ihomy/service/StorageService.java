package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.entity.StorageDevice;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.StorageDeviceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 存储设备管理服务(家庭级):默认系统设备(本地磁盘)恒在列表首位,其余为自定义设备。
 */
@Service
@RequiredArgsConstructor
public class StorageService {

    private final StorageDeviceMapper storageDeviceMapper;
    private final StorageSyncRunner syncRunner;

    @Value("${file.upload-dir}")
    private String uploadDir;

    /* ---------- 设备 CRUD ---------- */

    public List<Map<String, Object>> listDevices(Long familyId) {
        List<Map<String, Object>> result = new ArrayList<>();
        result.add(systemDevice());
        storageDeviceMapper.selectList(new LambdaQueryWrapper<StorageDevice>()
                        .eq(StorageDevice::getFamilyId, familyId).orderByAsc(StorageDevice::getId))
                .forEach(d -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", d.getId());
                    m.put("name", d.getName());
                    m.put("deviceType", d.getDeviceType());
                    m.put("rootPath", d.getRootPath());
                    m.put("status", d.getStatus());
                    result.add(m);
                });
        return result;
    }

    private Map<String, Object> systemDevice() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", 0L);
        m.put("name", "系统(本地磁盘)");
        m.put("deviceType", "SYSTEM");
        m.put("rootPath", uploadDir);
        m.put("status", "ACTIVE");
        return m;
    }

    /** 取设备;id=0 或 null 返回 null(即系统默认设备) */
    public StorageDevice getDevice(Long familyId, Long deviceId) {
        if (deviceId == null || deviceId == 0L) return null;
        StorageDevice d = storageDeviceMapper.selectById(deviceId);
        if (d == null || !d.getFamilyId().equals(familyId)) {
            throw new BizException(ResultCode.NOT_FOUND, "存储设备不存在");
        }
        return d;
    }

    public StorageDevice addDevice(Long familyId, String name, String deviceType, String rootPath, SysUser user) {
        if (name == null || name.isBlank() || rootPath == null || rootPath.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "设备名称与路径不能为空");
        }
        Path root = Paths.get(rootPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new BizException(ResultCode.BAD_REQUEST, "路径不存在或不是目录: " + root);
        }
        StorageDevice d = new StorageDevice();
        d.setFamilyId(familyId);
        d.setName(name.trim());
        d.setDeviceType(deviceType == null || deviceType.isBlank() ? "NAS" : deviceType);
        d.setRootPath(root.toString());
        d.setStatus("ACTIVE");
        d.setCreatedBy(user == null ? null : user.getId());
        storageDeviceMapper.insert(d);
        return d;
    }

    public void updateDevice(Long familyId, Long deviceId, String name, String deviceType, String rootPath) {
        StorageDevice d = getDevice(familyId, deviceId);
        if (name != null && !name.isBlank()) d.setName(name.trim());
        if (deviceType != null && !deviceType.isBlank()) d.setDeviceType(deviceType);
        if (rootPath != null && !rootPath.isBlank()) {
            Path root = Paths.get(rootPath).toAbsolutePath().normalize();
            if (!Files.isDirectory(root)) {
                throw new BizException(ResultCode.BAD_REQUEST, "路径不存在或不是目录: " + root);
            }
            d.setRootPath(root.toString());
        }
        storageDeviceMapper.updateById(d);
    }

    public void deleteDevice(Long familyId, Long deviceId) {
        storageDeviceMapper.deleteById(getDevice(familyId, deviceId).getId());
    }

    /* ---------- 文件浏览/读取(防路径遍历) ---------- */

    private Path deviceRoot(StorageDevice device) {
        return Paths.get(device == null ? uploadDir : device.getRootPath()).toAbsolutePath().normalize();
    }

    private Path resolveSafe(Path root, String rel) {
        String r = rel == null ? "" : rel.trim().replace('\\', '/');
        if (r.startsWith("/")) r = r.substring(1);
        Path target = root.resolve(r).normalize();
        if (!target.startsWith(root)) {
            throw new BizException(ResultCode.BAD_REQUEST, "非法路径");
        }
        return target;
    }

    public List<Map<String, Object>> browse(StorageDevice device, String path) {
        Path root = deviceRoot(device);
        Path dir = resolveSafe(root, path);
        if (!Files.isDirectory(dir)) {
            throw new BizException(ResultCode.NOT_FOUND, "目录不存在");
        }
        List<Map<String, Object>> items = new ArrayList<>();
        try (var stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", p.getFileName().toString());
                m.put("isDir", Files.isDirectory(p));
                m.put("size", Files.isDirectory(p) ? null : Files.size(p));
                m.put("modified", Files.getLastModifiedTime(p).toMillis());
                items.add(m);
            }
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "读取目录失败: " + e.getMessage());
        }
        items.sort((a, b) -> {
            boolean da = (Boolean) a.get("isDir"), db = (Boolean) b.get("isDir");
            if (da != db) return da ? -1 : 1;
            return ((String) a.get("name")).compareToIgnoreCase((String) b.get("name"));
        });
        return items;
    }

    public byte[] readFileBytes(StorageDevice device, String path) {
        Path root = deviceRoot(device);
        Path f = resolveSafe(root, path);
        if (!Files.isRegularFile(f)) {
            throw new BizException(ResultCode.NOT_FOUND, "文件不存在");
        }
        try {
            return Files.readAllBytes(f);
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "读取文件失败");
        }
    }

    public String downloadName(StorageDevice device, String path) {
        return resolveSafe(deviceRoot(device), path).getFileName().toString();
    }

    /* ---------- 一键同步 ---------- */

    public Long startSync(StorageDevice device, boolean includeEmpty, SysUser user) {
        Long taskId = System.nanoTime();
        syncRunner.run(device, includeEmpty, user, taskId);
        return taskId;
    }

    public Map<String, Object> syncProgress(Long taskId) {
        return syncRunner.progress(taskId);
    }
}