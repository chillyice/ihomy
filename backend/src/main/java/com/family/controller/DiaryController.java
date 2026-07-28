package com.family.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.family.common.Result;
import com.family.dto.DiaryDTO;
import com.family.entity.Diary;
import com.family.entity.SysUser;
import com.family.security.SecurityHelper;
import com.family.service.DiaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
        return Result.success(diaryService.page(current, size, user.getFamilyId(),
                user.getId(), securityHelper.isOwner()));
    }

    @Operation(summary = "新建日志")
    @PostMapping
    public Result<Diary> create(@RequestBody DiaryDTO dto) {
        SysUser user = securityHelper.currentUser();
        return Result.success(diaryService.create(user.getId(), user.getFamilyId(), dto));
    }

    @Operation(summary = "更新日志")
    @PutMapping("/{id}")
    public Result<Diary> update(@PathVariable Long id, @RequestBody DiaryDTO dto) {
        return Result.success(diaryService.update(id, securityHelper.currentUserId(), dto));
    }

    @Operation(summary = "删除日志")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        diaryService.delete(id, securityHelper.currentUserId(), securityHelper.isOwner());
        return Result.success();
    }
}
