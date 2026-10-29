package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.common.Result;
import com.ihomy.dto.BookAccountDTO;
import com.ihomy.entity.BookAccount;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.BookAccountService;
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
 * 记账账户接口:现金/银行卡/支付宝等账户的增删改查与实时余额。
 * 账户为家庭共享数据,改删限创建人本人或家长;家庭隔离由 Service 兜底(跨家庭一律当不存在)。
 */
@Tag(name = "记账账户")
@RestController
@RequestMapping("/book/accounts")
@RequiredArgsConstructor
public class BookAccountController {

    private final BookAccountService bookAccountService;
    private final SecurityHelper securityHelper;

    private LoginUser current() {
        return securityHelper.current();
    }

    @Operation(summary = "账户列表与实时余额")
    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        return Result.success(bookAccountService.list(current().getFamilyId()));
    }

    @Operation(summary = "新增账户")
    @OperationLog(module = "BOOK", operationType = "CREATE", description = "新增记账账户")
    @PostMapping
    public Result<BookAccount> create(@Valid @RequestBody BookAccountDTO dto) {
        LoginUser user = current();
        return Result.success(bookAccountService.create(user.getUserId(), user.getFamilyId(), dto));
    }

    @Operation(summary = "修改账户(本人或家长)")
    @OperationLog(module = "BOOK", operationType = "UPDATE", description = "修改记账账户")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody BookAccountDTO dto) {
        LoginUser user = current();
        bookAccountService.update(id, user.getFamilyId(), user.getUserId(), securityHelper.isOwner(), dto);
        return Result.success();
    }

    @Operation(summary = "删除账户(本人或家长;账户下还有记录时不能删)")
    @OperationLog(module = "BOOK", operationType = "DELETE", description = "删除记账账户")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        LoginUser user = current();
        bookAccountService.delete(id, user.getFamilyId(), user.getUserId(), securityHelper.isOwner());
        return Result.success();
    }
}
