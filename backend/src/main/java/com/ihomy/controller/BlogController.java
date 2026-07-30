package com.ihomy.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ihomy.common.Result;
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
                                    @RequestParam(required = false) String keyword) {
        SysUser user = securityHelper.currentUser();
        return Result.success(blogService.page(current, size, user.getFamilyId(), keyword));
    }

    @Operation(summary = "博客详情")
    @GetMapping("/{id}")
    public Result<Blog> detail(@PathVariable Long id) {
        return Result.success(blogService.getDetail(id));
    }

    @Operation(summary = "新建博客")
    @PostMapping
    public Result<Blog> create(@Valid @RequestBody BlogDTO dto) {
        SysUser user = securityHelper.currentUser();
        return Result.success(blogService.create(user.getId(), user.getFamilyId(), dto));
    }

    @Operation(summary = "更新博客")
    @PutMapping("/{id}")
    public Result<Blog> update(@PathVariable Long id, @Valid @RequestBody BlogDTO dto) {
        return Result.success(blogService.update(id, securityHelper.currentUserId(), dto));
    }

    @Operation(summary = "删除博客")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        blogService.delete(id, securityHelper.currentUserId(), securityHelper.isOwner());
        return Result.success();
    }
}
