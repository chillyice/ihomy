package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.annotation.RequirePermission;
import com.ihomy.common.Result;
import com.ihomy.entity.StorageDevice;
import com.ihomy.entity.SysUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 存储管理接口(家庭级):设备 CRUD、文件浏览/下载、一键同步。
 * 设备管理需 @storage:manage(OWNER),浏览/同步需登录。
 */
@Tag(name = "存储管理")
@RestController
@RequestMapping("/storage")
@RequiredArgsConstructor
public class StorageController {

    private final StorageService storageService;
    private final SecurityHelper securityHelper;

    private SysUser currentUser() {
        return securityHelper.currentUser();
    }

    private Long currentFamilyId() {
        return securityHelper.current().getFamilyId();
    }

    @Operation(summary = "家庭存储设备列表(含默认系统设备)")
    @GetMapping("/device/list")
    public Result<List<Map<String, Object>>> listDevices() {
        return Result.success(storageService.listDevices(currentFamilyId()));
    }

    @Operation(summary = "添加存储设备")
    @OperationLog(module = "STORAGE", operationType = "CREATE", description = "添加存储设备")
    @RequirePermission("storage:manage")
    @PostMapping("/device")
    public Result<StorageDevice> addDevice(@RequestBody Map<String, String> body) {
        StorageDevice d = storageService.addDevice(currentFamilyId(), body.get("name"),
                body.get("deviceType"), body.get("rootPath"), currentUser());
        return Result.success(d);
    }

    @Operation(summary = "修改存储设备")
    @OperationLog(module = "STORAGE", operationType = "UPDATE", description = "修改存储设备")
    @RequirePermission("storage:manage")
    @PutMapping("/device/{id}")
    public Result<Void> updateDevice(@PathVariable Long id, @RequestBody Map<String, String> body) {
        storageService.updateDevice(currentFamilyId(), id, body.get("name"), body.get("deviceType"), body.get("rootPath"));
        return Result.success();
    }

    @Operation(summary = "删除存储设备")
    @OperationLog(module = "STORAGE", operationType = "DELETE", description = "删除存储设备")
    @RequirePermission("storage:manage")
    @DeleteMapping("/device/{id}")
    public Result<Void> deleteDevice(@PathVariable Long id) {
        storageService.deleteDevice(currentFamilyId(), id);
        return Result.success();
    }

    @Operation(summary = "浏览设备目录(仅自定义设备)")
    @GetMapping("/browse")
    public Result<List<Map<String, Object>>> browse(@RequestParam(required = false, defaultValue = "0") Long deviceId,
                                                    @RequestParam(required = false) String path) {
        if (deviceId == null || deviceId == 0L) throw new com.ihomy.common.BizException(com.ihomy.common.ResultCode.BAD_REQUEST, "系统设备不支持文件浏览,请添加自定义存储设备");
        return Result.success(storageService.browse(storageService.getDevice(currentFamilyId(), deviceId), path));
    }

    @Operation(summary = "读取设备文件(预览/下载,仅自定义设备)")
    @GetMapping("/file")
    public ResponseEntity<byte[]> file(@RequestParam(required = false, defaultValue = "0") Long deviceId,
                                       @RequestParam String path,
                                       @RequestParam(required = false, defaultValue = "false") boolean download) {
        if (deviceId == null || deviceId == 0L) throw new com.ihomy.common.BizException(com.ihomy.common.ResultCode.BAD_REQUEST, "系统设备不支持文件浏览,请添加自定义存储设备");
        StorageDevice device = storageService.getDevice(currentFamilyId(), deviceId);
        byte[] bytes = storageService.readFileBytes(device, path);
        HttpHeaders headers = new HttpHeaders();
        String name = storageService.downloadName(device, path);
        if (download) {
            String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
            headers.setContentDispositionFormData("attachment", name);
            headers.set("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        } else {
            headers.setContentType(guessMediaType(name));
            headers.setCacheControl("private, max-age=3600");
        }
        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }

    private MediaType guessMediaType(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (n.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (n.endsWith(".gif")) return MediaType.IMAGE_GIF;
        if (n.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        if (n.endsWith(".bmp")) return MediaType.parseMediaType("image/bmp");
        if (n.endsWith(".mp4")) return MediaType.parseMediaType("video/mp4");
        if (n.endsWith(".mp3")) return MediaType.parseMediaType("audio/mpeg");
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    @Operation(summary = "一键同步:设备目录→相册(后台异步)")
    @OperationLog(module = "STORAGE", operationType = "CREATE", description = "一键同步存储设备", saveArgs = false)
    @RequirePermission("storage:manage")
    @PostMapping("/sync")
    public Result<Map<String, Long>> sync(@RequestBody Map<String, Object> body) {
        Long deviceId = body.get("deviceId") == null ? 0L : Long.valueOf(body.get("deviceId").toString());
        boolean includeEmpty = Boolean.TRUE.equals(body.get("includeEmpty"));
        StorageDevice device = storageService.getDevice(currentFamilyId(), deviceId);
        Long taskId = storageService.startSync(device, includeEmpty, currentUser());
        return Result.success(Map.of("taskId", taskId));
    }

    @Operation(summary = "同步进度")
    @GetMapping("/sync/progress/{taskId}")
    public Result<Map<String, Object>> progress(@PathVariable Long taskId) {
        return Result.success(storageService.syncProgress(taskId));
    }
}