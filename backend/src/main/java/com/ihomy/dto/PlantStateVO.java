package com.ihomy.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 植物当前状态(读取时实时计算成长/营养/冷却,不落库)。
 */
@Data
public class PlantStateVO {
    private boolean planted;
    private String species;
    private String stage;
    private int stageIndex;
    private int totalStages;
    /** 当前阶段内进度 0-100(已到最终阶段恒 100) */
    private int progressPct;
    private long growthMinutes;
    /** 距下一阶段还需的成长分钟(已到最终阶段为 0) */
    private long minutesToNextStage;
    private long daysGrown;
    private int waterCount;
    private int sunCount;
    private int fertilizeCount;
    private int harvestCount;
    private LocalDateTime plantedAt;
    private LocalDateTime lastWateredAt;
    private LocalDateTime lastSunAt;
    private LocalDateTime lastFertilizedAt;
    private boolean waterReady;
    private boolean sunReady;
    private boolean fertilizeReady;
    private long waterCooldownMin;
    private long sunCooldownMin;
    private long fertilizeCooldownMin;
    private boolean thirsty;
    private boolean wilted;
    private boolean harvestable;
    /** 营养等级 0-100 */
    private int nutrient;
    /** 营养等级档位(用于前端展示):LOW/MEDIUM/HIGH */
    private String nutrientLevel;
    /** 当前天气(可能为 null):{condition, temp, text} */
    private Map<String, Object> weather;
    /** 是否正在「自动补水」(雨天/雷雨/下雪) */
    private boolean rainWatering;
    /** 当前天气对浇水的加成倍率(展示用) */
    private double weatherWaterFactor;
    /** 当前天气对晒太阳的加成倍率(展示用) */
    private double weatherSunFactor;
    /** 成长日志(最近的照料/收获动态) */
    private List<Map<String, Object>> logs;
}
