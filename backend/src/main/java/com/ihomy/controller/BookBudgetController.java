package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.common.Result;
import com.ihomy.dto.BookBudgetDTO;
import com.ihomy.entity.BookBudget;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.BookBudgetService;
import com.ihomy.service.BookPermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 记账预算接口:按支出分类设月度预算,列表带所选月份的实际支出对比。
 * 预算为家庭共享数据,可见/可写按成员账本档位校验;改删限创建人本人或可管理账本者。
 */
@Tag(name = "记账预算")
@RestController
@RequestMapping("/book/budget")
@RequiredArgsConstructor
public class BookBudgetController {

    private final BookBudgetService bookBudgetService;
    private final BookPermissionService bookPermissionService;
    private final SecurityHelper securityHelper;

    private LoginUser current() {
        return securityHelper.current();
    }

    @Operation(summary = "预算列表与当月支出对比(month=2026-07,缺省当月)")
    @GetMapping
    public Result<Map<String, Object>> list(@RequestParam(required = false) String month) {
        LoginUser user = current();
        bookPermissionService.requireRead(user);
        return Result.success(bookBudgetService.list(user.getFamilyId(), month));
    }

    @Operation(summary = "新增预算(同一分类只允许一条)")
    @OperationLog(module = "BOOK", operationType = "CREATE", description = "新增记账预算")
    @PostMapping
    public Result<BookBudget> create(@Valid @RequestBody BookBudgetDTO dto) {
        LoginUser user = current();
        bookPermissionService.requireWrite(user);
        return Result.success(bookBudgetService.create(user.getUserId(), user.getFamilyId(), dto));
    }

    @Operation(summary = "修改预算(本人或可管理账本者)")
    @OperationLog(module = "BOOK", operationType = "UPDATE", description = "修改记账预算")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody BookBudgetDTO dto) {
        LoginUser user = current();
        String level = bookPermissionService.requireWrite(user);
        bookBudgetService.update(id, user.getFamilyId(), user.getUserId(), bookPermissionService.canManage(level), dto);
        return Result.success();
    }

    @Operation(summary = "删除预算(本人或可管理账本者)")
    @OperationLog(module = "BOOK", operationType = "DELETE", description = "删除记账预算")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        LoginUser user = current();
        String level = bookPermissionService.requireWrite(user);
        bookBudgetService.delete(id, user.getFamilyId(), user.getUserId(), bookPermissionService.canManage(level));
        return Result.success();
    }
}
