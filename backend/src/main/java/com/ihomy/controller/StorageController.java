package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.annotation.RequirePermission;
import com.ihomy.common.Result;
import com.ihomy.entity.StorageDevice;
import com.ihomy.entity.SysUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.AlbumMapService;
import com.ihomy.service.SignedUrlService;
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
 * 存储管理接口(家庭级):设备 CRUD、文件浏览/下载、目录映射同步。
 * 设备管理与映射需 @storage:manage(OWNER),浏览需登录,签名中转免登录(签名即凭证)。
 */
@Tag(name = "存储管理")
@RestController
@RequestMapping("/storage")
@RequiredArgsConstructor
public class StorageController {

    private final StorageService storageService;
    private final AlbumMapService albumMapService;
    private final SignedUrlService signedUrlService;
    private final com.ihomy.service.ThumbnailService thumbnailService;
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
    public ResponseEntity<?> file(@RequestParam(required = false, defaultValue = "0") Long deviceId,
                                  @RequestParam String path,
                                  @RequestParam(required = false, defaultValue = "false") boolean download) {
        if (deviceId == null || deviceId == 0L) throw new com.ihomy.common.BizException(com.ihomy.common.ResultCode.BAD_REQUEST, "系统设备不支持文件浏览,请添加自定义存储设备");
        StorageDevice device = storageService.getDevice(currentFamilyId(), deviceId);
        return streamFromDevice(device, path, null, download, false);
    }

    @Operation(summary = "签名中转读取设备文件(img/video 标签专用,签名即凭证,10 分钟有效;thumb=1 返回 480px 缓存缩略图)")
    @GetMapping("/file-signed")
    public ResponseEntity<?> fileSigned(@RequestParam Long deviceId,
                                        @RequestParam String path,
                                        @RequestParam(required = false) Long fsId,
                                        @RequestParam long exp,
                                        @RequestParam String sig,
                                        @RequestParam(required = false, defaultValue = "false") boolean download,
                                        @RequestParam(required = false, defaultValue = "false") boolean thumb) {
        if (!signedUrlService.verify(deviceId, path, fsId, exp, sig)) {
            throw new com.ihomy.common.BizException(com.ihomy.common.ResultCode.UNAUTHORIZED, "链接已过期或签名无效");
        }
        StorageDevice device = storageService.getDeviceById(deviceId);
        if (device == null) throw new com.ihomy.common.BizException(com.ihomy.common.ResultCode.NOT_FOUND, "存储设备不存在");
        return streamFromDevice(device, path, fsId, download, thumb);
    }

    /** 设备文件流式返回:thumb=图片缩略图缓存;百度走 dlink 中转(InputStreamResource 不缓冲),本地/挂载读盘 */
    private ResponseEntity<?> streamFromDevice(StorageDevice device, String path, Long fsId,
                                               boolean download, boolean thumb) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        // 网格缩略图:命中缓存秒回,未命中下载原图生成;HEIC 等不可读格式回退原图
        if (thumb && !download && isImageName(name)) {
            byte[] thumbBytes = thumbnailService.thumb(device, path, fsId);
            if (thumbBytes != null) {
                HttpHeaders h = new HttpHeaders();
                h.setContentType(MediaType.IMAGE_JPEG);
                h.setCacheControl("private, max-age=86400");
                h.setContentLength(thumbBytes.length);
                return new ResponseEntity<>(thumbBytes, h, HttpStatus.OK);
            }
        }
        HttpHeaders headers = new HttpHeaders();
        if (download) {
            String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
            headers.set("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        } else {
            headers.setContentType(guessMediaType(name));
            headers.setCacheControl("private, max-age=3600");
        }
        if ("BAIDU".equals(device.getDeviceType())) {
            StorageService.BaiduFileStream fs = storageService.baiduOpen(device, path, fsId);
            if (fs.length() > 0) headers.setContentLength(fs.length());
            return new ResponseEntity<>(new org.springframework.core.io.InputStreamResource(fs.in()), headers, HttpStatus.OK);
        }
        // 本地/挂载设备返回 PathResource 流式输出:大视频不全量入堆,Spring 对 Resource 自动支持 Range 请求(拖进度条)
        org.springframework.core.io.PathResource res =
                new org.springframework.core.io.PathResource(storageService.resolveLocalFile(device, path));
        return new ResponseEntity<>(res, headers, HttpStatus.OK);
    }

