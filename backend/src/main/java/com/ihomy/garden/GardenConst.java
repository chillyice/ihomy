package com.ihomy.garden;

import java.security.MessageDigest;
import java.util.Map;
import java.util.Set;

/**
 * 家圃时光工程常量(架构分册 §19-B 的最小闭环子集)。
 * 数值一律整数(基点/分钟/秒),核心禁浮点比较(NFR-02)。
 */
public final class GardenConst {

    private GardenConst() {
    }

    /** 合成 op_id 的 Crockford Base32 字母表(与 Ulid 同制,须过 Ulid.isValid) */
    private static final char[] SYNTH_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

    /** P1 最小闭环的世界阶段:任务链(节气节点)落地前恒为 1(只开窗台 0-2 号盆与阶段 1 作物) */
    public static final int WORLD_STAGE = 1;

    // ---- 归属(场域级常量,PLOT_OWNERSHIP;不进 content)----
    public static final String OWNER_SHARED = "";
    public static final Set<String> PERSONAL_VENUES = Set.of("WINDOWSILL", "PLANTER", "TERRARIUM");
    public static final Set<String> SHARED_VENUES = Set.of("BACKYARD", "FARM", "GREENHOUSE");

    // ---- 费用(园币)----
    public static final int PREPARE_COST = 5;          // 备盆(玩法稿 §5.6)
    public static final int FERTILIZE_COST = 15;       // 施肥(FR-08)
    public static final int FIRST_MAIL_COINS = 30;     // 首封初始礼包(FR-27)

    // ---- 照料冷却与加成(分钟;FR-07/FR-08)----
    public static final int CARE_COOLDOWN_MINUTES = 720;
    public static final int WATER_BOOST_MINUTES = 120;
    public static final int SUN_BOOST_MINUTES = 120;
    public static final int FERTILIZE_BOOST_MINUTES = 360;

    // ---- 市集(玩法稿 §12.2,2026-11-28 实装)----
    public static final int MARKET_PRICE_CAP_BP = 300;   // 挂单价 ≤ 参考价 ×3(家庭保护)
    public static final int MARKET_TTL_HOURS = 48;       // 挂单 48h 自动下架
    public static final int MARKET_BATCH_MIN_QTY = 10;   // 整批出货:同种一次卖 ≥10 份
    public static final int MARKET_BATCH_BONUS_BP = 105; // 整批回收额 ×1.05

    // ---- 天气照料倍率(玩法稿 §8 表,2026-11-28 实装;晒太阳再乘季节系数 = 「当日阳光量」)----
    public static final Map<String, Integer> WEATHER_SUN_BP =
            Map.of("clear", 150, "cloud", 100, "rain", 50, "snow", 30, "fog", 70, "thunder", 40);
    public static final Map<String, Integer> WEATHER_WATER_BP =
            Map.of("clear", 100, "cloud", 100, "rain", 150, "snow", 80, "fog", 90, "thunder", 130);
    /** 晒太阳的季节系数(首版标定,待试玩调;浇水不乘) */
    public static final Map<String, Integer> SUN_SEASON_BP =
            Map.of("SPRING", 100, "SUMMER", 120, "AUTUMN", 90, "WINTER", 70);

    // ---- 成长乘子(基点,100 = ×1.00)----
    public static final int SOIL_PREFERRED_BP = 120;   // 偏好 ×1.2(§0.7)
    public static final int SOIL_COMPATIBLE_BP = 100;  // 兼容 ×1.0
    public static final int SOIL_MISMATCH_BP = 70;     // 错配 ×0.7
    public static final int PREPARED_BP = 105;         // 已备盆 ×1.05(FR-04a)
    public static final int UNPREPARED_BP = 90;        // 未备盆 ×0.90
    public static final int DROUGHT_RATE_BP = 70;      // 缺水期间成长 ×0.7(§10 状态异常)
    public static final int COLD_STRESS_BP = 60;       // 低温胁迫:日均温 < 作物耐寒下限(§8.1,仅室外)
    public static final int HEAT_STRESS_BP = 70;       // 高温胁迫:日均温 > 作物耐热上限(§8.1,仅室外)
    /** 室内逆季软化下限(§7:窗台/花箱 ×0.5 取下限、缸 ×0.7;大棚季节免疫) */
    public static final int INDOOR_OFF_SEASON_FLOOR_BP = 50;
    public static final int TERRARIUM_OFF_SEASON_FLOOR_BP = 70;
    public static final int GREENHOUSE_SEASON_BP = 100; // 大棚免疫

