package com.ihomy.controller;

import com.ihomy.common.BizException;
import com.ihomy.common.Result;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.UserPrefDTO;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.UserPrefService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 用户界面偏好接口:首页布局这类「跟人走」的前端偏好,登录后跨设备同步。
 * 只读写当前用户自己的数据,不走 @RequirePermission(与家庭角色无关,谁登录就同步谁的)。
 */
@Tag(name = "用户界面偏好")
@RestController
@RequestMapping("/user-pref")
@RequiredArgsConstructor
public class UserPrefController {

    private final UserPrefService userPrefService;
    private final SecurityHelper securityHelper;

    @Operation(summary = "我的界面偏好(键 → JSON 文本)")
    @GetMapping
    public Result<Map<String, String>> mine() {
        return Result.success(userPrefService.listMine(currentUserId()));
    }

    /**
     * 保存一份界面偏好(没有就插、有就覆盖)。
     * 不记操作日志:整理一次首页布局会连发十几条(每次拖拽/缩放松手各提交一次),属高频低价值写入,
     * 与「已读」类端点同一口径。
     */
    @Operation(summary = "保存界面偏好")
    @PutMapping
    public Result<Void> save(@Valid @RequestBody UserPrefDTO dto) {
        userPrefService.save(currentUserId(), dto.getKey(), dto.getValue());
        return Result.success();
    }

    private Long currentUserId() {
        Long userId = securityHelper.currentUserId();
        if (userId == null) throw new BizException(ResultCode.UNAUTHORIZED);
        return userId;
    }
}
