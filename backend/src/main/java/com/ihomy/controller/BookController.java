package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.common.Result;
import com.ihomy.dto.BookDTO;
import com.ihomy.entity.BookRecord;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.BookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 记账接口:家庭共享账本,按月查询统计;改删校验记录人本人或家长。
 */
@Tag(name = "记账")
@RestController
@RequestMapping("/book")
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;
    private final SecurityHelper securityHelper;

    private LoginUser current() {
        return securityHelper.current();
    }

    @Operation(summary = "按月查询明细与统计(month=2026-07,缺省当月)")
    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam(required = false) String month) {
        return Result.success(bookService.list(current().getFamilyId(), month));
    }

    @Operation(summary = "本月收支摘要(供首页卡片)")
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        return Result.success(bookService.summary(current().getFamilyId()));
    }

    @Operation(summary = "记一笔(支出/收入/转账)")
    @OperationLog(module = "BOOK", operationType = "CREATE", description = "记账", saveArgs = false)
    @PostMapping
    public Result<BookRecord> create(@RequestBody BookDTO dto) {
        LoginUser user = current();
        return Result.success(bookService.create(user.getUserId(), user.getFamilyId(), dto));
    }

    @Operation(summary = "改账(记录人本人或家长)")
    @OperationLog(module = "BOOK", operationType = "UPDATE", description = "修改账目")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody BookDTO dto) {
        LoginUser user = current();
        bookService.update(id, user.getFamilyId(), user.getUserId(), securityHelper.isOwner(), dto);
        return Result.success();
    }

    @Operation(summary = "删账(记录人本人或家长)")
    @OperationLog(module = "BOOK", operationType = "DELETE", description = "删除账目")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        LoginUser user = current();
        bookService.delete(id, user.getFamilyId(), user.getUserId(), securityHelper.isOwner());
        return Result.success();
    }
}