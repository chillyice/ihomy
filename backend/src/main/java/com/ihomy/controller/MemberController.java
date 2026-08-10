package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.annotation.RequirePermission;
import com.ihomy.common.BizException;
import com.ihomy.common.Result;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.RoleDTO;
import com.ihomy.entity.InvitationCode;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.SysUserMapper;
import com.ihomy.security.SecurityHelper;
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

    private final SysUserMapper sysUserMapper;
    private final SecurityHelper securityHelper;
    private final MemberManagementService memberService;

    @Operation(summary = "家庭成员列表（含角色）")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        SysUser user = securityHelper.currentUser();
        if (user == null) throw new BizException(ResultCode.UNAUTHORIZED);
        return Result.success(sysUserMapper.selectMembersByFamily(user.getFamilyId()));
    }

    @Operation(summary = "修改成员角色")
    @RequirePermission("user:manage")
    @OperationLog(module = "USER", operationType = "UPDATE", description = "修改成员角色", saveArgs = false)
    @PutMapping("/{userId}/role")
    public Result<Void> setRole(@PathVariable Long userId, @RequestBody RoleDTO dto) {
        SysUser user = securityHelper.currentUser();
        memberService.setRole(user.getFamilyId(), userId, dto.getRoleCode());
        return Result.success();
    }

    @Operation(summary = "移出家庭成员")
    @RequirePermission("user:manage")
    @OperationLog(module = "USER", operationType = "DELETE", description = "移出家庭成员", saveArgs = false)
    @DeleteMapping("/{userId}")
    public Result<Void> remove(@PathVariable Long userId) {
        SysUser user = securityHelper.currentUser();
        memberService.removeMember(user.getId(), user.getFamilyId(), userId);
        return Result.success();
    }

    @Operation(summary = "生成邀请码")
    @RequirePermission("invite:create")
    @OperationLog(module = "INVITE", operationType = "CREATE", description = "生成邀请码")
    @PostMapping("/invite")
    public Result<Map<String, Object>> createInvite(@RequestBody(required = false) RoleDTO dto) {
        SysUser user = securityHelper.currentUser();
        return Result.success(memberService.createInvite(user.getFamilyId(), user.getId(), dto == null ? null : dto.getRoleCode()));
    }

    @Operation(summary = "邀请码列表")
    @RequirePermission("invite:create")
    @GetMapping("/invite")
    public Result<List<InvitationCode>> inviteList() {
        SysUser user = securityHelper.currentUser();
        return Result.success(memberService.inviteList(user.getFamilyId()));
    }
}
