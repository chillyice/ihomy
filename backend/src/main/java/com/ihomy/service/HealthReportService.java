package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.HealthAlerts;
import com.ihomy.common.HealthMetric;
import com.ihomy.common.HealthReportCard;
import com.ihomy.entity.FamilyHealthSample;
import com.ihomy.entity.FamilyHealthSleep;
import com.ihomy.entity.FamilyHealthSleepStage;
import com.ihomy.mapper.FamilyHealthSampleMapper;
import com.ihomy.mapper.FamilyHealthSleepMapper;
import com.ihomy.mapper.FamilyHealthSleepStageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;

/**
 * 健康报表聚合:本人查看与分享快照共用同一口径(卡片目录见 common/HealthReportCard)。
 * 「默认显示最新数据」由 latestDate 决定——取最近一条样本日与最近一晚醒来日里较晚的那个,
 * 所有窗口都锚到它,而不是自然日「今天」(睡眠按入睡日落库,上午取今天会全是空)。
 * 不缓存(敏感数据);调用方负责鉴权,本类只按 userId 取数。
 */
@Service
@RequiredArgsConstructor
public class HealthReportService {

    private static final int SERIES_DAYS = 7;
    private static final int BASELINE_DAYS = 14;
    private static final int WEIGHT_TREND_DAYS = 30;
    private static final int POINT_LIMIT = 2000;

    /** 参与异常提醒的指标:参考区间与基线判定都在这两组里(敏感指标不在其中) */
    private static final Set<String> ALERT_METRICS = Set.of(
            "RESTING_HR", "HRV", "RESP_RATE", "SPO2", "BODY_TEMP",
            "BP_SYSTOLIC", "BP_DIASTOLIC", "BLOOD_GLUCOSE");

    private final FamilyHealthSampleMapper sampleMapper;
    private final FamilyHealthSleepMapper sleepMapper;
    private final FamilyHealthSleepStageMapper stageMapper;

    /**
     * 组装报表。
     *
     * @param cards            卡片 id 列表(未知/重复会被收口,空清单回落默认五项)
     * @param includeSensitive 是否允许敏感卡片(月经类)进入报表
     * @param anchorDate       锚定日;null = 自动取最近一天;晚于最近一天会回落到最近一天
     */
    public Map<String, Object> build(Long userId, List<String> cards, boolean includeSensitive, LocalDate anchorDate) {
        List<String> picked = new ArrayList<>();
        for (String id : HealthReportCard.sanitize(cards)) {
            if (HealthReportCard.isSensitive(id) && !includeSensitive) continue;
            picked.add(id);
        }
        if (picked.isEmpty()) {
            picked = new ArrayList<>(HealthReportCard.DEFAULTS);
        }

        LocalDate latest = latestDate(userId);
        LocalDate anchor = anchorDate == null ? (latest == null ? LocalDate.now() : latest) : anchorDate;
        if (latest != null && anchor.isAfter(latest)) {
            anchor = latest;
        }

        List<Map<String, Object>> built = new ArrayList<>();
        for (String id : picked) {
            built.add(buildCard(userId, id, anchor));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("latestDate", latest);
        data.put("anchorDate", anchor);
        data.put("generatedAt", LocalDateTime.now());
        data.put("cards", built);
        return data;
    }

    /** 最近一天有数据的日期:样本日与睡眠醒来日取较晚者 */
    public LocalDate latestDate(Long userId) {
        LocalDate latest = null;
        FamilyHealthSample sample = sampleMapper.selectOne(new LambdaQueryWrapper<FamilyHealthSample>()
                .eq(FamilyHealthSample::getUserId, userId)
                .orderByDesc(FamilyHealthSample::getStartTime)
                .last("LIMIT 1"));
        if (sample != null && sample.getStartTime() != null) {
            latest = sample.getStartTime().toLocalDate();
        }
        FamilyHealthSleep night = sleepMapper.selectOne(new LambdaQueryWrapper<FamilyHealthSleep>()
                .eq(FamilyHealthSleep::getUserId, userId)
                .orderByDesc(FamilyHealthSleep::getEndTime)
                .last("LIMIT 1"));
        if (night != null) {
            LocalDate wokeUp = night.getEndTime() != null ? night.getEndTime().toLocalDate() : night.getSleepDate();
            if (wokeUp != null && (latest == null || wokeUp.isAfter(latest))) {
                latest = wokeUp;
            }
        }
        return latest;
    }

    // ==================== 各卡片 ====================

    private Map<String, Object> buildCard(Long userId, String id, LocalDate anchor) {
        return switch (id) {
            case HealthReportCard.SLEEP_LAST -> sleepLast(userId, anchor);
            case HealthReportCard.SLEEP_7D -> sleepSeries(userId, anchor);
            case HealthReportCard.STEPS_7D -> daySeries(userId, "STEPS", anchor);
            case HealthReportCard.ENERGY_7D -> daySeries(userId, "ACTIVE_ENERGY", anchor);
            case HealthReportCard.EXERCISE_7D -> daySeries(userId, "EXERCISE_MIN", anchor);
            case HealthReportCard.RESTING_HR_7D -> daySeries(userId, "RESTING_HR", anchor);
            case HealthReportCard.HR_DAY -> pointDay(userId, "HEART_RATE", anchor);
            case HealthReportCard.SPO2_DAY -> pointDay(userId, "SPO2", anchor);
            case HealthReportCard.WEIGHT_TREND -> weightTrend(userId, anchor);
            case HealthReportCard.MENSTRUAL_7D -> daySeries(userId, "MENSTRUAL_FLOW", anchor);
            case HealthReportCard.ALERTS -> alerts(userId, anchor);
            default -> emptyCard(id);
        };
    }

    /** 最近一晚睡眠节奏:该锚定日之前(含)最近一晚 + 分期区间 */
    private Map<String, Object> sleepLast(Long userId, LocalDate anchor) {
        FamilyHealthSleep night = sleepMapper.selectOne(new LambdaQueryWrapper<FamilyHealthSleep>()
                .eq(FamilyHealthSleep::getUserId, userId)
                .le(FamilyHealthSleep::getSleepDate, anchor)
                .orderByDesc(FamilyHealthSleep::getEndTime)
                .last("LIMIT 1"));
        Map<String, Object> card = card(HealthReportCard.SLEEP_LAST);
        card.put("hasData", night != null);
        if (night == null) return card;
        card.put("sleep", sleepMap(night));
        List<Map<String, Object>> stages = new ArrayList<>();
        for (FamilyHealthSleepStage s : stageMapper.selectList(new LambdaQueryWrapper<FamilyHealthSleepStage>()
                .eq(FamilyHealthSleepStage::getSleepId, night.getId())
                .orderByAsc(FamilyHealthSleepStage::getStartTime))) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("stage", s.getStage());
            item.put("startTime", s.getStartTime());
            item.put("endTime", s.getEndTime());
            item.put("durationMin", s.getDurationMin());
            stages.add(item);
        }
        card.put("stages", stages);
        return card;
    }

