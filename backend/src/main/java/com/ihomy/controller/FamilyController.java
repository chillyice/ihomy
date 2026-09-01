package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.annotation.RequirePermission;
import com.ihomy.common.BizException;
import com.ihomy.common.Result;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.FamilyDTO;
import com.ihomy.entity.Family;
import com.ihomy.entity.SysRole;
import com.ihomy.entity.SysUser;
import com.ihomy.entity.SysUserRole;
import com.ihomy.mapper.FamilyMapper;
import com.ihomy.mapper.SysRoleMapper;
import com.ihomy.mapper.SysUserRoleMapper;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.BlogService;
import com.ihomy.service.MultiFamilyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 家庭接口:创建新家庭/公开家庭搜索/入家申请与审核(family:manage),以及当前家庭信息与设置更新。
 */
@Tag(name = "家庭设置")
@RestController
@RequestMapping("/family")
@RequiredArgsConstructor
public class FamilyController {

    private final FamilyMapper familyMapper;
    private final SecurityHelper securityHelper;
    private final MultiFamilyService multiFamilyService;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final StringRedisTemplate redisTemplate;
    private final BlogService blogService;

    @Operation(summary = "创建新家庭(当前用户绑定 OWNER)")
    @OperationLog(module = "FAMILY", operationType = "CREATE", description = "创建新家庭", saveArgs = false)
    @PostMapping
    public Result<Family> create(@RequestBody FamilyDTO dto) {
        SysUser user = securityHelper.currentUser();
        if (user == null) throw new BizException(ResultCode.UNAUTHORIZED);
        if (dto.getName() == null || dto.getName().isBlank()) throw new BizException(ResultCode.BAD_REQUEST);

        Family family = new Family();
        family.setName(dto.getName().trim());
        family.setCoverText("欢迎来到我们的家庭空间");
        family.setShareToken(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        family.setOwnerId(user.getId());
        familyMapper.insert(family);

        // 注入初始博客分类(未分类/生活随笔等,幂等)
        blogService.seedDefaultCategories(family.getId());

        SysRole ownerRole = sysRoleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, "OWNER"));
        if (ownerRole != null) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(user.getId());
            ur.setRoleId(ownerRole.getId());
            ur.setFamilyId(family.getId());
            sysUserRoleMapper.insert(ur);
        }

        redisTemplate.opsForValue().set("user:curfamily:" + user.getId(), String.valueOf(family.getId()));

        return Result.success(family);
    }

    @Operation(summary = "搜索公开家庭")
    @GetMapping("/search")
    public Result<List<Map<String, Object>>> search(@RequestParam(required = false) String keyword) {
        SysUser user = securityHelper.currentUser();
        Long userId = user == null ? null : user.getId();
        return Result.success(multiFamilyService.search(keyword, userId));
    }

    @Operation(summary = "提交入家申请")
    @OperationLog(module = "FAMILY", operationType = "CREATE", description = "提交入家申请", saveArgs = false)
    @PostMapping("/apply")
    public Result<Void> apply(@RequestBody Map<String, Object> body) {
        SysUser user = securityHelper.currentUser();
        if (user == null) throw new BizException(ResultCode.UNAUTHORIZED);
        Long familyId = body.get("familyId") == null ? null : Long.valueOf(body.get("familyId").toString());
        if (familyId == null) throw new BizException(ResultCode.BAD_REQUEST);
        String message = body.get("message") == null ? null : body.get("message").toString();
        multiFamilyService.apply(user.getId(), familyId, message);
        return Result.success();
    }

    @Operation(summary = "入家申请列表")
    @RequirePermission("family:manage")
    @GetMapping("/apply/list")
    public Result<List<Map<String, Object>>> applyList() {
        SysUser user = securityHelper.currentUser();
        return Result.success(multiFamilyService.applyList(user.getFamilyId()));
    }

    @Operation(summary = "审核入家申请")
    @RequirePermission("family:manage")
    @OperationLog(module = "FAMILY", operationType = "CONFIG", description = "审核入家申请", saveArgs = false)
    @PutMapping("/apply/{id}")
    public Result<Void> handleApply(@PathVariable Long id, @RequestParam String action) {
        SysUser user = securityHelper.currentUser();
        multiFamilyService.handleApply(id, user.getFamilyId(), user.getId(), action);
        return Result.success();
    }

    @Operation(summary = "当前家庭信息")
    @GetMapping
    public Result<Family> current() {
        SysUser user = securityHelper.currentUser();
        if (user == null) throw new BizException(ResultCode.UNAUTHORIZED);
        Family f = familyMapper.selectById(user.getFamilyId());
        if (f == null) throw new BizException(ResultCode.NOT_FOUND);
        return Result.success(f);
    }

    @Operation(summary = "更新家庭设置")
    @RequirePermission("family:manage")
    @OperationLog(module = "FAMILY", operationType = "CONFIG", description = "更新家庭设置", saveArgs = false)
    @PutMapping
    public Result<Family> update(@RequestBody FamilyDTO dto) {
        SysUser user = securityHelper.currentUser();
        Family f = familyMapper.selectById(user.getFamilyId());
        if (f == null) throw new BizException(ResultCode.NOT_FOUND);
        if (dto.getName() != null && !dto.getName().isBlank()) f.setName(dto.getName());
        if (dto.getCoverImage() != null) f.setCoverImage(dto.getCoverImage());
        if (dto.getCoverText() != null) f.setCoverText(dto.getCoverText());
        if (dto.getCoverSubtitle() != null) f.setCoverSubtitle(dto.getCoverSubtitle());
        if (dto.getDescription() != null) f.setDescription(dto.getDescription());
        if (dto.getIsPublic() != null) f.setIsPublic(dto.getIsPublic());
        if (dto.getMusicUrl() != null) f.setMusicUrl(dto.getMusicUrl());
        if (dto.getMusicTitle() != null) f.setMusicTitle(dto.getMusicTitle());
        // 天气位置偏好:显式 SET NULL(清空)或值(设置),绕过 MyBatis-Plus 默认忽略 null
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Family> w = new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
        w.eq(Family::getId, f.getId())
            .set(Family::getWeatherLat, dto.getWeatherLat())
            .set(Family::getWeatherLng, dto.getWeatherLng())
            .set(Family::getWeatherCity, dto.getWeatherCity());
        familyMapper.update(null, w);
        f.setWeatherLat(dto.getWeatherLat());
        f.setWeatherLng(dto.getWeatherLng());
        f.setWeatherCity(dto.getWeatherCity());
        return Result.success(f);
    }
}
