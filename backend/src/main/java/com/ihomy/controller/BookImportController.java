package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.common.Result;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.BookImportService;
import com.ihomy.service.BookPermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 记账流水导入接口:上传 CSV + 列映射,逐行校验后落成普通记账流水。
 * 列号从 0 起,-1 表示该列不映射;日期与金额必填,类型不映射时按金额正负判断。
 */
@Tag(name = "记账导入")
@RestController
@RequestMapping("/book")
@RequiredArgsConstructor
public class BookImportController {

    private final BookImportService bookImportService;
    private final BookPermissionService bookPermissionService;
    private final SecurityHelper securityHelper;

    @Operation(summary = "导入银行流水 CSV(≤2MB、≤2000 行;返回成功/失败条数与前 20 条错误)")
    @OperationLog(module = "BOOK", operationType = "CREATE", description = "导入记账流水", saveArgs = false)
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, Object>> importCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam int dateCol,
            @RequestParam(defaultValue = "-1") int typeCol,
            @RequestParam int amountCol,
            @RequestParam(defaultValue = "-1") int categoryCol,
            @RequestParam(defaultValue = "-1") int accountCol,
            @RequestParam(defaultValue = "-1") int remarkCol,
            @RequestParam(defaultValue = "1") int headerRows) {
        LoginUser user = securityHelper.current();
        // 导入会新增流水:只读档位直接拒绝
        bookPermissionService.requireWrite(user);
        BookImportService.CsvMapping mapping = new BookImportService.CsvMapping(
                dateCol, typeCol, amountCol, categoryCol, accountCol, remarkCol, headerRows);
        return Result.success(bookImportService.importCsv(user.getUserId(), user.getFamilyId(), file, mapping));
    }
}
