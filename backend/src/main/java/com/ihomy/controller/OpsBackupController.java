package com.ihomy.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ihomy.annotation.OperationLog;
import com.ihomy.annotation.RequirePermission;
import com.ihomy.common.BizException;
import com.ihomy.common.Result;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.BackupCreateDTO;
import com.ihomy.dto.BackupPolicyDTO;
import com.ihomy.entity.SysBackupJob;
import com.ihomy.entity.SysBackupPolicy;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.backup.BackupJobRunner;
import com.ihomy.service.backup.BackupKind;
import com.ihomy.service.backup.BackupPolicyService;
import com.ihomy.service.backup.BackupScheduler;
import com.ihomy.service.backup.BackupScope;
import com.ihomy.service.backup.BackupService;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 运维备份管理(V12.54 / V14.51):备份策略列表、立即执行、迁移包、任务列表、下载、校验与删除。
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

    @Operation(summary = "备份概览(占用空间/磁盘余量/最近成功/策略数量)")
    @RequirePermission("ops:view")
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.success(backupService.overview());
    }

    @Operation(summary = "备份策略列表(含最近执行与下次执行)")
    @RequirePermission("ops:view")
    @GetMapping("/policies")
    public Result<List<Map<String, Object>>> policies() {
        return Result.success(policyService.views());
    }

    @Operation(summary = "新建备份策略")
    @RequirePermission("ops:view")
    @OperationLog(module = "OPS", operationType = "CREATE", description = "新建备份策略")
    @PostMapping("/policies")
    public Result<Map<String, Object>> createPolicy(@Valid @RequestBody BackupPolicyDTO dto) {
        SysBackupPolicy saved = policyService.create(dto, securityHelper.currentUserId());
        scheduler.reload();
        return Result.success(policyService.view(saved, null, LocalDateTime.now()));
    }

    @Operation(summary = "编辑备份策略")
    @RequirePermission("ops:view")
    @OperationLog(module = "OPS", operationType = "UPDATE", description = "编辑备份策略")
    @PutMapping("/policies/{id}")
    public Result<Map<String, Object>> updatePolicy(@PathVariable Long id, @Valid @RequestBody BackupPolicyDTO dto) {
        SysBackupPolicy saved = policyService.update(id, dto);
        scheduler.reload();
        return Result.success(policyService.view(saved, null, LocalDateTime.now()));
    }

    @Operation(summary = "删除备份策略(历史执行记录保留)")
    @RequirePermission("ops:view")
    @OperationLog(module = "OPS", operationType = "DELETE", description = "删除备份策略")
    @DeleteMapping("/policies/{id}")
    public Result<Boolean> deletePolicy(@PathVariable Long id) {
        policyService.delete(id);
        scheduler.reload();
        return Result.success(true);
    }

    @Operation(summary = "立即执行一条备份策略")
    @RequirePermission("ops:view")
    @OperationLog(module = "OPS", operationType = "CREATE", description = "按策略执行数据备份")
    @PostMapping("/policies/{id}/run")
    public Result<SysBackupJob> runPolicy(@PathVariable Long id) {
        SysBackupPolicy policy = policyService.mustGet(id);
        SysBackupJob job = backupService.createPolicy(policy, securityHelper.currentUserId(), "MANUAL");
        runner.run(job.getId());
        return Result.success(job);
    }

    @Operation(summary = "可用备份模块目录(表数量与是否含上传文件)")
    @RequirePermission("ops:view")
    @GetMapping("/modules")
    public Result<List<Map<String, Object>>> modules() {
        return Result.success(policyService.moduleCatalog());
    }

    @Operation(summary = "备份任务分页(全实例,可按策略/类型/状态筛选)")
    @RequirePermission("ops:view")
    @GetMapping("/jobs")
    public Result<IPage<SysBackupJob>> jobs(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long policyId) {
        return Result.success(backupService.page(current, size, kind, status, BackupScope.INSTANCE, null, policyId));
    }

    @Operation(summary = "立即执行一次固定类型备份/生成迁移包")
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
        if (kind == BackupKind.POLICY) {
            throw new BizException(ResultCode.BAD_REQUEST, "请从备份策略列表执行策略备份");
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
