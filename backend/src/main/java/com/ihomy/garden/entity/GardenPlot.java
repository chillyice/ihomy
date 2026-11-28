package com.ihomy.garden.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 花园地块(games.garden_plot):owner_uid 空=全家共享、非空=个人房间(仅本人可写,FR-01/FR-03);
 * 生长阶段与成熟度不落库,由 planted_at + 照料历史实时推导(架构分册 §16.3)。
 */
@Data
@TableName("games.garden_plot")
public class GardenPlot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    /** 归属:''=全家共享;非空=该成员的 player_uid */
    private String ownerUid;
    private String venue;
    private Integer slotNo;
    private String soilType;
    private Integer prepared;
    private String cropCode;
    private String variant;
    private String prevFamily;
    private String facility;
    private LocalDateTime plantedAt;
    private LocalDateTime lastWateredAt;
    private LocalDateTime lastSunAt;
    private LocalDateTime lastMistAt;
    private LocalDateTime lastFertilizedAt;
    private Integer careBoostMinutes;
    private Integer waterCount;
    private Integer sunCount;
    private Integer mistCount;
    private Integer fertilizeCount;
    private Integer harvestCount;
    private String createdBy;
    private LocalDateTime createdAt;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
