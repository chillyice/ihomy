package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 同类异常聚合预警(report_alert):
 * 日志里同类问题在统计窗口内达到阈值时由 LogAlertService 落一条,供运维页「异常预警」查看与标记已处理。
 * 只是把「同一类」问题汇总成一条,不含任何用户内容明细。
 */
@Data
@TableName("report_alert")
public class ReportAlert {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 同类问题指纹(归一化后哈希) */
    private String fingerprint;
    /** 日志级别:ERROR/WARN */
    private String level;
    /** 来源 logger */
    private String logger;
    /** 问题摘要(异常类型 + 位置,或归一化消息) */
    private String title;
    /** 首次出现时的原始日志消息 */
    private String sampleMessage;
    /** 首次出现时的追踪号 tid(定时任务等异步链路可能为空) */
    private String sampleTraceId;
    /** 窗口内出现次数 */
    private Integer occurrenceCount;
    /** 统计窗口(秒) */
    private Integer windowSeconds;
    /** 触发阈值 */
    private Integer threshold;
    private LocalDateTime firstSeen;
    private LocalDateTime lastSeen;
    /** OPEN待处理 / ACKED已处理 */
    private String status;
    /** 是否已邮件推送:0未推送 1已推送 */
    private Integer notified;
    private Long ackedBy;
    private LocalDateTime ackedAt;
    private LocalDateTime createdAt;
}
