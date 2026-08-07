package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.common.Result;
import com.ihomy.dto.AnniversaryDTO;
import com.ihomy.entity.Anniversary;
import com.ihomy.entity.SysUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.AnniversaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 家庭纪念日接口:列表(访客可读)与增删改(需登录)。
 */
@Tag(name = "家庭纪念日")
@RestController
@RequestMapping("/anniversary")
@RequiredArgsConstructor
public class AnniversaryController {

    private final AnniversaryService anniversaryService;
    private final SecurityHelper securityHelper;

    @Operation(summary = "纪念日列表")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        SysUser user = securityHelper.currentUser();
        Long familyId = user == null ? null : user.getFamilyId();
        return Result.success(anniversaryService.list(familyId));
    }

    @Operation(summary = "新增纪念日")
    @OperationLog(module = "ANNIVERSARY", operationType = "CREATE", description = "新增纪念日")
    @PostMapping
    public Result<Anniversary> create(@RequestBody AnniversaryDTO dto) {
        SysUser user = securityHelper.currentUser();
        return Result.success(anniversaryService.create(user.getId(), user.getFamilyId(), dto));
    }

    @Operation(summary = "更新纪念日")
    @OperationLog(module = "ANNIVERSARY", operationType = "UPDATE", description = "修改纪念日")
    @PutMapping("/{id}")
    public Result<Anniversary> update(@PathVariable Long id, @RequestBody AnniversaryDTO dto) {
        SysUser user = securityHelper.currentUser();
        return Result.success(anniversaryService.update(id, user.getFamilyId(), dto));
    }

    @Operation(summary = "删除纪念日")
    @OperationLog(module = "ANNIVERSARY", operationType = "DELETE", description = "删除纪念日")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        SysUser user = securityHelper.currentUser();
        anniversaryService.delete(id, user.getFamilyId());
        return Result.success();
    }
}
