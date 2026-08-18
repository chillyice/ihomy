package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.common.BizException;
import com.ihomy.common.Result;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.RecipeDTO;
import com.ihomy.entity.Recipe;
import com.ihomy.entity.SysUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.RecipeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 厨房菜谱接口:菜单页(按类别分组 + 今日推荐)/ 菜谱详情 / CRUD。
 */
@Tag(name = "厨房")
@RestController
@RequestMapping("/kitchen")
@RequiredArgsConstructor
public class RecipeController {

    private final RecipeService recipeService;
    private final SecurityHelper securityHelper;

    @Operation(summary = "菜单页(按类别分组 + 今日推荐)")
    @GetMapping("/menu")
    public Result<Map<String, Object>> menu() {
        SysUser user = securityHelper.currentUser();
        Long familyId = user == null ? null : user.getFamilyId();
        return Result.success(recipeService.menu(familyId));
    }

    @Operation(summary = "菜谱详情")
    @GetMapping("/recipe/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        SysUser user = securityHelper.currentUser();
        Long familyId = user == null ? null : user.getFamilyId();
        return Result.success(recipeService.detail(id, familyId));
    }

    @Operation(summary = "新增菜谱")
    @OperationLog(module = "RECIPE", operationType = "CREATE", description = "新增菜谱", saveArgs = false)
    @PostMapping("/recipe")
    public Result<Recipe> create(@RequestBody RecipeDTO dto) {
        SysUser user = securityHelper.currentUser();
        if (user == null) throw new BizException(ResultCode.UNAUTHORIZED);
        return Result.success(recipeService.create(user.getId(), user.getFamilyId(), dto));
    }

    @Operation(summary = "更新菜谱")
    @OperationLog(module = "RECIPE", operationType = "UPDATE", description = "更新菜谱", saveArgs = false)
    @PutMapping("/recipe/{id}")
    public Result<Recipe> update(@PathVariable Long id, @RequestBody RecipeDTO dto) {
        SysUser user = securityHelper.currentUser();
        return Result.success(recipeService.update(id, user.getFamilyId(), user.getId(), securityHelper.isOwner(), dto));
    }

    @Operation(summary = "删除菜谱")
    @OperationLog(module = "RECIPE", operationType = "DELETE", description = "删除菜谱")
    @DeleteMapping("/recipe/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        SysUser user = securityHelper.currentUser();
        recipeService.delete(id, user.getFamilyId(), user.getId(), securityHelper.isOwner());
        return Result.success();
    }
}
