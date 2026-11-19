package com.ihomy.common;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 健康指标目录:上报校验与展示单位的唯一来源(数据字典 sys_dict_item 的 health_metric 组同口径)。
 * 单位由目录定死,上报端不传单位——避免同一个指标混进 kg 和斤。
 * 新增指标 = 这里加一行 + 字典加一行 + 前端 healthMetrics.js 加一行,表与接口不动。
 * 敏感组(MENSTRUAL_FLOW)默认不采:服务端先查成员同意开关,未开启整条以 CONSENT_REQUIRED 拒收,
 * 且读接口对任何人都不返回他人的敏感指标行(OWNER 亦然)。
 */
public final class HealthMetric {

    private HealthMetric() {
    }

    /** 单点或客户端小时聚合(心率) */
    public static final String PERIOD_POINT = "POINT";
    /** 日聚合 */
    public static final String PERIOD_DAY = "DAY";
    /** 会话型(睡眠走两张 sleep 表,不进 sample) */
    public static final String PERIOD_SESSION = "SESSION";

    public static final String SLEEP = "SLEEP";
    public static final String MENSTRUAL_FLOW = "MENSTRUAL_FLOW";

    public static final Set<String> PROVIDERS = Set.of("SHORTCUT", "HEALTH_CONNECT", "IOS_NATIVE");

    public static final Set<String> SLEEP_STAGES = Set.of("AWAKE", "LIGHT", "DEEP", "REM", "INBED", "UNKNOWN");

    /**
     * 睡眠阶段归一:除枚举本身,也认 Apple 健康那份本地化写法——快捷指令「查找健康样本」取到的
     * 「值」直接透传就是「深睡/核心睡眠/快速眼动睡眠/清醒/在床」这类文字(英文机则是 Asleep (Core) 等),
     * 不必在手机上搭条件分支。认不出才落 UNKNOWN(只记会话、不分期),不会因此整条拒收。
     */
    public static String normalizeSleepStage(String raw) {
        if (raw == null) {
            return "UNKNOWN";
        }
        String t = raw.trim().toUpperCase(Locale.ROOT);
        if (t.isEmpty()) {
            return "UNKNOWN";
        }
        if (SLEEP_STAGES.contains(t)) {
            return t;
        }
        if (t.contains("深") || t.contains("DEEP")) {
            return "DEEP";
        }
        if (t.contains("快速眼动") || t.contains("REM")) {
            return "REM";
        }
        if (t.contains("核心") || t.contains("浅") || t.contains("CORE") || t.contains("LIGHT")) {
            return "LIGHT";
        }
        if (t.contains("清醒") || t.contains("AWAKE")) {
            return "AWAKE";
        }
        if (t.contains("在床") || t.contains("IN BED") || t.contains("INBED")) {
            return "INBED";
        }
        if (t.contains("睡眠") || t.contains("ASLEEP") || t.contains("SLEEP")) {
            return "LIGHT";
        }
        return "UNKNOWN";
    }

    private static final Set<String> SOURCES = Set.of(
            "GARMIN", "HUAWEI", "APPLE", "XIAOMI", "OPPO", "SAMSUNG", "HONOR", "VIVO", "ZEPP", "OTHER");

    /** 一个指标的定义:周期、单位、合理范围(超出即整条拒收,不截断)、是否敏感组 */
    public record Def(String code, String group, String period, String unit,
                      double min, double max, boolean sensitive) {
    }

    private static final Map<String, Def> DEFS = new LinkedHashMap<>();

    private static void put(String code, String group, String period, String unit, double min, double max) {
        DEFS.put(code, new Def(code, group, period, unit, min, max, false));
    }