    private boolean isImageName(String name) {
        String n = name.toLowerCase();
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".gif")
                || n.endsWith(".webp") || n.endsWith(".bmp");
    }

    private MediaType guessMediaType(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (n.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (n.endsWith(".gif")) return MediaType.IMAGE_GIF;
        if (n.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        if (n.endsWith(".bmp")) return MediaType.parseMediaType("image/bmp");
        if (n.endsWith(".mp4") || n.endsWith(".m4v")) return MediaType.parseMediaType("video/mp4");
        if (n.endsWith(".mkv")) return MediaType.parseMediaType("video/x-matroska");
        if (n.endsWith(".webm")) return MediaType.parseMediaType("video/webm");
        if (n.endsWith(".mov")) return MediaType.parseMediaType("video/quicktime");
        if (n.endsWith(".avi")) return MediaType.parseMediaType("video/x-msvideo");
        if (n.endsWith(".mp3")) return MediaType.parseMediaType("audio/mpeg");
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    @Operation(summary = "百度网盘接入凭证(密钥不回传,仅返回是否已配置)")
    @RequirePermission("storage:manage")
    @GetMapping("/baidu/credential")
    public Result<Map<String, Object>> baiduCredential() {
        return Result.success(storageService.getBaiduCredential(currentFamilyId()));
    }

    @Operation(summary = "保存百度网盘接入凭证(密钥加密入库,留空保留原值)")
    @OperationLog(module = "STORAGE", operationType = "UPDATE", description = "保存百度网盘凭证", saveArgs = false)
    @RequirePermission("storage:manage")
    @PutMapping("/baidu/credential")
    public Result<Void> saveBaiduCredential(@RequestBody Map<String, String> body) {
        storageService.saveBaiduCredential(currentFamilyId(), body.get("appId"), body.get("appKey"),
                body.get("secretKey"), body.get("signKey"));
        return Result.success();
    }

    @Operation(summary = "生成百度网盘 OAuth 授权跳转 URL(state 绑定家庭,10 分钟有效)")
    @RequirePermission("storage:manage")
    @GetMapping("/baidu/auth/url")
    public Result<Map<String, String>> baiduAuthUrl(@RequestParam String redirectUri) {
        return Result.success(Map.of("url", storageService.getBaiduAuthUrl(currentFamilyId(), redirectUri)));
    }

    @Operation(summary = "百度网盘 OAuth 授权回调:授权码换 token 并加密存储")
    @OperationLog(module = "STORAGE", operationType = "UPDATE", description = "百度网盘OAuth授权", saveArgs = false)
    @RequirePermission("storage:manage")
    @PostMapping("/baidu/auth/callback")
    public Result<Void> baiduAuthCallback(@RequestBody Map<String, String> body) {
        storageService.baiduAuthCallback(currentFamilyId(), body.get("code"), body.get("state"), body.get("redirectUri"));
        return Result.success();
    }

    @Operation(summary = "从设备同步:勾选目录映射为层级相册(影子记录,不拷贝文件)")
    @OperationLog(module = "STORAGE", operationType = "CREATE", description = "设备目录映射同步", saveArgs = false)
    @RequirePermission("storage:manage")
    @PostMapping("/map")
    public Result<Map<String, Long>> map(@RequestBody Map<String, Object> body) {
        Long deviceId = body.get("deviceId") == null ? 0L : Long.valueOf(body.get("deviceId").toString());
        @SuppressWarnings("unchecked")
        List<String> paths = (List<String>) body.get("paths");
        Long taskId = albumMapService.createMapping(currentUser(), currentFamilyId(), deviceId, paths);
        return Result.success(Map.of("taskId", taskId));
    }

    @Operation(summary = "清空设备缩略图缓存(下次打开相册重新生成)")
    @OperationLog(module = "STORAGE", operationType = "DELETE", description = "清理缩略图缓存")
    @RequirePermission("storage:manage")
    @DeleteMapping("/thumbs")
    public Result<Integer> clearThumbs() {
        return Result.success(thumbnailService.clearCache());
    }

    @Operation(summary = "同步/映射任务进度")
    @GetMapping("/sync/progress/{taskId}")
    public Result<Map<String, Object>> progress(@PathVariable Long taskId) {        return Result.success(albumMapService.progress(taskId));
    }
}