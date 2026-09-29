package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.annotation.RequirePermission;
import com.ihomy.common.Result;
import com.ihomy.common.ResultCode;
import com.ihomy.common.BizException;
import com.ihomy.dto.MediaProgressDTO;
import com.ihomy.dto.MediaServerDTO;
import com.ihomy.entity.SysUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.JellyfinService;
import com.ihomy.service.SignedUrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 放映厅媒体引擎接口(家庭级):
 * 配置管理(家长)/ 引擎状态 / 作品海报墙与详情 / 播放地址 / 观看状态 / 海报中转。
 * 家庭隔离:所有接口按当前登录用户的家庭取配置,条目 id 一律只在该家庭的媒体服务器里解析。
 */
@Tag(name = "放映厅-媒体引擎")
@RestController
@RequestMapping("/media")
@RequiredArgsConstructor
public class MediaController {

    private final JellyfinService jellyfinService;
    private final SignedUrlService signedUrlService;
    private final SecurityHelper securityHelper;

    private Long familyId() {
        SysUser user = securityHelper.currentUser();
        return user == null ? null : user.getFamilyId();
    }

    // ==================== 配置(家长) ====================

    @Operation(summary = "读取放映厅引擎配置(密码只回是否已设置)")
    @RequirePermission("storage:manage")
    @GetMapping("/config")
    public Result<Map<String, Object>> config() {
        return Result.success(jellyfinService.getConfig(familyId()));
    }

    @Operation(summary = "保存放映厅引擎配置")
    @OperationLog(module = "MEDIA", operationType = "UPDATE", description = "保存放映厅引擎配置", saveArgs = false)
    @RequirePermission("storage:manage")
    @PutMapping("/config")
    public Result<Map<String, Object>> saveConfig(@RequestBody MediaServerDTO dto) {
        return Result.success(jellyfinService.saveConfig(familyId(), securityHelper.currentUserId(), dto));
    }

    @Operation(summary = "移除放映厅引擎配置")
    @OperationLog(module = "MEDIA", operationType = "DELETE", description = "移除放映厅引擎配置", saveArgs = false)
    @RequirePermission("storage:manage")
    @DeleteMapping("/config")
    public Result<Void> removeConfig() {
        jellyfinService.removeConfig(familyId());
        return Result.success();
    }

    @Operation(summary = "测试放映厅引擎连通性(未填的项沿用已保存配置)")
    @OperationLog(module = "MEDIA", operationType = "QUERY", description = "测试放映厅引擎连通性", saveArgs = false)
    @RequirePermission("storage:manage")
    @PostMapping("/test")
    public Result<Map<String, Object>> test(@RequestBody(required = false) MediaServerDTO dto) {
        return Result.success(jellyfinService.testConnection(familyId(),
                dto == null ? new MediaServerDTO() : dto));
    }

    // ==================== 内容 ====================

    @Operation(summary = "放映厅引擎状态(未配置/连接状态/媒体库与数量)")
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.success(jellyfinService.status(familyId()));
    }

    @Operation(summary = "媒体库作品列表(电影+剧集)")
    @GetMapping("/works")
    public Result<List<Map<String, Object>>> works() {
        return Result.success(jellyfinService.works(familyId()));
    }

    @Operation(summary = "媒体库作品详情(剧集含分季分集)")
    @GetMapping("/works/{itemId}")
    public Result<Map<String, Object>> work(@PathVariable String itemId) {
        return Result.success(jellyfinService.workDetail(familyId(), itemId));
    }

    @Operation(summary = "获取播放地址")
    @GetMapping("/works/{itemId}/play")
    public Result<Map<String, Object>> play(@PathVariable String itemId) {
        return Result.success(jellyfinService.playUrl(familyId(), itemId));
    }

    @Operation(summary = "标记看过/取消看过")
    @OperationLog(module = "MEDIA", operationType = "UPDATE", description = "标记作品观看状态")
    @PostMapping("/works/{itemId}/played")
    public Result<Void> played(@PathVariable String itemId, @RequestBody Map<String, Object> body) {
        boolean played = body.get("played") == null || Boolean.parseBoolean(body.get("played").toString());
        jellyfinService.markPlayed(familyId(), itemId, played);
        return Result.success();
    }

    /** 播放器按节流上报进度,属高频噪音端点,不写操作日志 */
    @Operation(summary = "上报观看进度(继续观看用)")
    @PostMapping("/works/{itemId}/progress")
    public Result<Void> progress(@PathVariable String itemId, @RequestBody MediaProgressDTO dto) {
        jellyfinService.reportProgress(familyId(), itemId, dto);
        return Result.success();
    }

    @Operation(summary = "继续观看列表")
    @GetMapping("/resume")
    public Result<List<Map<String, Object>>> resume() {
        return Result.success(jellyfinService.resume(familyId()));
    }

    // ==================== 海报中转(签名免登录) ====================

    /**
     * 海报/剧照中转:URL 自带 HMAC 签名,供 &lt;img&gt; 直接引用(不带 JWT),
     * 也让媒体服务器令牌只留在后端。缓存交给浏览器(图片本身按条目 id 与图片标记固定)。
     */
    @Operation(summary = "读取作品海报(签名 URL)")
    @GetMapping("/image-signed")
    public ResponseEntity<byte[]> imageSigned(@RequestParam Long familyId,
                                              @RequestParam String itemId,
                                              @RequestParam(required = false, defaultValue = "Primary") String type,
                                              @RequestParam(required = false) Integer maxWidth,
                                              @RequestParam long exp,
                                              @RequestParam String sig) {
        if (!signedUrlService.verifyMediaImage(familyId, itemId, type, maxWidth, exp, sig)) {
            throw new BizException(ResultCode.UNAUTHORIZED, "链接已过期或签名无效");
        }
        JellyfinService.Image image = jellyfinService.image(familyId, itemId, type, maxWidth);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .body(image.data());
    }
}
