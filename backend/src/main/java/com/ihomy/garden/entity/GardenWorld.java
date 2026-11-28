package com.ihomy.garden.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 花园世界注册表(games.garden_world):ihomy 模式 id = family_id(恒等映射);
 * last_mail_date 是每日信封的幂等闸门(FR-27),settings JSON 存 cycle_start_at。
 */
@Data
@TableName("games.garden_world")
public class GardenWorld {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String worldId;
    private String kind;
    private String origin;
    private String authority;
    private String externalRef;
    private Integer hostEpoch;
    private String contentVersion;
    private String settings;
    private LocalDate lastMailDate;
    private String importedFrom;
    private LocalDateTime importedAt;
    private LocalDateTime settledAt;
    private String createdBy;
    private LocalDateTime createdAt;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
