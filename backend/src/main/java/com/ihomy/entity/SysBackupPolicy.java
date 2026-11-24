package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 备份策略(sys_backup_policy,V14.51)。
 *
 * <p>一条策略 = 勾选的备份模块 + 执行周期 + 可选保留天数 + 启停开关。
 * 策略只描述「备份什么、什么时候备份、留多久」,真正的执行记录落在 sys_backup_job。
 * 模块码见 {@code service/backup/BackupModule}。</p>
 */
@Data
@TableName("sys_backup_policy")
public class SysBackupPolicy {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    /** 模块码,逗号分隔,按 BackupModule 枚举顺序保存 */
    private String modules;
    /** DAILY / WEEKLY / MONTHLY */
    private String scheduleType;
    /** HH:mm */
    private String scheduleTime;
    /** WEEKLY: 1-7(周一=1);MONTHLY: 1-28;DAILY 为 NULL */
    private Integer scheduleDay;
    /** 保留天数,NULL = 不自动清理 */
    private Integer retentionDays;
    private Boolean enabled;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
