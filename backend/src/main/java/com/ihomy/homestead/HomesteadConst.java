package com.ihomy.homestead;

import java.util.Set;

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

    /**
     * 单次结算最多推进多少次动作:100 级 + 24 小时最快动作也就几万次,
     * 这个上限只防内容写错(耗时 0)导致死循环,正常远到不了。
     */
    public static final int MAX_COMPLETIONS_PER_SETTLE = 200_000;

    /** 速度基数(100%) */
    public static final int SPEED_BASE_BP = 10000;
    /** 每级技能加速 1%(100 级 +99%) */
    public static final int LEVEL_STEP_BP = 100;
    /** 工具最高阶(steps 数量,1 阶即已制造) */
    public static final int MAX_TOOL_TIER = 5;
    /** 装备最高强化等级(0→5) */
    public static final int MAX_GEAR_TIER = 5;
    /** 装备部位数量与每个部位的件数 */
    public static final int SLOT_COUNT = 5;
    public static final int GEAR_PER_SLOT = 3;
    /** 部位编码(与 equipment.json 的 slot 一致) */
    public static final Set<String> GEAR_SLOTS = Set.of("HEAD", "BODY", "HANDS", "FEET", "CHARM");

    /** 同时生效的料理 buff 上限(条) */
    public static final int MAX_ACTIVE_BUFFS = 3;
    /** 加成封顶:速度/经验最多 +300%(有效步进防止数值失控) */
    public static final int MAX_SPEED_BONUS_BP = 30000;
    public static final int MAX_XP_BONUS_BP = 30000;
    /** 收集物/稀有/省料都是概率,封顶 50% */
    public static final int MAX_CHANCE_BP = 5000;

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
