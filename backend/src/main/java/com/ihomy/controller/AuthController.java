package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.common.BizException;
import com.ihomy.common.Result;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.LoginDTO;
import com.ihomy.dto.RegisterDTO;
import com.ihomy.entity.SysUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.AuthService;
import com.ihomy.service.CaptchaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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

    @Operation(summary = "获取图形验证码(注册用)")
    @GetMapping("/captcha")
    public Result<Map<String, String>> captcha() {
        return Result.success(captchaService.generate());
    }

    @Operation(summary = "登录")
    @OperationLog(module = "AUTH", operationType = "LOGIN", description = "用户登录", saveArgs = false)
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success(authService.login(dto));
    }

    @Operation(summary = "注册（创建家庭）")
    @OperationLog(module = "AUTH", operationType = "CREATE", description = "注册新家庭", saveArgs = false)
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@Valid @RequestBody RegisterDTO dto) {
        return Result.success(authService.register(dto));
    }

    @Operation(summary = "登出")
    @OperationLog(module = "AUTH", operationType = "LOGOUT", description = "用户登出")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        authService.logout(request.getHeader("Authorization"));
        return Result.success();
    }

    @Operation(summary = "刷新令牌")
    @PostMapping("/refresh")
    public Result<Map<String, Object>> refresh(@RequestBody Map<String, String> body) {
        return Result.success(authService.refresh(body.get("refreshToken")));
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
