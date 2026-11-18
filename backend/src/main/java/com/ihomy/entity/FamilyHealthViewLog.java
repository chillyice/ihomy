package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 健康数据查看审计(family_health_view_log):轻量——只记谁在何时看了谁的什么范围,不记具体数值。
 * 只在跨人读取时写(本人读自己不记,避免自读刷屏);写失败只 WARN,不阻断读接口。保留 90 天。
 */
@Data
@TableName("family_health_view_log")
public class FamilyHealthViewLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private Long viewerId;
    private Long targetUserId;
    private String scope;
    private String metric;
    private LocalDate fromDate;
    private LocalDate toDate;
    private String viewerIp;
    private LocalDateTime createdAt;
}
