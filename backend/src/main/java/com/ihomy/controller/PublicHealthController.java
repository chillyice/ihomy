package com.ihomy.controller;

import com.ihomy.common.Ips;
import com.ihomy.common.Result;
import com.ihomy.service.HealthShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 健康报表分享的公开读口:免登录,凭令牌只看那一份冻结快照。
 * 令牌不存在 / 已过期 / 已被本人删除,一律 404(不泄露「曾经存在过」)。
 * 这里不碰任何健康业务表——能看到什么完全由生成时写进 snapshot 的内容决定。
 */
@Tag(name = "健康报表分享(公开)")
@RestController
@RequestMapping("/public/health")
@RequiredArgsConstructor
public class PublicHealthController {

    private final HealthShareService shareService;

    @Operation(summary = "凭分享令牌查看健康报表快照(免登录;失效即 404)")
    @GetMapping("/share/{token}")
    public Result<Map<String, Object>> shared(@PathVariable String token, HttpServletRequest request) {
        return Result.success(shareService.open(token, Ips.realIp(request)));
    }
}