    // ---- 缺水/枯萎阈值(小时;FR-10;按耐旱性分级,todo 数值待标定)----
    public static final int THIRSTY_HOURS_NORMAL = 48;
    public static final int WILT_HOURS_NORMAL = 72;
    public static final int THIRSTY_HOURS_TOLERANT = 96;
    public static final int WILT_HOURS_TOLERANT = 144;
    public static final int THIRSTY_HOURS_EXTREME = 240;
    public static final int WILT_HOURS_EXTREME = 360;

    // ---- 阶段(§10:0/0.1/0.35/0.6/0.85/1.0,基点 = 万分比)----
    public static final int[] STAGE_THRESHOLDS_BP = {0, 1000, 3500, 6000, 8500, 10000};
    public static final String[] STAGE_NAMES = {"SEED", "SPROUT", "SEEDLING", "BUD", "FLOWER", "FRUIT"};

    // ---- 仓库容量(FR-13:90 + 20 × (共享已解锁 + 个人已解锁 × 成员数))----
    public static final int WAREHOUSE_BASE = 90;
    public static final int WAREHOUSE_PER_PLOT = 20;

    // ---- 每日信封(FR-27)----
    public static final int MAIL_BACKFILL_DAYS = 3;

    // ---- 经济(§7:错季卖价 ×0.5)----
    public static final int OFF_SEASON_PRICE_BP = 50;

    // ---- 仓库物品码规则 ----
    public static final String SEED_PREFIX = "SEED_";
    public static final String ITEM_TYPE_FRUIT = "FRUIT";
    public static final String ITEM_TYPE_CONSUMABLE = "CONSUMABLE";

    public static boolean isPersonalVenue(String venue) {
        return PERSONAL_VENUES.contains(venue);
    }

    public static boolean isSharedVenue(String venue) {
        return SHARED_VENUES.contains(venue);
    }

    /** 归属校验:共享放行、本人放行(架构分册 §17.1 统一前置) */
    public static boolean mayOperate(String ownerUid, String playerUid) {
        return OWNER_SHARED.equals(ownerUid) || ownerUid.equals(playerUid);
    }

    /** 耐旱分级 → 缺水提示阈值(小时) */
    public static int thirstyHours(String hardiness) {
        return switch (hardiness == null ? "NORMAL" : hardiness) {
            case "TOLERANT" -> THIRSTY_HOURS_TOLERANT;
            case "EXTREME" -> THIRSTY_HOURS_EXTREME;
            default -> THIRSTY_HOURS_NORMAL;
        };
    }

    /** 耐旱分级 → 枯萎冻结阈值(小时) */
    public static int wiltHours(String hardiness) {
        return switch (hardiness == null ? "NORMAL" : hardiness) {
            case "TOLERANT" -> WILT_HOURS_TOLERANT;
            case "EXTREME" -> WILT_HOURS_EXTREME;
            default -> WILT_HOURS_NORMAL;
        };
    }

    /** 物品码:果实=作物码,种子包=SEED_ 前缀 */
    public static String seedItemCode(String cropCode) {
        return SEED_PREFIX + cropCode;
    }

    /** 世界级设置 JSON 的 cycle_start_at 键值(settings 为 JSON 字符串存储) */
    public static final String SETTINGS_CYCLE_START_KEY = "cycle_start_at";

    /** 世界级设置 JSON 的 stage 覆盖键(隔离测试夹具;正式世界不写,任务链落地后仍是唯一跳闸通道) */
    public static final String SETTINGS_STAGE_KEY = "stage";

    /** 世界级设置 JSON 的 day_offset 覆盖键(节气事件测试夹具:事件日期视图 = 真实日期 + N 天;正式世界不写) */
    public static final String SETTINGS_DAY_OFFSET_KEY = "day_offset";

    /**
     * 世界级设置 JSON 的日均温覆盖键(气温双轨测试夹具:`temp_fixture_c` = 强制日均温℃;正式世界不写)。
     * 有了它,低温/高温胁迫的验收不必等真的寒潮或酷暑,也不会因真实天气随季节漂移而让断言失真。
     */
    public static final String SETTINGS_TEMP_FIXTURE_KEY = "temp_fixture_c";

    /**
     * 世界级设置 JSON 的周挑战夹具键(仅测试):`weekly_fixture` = 逗号分隔的任务码,强制本周在窗的挑战,
     * 不必等轮换转到某条才能验收;正式世界不写(不写则按 ISO 周序号轮换)。
     */
    public static final String SETTINGS_WEEKLY_FIXTURE_KEY = "weekly_fixture";