    /** 近 7 天睡眠时长(按入睡日合计,夜睡 + 小睡都算) */
    private Map<String, Object> sleepSeries(Long userId, LocalDate anchor) {
        LocalDate from = anchor.minusDays(SERIES_DAYS - 1L);
        Map<LocalDate, Double> byDate = new HashMap<>();
        for (FamilyHealthSleep s : sleepMapper.selectList(new LambdaQueryWrapper<FamilyHealthSleep>()
                .eq(FamilyHealthSleep::getUserId, userId)
                .between(FamilyHealthSleep::getSleepDate, from, anchor))) {
            byDate.merge(s.getSleepDate(), Objects.requireNonNullElse(s.getTotalMin(), 0).doubleValue(), Double::sum);
        }
        Map<String, Object> card = card(HealthReportCard.SLEEP_7D);
        card.put("metric", HealthMetric.SLEEP);
        card.put("unit", HealthMetric.find(HealthMetric.SLEEP).unit());
        card.put("from", from);
        card.put("to", anchor);
        fillSeries(card, from, anchor, byDate.isEmpty() ? null : byDate);
        return card;
    }

    /** 近 7 天日聚合指标:没数据的天返回 null(前端不画,不伪造 0) */
    private Map<String, Object> daySeries(Long userId, String metric, LocalDate anchor) {
        LocalDate from = anchor.minusDays(SERIES_DAYS - 1L);
        Map<LocalDate, Double> byDate = new HashMap<>();
        List<FamilyHealthSample> rows = sampleMapper.selectList(new LambdaQueryWrapper<FamilyHealthSample>()
                .eq(FamilyHealthSample::getUserId, userId)
                .eq(FamilyHealthSample::getMetric, metric)
                .eq(FamilyHealthSample::getPeriod, HealthMetric.PERIOD_DAY)
                .ge(FamilyHealthSample::getStartTime, from.atStartOfDay())
                .lt(FamilyHealthSample::getStartTime, anchor.plusDays(1).atStartOfDay()));
        for (FamilyHealthSample r : rows) {
            if (r.getValueNum() == null) continue;
            byDate.put(r.getStartTime().toLocalDate(), r.getValueNum().doubleValue());
        }
        Map<String, Object> card = card(metric.equals(HealthMetric.MENSTRUAL_FLOW)
                ? HealthReportCard.MENSTRUAL_7D : cardIdOf(metric));
        card.put("metric", metric);
        HealthMetric.Def def = HealthMetric.find(metric);
        card.put("unit", def == null ? "" : def.unit());
        card.put("from", from);
        card.put("to", anchor);
        fillSeries(card, from, anchor, byDate.isEmpty() ? null : byDate);
        return card;
    }

