package com.ihomy.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 指标目录:单位定死、范围校验、敏感组标记、来源归一。
 * 新增指标只加目录一行(表与接口不动),这里守住「码/单位/周期」不被误改。
 */
class HealthMetricTest {

    @Test
    void findsMetricIgnoringCaseAndSpaces() {
        HealthMetric.Def def = HealthMetric.find(" steps ");
        assertThat(def).isNotNull();
        assertThat(def.code()).isEqualTo("STEPS");
        assertThat(def.period()).isEqualTo(HealthMetric.PERIOD_DAY);
        assertThat(def.unit()).isEqualTo("步");
    }

    @Test
    void unknownAndSleepHandling() {
        assertThat(HealthMetric.find("NOT_A_METRIC")).isNull();
        assertThat(HealthMetric.find(null)).isNull();
        // 睡眠在目录里保留,但周期是 SESSION——sample 管道必须拒收
        assertThat(HealthMetric.find("sleep").period()).isEqualTo(HealthMetric.PERIOD_SESSION);
    }

    @Test
    void heartRateIsPointAndWeightRangeIsSane() {
        HealthMetric.Def hr = HealthMetric.find("HEART_RATE");
        assertThat(hr.period()).isEqualTo(HealthMetric.PERIOD_POINT);
        assertThat(hr.unit()).isEqualTo("bpm");
        assertThat(hr.min()).isEqualTo(20);
        assertThat(hr.max()).isEqualTo(250);

        HealthMetric.Def weight = HealthMetric.find("WEIGHT");
        assertThat(weight.min()).isEqualTo(1);
        assertThat(weight.max()).isEqualTo(300);
    }

    @Test
    void menstrualFlowIsSensitiveWithFiveLevels() {
        HealthMetric.Def def = HealthMetric.find("MENSTRUAL_FLOW");
        assertThat(def.sensitive()).isTrue();
        assertThat(def.group()).isEqualTo("SENSITIVE");
        assertThat(def.min()).isEqualTo(0);
        assertThat(def.max()).isEqualTo(4);
        assertThat(HealthMetric.isSensitive("menstrual_flow")).isTrue();
        assertThat(HealthMetric.isSensitive("STEPS")).isFalse();
    }

    @Test
    void normalizesVendorSource() {
        assertThat(HealthMetric.normalizeSource("Garmin")).isEqualTo("GARMIN");
        assertThat(HealthMetric.normalizeSource("com.garmin.android.apps.connectmobile")).isEqualTo("GARMIN");
        assertThat(HealthMetric.normalizeSource("com.huawei.health")).isEqualTo("HUAWEI");
        assertThat(HealthMetric.normalizeSource("Apple Health")).isEqualTo("APPLE");
        assertThat(HealthMetric.normalizeSource(null)).isEqualTo("OTHER");
        assertThat(HealthMetric.normalizeSource("unknown-vendor")).isEqualTo("OTHER");
    }

    @Test
    void providerAndStageWhitelists() {
        assertThat(HealthMetric.PROVIDERS).containsExactlyInAnyOrder(
                "SHORTCUT", "HEALTH_CONNECT", "IOS_NATIVE");
        assertThat(HealthMetric.SLEEP_STAGES).contains("DEEP", "LIGHT", "REM", "AWAKE", "INBED", "UNKNOWN");
    }
}
