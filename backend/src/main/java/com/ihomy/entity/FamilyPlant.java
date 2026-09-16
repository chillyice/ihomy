package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 家庭共养植物实体(family_plant):全家一棵、实时养成;阶段/营养由累计成长分钟与施肥历史实时推导,不落库。
 */
@Data
@TableName("family_plant")
public class FamilyPlant {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private String species;
    private LocalDateTime plantedAt;
    private LocalDateTime lastWateredAt;
    private LocalDateTime lastSunAt;
    private LocalDateTime lastFertilizedAt;
    private Integer waterCount;
    private Integer sunCount;
    private Integer fertilizeCount;
    private Integer harvestCount;
    /** 累计照料加成(分钟,已按当日天气修正;收获清零) */
    private Integer careBoostMinutes;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
