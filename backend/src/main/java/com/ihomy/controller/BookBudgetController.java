package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.common.Result;
import com.ihomy.dto.BookBudgetDTO;
import com.ihomy.entity.BookBudget;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.BookBudgetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 记账预算接口:按支出分类设月度预算,列表带所选月份的实际支出对比。
 * 预算为家庭共享数据,改删限创建人本人或家长;家庭隔离由 Service 兜底(跨家庭一律当不存在)。
 */
@Tag(name = "记账预算")
@RestController
@RequestMapping("/book/budget")
@RequiredArgsConstructor
public class BookBudgetController {

    private final BookBudgetService bookBudgetService;
    private final SecurityHelper securityHelper;

    private LoginUser current() {
        return securityHelper.current();
    }

    @Operation(summary = "预算列表与当月支出对比(month=2026-07,缺省当月)")
    @GetMapping
    public Result<Map<String, Object>> list(@RequestParam(required = false) String month) {
        return Result.success(bookBudgetService.list(current().getFamilyId(), month));
    }

    @Operation(summary = "新增预算(同一分类只允许一条)")
    @OperationLog(module = "BOOK", operationType = "CREATE", description = "新增记账预算")
    @PostMapping
    public Result<BookBudget> create(@Valid @RequestBody BookBudgetDTO dto) {
        LoginUser user = current();
        return Result.success(bookBudgetService.create(user.getUserId(), user.getFamilyId(), dto));
    }

    @Operation(summary = "修改预算(本人或家长)")
    @OperationLog(module = "BOOK", operationType = "UPDATE", description = "修改记账预算")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody BookBudgetDTO dto) {
        LoginUser user = current();
        bookBudgetService.update(id, user.getFamilyId(), user.getUserId(), securityHelper.isOwner(), dto);
        return Result.success();
    }

    @Operation(summary = "删除预算(本人或家长)")
    @OperationLog(module = "BOOK", operationType = "DELETE", description = "删除记账预算")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        LoginUser user = current();
        bookBudgetService.delete(id, user.getFamilyId(), user.getUserId(), securityHelper.isOwner());
        return Result.success();
    }
}
