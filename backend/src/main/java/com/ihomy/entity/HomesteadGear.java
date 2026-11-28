package com.ihomy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 小院慢生活装备实例(game_homestead_gear):同一蓝本可有多件,一行一个实例。
 * 蓝本(效果/配方/战力)在 content/homestead/equipment.json;quality 决定效果倍率与词缀条数,
 * affixes 是掷出来的绝对值(SPEED:ALL:120,XP:FARM:80),enhance 0–5 每级 +50%,locked 防误分解。
 */
@Data
@TableName("game_homestead_gear")
public class HomesteadGear {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String gearCode;
    /** 品质编码(COMMON/FINE/RARE/EPIC/LEGENDARY;打造装固定 COMMON) */
    private String quality;
    /** 词缀紧凑串(打造装为空串) */
    private String affixes;
    private Integer enhance;
    /** 1 = 锁定(不能分解 / 重铸) */
    private Integer locked;
    /** 词缀重铸累计次数(确定性掷点序号,每次重铸 +1 保证结果必变) */
    private Integer rerollSeq;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
