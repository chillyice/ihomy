package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.common.BizException;
import com.ihomy.common.Result;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.ProfileDTO;
import com.ihomy.entity.SysUser;
import com.ihomy.entity.UserLabel;
import com.ihomy.mapper.SysUserMapper;
import com.ihomy.mapper.UserLabelMapper;
import com.ihomy.security.SecurityHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 个人资料接口:查看/更新当前用户(昵称/头像/生日/性别)。
 */
@Tag(name = "个人资料")
@RestController
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final SysUserMapper sysUserMapper;
    private final UserLabelMapper userLabelMapper;
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

    @Operation(summary = "当前家庭内的身份标签")
    @GetMapping("/label")
    public Result<UserLabel> myLabel() {
        SysUser user = securityHelper.currentUser();
        UserLabel ul = userLabelMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserLabel>()
                .eq(UserLabel::getUserId, user.getId()).eq(UserLabel::getFamilyId, user.getFamilyId()));
        return Result.success(ul);
    }

    @Operation(summary = "设置身份标签(预设置: 爸爸/妈妈,其余自定义)")
    @OperationLog(module = "USER", operationType = "UPDATE", description = "设置身份标签", saveArgs = false)
    @PutMapping("/label")
    public Result<UserLabel> saveLabel(@RequestBody UserLabel dto) {
        SysUser user = securityHelper.currentUser();
        String label = dto.getLabel();
        if (label == null || label.isBlank() || label.length() > 20) throw new BizException(ResultCode.BAD_REQUEST);
        UserLabel existing = userLabelMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserLabel>()
                .eq(UserLabel::getUserId, user.getId()).eq(UserLabel::getFamilyId, user.getFamilyId()));
        if (existing == null) {
            existing = new UserLabel();
            existing.setUserId(user.getId());
            existing.setFamilyId(user.getFamilyId());
            existing.setCreatedAt(LocalDateTime.now());
        }
        existing.setLabel(label);
        existing.setColor(dto.getColor());
        if (existing.getId() == null) userLabelMapper.insert(existing); else userLabelMapper.updateById(existing);
        return Result.success(existing);
    }

    /** 删除身份标签(恢复默认无标签) */
    @Operation(summary = "取消身份标签")
    @DeleteMapping("/label")
    public Result<Void> removeLabel() {
        SysUser user = securityHelper.currentUser();
        userLabelMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserLabel>()
                .eq(UserLabel::getUserId, user.getId()).eq(UserLabel::getFamilyId, user.getFamilyId()));
        return Result.success();
    }
}
