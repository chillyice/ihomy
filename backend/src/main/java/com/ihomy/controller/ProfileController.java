package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.common.Result;
import com.ihomy.dto.ProfileDTO;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.SysUserMapper;
import com.ihomy.security.SecurityHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 个人资料接口:查看/更新当前用户(昵称/头像/生日/性别)。
 */
@Tag(name = "个人资料")
@RestController
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final SysUserMapper sysUserMapper;
    private final SecurityHelper securityHelper;

    @Operation(summary = "当前用户资料")
    @GetMapping
    public Result<SysUser> me() {
        return Result.success(securityHelper.currentUser());
    }

    @Operation(summary = "更新个人资料")
    @OperationLog(module = "USER", operationType = "UPDATE", description = "更新个人资料", saveArgs = false)
    @PutMapping
    public Result<SysUser> update(@RequestBody ProfileDTO dto) {
        SysUser user = securityHelper.currentUser();
        if (dto.getNickname() != null && !dto.getNickname().isBlank()) user.setNickname(dto.getNickname());
        if (dto.getAvatar() != null) user.setAvatar(dto.getAvatar());
        if (dto.getBirthday() != null) user.setBirthday(dto.getBirthday());
        if (dto.getGender() != null) user.setGender(dto.getGender());
        sysUserMapper.updateById(user);
        return Result.success(user);
    }
}
