package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.common.Result;
import com.ihomy.dto.ReminderDTO;
import com.ihomy.entity.Reminder;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.ReminderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 提醒事项接口:家庭内全员可增删改与完成勾选,到点由定时任务发站内通知。
 */
@Tag(name = "提醒事项")
@RestController
@RequestMapping("/reminder")
@RequiredArgsConstructor
public class ReminderController {

    private final ReminderService reminderService;
    private final SecurityHelper securityHelper;

    private LoginUser current() {
        return securityHelper.current();
    }

    @Operation(summary = "家庭提醒列表")
    @GetMapping("/list")
    public Result<List<Reminder>> list() {
        return Result.success(reminderService.list(current().getFamilyId()));
    }

    @Operation(summary = "新增提醒")
    @OperationLog(module = "REMINDER", operationType = "CREATE", description = "新增提醒")
    @PostMapping
    public Result<Reminder> create(@RequestBody ReminderDTO dto) {
        LoginUser user = current();
        return Result.success(reminderService.create(user.getUserId(), user.getFamilyId(), dto));
    }

    @Operation(summary = "编辑提醒")
    @OperationLog(module = "REMINDER", operationType = "UPDATE", description = "编辑提醒")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody ReminderDTO dto) {
        reminderService.update(id, current().getFamilyId(), dto);
        return Result.success();
    }

    @Operation(summary = "删除提醒")
    @OperationLog(module = "REMINDER", operationType = "DELETE", description = "删除提醒")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        reminderService.delete(id, current().getFamilyId());
        return Result.success();
    }

    @Operation(summary = "完成/取消完成")
    @PostMapping("/{id}/toggle-done")
    public Result<Void> toggleDone(@PathVariable Long id) {
        reminderService.toggleDone(id, current().getFamilyId());
        return Result.success();
    }
}