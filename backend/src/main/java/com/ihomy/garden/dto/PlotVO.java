package com.ihomy.garden.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 地块视图(全景 / 动作响应通用):生长全部为实时推导值,不落库。
 * 别人的个人地块照常下发(mine=false,只读可见,玩法稿 §3.3)。
 */
@Data
public class PlotVO {
    private Long id;
    private String venue;
    private Integer slotNo;
    /** ''=全家共享;非空=成员 player_uid */
    private String ownerUid;
    private boolean mine;
    private String ownerName;
    private String soilType;
    private boolean prepared;
    private String cropCode;
    private String cropName;
    /** 形态(影子 / 渲染用,content 配置) */
    private String form;
    /** 阶段 0–5:SEED/SPROUT/SEEDLING/BUD/FLOWER/FRUIT */
    private Integer stage;
    private String stageName;
    /** 成长进度(基点 0–10000;10000 = 可收) */
    private Integer growthRatioBp;
    private boolean harvestable;
    private boolean thirsty;
    private Long minutesToHarvest;
    private Long minutesToNextStage;
    /** 各照料冷却剩余秒数(0 = 已就绪;前端据此倒计时,FR-07) */
    private Long waterCooldownSec;
    private Long sunCooldownSec;
    private Long fertilizeCooldownSec;
    private Integer careBoostMinutes;
    /** 天气事件(FROST/DROUGHT/AUTO_WATER,null=无);读时推导,只作用于室外(§8) */
    private String weatherEvent;
    /** 气温胁迫码(COLD_STRESS/HEAT_STRESS,null=无);室外按作物耐温区间判,与 weatherEvent 各说各的(§8.1) */
    private String tempStress;
    /** 气温胁迫乘子基点(100 = 无胁迫;无胁迫时不下发,避免前端多判一个值) */
    private Integer tempStressBp;
    private LocalDateTime plantedAt;
    private LocalDateTime lastWateredAt;
    private LocalDateTime lastSunAt;
    private LocalDateTime lastFertilizedAt;
}
