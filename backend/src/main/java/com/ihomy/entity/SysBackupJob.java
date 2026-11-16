package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 备份/迁移任务记录(sys_backup_job)。
 * 目录与文件是备份的事实,本表只记状态与统计;status=PURGED 表示文件已清理但保留审计。
 */
@Data
@TableName("sys_backup_job")
public class SysBackupJob {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String kind;
    private String scope;
    private String triggerType;
    private String status;
    private Long familyId;
    private Long operatorId;
    private String dirName;
    private Long sizeBytes;
    private Long dbBytes;
    private Integer fileCount;
    private Long dbRowCount;
    private Long durationMs;
    private String message;
    private String manifestSha256;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime purgedAt;
    private LocalDateTime createdAt;
}
