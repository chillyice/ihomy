package com.ihomy.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ihomy.annotation.OperationLog;
import com.ihomy.common.Result;
import com.ihomy.dto.DiaryDTO;
import com.ihomy.entity.Diary;
import com.ihomy.entity.SysUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.DiaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 生活日志(日记)接口:列表/写/改/删。
 */
@Tag(name = "日志")
@RestController
@RequestMapping("/diary")
@RequiredArgsConstructor
public class DiaryController {

    private final DiaryService diaryService;
    private final SecurityHelper securityHelper;

    @Operation(summary = "日志分页列表")
    @GetMapping("/list")
    public Result<IPage<Diary>> list(@RequestParam(defaultValue = "1") int current,
                                     @RequestParam(defaultValue = "20") int size) {
        SysUser user = securityHelper.currentUser();
        Long familyId = user == null ? null : user.getFamilyId();
        Long userId = user == null ? null : user.getId();
        return Result.success(diaryService.page(current, size, familyId, userId, securityHelper.isOwner()));
    }

    @Operation(summary = "日志详情")
    @GetMapping("/{id}")
    public Result<Diary> detail(@PathVariable Long id) {
        return Result.success(diaryService.getById(id));
    }

    @Operation(summary = "新建日志")
    @OperationLog(module = "DIARY", operationType = "CREATE", description = "写日记")
    @PostMapping
    public Result<Diary> create(@RequestBody DiaryDTO dto) {
        SysUser user = securityHelper.currentUser();
        return Result.success(diaryService.create(user.getId(), user.getFamilyId(), dto));
    }

    @Operation(summary = "更新日志")
    @OperationLog(module = "DIARY", operationType = "UPDATE", description = "修改日记")
    @PutMapping("/{id}")
    public Result<Diary> update(@PathVariable Long id, @RequestBody DiaryDTO dto) {
        return Result.success(diaryService.update(id, securityHelper.currentUserId(), dto));
    }

    @Operation(summary = "删除日志")
    @OperationLog(module = "DIARY", operationType = "DELETE", description = "删除日记")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        diaryService.delete(id, securityHelper.currentUserId(), securityHelper.isOwner());
        return Result.success();
    }
}
