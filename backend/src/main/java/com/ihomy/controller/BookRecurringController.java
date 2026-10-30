package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.common.Result;
import com.ihomy.dto.BookRecurringDTO;
import com.ihomy.entity.BookRecurring;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
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
 * 生成出来的是普通记账流水;模板为家庭共享数据,改删限创建人本人或家长。
 */
@Tag(name = "记账周期模板")
@RestController
@RequestMapping("/book/recurring")
@RequiredArgsConstructor
public class BookRecurringController {

    private final BookRecurringService recurringService;
    private final SecurityHelper securityHelper;

    private LoginUser current() {
        return securityHelper.current();
    }

    @Operation(summary = "周期模板列表(含下次生成日)")
    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        return Result.success(recurringService.list(current().getFamilyId()));
    }

    @Operation(summary = "新增周期模板")
    @OperationLog(module = "BOOK", operationType = "CREATE", description = "新增周期账目")
    @PostMapping
    public Result<BookRecurring> create(@Valid @RequestBody BookRecurringDTO dto) {
        LoginUser user = current();
        return Result.success(recurringService.create(user.getUserId(), user.getFamilyId(), dto));
    }

    @Operation(summary = "修改周期模板(本人或家长)")
    @OperationLog(module = "BOOK", operationType = "UPDATE", description = "修改周期账目")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody BookRecurringDTO dto) {
        LoginUser user = current();
        recurringService.update(id, user.getFamilyId(), user.getUserId(), securityHelper.isOwner(), dto);
        return Result.success();
    }

    @Operation(summary = "暂停或恢复周期模板(本人或家长)")
    @OperationLog(module = "BOOK", operationType = "UPDATE", description = "启停周期账目")
    @PutMapping("/{id}/enabled")
    public Result<Void> setEnabled(@PathVariable Long id, @RequestParam Integer enabled) {
        LoginUser user = current();
        recurringService.setEnabled(id, user.getFamilyId(), user.getUserId(), securityHelper.isOwner(), enabled);
        return Result.success();
    }

    @Operation(summary = "删除周期模板(本人或家长;已生成的流水保留)")
    @OperationLog(module = "BOOK", operationType = "DELETE", description = "删除周期账目")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        LoginUser user = current();
        recurringService.delete(id, user.getFamilyId(), user.getUserId(), securityHelper.isOwner());
        return Result.success();
    }

    @Operation(summary = "立即生成本家庭的到期流水(与每日定时任务同一逻辑,重复执行不会重复生成)")
    @OperationLog(module = "BOOK", operationType = "CREATE", description = "生成周期账目")
    @PostMapping("/run")
    public Result<Map<String, Object>> run() {
        int generated = recurringService.generateForFamily(current().getFamilyId());
        return Result.success(Map.of("generated", generated));
    }
}
