package com.ihomy.service.backup;

/** 备份任务状态。PURGED 表示文件已按保留策略清理,记录仍保留用于审计。 */
public enum BackupStatus {
    RUNNING,
    SUCCESS,
    FAILED,
    PURGED
}
