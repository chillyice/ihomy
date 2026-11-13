package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.common.Result;
import com.ihomy.dto.AudienceDTO;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.AudienceService;
import com.ihomy.service.access.ContentKind;
import com.ihomy.service.access.ViewerScopeResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 内容分享范围(「给谁看」)接口:照片/相册先接入,其余内容类型按阶段 C 补齐。
 * 只有内容作者本人(或家长按家庭策略)能改,跨家庭一律 404;接收家庭只读。
 */
@Tag(name = "内容分享")
@RestController
@RequestMapping
@RequiredArgsConstructor
public class AudienceController {

    private final AudienceService audienceService;
    private final ViewerScopeResolver viewerScopeResolver;
    private final SecurityHelper securityHelper;

    @Operation(summary = "照片的分享范围")
    @GetMapping("/photo/{id}/audience")
    public Result<Map<String, Object>> photoAudience(@PathVariable Long id) {
        return Result.success(audienceService.view(ContentKind.PHOTO, id, viewerScopeResolver.resolve()));
    }

    @Operation(summary = "设置照片的分享范围(空=只给自家人)")
    @OperationLog(module = "PHOTO", operationType = "UPDATE", description = "设置照片分享范围")
    @PutMapping("/photo/{id}/audience")
    public Result<Map<String, Object>> setPhotoAudience(@PathVariable Long id, @RequestBody AudienceDTO dto) {
        return Result.success(audienceService.set(ContentKind.PHOTO, id, dto.getCircleIds(),
                securityHelper.currentUser(), viewerScopeResolver.resolve()));
    }

    @Operation(summary = "收回照片分享(不再给任何外部家庭看)")
    @OperationLog(module = "PHOTO", operationType = "DELETE", description = "收回照片分享")
    @DeleteMapping("/photo/{id}/audience")
    public Result<Void> revokePhotoAudience(@PathVariable Long id) {
        audienceService.revokeOne(ContentKind.PHOTO, id, viewerScopeResolver.resolve());
        return Result.success();
    }

    @Operation(summary = "相册的分享范围")
    @GetMapping("/album/{id}/audience")
    public Result<Map<String, Object>> albumAudience(@PathVariable Long id) {
        return Result.success(audienceService.view(ContentKind.ALBUM, id, viewerScopeResolver.resolve()));
    }

    @Operation(summary = "设置相册的分享范围(空=只给自家人)")
    @OperationLog(module = "ALBUM", operationType = "UPDATE", description = "设置相册分享范围")
    @PutMapping("/album/{id}/audience")
    public Result<Map<String, Object>> setAlbumAudience(@PathVariable Long id, @RequestBody AudienceDTO dto) {
        return Result.success(audienceService.set(ContentKind.ALBUM, id, dto.getCircleIds(),
                securityHelper.currentUser(), viewerScopeResolver.resolve()));
    }

    @Operation(summary = "收回相册分享")
    @OperationLog(module = "ALBUM", operationType = "DELETE", description = "收回相册分享")
    @DeleteMapping("/album/{id}/audience")
    public Result<Void> revokeAlbumAudience(@PathVariable Long id) {
        audienceService.revokeOne(ContentKind.ALBUM, id, viewerScopeResolver.resolve());
        return Result.success();
    }
}
