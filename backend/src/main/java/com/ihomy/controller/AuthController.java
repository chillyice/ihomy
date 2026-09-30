package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.common.BizException;
import com.ihomy.common.Ips;
import com.ihomy.common.Result;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.LoginDTO;
import com.ihomy.dto.RegisterDTO;
import com.ihomy.entity.SysUser;
import com.ihomy.security.AuthCookie;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.AuthGuardService;
import com.ihomy.service.AuthService;
import com.ihomy.service.CaptchaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 认证接口:验证码/登录/注册/登出/刷新,以及多家庭列表、切换、邀请码加入。
 */
@Tag(name = "认证")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SecurityHelper securityHelper;
    private final CaptchaService captchaService;
    private final AuthGuardService authGuard;
    private final AuthCookie authCookie;

    @Operation(summary = "获取图形验证码(注册用)")
    @GetMapping("/captcha")
    public Result<Map<String, String>> captcha(HttpServletRequest request) {
        // 防刷:同一 IP 每分钟最多 30 次
        authGuard.checkRate("captcha", Ips.realIp(request), 30, 60);
        return Result.success(captchaService.generate());
    }

    @Operation(summary = "登录")
    @OperationLog(module = "AUTH", operationType = "LOGIN", description = "用户登录", saveArgs = false)
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginDTO dto, HttpServletRequest request,
                                             HttpServletResponse response) {
        String ip = Ips.realIp(request);
        // 防爆破:同一 IP 每分钟最多 30 次登录请求(账号级失败计数在 AuthGuardService)
        authGuard.checkRate("login", ip, 30, 60);
        Map<String, Object> data = authService.login(dto, ip);
        // 同步下发访问令牌 cookie:图片等静态资源请求带不了 Authorization 头,登录态靠它读 /files
        attachAccessToken(request, response, data);
        return Result.success(data);
    }

    @Operation(summary = "注册（创建家庭）")
    @OperationLog(module = "AUTH", operationType = "CREATE", description = "注册新家庭", saveArgs = false)
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@Valid @RequestBody RegisterDTO dto, HttpServletRequest request,
                                                HttpServletResponse response) {
        // 防刷:同一 IP 每小时最多 10 次注册
        authGuard.checkRate("register", Ips.realIp(request), 10, 3600);
        Map<String, Object> data = authService.register(dto);
        attachAccessToken(request, response, data);
        return Result.success(data);
    }

    @Operation(summary = "登出(同时吊销本次会话的刷新令牌)")
    @OperationLog(module = "AUTH", operationType = "LOGOUT", description = "用户登出", saveArgs = false)
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request, @RequestBody(required = false) Map<String, String> body,
                               HttpServletResponse response) {
        authService.logout(request.getHeader("Authorization"), body == null ? null : body.get("refreshToken"));
        authCookie.clear(response);
        return Result.success();
    }

    @Operation(summary = "刷新令牌")
    @PostMapping("/refresh")
    public Result<Map<String, Object>> refresh(@RequestBody Map<String, String> body, HttpServletRequest request,
                                               HttpServletResponse response) {
        Map<String, Object> data = authService.refresh(body.get("refreshToken"));
        attachAccessToken(request, response, data);
        return Result.success(data);
    }

    @Operation(summary = "获取壁纸令牌(桌面壁纸专用)")
    @OperationLog(module = "AUTH", operationType = "CREATE", description = "获取壁纸令牌", saveArgs = false)
    @PostMapping("/wallpaper-token")
    public Result<Map<String, String>> wallpaperToken(HttpServletRequest request, HttpServletResponse response) {
        SysUser user = securityHelper.currentUser();
        if (user == null) throw new BizException(ResultCode.UNAUTHORIZED);
        Map<String, String> data = authService.wallpaperToken(user);
        // 独立长寿命令牌只在没有会话 cookie 时补一枚(壁纸独立页可能开在全新浏览器),不覆盖正常会话
        authCookie.attachIfAbsent(request, response, data.get("refreshToken"));
        return Result.success(data);
    }

    /** 登录/注册/刷新后把访问令牌放进 cookie:图片等静态资源请求带不了 Authorization 头,登录态靠它读 /files */
    private void attachAccessToken(HttpServletRequest request, HttpServletResponse response, Map<String, Object> data) {
        Object token = data == null ? null : data.get("accessToken");
        if (token instanceof String s && !s.isBlank()) authCookie.attach(request, response, s);
    }

    @Operation(summary = "当前用户信息")
    @GetMapping("/me")
    public Result<Object> me() {
        return Result.success(authService.currentUser());
    }

    @Operation(summary = "我的家庭列表")
    @GetMapping("/families")
    public Result<List<Map<String, Object>>> families() {
        SysUser user = securityHelper.currentUser();
        if (user == null) throw new BizException(ResultCode.UNAUTHORIZED);
        return Result.success(authService.listFamilies(user.getId()));
    }

    @Operation(summary = "切换当前家庭")
    @OperationLog(module = "AUTH", operationType = "CONFIG", description = "切换家庭", saveArgs = false)
    @PostMapping("/family/switch")
    public Result<Map<String, Object>> switchFamily(@RequestBody Map<String, Object> body) {
        SysUser user = securityHelper.currentUser();
        if (user == null) throw new BizException(ResultCode.UNAUTHORIZED);
        Long familyId = body.get("familyId") == null ? null : Long.valueOf(body.get("familyId").toString());
        Boolean setDefault = body.get("setDefault") == null ? null : Boolean.valueOf(body.get("setDefault").toString());
        return Result.success(authService.switchFamily(user.getId(), familyId, setDefault));
    }

    @Operation(summary = "已登录用户通过邀请码加入家庭")
    @OperationLog(module = "AUTH", operationType = "CREATE", description = "加入家庭", saveArgs = false)
    @PostMapping("/join")
    public Result<Void> join(@RequestBody Map<String, String> body) {
        SysUser user = securityHelper.currentUser();
        if (user == null) throw new BizException(ResultCode.UNAUTHORIZED);
        authService.joinFamily(user.getId(), body.get("inviteCode"));
        return Result.success();
    }
}
