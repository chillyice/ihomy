package com.ihomy.service.backup;

/**
 * 备份类型。与 sys_backup_job.kind 同值:
 * - BASIC:基础数据(账号/权限/家庭/日程/计划/记账等结构化数据)
 * - CONTENT:内容数据(content_/report_/game_ 表 + uploads 文件增量快照)
 * - FULL:全量快照(基础 + 内容 + 全部文件)
 * - MIGRATION:迁移包(内容同 FULL,但只手动生成、不参与自动清理)
 */
public enum BackupKind {
    BASIC,
    CONTENT,
    FULL,
    MIGRATION;

    public boolean includesFiles() {
        return this == CONTENT || this == FULL || this == MIGRATION;
    }

    public boolean isFullDump() {
        return this == FULL || this == MIGRATION;
    }
}
