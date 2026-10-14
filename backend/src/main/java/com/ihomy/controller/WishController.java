package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.common.Result;
import com.ihomy.dto.WishDTO;
import com.ihomy.entity.Family;
import com.ihomy.entity.Wish;
import com.ihomy.mapper.FamilyMapper;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.WishService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 愿望单接口:家庭成员均可提出愿望、标记达成/放弃。
 * 列表对游客开放:未登录时只返回默认演示家庭的公开(PUBLIC)愿望,写接口仍需登录。
 */
@Tag(name = "愿望单")
@RestController
@RequestMapping("/wish")
@RequiredArgsConstructor
public class WishController {

    private final WishService wishService;
    private final SecurityHelper securityHelper;
    private final FamilyMapper familyMapper;

    private LoginUser current() {
        return securityHelper.current();
    }

    @Operation(summary = "家庭愿望列表(游客可读,只返回公开愿望)")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        LoginUser user = current();
        if (user != null) return Result.success(wishService.list(user.getFamilyId()));
        Family f = familyMapper.selectDefault();
        return Result.success(f == null ? List.of() : wishService.listPublic(f.getId()));
    }

    @Operation(summary = "新增愿望")
    @OperationLog(module = "WISH", operationType = "CREATE", description = "新增愿望")
    @PostMapping
    public Result<Wish> create(@Valid @RequestBody WishDTO dto) {
        LoginUser user = current();
        return Result.success(wishService.create(user.getUserId(), user.getFamilyId(), dto));
    }

    @Operation(summary = "编辑愿望(含达成/放弃状态切换)")
    @OperationLog(module = "WISH", operationType = "UPDATE", description = "编辑愿望")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody WishDTO dto) {
        wishService.update(id, current().getFamilyId(), dto);
        return Result.success();
    }

    @Operation(summary = "删除愿望")
    @OperationLog(module = "WISH", operationType = "DELETE", description = "删除愿望")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        wishService.delete(id, current().getFamilyId());
        return Result.success();
    }
}