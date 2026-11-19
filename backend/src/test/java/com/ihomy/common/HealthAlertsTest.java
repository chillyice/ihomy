package com.ihomy.common;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 健康异常提醒:参考区间出界报 WARN,个人基线偏离报 INFO,敏感指标永不进提醒。
 * 门槛与排序都在这里钉住——新用户样本不足时绝不能天天报「异常」。
 */
class HealthAlertsTest {

    private static List<String> metricsOf(List<HealthAlerts.Alert> alerts) {
        return alerts.stream().map(HealthAlerts.Alert::metric).toList();
    }

    @Test
    void valueInsideReferenceRangeProducesNoAlert() {
        Map<String, Double> latest = Map.of("SPO2", 98.0, "RESTING_HR", 60.0, "BODY_TEMP", 36.5);
        assertThat(HealthAlerts.evaluate(latest, Map.of())).isEmpty();
    }

    @Test
    void outOfReferenceRangeIsWarn() {
        Map<String, Double> latest = Map.of("SPO2", 92.0);
        List<HealthAlerts.Alert> found = HealthAlerts.evaluate(latest, Map.of());
        assertThat(found).hasSize(1);
        assertThat(found.get(0).level()).isEqualTo(HealthAlerts.LEVEL_WARN);
        assertThat(found.get(0).reason()).isEqualTo(HealthAlerts.REASON_REF);
        assertThat(found.get(0).refLow()).isEqualTo(95.0);
        assertThat(found.get(0).refHigh()).isEqualTo(100.0);
        assertThat(found.get(0).unit()).isEqualTo("%");
    }

    @Test
    void baselineDeviationNeedsEnoughDays() {
        Map<String, Double> latest = Map.of("RESTING_HR", 100.0);
        // 只有 6 天样本:不判基线偏离(阈值是 7 天)
        Map<String, double[]> sixDays = new HashMap<>();
        sixDays.put("RESTING_HR", new double[]{60, 60, 60, 60, 60, 60});
        assertThat(HealthAlerts.evaluate(latest, sixDays)).isEmpty();

        Map<String, double[]> sevenDays = new HashMap<>();
        sevenDays.put("RESTING_HR", new double[]{55, 60, 58, 62, 57, 63, 59});
        // 100 仍在参考区间内(45–100),所以走基线判定:偏离 >2σ 报 INFO
        List<HealthAlerts.Alert> found = HealthAlerts.evaluate(latest, sevenDays);
        assertThat(metricsOf(found)).containsExactly("RESTING_HR");
        assertThat(found.get(0).level()).isEqualTo(HealthAlerts.LEVEL_INFO);
        assertThat(found.get(0).reason()).isEqualTo(HealthAlerts.REASON_BASELINE);
        assertThat(found.get(0).baseline()).isEqualTo(59.14);
    }

    @Test
    void baselineSkipMetricAlreadyWarnedByReference() {
        // SPO2 92 已出参考区间:只报一次 WARN,不重复报基线偏离
        Map<String, Double> latest = Map.of("SPO2", 92.0);
        Map<String, double[]> history = new HashMap<>();
        history.put("SPO2", new double[]{98, 98, 98, 98, 98, 98, 98});
        List<HealthAlerts.Alert> found = HealthAlerts.evaluate(latest, history);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).level()).isEqualTo(HealthAlerts.LEVEL_WARN);
    }

    @Test
    void flatBaselineWithZeroStdDoesNotReportEverything() {
        // σ=0(恒定基线)时不做偏离判定,否则任一点抖动都会刷屏
        Map<String, Double> latest = Map.of("HRV", 50.0);
        Map<String, double[]> history = new HashMap<>();
        history.put("HRV", new double[]{50, 50, 50, 50, 50, 50, 50});
        assertThat(HealthAlerts.evaluate(latest, history)).isEmpty();
    }

    @Test
    void sensitiveMetricsNeverAlert() {
        // 月经流量即使远超任何阈值也不进提醒表
        Map<String, Double> latest = Map.of("MENSTRUAL_FLOW", 999.0, "BODY_TEMP", 39.0);
        List<HealthAlerts.Alert> found = HealthAlerts.evaluate(latest, Map.of());
        assertThat(metricsOf(found)).containsExactly("BODY_TEMP");
    }

    @Test
    void cappedAtFiveWarnFirst() {
        Map<String, Double> latest = new HashMap<>();
        latest.put("SPO2", 80.0);
        latest.put("RESTING_HR", 140.0);
        latest.put("RESP_RATE", 40.0);
        latest.put("BODY_TEMP", 40.0);
        latest.put("BP_SYSTOLIC", 200.0);
        latest.put("BP_DIASTOLIC", 120.0);
        latest.put("BLOOD_GLUCOSE", 15.0);
        List<HealthAlerts.Alert> found = HealthAlerts.evaluate(latest, Map.of());
        assertThat(found).hasSize(HealthAlerts.MAX_ALERTS);
        assertThat(found).allMatch(a -> HealthAlerts.LEVEL_WARN.equals(a.level()));
        // 偏离幅度最大的排最前(GLUCOSE 3.9–7.8 → 15 偏离约 156%)
        assertThat(found.get(0).metric()).isEqualTo("BLOOD_GLUCOSE");
    }
}
