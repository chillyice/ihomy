package com.ihomy.controller;

import com.ihomy.common.Result;
import com.ihomy.entity.SysUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.ContentLikeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 点赞接口:切换点赞与查询状态(博客/日记/照片统一)。
 */
@Tag(name = "点赞")
@RestController
@RequestMapping("/like")
@RequiredArgsConstructor
public class LikeController {

    private final ContentLikeService likeService;
    private final SecurityHelper securityHelper;

    @Operation(summary = "点赞/取消点赞")
    @PostMapping("/toggle")
    public Result<Map<String, Object>> toggle(@RequestBody Map<String, Object> body) {
        SysUser user = securityHelper.currentUser();
        String contentType = (String) body.get("contentType");
        Object cid = body.get("contentId");
        if (contentType == null || contentType.isBlank() || !(cid instanceof Number)) {
            throw new com.ihomy.common.BizException(com.ihomy.common.ResultCode.BAD_REQUEST);
        }
        Long contentId = ((Number) cid).longValue();
        return Result.success(likeService.toggle(user.getId(), user.getFamilyId(), contentType, contentId));
    }

    @Operation(summary = "点赞状态")
    @GetMapping("/state")
    public Result<Map<String, Object>> state(@RequestParam String contentType, @RequestParam Long contentId) {
        Long userId = securityHelper.currentUserId();
        return Result.success(likeService.state(userId, contentType, contentId));
    }
}
