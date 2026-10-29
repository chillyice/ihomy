package com.ihomy.common;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 提醒重复规则:四种重复的命中/不命中,以及闰日、月末日号边界。
 * 提醒到点通知与「今日」聚合共用这一份口径。
 */
class RepeatScheduleTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 29); // 周四

    @Test
    void onceHitsOnlyOnItsOwnDay() {
        assertThat(RepeatSchedule.dueToday(DictConst.REPEAT_ONCE, TODAY, TODAY)).isTrue();
        assertThat(RepeatSchedule.dueToday(DictConst.REPEAT_ONCE, TODAY.minusDays(1), TODAY)).isFalse();
        assertThat(RepeatSchedule.dueToday(DictConst.REPEAT_ONCE, TODAY.plusDays(1), TODAY)).isFalse();
    }

    @Test
    void nullRepeatTypeTreatedAsOnce() {
        assertThat(RepeatSchedule.dueToday(null, TODAY, TODAY)).isTrue();
        assertThat(RepeatSchedule.dueToday(null, TODAY.plusDays(1), TODAY)).isFalse();
    }

    @Test
    void dailyAlwaysHits() {
        assertThat(RepeatSchedule.dueToday(DictConst.REPEAT_DAILY, TODAY.minusYears(3), TODAY)).isTrue();
        assertThat(RepeatSchedule.dueToday(DictConst.REPEAT_DAILY, TODAY.plusYears(3), TODAY)).isTrue();
    }

    @Test
    void weeklyComparesDayOfWeek() {
        assertThat(RepeatSchedule.dueToday(DictConst.REPEAT_WEEKLY, TODAY.minusWeeks(5), TODAY)).isTrue();
        assertThat(RepeatSchedule.dueToday(DictConst.REPEAT_WEEKLY, TODAY.minusDays(1), TODAY)).isFalse();
    }

    @Test
    void monthlyComparesDayOfMonth() {
        assertThat(RepeatSchedule.dueToday(DictConst.REPEAT_MONTHLY,
                LocalDate.of(2026, 3, 29), TODAY)).isTrue();
        assertThat(RepeatSchedule.dueToday(DictConst.REPEAT_MONTHLY,
                LocalDate.of(2026, 3, 28), TODAY)).isFalse();
    }

    @Test
    void monthlyDay31SkipsShorterMonths() {
        // 基准 31 号:2 月与 30 天月份没有这一天,不触发
        LocalDate base = LocalDate.of(2026, 1, 31);
        assertThat(RepeatSchedule.dueToday(DictConst.REPEAT_MONTHLY, base, LocalDate.of(2026, 2, 28))).isFalse();
        assertThat(RepeatSchedule.dueToday(DictConst.REPEAT_MONTHLY, base, LocalDate.of(2026, 3, 31))).isTrue();
    }

    @Test
    void leapDayOnceDoesNotDriftToFeb28() {
        LocalDate leap = LocalDate.of(2024, 2, 29);
        assertThat(RepeatSchedule.dueToday(DictConst.REPEAT_ONCE, leap, LocalDate.of(2025, 2, 28))).isFalse();
        assertThat(RepeatSchedule.dueToday(DictConst.REPEAT_MONTHLY, leap, LocalDate.of(2025, 2, 28))).isFalse();
    }

    @Test
    void missingBaseDateNeverHits() {
        assertThat(RepeatSchedule.dueToday(DictConst.REPEAT_DAILY, null, TODAY)).isFalse();
        assertThat(RepeatSchedule.dueToday(DictConst.REPEAT_ONCE, TODAY, null)).isFalse();
    }
}
