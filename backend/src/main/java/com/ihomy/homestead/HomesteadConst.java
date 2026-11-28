package com.ihomy.homestead;

import java.util.List;
import java.util.Map;

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

    /**
     * 12 个穿戴格(顺序即界面顺序)。戒指家族占两格,同一件实例只能穿在一格。
     * 装备蓝本的 slot 用家族码(RING),穿戴格用具体格码(RING_A/RING_B)。
     */
    public static final List<String> GEAR_SLOTS = List.of(
            "HEAD", "NECK", "SHOULDER", "BODY", "CLOAK", "WRIST",
            "HANDS", "WAIST", "FEET", "RING_A", "RING_B", "CHARM");
    /** 11 个槽位家族(装备蓝本按家族挂,戒指两格共用一个家族) */
    public static final List<String> GEAR_FAMILIES = List.of(
            "HEAD", "NECK", "SHOULDER", "BODY", "CLOAK", "WRIST",
            "HANDS", "WAIST", "FEET", "RING", "CHARM");
    /** 每个家族几件蓝本(3 件打造档 + 1 件掉落专属) */
    public static final int GEAR_PER_FAMILY = 4;
    /** 穿戴格 → 家族(RING_A/RING_B 都归 RING,其余同名) */
    public static final Map<String, String> SLOT_FAMILY = Map.ofEntries(
            Map.entry("HEAD", "HEAD"),
            Map.entry("NECK", "NECK"),
            Map.entry("SHOULDER", "SHOULDER"),
            Map.entry("BODY", "BODY"),
            Map.entry("CLOAK", "CLOAK"),
            Map.entry("WRIST", "WRIST"),
            Map.entry("HANDS", "HANDS"),
            Map.entry("WAIST", "WAIST"),
            Map.entry("FEET", "FEET"),
            Map.entry("RING_A", "RING"),
            Map.entry("RING_B", "RING"),
            Map.entry("CHARM", "CHARM"));

    /** 装备来源:打造 / 探险与宝箱掉落 */
    public static final String SOURCE_CRAFT = "CRAFT";
    public static final String SOURCE_DROP = "DROP";
    /** 打造出来的装备固定品质 */
    public static final String QUALITY_COMMON = "COMMON";

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

    // ---------------------------------------------------------------- 探险

    /** 每天可派遣的探险次数(与挂机并行,不分时段) */
    public static final int EXPEDITION_PER_DAY = 3;
    /** 探险判定抖动幅度(±10%,确定性掷点) */
    public static final int EXPEDITION_JITTER_BP = 1000;
    /** 探险事件判定的技能权重(每级 3 分) */
    public static final int EXPEDITION_LEVEL_WEIGHT = 3;
    /** 探险状态 */
    public static final String TRIP_ACTIVE = "ACTIVE";
    public static final String TRIP_DONE = "DONE";
    /** 宝箱品质提升概率表:QUALITY_ROLL_BANDS[i] = 掷点 < 该值时跳到第 i 档(从品质下限起算) */
    public static final int[] QUALITY_ROLL_BANDS = {60, 85, 96, 100};

    // ---------------------------------------------------------------- 换装预设

    /** 免费预设套数(建号即有) */
    public static final int LOADOUT_FREE = 2;
    /** 可拥有的预设上限 */
    public static final int MAX_LOADOUTS = 5;
    /** 第 3~5 套的解锁价(下标 = 套号,0 号占位) */
    public static final long[] LOADOUT_PRICES = {0L, 0L, 0L, 20_000L, 60_000L, 150_000L};

    // ---------------------------------------------------------------- 其他花费

    /** 词缀重铸:铜板 + 稀有材质(内容表里的 RARE 类物品)数量 */
    public static final long REROLL_COINS = 3_000L;
    public static final int REROLL_RARE_QTY = 2;
}
