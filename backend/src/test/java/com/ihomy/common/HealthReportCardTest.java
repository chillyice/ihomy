package com.ihomy.common;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 报表卡片目录:清单收口(去重/未知忽略/空回落默认)与敏感卡片标记。
 * 前端 views/health/reportCards.js 与这里同口径,卡片 id 改名必须两边一起改。
 */
class HealthReportCardTest {

    @Test
    void sanitizeKeepsOrderAndDedupes() {
        List<String> picked = HealthReportCard.sanitize(List.of("hr_day", "steps_7d", "hr_day", " HR_DAY "));
        assertThat(picked).containsExactly(HealthReportCard.HR_DAY, HealthReportCard.STEPS_7D);
    }

    @Test
    void sanitizeDropsUnknownAndFallsBackToDefaults() {
        assertThat(HealthReportCard.sanitize(List.of("nope", " "))).isEqualTo(HealthReportCard.DEFAULTS);
        assertThat(HealthReportCard.sanitize(List.of())).isEqualTo(HealthReportCard.DEFAULTS);
        assertThat(HealthReportCard.sanitize(null)).isEqualTo(HealthReportCard.DEFAULTS);
    }

    @Test
    void sanitizeCapsAtMaxCards() {
        List<String> many = new java.util.ArrayList<>();
        // 同一张卡重复 20 次也只能算一张;目录本身只有 13 张,上限 13 不会被撑破
        for (int i = 0; i < 20; i++) many.add(HealthReportCard.SLEEP_LAST);
        many.addAll(HealthReportCard.allIds());
        assertThat(HealthReportCard.sanitize(many)).hasSize(HealthReportCard.allIds().size());
        assertThat(HealthReportCard.allIds()).hasSizeLessThanOrEqualTo(HealthReportCard.MAX_CARDS);
    }

    @Test
    void onlyMenstrualCardIsSensitive() {
        for (String id : HealthReportCard.allIds()) {
            assertThat(HealthReportCard.isSensitive(id))
                    .as("卡片 %s 的敏感标记", id)
                    .isEqualTo(HealthReportCard.MENSTRUAL_7D.equals(id));
        }
    }

    @Test
    void defaultsMatchProductSpec() {
        assertThat(HealthReportCard.DEFAULTS).containsExactly(
                HealthReportCard.SLEEP_LAST,
                HealthReportCard.STEPS_7D,
                HealthReportCard.ENERGY_7D,
                HealthReportCard.HR_DAY,
                HealthReportCard.ALERTS);
    }
}
