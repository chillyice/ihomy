package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 家庭成员训练记录(family_health_workout):一次训练一行,按 (user_id, source_app, start_time) 幂等覆盖。
 * 与样本表分开的原因:训练有起止、类型、距离、消耗多个维度,折进样本表只能留一个数字。
 */
@Data
@TableName("family_health_workout")
public class FamilyHealthWorkout {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private Long userId;
    private String workoutType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer durationMin;
    private BigDecimal distanceKm;
    private BigDecimal energyKcal;
    private BigDecimal avgHr;
    private String source;
    private String sourceApp;
    private String provider;
    private LocalDateTime createdAt;
}
