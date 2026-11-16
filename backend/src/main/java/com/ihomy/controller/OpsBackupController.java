package com.ihomy.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ihomy.annotation.OperationLog;
import com.ihomy.annotation.RequirePermission;
import com.ihomy.common.BizException;
import com.ihomy.common.Result;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.BackupCreateDTO;
import com.ihomy.entity.SysBackupJob;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.backup.BackupKind;
import com.ihomy.service.backup.BackupPolicy;
import com.ihomy.service.backup.BackupPolicyService;
import com.ihomy.service.backup.BackupScheduler;
import com.ihomy.service.backup.BackupScope;
import com.ihomy.service.backup.BackupService;
import com.ihomy.service.backup.BackupJobRunner;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

/**
 * 运维备份管理(V12.54):基础/内容/全量/迁移包的生成、列表、下载、校验、删除与定时策略。
 * 仅 OPS 可见(@RequirePermission + OpsAccessFilter 双保险)。
 */
@Tag(name = "备份与迁移")
@RestController
@RequestMapping("/ops/backup")
@RequiredArgsConstructor
public class OpsBackupController {

    private final BackupService backupService;
    private final BackupPolicyService policyService;
    private final BackupScheduler scheduler;
    private final BackupJobRunner runner;
    private final SecurityHelper securityHelper;

    @Operation(summary = "备份概览(占用空间/磁盘余量/最近成功/下次执行/策略)")
    @RequirePermission("ops:view")
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.success(backupService.overview());
    }

    @Operation(summary = "读取定时备份策略")
    @RequirePermission("ops:view")
    @GetMapping("/policy")
    public Result<BackupPolicy> policy() {
        return Result.success(policyService.load());
    }

    @Operation(summary = "保存定时备份策略(立即生效)")
    @RequirePermission("ops:view")
    @OperationLog(module = "OPS", operationType = "UPDATE", description = "保存定时备份策略")
    @PutMapping("/policy")
    public Result<BackupPolicy> savePolicy(@RequestBody BackupPolicy policy) {
        BackupPolicy saved = policyService.save(policy);
        scheduler.reload();
        return Result.success(saved);
    }

    @Operation(summary = "备份任务分页(全实例,可按类型/状态筛选)")
    @RequirePermission("ops:view")
    @GetMapping("/jobs")
    public Result<IPage<SysBackupJob>> jobs(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String status) {
        return Result.success(backupService.page(current, size, kind, status, BackupScope.INSTANCE, null));
    }

    @Operation(summary = "立即执行一次备份/生成迁移包")
    @RequirePermission("ops:view")
    @OperationLog(module = "OPS", operationType = "CREATE", description = "手动执行数据备份")
    @PostMapping("/jobs")
    public Result<SysBackupJob> create(@Valid @RequestBody BackupCreateDTO dto) {
        BackupKind kind;
        try {
            kind = BackupKind.valueOf(dto.getKind().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "备份类型不正确");
        }
        SysBackupJob job = backupService.create(kind, BackupScope.INSTANCE, null,
                securityHelper.currentUserId(), "MANUAL");
        runner.run(job.getId());
        return Result.success(job);
    }

    @Operation(summary = "校验备份完整性(meta 哈希/SQL gzip/文件清单)")
    @RequirePermission("ops:view")
    @PostMapping("/jobs/{id}/verify")
    public Result<Map<String, Object>> verify(@PathVariable Long id) {
        return Result.success(backupService.verify(id));
    }

    @Operation(summary = "删除备份文件(保留审计记录)")
    @RequirePermission("ops:view")
    @OperationLog(module = "OPS", operationType = "DELETE", description = "删除备份文件")
    @DeleteMapping("/jobs/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        backupService.deleteJob(id);
        return Result.success(true);
    }

    @Operation(summary = "下载备份包(zip,超过站内上限请直接在服务器复制)")
    @RequirePermission("ops:view")
    @GetMapping("/jobs/{id}/download")
    public void download(@PathVariable Long id, HttpServletResponse response) throws IOException {
        SysBackupJob job = backupService.mustGet(id);
        response.setContentType("application/zip");
        String name = "ihomy-" + job.getKind().toLowerCase() + "-" + job.getId() + ".zip";
        response.setHeader("Content-Disposition", "attachment; filename=\"" + name + "\"");
        backupService.streamZip(id, response.getOutputStream());
    }
}
