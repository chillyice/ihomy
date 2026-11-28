package com.ihomy.garden.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 成长日志(games.garden_log):追加型行为流水;op_id 全局唯一是幂等与重放依据
 * (Idempotency-Key 头 → uk_op_id 兜底,架构分册 §17);保留 180 天后分批物理删(§15.2)。
 */
@Data
@TableName("games.garden_log")
public class GardenLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private String actorUid;
    private String opId;
    private Long lamport;
    private String deviceId;
    private String origin;
    private String action;
    private String cropCode;
    /** 地块ID(地块动作落,拆信/任务/技能行不落):照料 XP 每地块每日一次护栏 + 按地块窗口审计 */
    private Long plotId;
    private String variant;
    private String message;
    private String detail;
    private LocalDateTime createdAt;
}