    /** 补齐 7 天网格并算合计/日均 */
    private void fillSeries(Map<String, Object> card, LocalDate from, LocalDate to, Map<LocalDate, Double> byDate) {
        List<Map<String, Object>> items = new ArrayList<>();
        double total = 0;
        int days = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            Double value = byDate == null ? null : byDate.get(d);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", d);
            item.put("value", value);
            items.add(item);
            if (value != null) {
                total += value;
                days++;
            }
        }
        card.put("hasData", days > 0);
        card.put("items", items);
        card.put("total", days == 0 ? null : round(total));
        card.put("avg", days == 0 ? null : round(total / days));
    }

    /** 最近一天的点指标(心率/血氧):找到锚定日之前最近一个有数据的那天,返回当天的点 */
    private Map<String, Object> pointDay(Long userId, String metric, LocalDate anchor) {
        FamilyHealthSample last = sampleMapper.selectOne(new LambdaQueryWrapper<FamilyHealthSample>()
                .eq(FamilyHealthSample::getUserId, userId)
                .eq(FamilyHealthSample::getMetric, metric)
                .lt(FamilyHealthSample::getStartTime, anchor.plusDays(1).atStartOfDay())
                .orderByDesc(FamilyHealthSample::getStartTime)
                .last("LIMIT 1"));
        Map<String, Object> card = card(metric.equals("SPO2") ? HealthReportCard.SPO2_DAY : HealthReportCard.HR_DAY);
        card.put("metric", metric);
        HealthMetric.Def def = HealthMetric.find(metric);
        card.put("unit", def == null ? "" : def.unit());
        if (last == null) {
            card.put("hasData", false);
            return card;
        }
        LocalDate day = last.getStartTime().toLocalDate();
        List<FamilyHealthSample> rows = sampleMapper.selectList(new LambdaQueryWrapper<FamilyHealthSample>()
                .eq(FamilyHealthSample::getUserId, userId)
                .eq(FamilyHealthSample::getMetric, metric)
                .ge(FamilyHealthSample::getStartTime, day.atStartOfDay())
                .lt(FamilyHealthSample::getStartTime, day.plusDays(1).atStartOfDay())
                .orderByAsc(FamilyHealthSample::getStartTime)
                .last("LIMIT " + POINT_LIMIT));
        List<Map<String, Object>> points = new ArrayList<>();
        double sum = 0;
        Double min = null;
        Double max = null;
        int count = 0;
        for (FamilyHealthSample r : rows) {
            if (r.getValueNum() == null) continue;
            double v = r.getValueNum().doubleValue();
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("startTime", r.getStartTime());
            p.put("value", round(v));
            p.put("min", r.getValueMin() == null ? null : round(r.getValueMin().doubleValue()));
            p.put("max", r.getValueMax() == null ? null : round(r.getValueMax().doubleValue()));
            points.add(p);
            sum += v;
            count++;
            min = min == null ? v : Math.min(min, v);
            max = max == null ? v : Math.max(max, v);
        }
        card.put("date", day);
        card.put("hasData", count > 0);
        card.put("points", points);
        card.put("avg", count == 0 ? null : round(sum / count));
        card.put("min", min == null ? null : round(min));
        card.put("max", max == null ? null : round(max));
        return card;
    }

    /** 体重趋势:近 30 天(最多 60 个点),带区间变化供卡片直接显示 */
    private Map<String, Object> weightTrend(Long userId, LocalDate anchor) {
        LocalDate from = anchor.minusDays(WEIGHT_TREND_DAYS - 1L);
        List<FamilyHealthSample> rows = sampleMapper.selectList(new LambdaQueryWrapper<FamilyHealthSample>()
                .eq(FamilyHealthSample::getUserId, userId)
                .eq(FamilyHealthSample::getMetric, "WEIGHT")
                .ge(FamilyHealthSample::getStartTime, from.atStartOfDay())
                .lt(FamilyHealthSample::getStartTime, anchor.plusDays(1).atStartOfDay())
                .orderByAsc(FamilyHealthSample::getStartTime)
                .last("LIMIT 60"));
        List<Map<String, Object>> points = new ArrayList<>();
        for (FamilyHealthSample r : rows) {
            if (r.getValueNum() == null) continue;
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("startTime", r.getStartTime());
            p.put("value", round(r.getValueNum().doubleValue()));
            points.add(p);
        }
        Map<String, Object> card = card(HealthReportCard.WEIGHT_TREND);
        HealthMetric.Def def = HealthMetric.find("WEIGHT");
        card.put("metric", "WEIGHT");
        card.put("unit", def == null ? "kg" : def.unit());
        card.put("from", from);
        card.put("to", anchor);
        card.put("hasData", !points.isEmpty());
        card.put("points", points);
        if (!points.isEmpty()) {
            double first = round(((Number) points.get(0).get("value")).doubleValue());
            double latest = round(((Number) points.get(points.size() - 1).get("value")).doubleValue());
            card.put("latest", latest);
            card.put("change", round(latest - first));
        }
        return card;
    }

    /** 异常提醒:参考区间 + 个人基线,口径全在 common/HealthAlerts */
    private Map<String, Object> alerts(Long userId, LocalDate anchor) {
        LocalDate from = anchor.minusDays(BASELINE_DAYS - 1L);
        List<FamilyHealthSample> rows = sampleMapper.selectList(new LambdaQueryWrapper<FamilyHealthSample>()
                .eq(FamilyHealthSample::getUserId, userId)
                .in(FamilyHealthSample::getMetric, ALERT_METRICS)
                .ge(FamilyHealthSample::getStartTime, from.atStartOfDay())
                .lt(FamilyHealthSample::getStartTime, anchor.plusDays(1).atStartOfDay()));

        // 指标 → 日期 → 当日均值(点指标一天多个点取平均,日聚合一天一行)
        Map<String, Map<LocalDate, double[]>> daily = new HashMap<>();
        for (FamilyHealthSample r : rows) {
            if (r.getValueNum() == null) continue;
            if (HealthMetric.isSensitive(r.getMetric())) continue;
            LocalDate day = r.getStartTime().toLocalDate();
            Map<LocalDate, double[]> byDate = daily.computeIfAbsent(r.getMetric(), k -> new HashMap<>());
            double[] acc = byDate.computeIfAbsent(day, k -> new double[2]);
            acc[0] += r.getValueNum().doubleValue();
            acc[1] += 1;
        }

        Map<String, Double> latest = new HashMap<>();
        Map<String, double[]> history = new HashMap<>();
        for (Map.Entry<String, Map<LocalDate, double[]>> e : daily.entrySet()) {
            List<LocalDate> days = new ArrayList<>(e.getValue().keySet());
            days.sort(LocalDate::compareTo);
            double[] series = new double[days.size()];
            for (int i = 0; i < days.size(); i++) {
                double[] acc = e.getValue().get(days.get(i));
                series[i] = acc[0] / acc[1];
            }
            history.put(e.getKey(), series);
            latest.put(e.getKey(), series[series.length - 1]);
        }

        List<HealthAlerts.Alert> found = HealthAlerts.evaluate(latest, history);
        List<Map<String, Object>> items = new ArrayList<>();
        for (HealthAlerts.Alert a : found) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("metric", a.metric());
            item.put("level", a.level());
            item.put("value", a.value());
            item.put("unit", a.unit());
            item.put("refLow", a.refLow());
            item.put("refHigh", a.refHigh());
            item.put("baseline", a.baseline());
            item.put("reason", a.reason());
            items.add(item);
        }
        Map<String, Object> card = card(HealthReportCard.ALERTS);
        card.put("hasData", !history.isEmpty());
        card.put("items", items);
        card.put("ok", !history.isEmpty() && items.isEmpty());
        return card;
    }

    // ==================== 公共小工具 ====================

    private Map<String, Object> card(String id) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("id", id);
        return card;
    }

    private Map<String, Object> emptyCard(String id) {
        Map<String, Object> card = card(id);
        card.put("hasData", false);
        return card;
    }

    /** 日聚合指标 → 卡片 id(只有固定的几类走进报表) */
    private String cardIdOf(String metric) {
        return switch (metric) {
            case "STEPS" -> HealthReportCard.STEPS_7D;
            case "ACTIVE_ENERGY" -> HealthReportCard.ENERGY_7D;
            case "EXERCISE_MIN" -> HealthReportCard.EXERCISE_7D;
            case "RESTING_HR" -> HealthReportCard.RESTING_HR_7D;
            default -> metric.toLowerCase();
        };
    }

    private Map<String, Object> sleepMap(FamilyHealthSleep r) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", r.getId());
        item.put("sleepDate", r.getSleepDate());
        item.put("kind", r.getKind());
        item.put("startTime", r.getStartTime());
        item.put("endTime", r.getEndTime());
        item.put("totalMin", r.getTotalMin());
        item.put("deepMin", r.getDeepMin());
        item.put("lightMin", r.getLightMin());
        item.put("remMin", r.getRemMin());
        item.put("awakeMin", r.getAwakeMin());
        item.put("stageLevel", r.getStageLevel());
        item.put("source", r.getSource());
        item.put("provider", r.getProvider());
        return item;
    }

    private static double round(BigDecimal v) {
        return round(v.doubleValue());
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
