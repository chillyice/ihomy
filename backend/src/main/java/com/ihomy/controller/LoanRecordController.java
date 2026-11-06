package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.annotation.RequirePermission;
import com.ihomy.common.Result;
import com.ihomy.dto.FamilyLoanDTO;
import com.ihomy.dto.LoanRepaySyncDTO;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.BookPermissionService;
import com.ihomy.service.LoanRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 家庭贷款记录接口:登记真实贷款与事件时间轴(利率调整/提前还款/换还款账户),
 * 还款流水由前端按事件时间轴重算展示;绑定还款账户后,已到期期次自动补记到记账本。
 */
@Tag(name = "家庭贷款记录")
@RestController
@RequestMapping("/loan")
@RequiredArgsConstructor
public class LoanRecordController {

    private final LoanRecordService loanRecordService;
    private final BookPermissionService bookPermissionService;
    private final SecurityHelper securityHelper;

    @Operation(summary = "贷款记录列表(含事件时间轴与已记账期次)")
    @RequirePermission("loan:view")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        LoginUser user = securityHelper.current();
        return Result.success(loanRecordService.list(user.getFamilyId()));
    }

    @Operation(summary = "新增贷款记录")
    @RequirePermission("loan:manage")
    @OperationLog(module = "LOAN", operationType = "CREATE", description = "新增贷款记录")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody FamilyLoanDTO dto) {
        LoginUser user = securityHelper.current();
        return Result.success(loanRecordService.create(user.getUserId(), user.getFamilyId(), dto));
    }

    @Operation(summary = "编辑贷款记录(事件时间轴整体替换)")
    @RequirePermission("loan:manage")
    @OperationLog(module = "LOAN", operationType = "UPDATE", description = "编辑贷款记录")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody FamilyLoanDTO dto) {
        LoginUser user = securityHelper.current();
        loanRecordService.update(id, user.getFamilyId(), dto);
        return Result.success();
    }

    @Operation(summary = "删除贷款记录")
    @RequirePermission("loan:manage")
    @OperationLog(module = "LOAN", operationType = "DELETE", description = "删除贷款记录")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        LoginUser user = securityHelper.current();
        loanRecordService.delete(id, user.getFamilyId());
        return Result.success();
    }

    /** 系统对账口径的补偿接口(页面加载时按流水补记到期月供、纠正换账户后的账目归属),高频且幂等,不记操作日志 */
    @Operation(summary = "同步贷款还款记账(补记已到期期次;换账户后把已记账目迁到新账户)")
    @RequirePermission("loan:view")
    @PostMapping("/repay/sync")
    public Result<Map<String, Object>> syncRepays(@Valid @RequestBody LoanRepaySyncDTO dto) {
        LoginUser user = securityHelper.current();
        // 自动记账 = 写家庭账本,只读档位不生成(避免只读成员把账本改出窟窿)
        bookPermissionService.requireWrite(user);
        return Result.success(loanRecordService.syncRepays(user.getFamilyId(), dto));
    }
}
