package com.ihomy.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ihomy.annotation.OperationLog;
import com.ihomy.annotation.RequirePermission;
import com.ihomy.common.BizException;
import com.ihomy.common.Result;
import com.ihomy.common.ResultCode;
import com.ihomy.entity.SysBackupJob;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.backup.BackupKind;
import com.ihomy.service.backup.BackupJobRunner;
import com.ihomy.service.backup.BackupScope;
import com.ihomy.service.backup.BackupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * 家庭数据导出(V12.54):家长把本家庭的数据库记录与引用文件打成迁移包。
 * 只做导出,不做站内导入;包内保留原始 ID,恢复说明见包内 RESTORE.md。
 */
@Tag(name = "家庭数据导出")
@RestController
@RequestMapping("/backup")
@RequiredArgsConstructor
public class FamilyBackupController {

    private final BackupService backupService;
    private final BackupJobRunner runner;
    private final SecurityHelper securityHelper;

    private Long familyId() {
        Long familyId = securityHelper.current() == null ? null : securityHelper.current().getFamilyId();
        if (familyId == null) throw new BizException(ResultCode.FORBIDDEN, "当前账号没有所属家庭");
        return familyId;
    }

    private SysBackupJob own(Long id) {
        SysBackupJob job = backupService.mustGet(id);
        if (!BackupScope.FAMILY.name().equals(job.getScope())
                || !familyId().equals(job.getFamilyId())) {
            throw new BizException(ResultCode.NOT_FOUND, "备份任务不存在");
        }
        return job;
    }

    @Operation(summary = "本家庭的数据导出记录(分页)")
    @RequirePermission("family:manage")
    @GetMapping("/jobs")
    public Result<IPage<SysBackupJob>> jobs(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(backupService.page(current, size, null, null, BackupScope.FAMILY, familyId(), null));
    }

    @Operation(summary = "导出本家庭数据(后台生成迁移包)")
    @RequirePermission("family:manage")
    @OperationLog(module = "BACKUP", operationType = "CREATE", description = "导出家庭数据包")
    @PostMapping("/jobs")
    public Result<SysBackupJob> create() {
        SysBackupJob job = backupService.create(BackupKind.MIGRATION, BackupScope.FAMILY, familyId(),
                securityHelper.currentUserId(), "MANUAL");
        runner.run(job.getId());
        return Result.success(job);
    }

    @Operation(summary = "下载本家庭的数据包")
    @RequirePermission("family:manage")
    @GetMapping("/jobs/{id}/download")
    public void download(@PathVariable Long id, HttpServletResponse response) throws IOException {
        SysBackupJob job = own(id);
        backupService.checkFamilyDiaryPrivacy(job);
        response.setContentType("application/zip");
        String name = "ihomy-family-" + job.getId() + ".zip";
        response.setHeader("Content-Disposition", "attachment; filename=\"" + name + "\"");
        backupService.streamZip(id, response.getOutputStream());
    }

    @Operation(summary = "删除本家庭的数据包文件")
    @RequirePermission("family:manage")
    @OperationLog(module = "BACKUP", operationType = "DELETE", description = "删除家庭数据包")
    @DeleteMapping("/jobs/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        own(id);
        backupService.deleteJob(id);
        return Result.success(true);
    }
}
