package com.family.controller;

import com.family.common.BizException;
import com.family.common.Result;
import com.family.common.ResultCode;
import com.family.dto.HomeModuleDTO;
import com.family.entity.HomeModule;
import com.family.entity.SysUser;
import com.family.security.SecurityHelper;
import com.family.service.HomeModuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "首页模块")
@RestController
@RequestMapping("/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeModuleService homeModuleService;
    private final SecurityHelper securityHelper;

    @Operation(summary = "获取首页启用的模块列表")
    @GetMapping("/modules")
    public Result<List<HomeModule>> modules() {
        SysUser user = securityHelper.currentUser();
        Long familyId = user == null ? null : user.getFamilyId();
        return Result.success(homeModuleService.listEnabled(familyId));
    }

    @Operation(summary = "获取全部模块（含禁用，Owner）")
    @GetMapping("/modules/all")
    public Result<List<HomeModule>> allModules() {
        assertOwner();
        SysUser user = securityHelper.currentUser();
        return Result.success(homeModuleService.listAll(user.getFamilyId()));
    }

    @Operation(summary = "更新模块配置（位置/排序/启用，Owner）")
    @PutMapping("/modules")
    public Result<Void> updateModules(@RequestBody HomeModuleDTO dto) {
        assertOwner();
        SysUser user = securityHelper.currentUser();
        homeModuleService.updateConfig(user.getFamilyId(), dto);
        return Result.success();
    }

    @Operation(summary = "新增模块（后期扩展新功能用）")
    @PostMapping("/modules")
    public Result<HomeModule> addModule(@RequestBody HomeModule module) {
        assertOwner();
        SysUser user = securityHelper.currentUser();
        module.setFamilyId(user.getFamilyId());
        if (module.getEnabled() == null) module.setEnabled(1);
        if (module.getSortOrder() == null) module.setSortOrder(0);
        return Result.success(homeModuleService.addModule(module));
    }

    @Operation(summary = "首页聚合数据")
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        SysUser user = securityHelper.currentUser();
        Map<String, Object> data = new HashMap<>();
        data.put("modules", homeModuleService.listEnabled(user == null ? null : user.getFamilyId()));
        data.put("user", user);
        return Result.success(data);
    }

    private void assertOwner() {
        if (!securityHelper.isOwner()) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
    }
}
