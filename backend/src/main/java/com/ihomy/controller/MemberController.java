package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.annotation.RequirePermission;
import com.ihomy.common.BizException;
import com.ihomy.common.Result;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.RoleDTO;
import com.ihomy.entity.InvitationCode;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.AuthService;
import com.ihomy.service.MemberManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 成员管理接口:列表/改角色/移出(需 user:manage),邀请码生成与列表(invite:create)。
 */
@Tag(name = "成员")
@RestController
@RequestMapping("/member")
@RequiredArgsConstructor
public class MemberController {

    private final SecurityHelper securityHelper;
    private final MemberManagementService memberService;
    private final AuthService authService;

    @Operation(summary = "家庭成员列表（含角色与在线状态）")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        LoginUser user = securityHelper.current();
        if (user == null) throw new BizException(ResultCode.UNAUTHORIZED);
        return Result.success(memberService.listMembers(user.getFamilyId()));
    }

    @Operation(summary = "切换我的在线状态")
    @OperationLog(module = "USER", operationType = "UPDATE", description = "切换在线状态", saveArgs = false)
    @PutMapping("/presence")
    public Result<Map<String, Object>> setPresence(@RequestBody Map<String, String> body) {
        LoginUser user = securityHelper.current();
        if (user == null) throw new BizException(ResultCode.UNAUTHORIZED);
        String status = body == null ? null : body.get("status");
        memberService.setPresence(user.getUserId(), user.getFamilyId(), status);
        // SysUser 有 5 分钟缓存:改完立即失效,避免 /profile 的 updateById 拿旧状态写回
        securityHelper.invalidateUser(user.getUserId());
        return Result.success(Map.of("status", status));
    }

    @Operation(summary = "修改成员角色")
    @RequirePermission("user:manage")
    @OperationLog(module = "USER", operationType = "UPDATE", description = "修改成员角色", saveArgs = false)
    @PutMapping("/{userId}/role")
    public Result<Void> setRole(@PathVariable Long userId, @RequestBody RoleDTO dto) {
        LoginUser user = securityHelper.current();
        memberService.setRole(user.getFamilyId(), userId, dto.getRoleCode());
        securityHelper.invalidatePerms(userId, user.getFamilyId());
        return Result.success();
    }

    @Operation(summary = "移出家庭成员")
    @RequirePermission("user:manage")
    @OperationLog(module = "USER", operationType = "DELETE", description = "移出家庭成员", saveArgs = false)
    @DeleteMapping("/{userId}")
    public Result<Void> remove(@PathVariable Long userId) {
        LoginUser user = securityHelper.current();
        memberService.removeMember(user.getUserId(), user.getFamilyId(), userId);
        securityHelper.invalidatePerms(userId, user.getFamilyId());
        authService.clearCurFamilyIf(userId, user.getFamilyId());
        return Result.success();
    }

    @Operation(summary = "生成邀请码")
    @RequirePermission("invite:create")
    @OperationLog(module = "INVITE", operationType = "CREATE", description = "生成邀请码")
    @PostMapping("/invite")
    public Result<Map<String, Object>> createInvite(@RequestBody(required = false) RoleDTO dto) {
        LoginUser user = securityHelper.current();
        return Result.success(memberService.createInvite(user.getFamilyId(), user.getUserId(), dto == null ? null : dto.getRoleCode()));
    }

    @Operation(summary = "邀请码列表")
    @RequirePermission("invite:create")
    @GetMapping("/invite")
    public Result<List<InvitationCode>> inviteList() {
        LoginUser user = securityHelper.current();
        return Result.success(memberService.inviteList(user.getFamilyId()));
    }
}
