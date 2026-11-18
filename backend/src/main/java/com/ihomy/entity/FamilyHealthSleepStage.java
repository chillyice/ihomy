package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 睡眠分期区间(family_health_sleep_stage):画逐段曲线用,随所属睡眠会话重算级联重建。
 * 派生数据,重算时物理删(走 Mapper XML)。
 */
@Data
@TableName("family_health_sleep_stage")
public class FamilyHealthSleepStage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private Long userId;
    private Long sleepId;
    private String stage;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer durationMin;
}
