package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.common.Result;
import com.ihomy.dto.BookRecurringDTO;
import com.ihomy.entity.BookRecurring;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.BookPermissionService;
import com.ihomy.service.BookRecurringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 记账周期模板接口:按周期自动生成固定收支(房租/工资/定投等)。
 * 生成出来的是普通记账流水;模板为家庭共享数据,可见/可写按成员账本档位校验。
 */
@Tag(name = "记账周期模板")
@RestController
@RequestMapping("/book/recurring")
@RequiredArgsConstructor
public class BookRecurringController {

    private final BookRecurringService recurringService;
    private final BookPermissionService bookPermissionService;
    private final SecurityHelper securityHelper;

    private LoginUser current() {
        return securityHelper.current();
    }

    @Operation(summary = "周期模板列表(含下次生成日)")
    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        LoginUser user = current();
        bookPermissionService.requireRead(user);
        return Result.success(recurringService.list(user.getFamilyId()));
    }

    @Operation(summary = "新增周期模板")
    @OperationLog(module = "BOOK", operationType = "CREATE", description = "新增周期账目")
    @PostMapping
    public Result<BookRecurring> create(@Valid @RequestBody BookRecurringDTO dto) {
        LoginUser user = current();
        bookPermissionService.requireWrite(user);
        return Result.success(recurringService.create(user.getUserId(), user.getFamilyId(), dto));
    }

    @Operation(summary = "修改周期模板(本人或可管理账本者)")
    @OperationLog(module = "BOOK", operationType = "UPDATE", description = "修改周期账目")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody BookRecurringDTO dto) {
        LoginUser user = current();
        String level = bookPermissionService.requireWrite(user);
        recurringService.update(id, user.getFamilyId(), user.getUserId(), bookPermissionService.canManage(level), dto);
        return Result.success();
    }

    @Operation(summary = "暂停或恢复周期模板(本人或可管理账本者)")
    @OperationLog(module = "BOOK", operationType = "UPDATE", description = "启停周期账目")
    @PutMapping("/{id}/enabled")
    public Result<Void> setEnabled(@PathVariable Long id, @RequestParam Integer enabled) {
        LoginUser user = current();
        String level = bookPermissionService.requireWrite(user);
        recurringService.setEnabled(id, user.getFamilyId(), user.getUserId(), bookPermissionService.canManage(level), enabled);
        return Result.success();
    }

    @Operation(summary = "删除周期模板(本人或可管理账本者;已生成的流水保留)")
    @OperationLog(module = "BOOK", operationType = "DELETE", description = "删除周期账目")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        LoginUser user = current();
        String level = bookPermissionService.requireWrite(user);
        recurringService.delete(id, user.getFamilyId(), user.getUserId(), bookPermissionService.canManage(level));
        return Result.success();
    }

    @Operation(summary = "立即生成本家庭的到期流水(与每日定时任务同一逻辑,重复执行不会重复生成)")
    @OperationLog(module = "BOOK", operationType = "CREATE", description = "生成周期账目")
    @PostMapping("/run")
    public Result<Map<String, Object>> run() {
        LoginUser user = current();
        bookPermissionService.requireWrite(user);
        int generated = recurringService.generateForFamily(user.getFamilyId());
        return Result.success(Map.of("generated", generated));
    }
}
