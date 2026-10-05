package com.ihomy.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ihomy.annotation.OperationLog;
import com.ihomy.annotation.RequirePermission;
import com.ihomy.common.Result;
import com.ihomy.entity.ReportAlert;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.LogAlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 异常预警接口(V10.15):同类日志问题聚合成的预警,供运维页查看与标记已处理。
 * 仅 OPS 角色可访问(@RequirePermission + OpsAccessFilter 双保险);
 * 只返回系统级日志聚合信息,不涉及任何用户/家庭内容明细。
 */
@Tag(name = "异常预警")
@RestController
@RequestMapping("/ops/alerts")
@RequiredArgsConstructor
public class OpsAlertController {

    private final LogAlertService logAlertService;
    private final SecurityHelper securityHelper;

    @Operation(summary = "异常预警分页(可按状态/级别筛选,按最近出现时间倒序)")
    @RequirePermission("ops:view")
    @GetMapping
    public Result<IPage<ReportAlert>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String level) {
        return Result.success(logAlertService.page(current, size, status, level));
    }

    @Operation(summary = "异常预警汇总(待处理/今日新增/累计)")
    @RequirePermission("ops:view")
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        return Result.success(logAlertService.summary());
    }

    @Operation(summary = "标记单条异常预警已处理")
    @RequirePermission("ops:view")
    @OperationLog(module = "OPS", operationType = "UPDATE", description = "标记异常预警已处理")
    @PostMapping("/{id}/ack")
    public Result<Boolean> ack(@PathVariable Long id) {
        return Result.success(logAlertService.ack(id, securityHelper.currentUserId()));
    }

    @Operation(summary = "一键标记全部待处理预警已处理")
    @RequirePermission("ops:view")
    @OperationLog(module = "OPS", operationType = "UPDATE", description = "一键处理异常预警")
    @PostMapping("/ack-all")
    public Result<Integer> ackAll() {
        return Result.success(logAlertService.ackAll(securityHelper.currentUserId()));
    }
}
