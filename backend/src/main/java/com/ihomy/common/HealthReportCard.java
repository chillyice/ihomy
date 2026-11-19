package com.ihomy.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 健康报表卡片目录:报表聚合(HealthReportService)与分享快照的唯一口径,
 * 前端 views/health/reportCards.js 与它同口径。
 * 新增卡片 = 这里加一行 + HealthReportService 加一段 + 前端 reportCards.js 加一行。
 * 敏感卡片(月经)只在本人已开启同意时可选,分享时还要逐次勾选。
 */
public final class HealthReportCard {

    public static final String SLEEP_LAST = "sleep_last";
    public static final String SLEEP_7D = "sleep_7d";
    public static final String STEPS_7D = "steps_7d";
    public static final String ENERGY_7D = "energy_7d";
    public static final String EXERCISE_7D = "exercise_7d";
    public static final String RESTING_HR_7D = "resting_hr_7d";
    public static final String HR_DAY = "hr_day";
    public static final String SPO2_DAY = "spo2_day";
    public static final String WEIGHT_TREND = "weight_trend";
    public static final String MENSTRUAL_7D = "menstrual_7d";
    public static final String ALERTS = "alerts";

    /** 单张报表最多 12 张卡片(偏好值与分享快照都按这个上限收口) */
    public static final int MAX_CARDS = 12;

    /** 一个卡片:是否敏感组、取数窗口天数(锚定日往前) */
    public record Def(String id, boolean sensitive, int rangeDays) {
    }

    private static final Map<String, Def> DEFS = new LinkedHashMap<>();

    private static void put(String id, int rangeDays) {
        DEFS.put(id, new Def(id, false, rangeDays));
    }

    static {
        put(SLEEP_LAST, 1);
        put(SLEEP_7D, 7);
        put(STEPS_7D, 7);
        put(ENERGY_7D, 7);
        put(EXERCISE_7D, 7);
        put(RESTING_HR_7D, 7);
        put(HR_DAY, 1);
        put(SPO2_DAY, 1);
        put(WEIGHT_TREND, 30);
        put(ALERTS, 14);
        DEFS.put(MENSTRUAL_7D, new Def(MENSTRUAL_7D, true, 7));
    }

    /** 默认报表 = 最近一晚睡眠节奏 + 近 7 天步数/活动消耗 + 最近一天心率 + 异常提醒 */
    public static final List<String> DEFAULTS =
            List.of(SLEEP_LAST, STEPS_7D, ENERGY_7D, HR_DAY, ALERTS);

    public static Def find(String id) {
        if (id == null) return null;
        return DEFS.get(id.trim().toLowerCase(Locale.ROOT));
    }

    public static boolean isSensitive(String id) {
        Def d = find(id);
        return d != null && d.sensitive();
    }

    /** 全部卡片 id(前端下拉/运维排障用),顺序即默认展示顺序 */
    public static List<String> allIds() {
        return List.copyOf(DEFS.keySet());
    }

    /**
     * 收口调用方传来的卡片清单:去空白、去重、未知卡片忽略;空清单回落默认五项。
     * 敏感卡片是否放行由调用方按同意开关再判(这里只做形状校验)。
     */
    public static List<String> sanitize(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return DEFAULTS;
        }
        List<String> out = new ArrayList<>();
        for (String item : raw) {
            if (item == null) continue;
            Def def = find(item);
            if (def == null) continue;
            if (!out.contains(def.id())) out.add(def.id());
            if (out.size() >= MAX_CARDS) break;
        }
        return out.isEmpty() ? DEFAULTS : out;
    }
}
