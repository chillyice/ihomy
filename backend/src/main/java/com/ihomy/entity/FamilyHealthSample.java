package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 家庭成员健康样本(family_health_sample):除睡眠外的全部指标。
 * period=DAY 为日聚合(一天一行),period=POINT 为单点或客户端小时聚合(心率取 avg,min/max/count 冗余)。
 * 禁止逐点心率入库——客户端先按小时聚合,否则单人一天上千行且请求被 2000 条上限卡死。
 */
@Data
@TableName("family_health_sample")
public class FamilyHealthSample {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private Long userId;
    private String metric;
    private String period;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal valueNum;
    private BigDecimal valueMin;
    private BigDecimal valueMax;
    private Integer sampleCount;
    private String unit;
    private String source;
    private String sourceApp;
    private String provider;
    private LocalDateTime createdAt;
}
