package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.common.Result;
import com.ihomy.dto.TaskDTO;
import com.ihomy.entity.Task;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 悬赏任务接口:登录家庭成员均可发布/领取/完成/取消,
 * 结算确认仅发布者,积分奖励流入积分体系。
 */
@Tag(name = "悬赏任务")
@RestController
@RequestMapping("/task")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final SecurityHelper securityHelper;

    /** 当前用户 + 家庭快照 */
    private LoginUser current() {
        return securityHelper.current();
    }

    @Operation(summary = "家庭任务列表")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        return Result.success(taskService.list(current().getFamilyId()));
    }

    @Operation(summary = "发布悬赏任务")
    @OperationLog(module = "TASK", operationType = "CREATE", description = "发布悬赏任务")
    @PostMapping
    public Result<Task> create(@RequestBody TaskDTO dto) {
        LoginUser user = current();
        return Result.success(taskService.create(user.getUserId(), user.getFamilyId(), dto));
    }

    @Operation(summary = "领取任务")
    @OperationLog(module = "TASK", operationType = "UPDATE", description = "领取任务")
    @PostMapping("/{id}/claim")
    public Result<Void> claim(@PathVariable Long id) {
        LoginUser user = current();
        taskService.claim(id, user.getFamilyId(), user.getUserId());
        return Result.success();
    }

    @Operation(summary = "放弃任务")
    @PostMapping("/{id}/abandon")
    public Result<Void> abandon(@PathVariable Long id) {
        LoginUser user = current();
        taskService.abandon(id, user.getFamilyId(), user.getUserId());
        return Result.success();
    }

    @Operation(summary = "完成申报(领取人)")
    @OperationLog(module = "TASK", operationType = "UPDATE", description = "申报完成任务")
    @PostMapping("/{id}/finish")
    public Result<Void> finish(@PathVariable Long id) {
        LoginUser user = current();
        taskService.finish(id, user.getFamilyId(), user.getUserId());
        return Result.success();
    }

    @Operation(summary = "确认结算(发布者,发放奖励)")
    @OperationLog(module = "TASK", operationType = "UPDATE", description = "确认任务完成")
    @PostMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id) {
        LoginUser user = current();
        taskService.confirm(id, user.getFamilyId(), user.getUserId());
        return Result.success();
    }

    @Operation(summary = "取消任务(发布者)")
    @OperationLog(module = "TASK", operationType = "UPDATE", description = "取消任务")
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        LoginUser user = current();
        taskService.cancel(id, user.getFamilyId(), user.getUserId());
        return Result.success();
    }
}