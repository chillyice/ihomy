package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.common.BizException;
import com.ihomy.common.Result;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.BookPermissionDTO;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.BookPermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 记账本权限接口:查询自己能看到账本的档位;家长给某个成员单独设置档位。
 * 档位只有家长能改,家长本人恒为可管理(不能被降级,避免把自己锁在账本外)。
 */
@Tag(name = "记账权限")
@RestController
@RequestMapping("/book/permission")
@RequiredArgsConstructor
public class BookPermissionController {

    private final BookPermissionService bookPermissionService;
    private final SecurityHelper securityHelper;

    private LoginUser current() {
        LoginUser user = securityHelper.current();
        if (user == null) throw new BizException(ResultCode.UNAUTHORIZED);
        return user;
    }

    @Operation(summary = "当前账号的账本权限(家长附带各成员档位)")
    @GetMapping
    public Result<Map<String, Object>> overview() {
        return Result.success(bookPermissionService.overview(current()));
    }

    @Operation(summary = "给某个成员设置账本权限档位(仅家长)")
    @OperationLog(module = "BOOK", operationType = "UPDATE", description = "设置成员账本权限")
    @PutMapping("/{userId}")
    public Result<Void> setLevel(@PathVariable Long userId, @Valid @RequestBody BookPermissionDTO dto) {
        bookPermissionService.setLevel(current(), userId, dto.getLevel());
        return Result.success();
    }
}
