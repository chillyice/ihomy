package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.common.BizException;
import com.ihomy.common.Result;
import com.ihomy.common.ResultCode;
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
 * 内容分享范围(「给谁看」)接口:照片/相册/博客/日记/视频/愿望/图书共用一套路径,
 * {@code type} 与 {@link ContentKind#code()} 同口径。保险箱永不进圈。
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

    @Operation(summary = "查看某条内容的分享范围")
    @GetMapping("/{type}/{id}/audience")
    public Result<Map<String, Object>> view(@PathVariable String type, @PathVariable Long id) {
        return Result.success(audienceService.view(kind(type), id, viewerScopeResolver.resolve()));
    }

    @Operation(summary = "设置某条内容的分享范围(空=只给自家人)")
    @OperationLog(module = "SHARE", operationType = "UPDATE", description = "设置内容分享范围")
    @PutMapping("/{type}/{id}/audience")
    public Result<Map<String, Object>> set(@PathVariable String type, @PathVariable Long id, @RequestBody AudienceDTO dto) {
        return Result.success(audienceService.set(kind(type), id, dto.getCircleIds(), dto.getTargetFamilyIds(),
                securityHelper.currentUser(), viewerScopeResolver.resolve()));
    }

    @Operation(summary = "收回某条内容的分享(不再给任何外部家庭看)")
    @OperationLog(module = "SHARE", operationType = "DELETE", description = "收回内容分享")
    @DeleteMapping("/{type}/{id}/audience")
    public Result<Void> revoke(@PathVariable String type, @PathVariable Long id) {
        audienceService.revokeOne(kind(type), id, viewerScopeResolver.resolve());
        return Result.success();
    }

    /** 路径里的类型码 → 内容类型;保险箱/智能家居与未知类型一律拒绝(不暴露内部枚举) */
    private ContentKind kind(String type) {
        for (ContentKind k : ContentKind.values()) {
            if (k != ContentKind.VAULT && k != ContentKind.IOT_DEVICE && k.code().equalsIgnoreCase(type)) return k;
        }
        throw new BizException(ResultCode.BAD_REQUEST, "这个内容暂时还不能分享到亲友圈");
    }
}
