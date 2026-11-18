package com.ihomy.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 月平均口径:优先只算夜睡,月内只有小睡时才退回全部会话,
 * 否则白天补觉会把「平均每晚」拉到几十分钟,看着像数据丢了。
 */
class HealthSleepStatsTest {

    @Test
    void nightSessionsDecideTheAverage() {
        // 20 晚夜睡共 8400 分钟(平均 420),另有 5 次小睡共 250 分钟
        assertThat(HealthSleepStats.nightlyAvg(8400, 20, 8650, 25)).isEqualTo(420);
    }

    @Test
    void fallsBackToAllSessionsWhenNoNightRow() {
        assertThat(HealthSleepStats.nightlyAvg(0, 0, 300, 4)).isEqualTo(75);
    }

    @Test
    void roundsToWholeMinutes() {
        assertThat(HealthSleepStats.nightlyAvg(421, 3, 0, 0)).isEqualTo(140);
    }

    @Test
    void emptyMonthHasNoAverage() {
        assertThat(HealthSleepStats.nightlyAvg(0, 0, 0, 0)).isNull();
        assertThat(HealthSleepStats.nightlyAvg(null, null, null, null)).isNull();
    }
}
