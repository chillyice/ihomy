package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.common.Result;
import com.ihomy.dto.FinanceDTO;
import com.ihomy.entity.FamilyFinanceProduct;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.BookPermissionService;
import com.ihomy.service.FinanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 理财储蓄接口:家庭持仓的增删改查,以及赎回时的记账联动。
 * 理财是记账本的页签之一,可见/可写按成员账本档位校验;改删限创建人本人或可管理账本者。
 */
@Tag(name = "家庭理财")
@RestController
@RequestMapping("/finance")
@RequiredArgsConstructor
public class FinanceController {

    private final FinanceService financeService;
    private final BookPermissionService bookPermissionService;
    private final SecurityHelper securityHelper;

    private LoginUser current() {
        return securityHelper.current();
    }

    @Operation(summary = "理财持仓列表(含资金账户与入账标记)")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        LoginUser user = current();
        bookPermissionService.requireRead(user);
        return Result.success(financeService.list(user.getFamilyId()));
    }

    @Operation(summary = "新增一笔理财(可同时记购买转出)")
    @OperationLog(module = "FINANCE", operationType = "CREATE", description = "新增理财持仓")
    @PostMapping
    public Result<FamilyFinanceProduct> create(@Valid @RequestBody FinanceDTO dto) {
        LoginUser user = current();
        bookPermissionService.requireWrite(user);
        return Result.success(financeService.create(user.getUserId(), user.getFamilyId(), dto));
    }

    @Operation(summary = "编辑理财(本人或可管理账本者;可同时记赎回转入与收益收入)")
    @OperationLog(module = "FINANCE", operationType = "UPDATE", description = "编辑理财持仓")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody FinanceDTO dto) {
        LoginUser user = current();
        String level = bookPermissionService.requireWrite(user);
        financeService.update(id, user.getFamilyId(), user.getUserId(), bookPermissionService.canManage(level), dto);
        return Result.success();
    }

    @Operation(summary = "删除理财(本人或可管理账本者)")
    @OperationLog(module = "FINANCE", operationType = "DELETE", description = "删除理财持仓")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        LoginUser user = current();
        String level = bookPermissionService.requireWrite(user);
        financeService.delete(id, user.getFamilyId(), user.getUserId(), bookPermissionService.canManage(level));
        return Result.success();
    }
}
