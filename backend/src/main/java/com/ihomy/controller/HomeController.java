package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.common.BizException;
import com.ihomy.common.Result;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.HomeModuleDTO;
import com.ihomy.entity.HomeModule;
import com.ihomy.entity.SysUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.ActivityFeedService;
import com.ihomy.service.HomeModuleService;
import com.ihomy.controller.PublicController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 首页模块接口:模块列表/配置(仅 OWNER)、模块扩展新增,以及登录用户的家庭动态流。
 */
@Tag(name = "首页模块")
@RestController
@RequestMapping("/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeModuleService homeModuleService;
    private final SecurityHelper securityHelper;
    private final ActivityFeedService activityFeedService;
    private final PublicController publicController;

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
    @OperationLog(module = "HOME", operationType = "CONFIG", description = "更新首页模块配置")
    @PutMapping("/modules")
    public Result<Void> updateModules(@RequestBody HomeModuleDTO dto) {
        assertOwner();
        SysUser user = securityHelper.currentUser();
        homeModuleService.updateConfig(user.getFamilyId(), dto);
        publicController.invalidateHomeCache(user.getFamilyId());
        return Result.success();
    }

    @Operation(summary = "新增模块（后期扩展新功能用）")
    @OperationLog(module = "HOME", operationType = "CREATE", description = "新增首页模块")
    @PostMapping("/modules")
    public Result<HomeModule> addModule(@RequestBody HomeModule module) {
        assertOwner();
        SysUser user = securityHelper.currentUser();
        module.setFamilyId(user.getFamilyId());
        if (module.getEnabled() == null) module.setEnabled(1);
        if (module.getSortOrder() == null) module.setSortOrder(0);
        HomeModule saved = homeModuleService.addModule(module);
        publicController.invalidateHomeCache(user.getFamilyId());
        return Result.success(saved);
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

    @Operation(summary = "家人动态流（登录用户）")
    @GetMapping("/feed")
    public Result<List<Map<String, Object>>> feed(@RequestParam(defaultValue = "20") int limit) {
        SysUser user = securityHelper.currentUser();
        Long familyId = user == null ? null : user.getFamilyId();
        return Result.success(activityFeedService.getFeed(familyId, limit, false, user == null ? null : user.getId(), securityHelper.isOwner()));
    }

    private void assertOwner() {
        if (!securityHelper.isOwner()) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
    }
}