    // ---- 周挑战(玩法稿 §15:复用任务悬赏机制,零新系统)----
    /** 每周在窗的挑战条数(池里按周轮换取这么多条;领取凭证 = ISO 周键) */
    public static final int WEEKLY_PER_WEEK = 3;

    // ---- 任务链与节气(详细设计 §2/§4;玩法稿 §3.2)----
    /** 阶段 N(N≥2)在第 3(N−1) 个节气;年终收尾(推阶段 10)在第 25 个节气起 */
    public static final int TERMS_PER_STAGE = 3;
    public static final int FINAL_STAGE = 10;
    public static final int FINAL_STAGE_MIN_TERM = 25;
    /** 推阶园币(只对新场域收费,附属阶段 2/5/8/9 与回填免费;玩法稿 §4.1 解锁表) */
    public static final Map<Integer, Integer> STAGE_COINS = Map.of(3, 200, 4, 400, 6, 800, 7, 1500, 10, 3000);

    // ---- 经验与技能点(详细设计 §6;玩法稿 §4.1/§4.2.5)----
    /** 照料 XP:每地块每日只计第一次(护栏防 36 块 × 12h 冷却刷爆阶梯) */
    public static final int XP_PER_CARE_DAY = 5;
    /**
     * 照料园币:每地块每日首次照料 +2(玩法稿 §12.1 收入表「照料每日 +2 园币/次,与照料同冷却」)。
     * 口径 = 每地块每日一次,与 XP 共用 garden_plot.last_care_date 去重位;满开 36 块 ≈ 72 币/日封顶,
     * 低于积分闸门 200 币/日,卖出仍是主要收入(契约 §13 / 详细设计《照料园币》)。
     */
    public static final int CARE_COINS_PER_DAY = 2;
    /** 新作物首收 XP(每种一次) */
    public static final int XP_FIRST_CROP = 30;
    /** 收获 XP 按 crop.unlock_stage 六档映射:1–2→15 / 3–4→25 / 5→40 / 6–7→60 / 8–9→90 / 10+→120 */
    public static int harvestStageXp(int unlockStage) {
        if (unlockStage <= 2) return 15;
        if (unlockStage <= 4) return 25;
        if (unlockStage == 5) return 40;
        if (unlockStage <= 7) return 60;
        if (unlockStage <= 9) return 90;
        return 120;
    }
    /** 技能层级点价(tier 1–5 每级;content/skills.json tierCost 同源) */
    public static final int[] SKILL_TIER_COST = {1, 2, 4, 6, 9};
    /** 一次性技能点奖励(12 成就 + 6 任务/里程碑,推导口径见详细设计 §6.2) */
    public static final int ONE_TIME_SKILL_POINTS = 18;

    /**
     * 服务端确定性合成 op_id(26 位,Crockford Base32,过 Ulid.isValid):任务领取/推阶/技能分配/
     * 批量子动作不依赖客户端 Idempotency-Key,同 (家庭, 对象, 版本) 并发双跑撞唯一键 → 只有一个成功。
     * tag: C=任务领取 S=推阶 A=技能分配 R=技能重置 B=批量子动作;含 familyId 保证全局唯一。
     * 判定(阶段/领取/已学)一律按行内容查,不按 op_id 反查——换编码不影响存量行。
     */
    public static String synthOpId(char tag, Object... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            for (Object p : parts) {
                sb.append('§').append(p);
            }
            byte[] hash = digest.digest(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            // SHA-256 → Crockford Base32,取 24 位;前缀 T + tag(均为字母表内字符)凑满 26 位
            char[] out = new char[26];
            out[0] = 'T';
            out[1] = tag;
            int bitBuf = 0;
            int bits = 0;
            int idx = 2;
            for (byte b : hash) {
                bitBuf = (bitBuf << 8) | (b & 0xFF);
                bits += 8;
                while (bits >= 5 && idx < 26) {
                    bits -= 5;
                    out[idx++] = SYNTH_ALPHABET[(bitBuf >>> bits) & 0x1F];
                }
                if (idx >= 26) {
                    break;
                }
            }
            while (idx < 26) {
                out[idx++] = SYNTH_ALPHABET[0];
            }
            return new String(out);
        } catch (Exception e) {
            throw new IllegalStateException("synthOpId 失败", e);
        }
    }
}
