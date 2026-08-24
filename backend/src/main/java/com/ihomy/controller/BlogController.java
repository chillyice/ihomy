package com.ihomy.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ihomy.annotation.OperationLog;
import com.ihomy.annotation.RequirePermission;
import com.ihomy.common.BizException;
import com.ihomy.common.Result;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.BlogDTO;
import com.ihomy.entity.Blog;
import com.ihomy.entity.SysUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.BlogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 博客接口:列表(按可见范围)/详情/发布/修改/删除。
 */
@Tag(name = "博客")
@RestController
@RequestMapping("/blog")
@RequiredArgsConstructor
public class BlogController {

    private final BlogService blogService;
    private final SecurityHelper securityHelper;

    @Operation(summary = "博客分页列表")
    @GetMapping("/list")
    public Result<IPage<Blog>> list(@RequestParam(defaultValue = "1") int current,
                                    @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) String category) {
        SysUser user = securityHelper.currentUser();
        Long familyId = user == null ? null : user.getFamilyId();
        Long userId = user == null ? null : user.getId();
        boolean isOwner = securityHelper.isOwner();
        return Result.success(blogService.page(current, size, familyId, userId, isOwner, keyword, category));
    }

    @Operation(summary = "博客分类列表（家庭级）")
    @GetMapping("/categories")
    public Result<List<String>> categories() {
        SysUser user = securityHelper.currentUser();
        Long familyId = user == null ? 1L : user.getFamilyId();
        return Result.success(blogService.categories(familyId));
    }

    @Operation(summary = "新增分类")
    @PostMapping("/categories")
    public Result<Void> addCategory(@RequestBody Map<String, String> body) {
        SysUser user = securityHelper.currentUser();
        if (user == null) throw new BizException(ResultCode.UNAUTHORIZED);
        blogService.addCategory(user.getFamilyId(), body.get("name"));
        return Result.success();
    }

    @Operation(summary = "重命名分类")
    @PutMapping("/categories")
    public Result<Void> renameCategory(@RequestBody Map<String, String> body) {
        SysUser user = securityHelper.currentUser();
        if (user == null) throw new BizException(ResultCode.UNAUTHORIZED);
        blogService.renameCategory(user.getFamilyId(), body.get("oldName"), body.get("newName"));
        return Result.success();
    }

    @Operation(summary = "删除分类")
    @DeleteMapping("/categories")
    public Result<Void> deleteCategory(@RequestParam String category, @RequestParam(defaultValue = "move") String mode) {
        SysUser user = securityHelper.currentUser();
        if (user == null) throw new BizException(ResultCode.UNAUTHORIZED);
        blogService.deleteCategory(user.getFamilyId(), category, mode);
        return Result.success();
    }

    @Operation(summary = "博客详情")
    @GetMapping("/{id}")
    public Result<Blog> detail(@PathVariable Long id) {
        SysUser user = securityHelper.currentUser();
        Long familyId = user == null ? null : user.getFamilyId();
        Long userId = user == null ? null : user.getId();
        boolean isOwner = securityHelper.isOwner();
        return Result.success(blogService.getDetail(id, familyId, userId, isOwner));
    }

    @Operation(summary = "新建博客")
    @OperationLog(module = "BLOG", operationType = "CREATE", description = "发布博客")
    @PostMapping
    public Result<Blog> create(@Valid @RequestBody BlogDTO dto) {
        SysUser user = securityHelper.currentUser();
        return Result.success(blogService.create(user.getId(), user.getFamilyId(), dto));
    }

    @Operation(summary = "更新博客")
    @OperationLog(module = "BLOG", operationType = "UPDATE", description = "修改博客")
    @PutMapping("/{id}")
    public Result<Blog> update(@PathVariable Long id, @Valid @RequestBody BlogDTO dto) {
        return Result.success(blogService.update(id, securityHelper.currentUserId(), dto));
    }

    @Operation(summary = "删除博客")
    @OperationLog(module = "BLOG", operationType = "DELETE", description = "删除博客")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        blogService.delete(id, securityHelper.currentUserId(), securityHelper.isOwner());
        return Result.success();
    }
}