    static {
        // 睡眠在字典里保留(客户端能力已在),但不进 sample;此处只为统一查询名称与单位
        put(SLEEP, "SLEEP", PERIOD_SESSION, "min", 0, 1440);
        // 活动
        put("STEPS", "ACTIVITY", PERIOD_DAY, "步", 0, 200000);
        put("DISTANCE", "ACTIVITY", PERIOD_DAY, "km", 0, 300);
        put("ACTIVE_ENERGY", "ACTIVITY", PERIOD_DAY, "kcal", 0, 20000);
        put("TOTAL_ENERGY", "ACTIVITY", PERIOD_DAY, "kcal", 0, 40000);
        put("FLIGHTS", "ACTIVITY", PERIOD_DAY, "层", 0, 1000);
        put("EXERCISE_MIN", "ACTIVITY", PERIOD_DAY, "min", 0, 1440);
        put("STAND_HOUR", "ACTIVITY", PERIOD_DAY, "h", 0, 24);
        put("MINDFUL_MIN", "ACTIVITY", PERIOD_DAY, "min", 0, 1440);
        // 心脏/体征
        put("HEART_RATE", "VITAL", PERIOD_POINT, "bpm", 20, 250);
        put("RESTING_HR", "VITAL", PERIOD_DAY, "bpm", 25, 150);
        put("HRV", "VITAL", PERIOD_DAY, "ms", 1, 400);
        put("SPO2", "VITAL", PERIOD_POINT, "%", 50, 100);
        put("RESP_RATE", "VITAL", PERIOD_DAY, "次/分", 4, 80);
        put("BODY_TEMP", "VITAL", PERIOD_POINT, "°C", 30, 45);
        put("BASAL_TEMP", "VITAL", PERIOD_DAY, "°C", 30, 45);
        put("BLOOD_GLUCOSE", "VITAL", PERIOD_POINT, "mmol/L", 1, 40);
        put("BP_SYSTOLIC", "VITAL", PERIOD_POINT, "mmHg", 40, 300);
        put("BP_DIASTOLIC", "VITAL", PERIOD_POINT, "mmHg", 20, 200);
        // 身体成分
        put("WEIGHT", "BODY", PERIOD_POINT, "kg", 1, 300);
        put("BODY_FAT", "BODY", PERIOD_POINT, "%", 1, 70);
        put("BMI", "BODY", PERIOD_POINT, "", 5, 100);
        put("HEIGHT", "BODY", PERIOD_POINT, "cm", 30, 250);
        put("WAIST", "BODY", PERIOD_POINT, "cm", 20, 250);
        // 步态(手机/手表自动采集,按天给一个均值):数值是「平均水平」而不是当日累计
        put("WALKING_SPEED", "GAIT", PERIOD_DAY, "km/h", 0, 25);
        put("WALKING_STEP_LENGTH", "GAIT", PERIOD_DAY, "cm", 0, 200);
        put("WALKING_DOUBLE_SUPPORT", "GAIT", PERIOD_DAY, "%", 0, 100);
        put("WALKING_ASYMMETRY", "GAIT", PERIOD_DAY, "%", 0, 100);
        put("WALKING_STEADINESS", "GAIT", PERIOD_DAY, "%", 0, 100);
        // 音量暴露(耳机/环境声级):按天给平均声级,单位 dB(A)
        put("HEADPHONE_AUDIO", "AUDIO", PERIOD_DAY, "dB", 0, 150);
        put("AMBIENT_AUDIO", "AUDIO", PERIOD_DAY, "dB", 0, 150);
        // 体能
        put("VO2MAX", "FITNESS", PERIOD_DAY, "ml/kg/min", 5, 100);
        // 敏感组:默认不采,成员自开后仅本人可见
        DEFS.put(MENSTRUAL_FLOW, new Def(MENSTRUAL_FLOW, "SENSITIVE", PERIOD_DAY, "级", 0, 4, true));
    }

    /** 按指标码查定义(大小写不敏感),未知返回 null */
    public static Def find(String code) {
        if (code == null) return null;
        return DEFS.get(code.trim().toUpperCase(Locale.ROOT));
    }

    public static boolean isSensitive(String code) {
        Def d = find(code);
        return d != null && d.sensitive();
    }

    /**
     * 长期趋势指标:虽然也存 POINT,但一条就是一次测量、量极少,保留策略不清它们
     * (体重/体脂/身高/腰围是多年的趋势底稿,清掉就再也补不回来;心率那种小时级 POINT 才需要限期清理)。
     */
    public static final Set<String> LONG_TERM_POINT = Set.of("WEIGHT", "BODY_FAT", "BMI", "HEIGHT", "WAIST");

    /** 来源归一到有限集合,未知记 OTHER(展示层按 source 标注「来自哪个 App」) */
    public static String normalizeSource(String source) {
        if (source == null) return "OTHER";
        String s = source.trim().toUpperCase(Locale.ROOT);
        if (s.isEmpty()) return "OTHER";
        // 快捷指令/原生包常传包名或 App 名,命中常见厂商关键字即归类
        for (String known : SOURCES) {
            if (s.contains(known)) return known;
        }
        if (s.contains("HEALTH")) return "APPLE";
        return "OTHER";
    }
}
