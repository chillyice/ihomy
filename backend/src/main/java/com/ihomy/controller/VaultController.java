package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.annotation.RequirePermission;
import com.ihomy.common.Ips;
import com.ihomy.common.Result;
import com.ihomy.dto.VaultItemDTO;
import com.ihomy.dto.VaultRevealDTO;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.VaultService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 家庭保险箱接口:账号密码条目的增删改查 + 密码揭示。
 *
 * 安全约定:列表只回掩码;明文仅 POST /vault/{id}/password 在再次校验当前账号登录密码后返回,
 * 该接口写操作日志留痕;新增/编辑/揭示接口的入参含明文密码,故 @OperationLog(saveArgs = false) 不入参留库。
 */
@Tag(name = "家庭保险箱")
@RestController
@RequestMapping("/vault")
@RequiredArgsConstructor
public class VaultController {

    private final VaultService vaultService;
    private final SecurityHelper securityHelper;

    @Operation(summary = "保险箱条目列表(密码仅返回掩码,仅含自己的与家庭共享的)")
    @RequirePermission("vault:view")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        LoginUser user = securityHelper.current();
        return Result.success(vaultService.list(user.getFamilyId(), user.getUserId()));
    }

    @Operation(summary = "新增条目")
    @RequirePermission("vault:manage")
    @OperationLog(module = "VAULT", operationType = "CREATE", description = "新增保险箱条目", saveArgs = false)
    @PostMapping
    public Result<Long> create(@Valid @RequestBody VaultItemDTO dto) {
        LoginUser user = securityHelper.current();
        return Result.success(vaultService.create(user.getUserId(), user.getFamilyId(), dto));
    }

    @Operation(summary = "编辑条目(密码留空表示不改)")
    @RequirePermission("vault:manage")
    @OperationLog(module = "VAULT", operationType = "UPDATE", description = "编辑保险箱条目", saveArgs = false)
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody VaultItemDTO dto) {
        LoginUser user = securityHelper.current();
        vaultService.update(id, user.getFamilyId(), user.getUserId(), dto);
        return Result.success();
    }

    @Operation(summary = "删除条目")
    @RequirePermission("vault:manage")
    @OperationLog(module = "VAULT", operationType = "DELETE", description = "删除保险箱条目")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        LoginUser user = securityHelper.current();
        vaultService.delete(id, user.getFamilyId(), user.getUserId());
        return Result.success();
    }

    @Operation(summary = "查看条目密码(需再次输入当前账号的登录密码,明文,操作留痕)")
    @RequirePermission("vault:view")
    @OperationLog(module = "VAULT", operationType = "QUERY", description = "查看保险箱密码",
            saveArgs = false, saveResult = false)
    @PostMapping("/{id}/password")
    public Result<Map<String, String>> password(@PathVariable Long id,
                                                @Valid @RequestBody VaultRevealDTO dto,
                                                HttpServletRequest request) {
        LoginUser user = securityHelper.current();
        String plain = vaultService.revealPassword(id, user.getFamilyId(), user.getUserId(),
                dto.getPassword(), Ips.realIp(request));
        return Result.success(Map.of("password", plain));
    }
}
