package com.ihomy.common;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 健康指标异常提醒(纯函数):只提示「去看看」,不做医疗判断、不推送、不落库。
 * 两层判定:
 *   ① 通用参考区间(REF_RANGE):最新值落在区间外 → WARN;
 *   ② 个人基线偏离(BASELINE):近 14 天同指标日均值的均值 ±2σ,且样本 ≥7 天 → INFO。
 * 敏感指标(月经类)永不进提醒;同一指标已命中参考区间就不再重复报基线偏离。
 * 阈值口径写死在这里,前端只按 reason/level 出文案与数值,不再各自判断。
 */
public final class HealthAlerts {

    public static final String LEVEL_WARN = "WARN";
    public static final String LEVEL_INFO = "INFO";
    public static final String REASON_REF = "REF_RANGE";
    public static final String REASON_BASELINE = "BASELINE";

    /** 单张报表最多显示 5 条,按严重度 + 偏离幅度排序 */
    public static final int MAX_ALERTS = 5;
    /** 个人基线至少要有 7 天数据才判偏离,否则新用户天天报「异常」 */
    public static final int MIN_BASELINE_DAYS = 7;

    public record Alert(String metric, String level, double value, String unit,
                        Double refLow, Double refHigh, Double baseline, String reason) {
    }

    private record Ref(double low, double high) {
    }

    /**
     * 通用参考区间(成人静息口径,只作提示用):
     * 超出即 WARN;区间内的个体差异交给基线判定。
     */
    private static final Map<String, Ref> REFS = new LinkedHashMap<>();

    static {
        REFS.put("SPO2", new Ref(95, 100));
        REFS.put("RESTING_HR", new Ref(45, 100));
        REFS.put("RESP_RATE", new Ref(10, 24));
        REFS.put("BODY_TEMP", new Ref(35.5, 37.5));
        REFS.put("BP_SYSTOLIC", new Ref(90, 140));
        REFS.put("BP_DIASTOLIC", new Ref(60, 90));
        REFS.put("BLOOD_GLUCOSE", new Ref(3.9, 7.8));
    }

    /** 参与基线偏离判定的指标(体重/身高这类长期慢变量不进,避免天天报) */
    private static final Set<String> BASELINE_METRICS =
            Set.of("RESTING_HR", "HRV", "RESP_RATE", "SPO2", "BODY_TEMP");

    private HealthAlerts() {
    }

    /**
     * @param latest  各指标最新值(点指标已按天取均值)
     * @param history 各指标近 N 天日均值序列(缺数据的天不补 0)
     */
    public static List<Alert> evaluate(Map<String, Double> latest, Map<String, double[]> history) {
        List<Alert> out = new ArrayList<>();
        if (latest == null || latest.isEmpty()) {
            return out;
        }
        for (Map.Entry<String, Ref> e : REFS.entrySet()) {
            String metric = e.getKey();
            Double value = latest.get(metric);
            if (value == null || !Double.isFinite(value)) continue;
            Ref ref = e.getValue();
            if (value >= ref.low() && value <= ref.high()) continue;
            out.add(new Alert(metric, LEVEL_WARN, round2(value), unitOf(metric), ref.low(), ref.high(),
                    baselineOf(history, metric), REASON_REF));
        }

        for (String metric : BASELINE_METRICS) {
            if (out.stream().anyMatch(a -> a.metric().equals(metric))) continue;
            Double value = latest.get(metric);
            if (value == null || !Double.isFinite(value)) continue;
            double[] series = history == null ? null : history.get(metric);
            if (series == null || series.length < MIN_BASELINE_DAYS) continue;
            double mean = mean(series);
            double std = std(series, mean);
            if (!Double.isFinite(mean) || !Double.isFinite(std) || std <= 0) continue;
            if (Math.abs(value - mean) <= 2 * std) continue;
            out.add(new Alert(metric, LEVEL_INFO, round2(value), unitOf(metric), null, null,
                    round2(mean), REASON_BASELINE));
        }

        out.sort(Comparator
                .comparingInt((Alert a) -> LEVEL_WARN.equals(a.level()) ? 0 : 1)
                .thenComparingDouble(a -> -deviation(a)));
        return out.size() > MAX_ALERTS ? new ArrayList<>(out.subList(0, MAX_ALERTS)) : out;
    }

    /** 偏离幅度:相对基线/参考中点的百分比,只用于排序 */
    private static double deviation(Alert a) {
        double center = a.baseline() != null ? a.baseline()
                : a.refLow() != null && a.refHigh() != null ? (a.refLow() + a.refHigh()) / 2 : 0;
        if (center <= 0) return 0;
        return Math.abs(a.value() - center) / center * 100;
    }

    private static Double baselineOf(Map<String, double[]> history, String metric) {
        if (history == null) return null;
        double[] series = history.get(metric);
        if (series == null || series.length < MIN_BASELINE_DAYS) return null;
        return round2(mean(series));
    }

    private static String unitOf(String metric) {
        HealthMetric.Def def = HealthMetric.find(metric);
        return def == null ? "" : def.unit();
    }

    private static double mean(double[] values) {
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.length;
    }

    private static double std(double[] values, double mean) {
        double acc = 0;
        for (double v : values) acc += (v - mean) * (v - mean);
        return Math.sqrt(acc / values.length);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
