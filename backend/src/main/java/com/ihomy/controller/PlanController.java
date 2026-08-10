package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.common.Result;
import com.ihomy.dto.PlanDTO;
import com.ihomy.dto.PlanTaskDTO;
import com.ihomy.entity.FamilyPlan;
import com.ihomy.entity.PlanTask;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.FamilyPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 家庭计划接口:计划与子任务两级管理,子任务完成度自动驱动计划状态。
 */
@Tag(name = "家庭计划")
@RestController
@RequestMapping("/plan")
@RequiredArgsConstructor
public class PlanController {

    private final FamilyPlanService planService;
    private final SecurityHelper securityHelper;

    private LoginUser current() {
        return securityHelper.current();
    }

    @Operation(summary = "家庭计划列表(含子任务与进度)")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        return Result.success(planService.list(current().getFamilyId()));
    }

    @Operation(summary = "创建计划")
    @OperationLog(module = "PLAN", operationType = "CREATE", description = "创建家庭计划")
    @PostMapping
    public Result<FamilyPlan> create(@RequestBody PlanDTO dto) {
        LoginUser user = current();
        return Result.success(planService.create(user.getUserId(), user.getFamilyId(), dto));
    }

    @Operation(summary = "编辑计划")
    @OperationLog(module = "PLAN", operationType = "UPDATE", description = "编辑家庭计划")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody PlanDTO dto) {
        planService.update(id, current().getFamilyId(), dto);
        return Result.success();
    }

    @Operation(summary = "删除计划(含子任务)")
    @OperationLog(module = "PLAN", operationType = "DELETE", description = "删除家庭计划")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        planService.delete(id, current().getFamilyId());
        return Result.success();
    }

    @Operation(summary = "添加子任务")
    @OperationLog(module = "PLAN", operationType = "CREATE", description = "添加计划子任务")
    @PostMapping("/{id}/task")
    public Result<PlanTask> addTask(@PathVariable Long id, @RequestBody PlanTaskDTO dto) {
        return Result.success(planService.addTask(id, current().getFamilyId(), dto));
    }

    @Operation(summary = "更新子任务(勾选完成/改指派等)")
    @OperationLog(module = "PLAN", operationType = "UPDATE", description = "更新计划子任务")
    @PutMapping("/task/{id}")
    public Result<Void> updateTask(@PathVariable Long id, @RequestBody PlanTaskDTO dto) {
        planService.updateTask(id, current().getFamilyId(), dto);
        return Result.success();
    }

    @Operation(summary = "删除子任务")
    @OperationLog(module = "PLAN", operationType = "DELETE", description = "删除计划子任务")
    @DeleteMapping("/task/{id}")
    public Result<Void> deleteTask(@PathVariable Long id) {
        planService.deleteTask(id, current().getFamilyId());
        return Result.success();
    }
}