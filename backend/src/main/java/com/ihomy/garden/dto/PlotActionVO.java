package com.ihomy.garden.dto;

import lombok.Data;

/**
 * 地块写动作(播种 / 备盆 / 照料 / 收获)统一响应:动作后的地块视图 + 钱包/仓库状态。
 */
@Data
public class PlotActionVO {
    private PlotVO plot;
    private Long walletCoins;
    private Integer inventoryUsed;
    private Integer inventoryCap;

    // ---- 照料 ----
    /** 本次照料入账园币(每地块每日首次 +2,其余 0;与 coinsSpent 分开记,不藏净额) */
    private Integer coinsEarned;

    // ---- 播种 ----
    /** PACK=消耗了种子包 / COINS=直接扣园币买种(§11) */
    private String seedUsed;
    private Integer coinsSpent;

    // ---- 收获 ----
    private String itemCode;
    private Integer yieldQty;
    private Integer coinsGranted;
    /** 本家庭首次收获该作物(图鉴新行,FR-18) */
    private boolean firstCodex;

    // ---- 断线重试 ----
    /** true = 该 Idempotency-Key 已生效过,本次为重放回执(返回当前状态、不重复结算,契约 §0);正常成功不带此键 */
    private Boolean alreadyDone;
}
