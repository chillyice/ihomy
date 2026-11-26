package com.ihomy.controller;

import com.ihomy.common.Result;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.AchievementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 成就接口(V15.17):登录即可访问,成就归属个人,不做家庭权限码。
 * 读取即结算(惰性解锁),前端游客不请求本接口。
 */
@Tag(name = "成就")
@RestController
@RequestMapping("/achievements")
@RequiredArgsConstructor
public class AchievementController {

    private final AchievementService achievementService;
    private final SecurityHelper securityHelper;

    @Operation(summary = "成就墙(结算并返回成就目录/概览/家庭榜)")
    @GetMapping
    public Result<Map<String, Object>> overview() {
        LoginUser user = securityHelper.current();
        return Result.success(achievementService.evaluate(user.getUserId(), user.getFamilyId()));
    }
}
