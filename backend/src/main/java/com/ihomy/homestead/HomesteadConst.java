package com.ihomy.homestead;

/**
 * 小院慢生活的全局常量(只放算法口径,不放内容;内容在 resources/content/homestead/*.json)。
 * 整数口径:倍率一律基点(bp,10000 = 100%),时间一律毫秒,核心不取时钟、不用浮点、不用随机。
 */
public final class HomesteadConst {

    private HomesteadConst() {
    }

    /** 离线最多补算时长:整 24 小时,超出部分丢弃(不惩罚在线时长,只防长期不回来数值失控) */
    public static final int OFFLINE_CAP_HOURS = 24;
    public static final long OFFLINE_CAP_MS = OFFLINE_CAP_HOURS * 3600L * 1000L;

    /** 结算分段上限:升满 50 级 + 两种材料耗尽 + 余量,正常远到不了 */
    public static final int MAX_SETTLE_SEGMENTS = 64;

    /** 速度基数(100%) */
    public static final int SPEED_BASE_BP = 10000;
    /** 每级技能加速 1%(50 级 +49%) */
    public static final int LEVEL_STEP_BP = 100;
    /** 每阶工具加速 8%(5 阶 +40%) */
    public static final int TOOL_STEP_BP = 800;
    /** 工具最高阶(steps 数量) */
    public static final int MAX_TOOL_TIER = 5;

    /** 铜板上限与物品堆上限:防极端离线把数值推到溢出 */
    public static final long MAX_COINS = 1_000_000_000_000L;
    public static final long MAX_ITEM_QTY = 1_000_000_000L;

    /** 小院指数口径:每级技能 1000 分,库存按卖价五成计入 */
    public static final long INDEX_LEVEL_POINTS = 1000L;
    public static final int INDEX_ITEM_SELL_BP = 5000;

    /** 基点基数(100% = 10000,溢价/折扣一律按它换算) */
    public static final int BP_BASE = 10_000;
    /** 赶集日:溢价幅度(15000 = 原价 1.5 倍) */
    public static final int MARKET_BONUS_BP = 15_000;
    /** 赶集日:当天共享的收购额度(件,两单共用;用完就回到原价,次日 0 点换货) */
    public static final int MARKET_QUOTA = 40;

    /** 动作停止原因(写入结算结果,前端据此提示) */
    public static final String STOP_MATERIALS = "MATERIALS";
}
