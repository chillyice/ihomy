package com.ihomy.common;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 健康数据保留水位:按「现在 − 保留天数」算删除线;保留天数置 0 或负数视为关闭,返回 null。
 * 与清理任务的分支一一对应(见 HealthRetentionService),错了会要么不清要么清太狠。
 */
class HealthRetentionTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 4, 20);

    @Test
    void cutoffCountsBackFromNow() {
        assertThat(HealthRetention.cutoff(NOW, 180)).isEqualTo(LocalDateTime.of(2026, 5, 22, 4, 20));
        assertThat(HealthRetention.cutoff(NOW, 90)).isEqualTo(LocalDateTime.of(2026, 8, 20, 4, 20));
        assertThat(HealthRetention.cutoff(NOW, 1)).isEqualTo(NOW.minusDays(1));
    }

    @Test
    void nonPositiveRetentionDisablesCleanup() {
        assertThat(HealthRetention.cutoff(NOW, 0)).isNull();
        assertThat(HealthRetention.cutoff(NOW, -1)).isNull();
    }
}
