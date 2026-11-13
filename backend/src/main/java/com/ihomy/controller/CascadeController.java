package com.ihomy.controller;

import com.ihomy.common.Result;
import com.ihomy.mapper.PhotoMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 照片瀑布接口:随机返回当前家庭照片(含上传者昵称/描述/地点),供首页飘落动效与全屏浏览。
 * 可见范围由 ViewerScopeResolver + ContentAccessService 统一判定(游客=演示家庭公开)。
 */
@Tag(name = "照片瀑布")
@RestController
@RequestMapping("/photo")
@RequiredArgsConstructor
public class CascadeController {

    private final PhotoMapper photoMapper;
    private final com.ihomy.service.SignedUrlService signedUrlService;
    private final com.ihomy.service.AudienceService audienceService;
    private final com.ihomy.service.access.ViewerScopeResolver viewerScopeResolver;
    private final com.ihomy.service.access.ContentAccessService contentAccess;

    @Operation(summary = "随机照片瀑布流(当前家庭,登录可用)")
    @GetMapping("/cascade")
    public Result<List<Map<String, Object>>> cascade(@RequestParam(defaultValue = "60") int limit) {
        int capped = Math.min(limit, 200);
        var viewer = viewerScopeResolver.resolve();
        List<Map<String, Object>> photos = photoMapper.selectCascadeByFamily(contentAccess.visibleFilter(viewer), capped);
        // 别人家分享进来的照片标出来源家庭(一次批量查询,禁逐条回填)
        Map<Long, Map<String, Object>> sources = Map.of();
        if (viewer.userId() != null && !photos.isEmpty()) {
            List<Long> ids = photos.stream()
                    .map(p -> ((Number) p.get("id")).longValue()).toList();
            sources = audienceService.sharedSources(com.ihomy.service.access.ContentKind.PHOTO, ids, viewer.familyId());
        }
        for (Map<String, Object> p : photos) {
            p.put("url", signedUrlService.resolve((String) p.get("url"))); // storage:// → 签名中转
            Object id = p.get("id");
            if (id != null) {
                Map<String, Object> source = sources.get(((Number) id).longValue());
                if (source != null) {
                    p.put("sharedFrom", source);
                    p.put("readOnly", true);
                }
            }
        }
        return Result.success(photos);
    }
}