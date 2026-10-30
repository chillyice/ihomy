package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.common.Result;
import com.ihomy.dto.BookDTO;
import com.ihomy.entity.BookRecord;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.BookPermissionService;
import com.ihomy.service.BookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 记账接口:家庭共享账本,按月查询统计;改删校验记录人本人或可管理账本者。
 * 可见性与读写档位由 BookPermissionService 按成员配置(家长可给某个成员单独设档)。
 */
@Tag(name = "记账")
@RestController
@RequestMapping("/book")
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;
    private final BookPermissionService bookPermissionService;
    private final SecurityHelper securityHelper;

    private LoginUser current() {
        return securityHelper.current();
    }

    @Operation(summary = "按月查询明细与统计(month=2026-07,缺省当月)")
    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam(required = false) String month) {
        LoginUser user = current();
        bookPermissionService.requireRead(user);
        return Result.success(bookService.list(user.getFamilyId(), month));
    }

    @Operation(summary = "本月收支摘要(供首页卡片)")
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        // 本接口在 SecurityConfig 里对游客放行(公开首页卡片),匿名时无家庭可算:返回空摘要,不能 NPE;
        // 被家长设为「不可见」的成员按游客口径给空摘要,不泄露家庭收支
        LoginUser user = current();
        boolean canRead = user != null && bookPermissionService.canRead(bookPermissionService.levelOf(user));
        return Result.success(bookService.summary(canRead ? user.getFamilyId() : null));
    }

    @Operation(summary = "总账汇总(累计收支、年度月度、分类与成员)")
    @GetMapping("/ledger")
    public Result<Map<String, Object>> ledger(@RequestParam(required = false) Integer year) {
        LoginUser user = current();
        bookPermissionService.requireRead(user);
        return Result.success(bookService.ledger(user.getFamilyId(), year));
    }

    @Operation(summary = "记一笔(支出/收入/转账)")
    @OperationLog(module = "BOOK", operationType = "CREATE", description = "记账", saveArgs = false)
    @PostMapping
    public Result<BookRecord> create(@Valid @RequestBody BookDTO dto) {
        LoginUser user = current();
        bookPermissionService.requireWrite(user);
        return Result.success(bookService.create(user.getUserId(), user.getFamilyId(), dto));
    }

    @Operation(summary = "改账(记录人本人或可管理账本者)")
    @OperationLog(module = "BOOK", operationType = "UPDATE", description = "修改账目")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody BookDTO dto) {
        LoginUser user = current();
        String level = bookPermissionService.requireWrite(user);
        bookService.update(id, user.getFamilyId(), user.getUserId(), bookPermissionService.canManage(level), dto);
        return Result.success();
    }

    @Operation(summary = "删账(记录人本人或可管理账本者)")
    @OperationLog(module = "BOOK", operationType = "DELETE", description = "删除账目")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        LoginUser user = current();
        String level = bookPermissionService.requireWrite(user);
        bookService.delete(id, user.getFamilyId(), user.getUserId(), bookPermissionService.canManage(level));
        return Result.success();
    }
}