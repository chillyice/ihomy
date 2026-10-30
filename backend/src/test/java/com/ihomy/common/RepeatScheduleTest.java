package com.ihomy.common;

import cn.hutool.core.date.ChineseDate;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 提醒重复规则:各种重复的命中/不命中,以及闰日、月末日号、阳历/农历每年与闰月边界。
 * 提醒到点通知、今日聚合与日程区间展开共用这一份口径。
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

    // ---------------------------------------------------------------- 每年重复(V10.95)

    @Test
    void yearlySolarComparesMonthAndDay() {
        LocalDate base = LocalDate.of(2024, 3, 8);
        assertThat(RepeatSchedule.dueToday(DictConst.REPEAT_YEARLY, base, LocalDate.of(2026, 3, 8))).isTrue();
        assertThat(RepeatSchedule.dueToday(DictConst.REPEAT_YEARLY, base, LocalDate.of(2030, 3, 8))).isTrue();
        assertThat(RepeatSchedule.dueToday(DictConst.REPEAT_YEARLY, base, LocalDate.of(2026, 3, 9))).isFalse();
        assertThat(RepeatSchedule.dueToday(DictConst.REPEAT_YEARLY, base, LocalDate.of(2026, 4, 8))).isFalse();
    }

    @Test
    void yearlySolarLeapDayOnlyHitsInLeapYears() {
        LocalDate base = LocalDate.of(2024, 2, 29);
        assertThat(RepeatSchedule.dueToday(DictConst.REPEAT_YEARLY, base, LocalDate.of(2028, 2, 29))).isTrue();
        // 平年不顺延到 2/28,也不在 3/1 触发
        assertThat(RepeatSchedule.dueToday(DictConst.REPEAT_YEARLY, base, LocalDate.of(2026, 2, 28))).isFalse();
        assertThat(RepeatSchedule.dueToday(DictConst.REPEAT_YEARLY, base, LocalDate.of(2026, 3, 1))).isFalse();
    }

    @Test
    void yearlyLunarComparesLunarMonthAndDay() {
        LocalDate base = LocalDate.of(2024, 9, 17); // 农历八月十五
        // 2026 年中秋 = 公历 2026-09-25(农历八月十五)
        assertThat(RepeatSchedule.dueToday(DictConst.REPEAT_YEARLY, base, LocalDate.of(2026, 9, 25),
                DictConst.CALENDAR_LUNAR, 8, 15, 0)).isTrue();
        assertThat(RepeatSchedule.dueToday(DictConst.REPEAT_YEARLY, base, LocalDate.of(2026, 9, 26),
                DictConst.CALENDAR_LUNAR, 8, 15, 0)).isFalse();
        // 给了农历字段但历法仍是阳历时按阳历月日比对,不命中
        assertThat(RepeatSchedule.dueToday(DictConst.REPEAT_YEARLY, base, LocalDate.of(2026, 9, 25),
                DictConst.CALENDAR_SOLAR, 8, 15, 0)).isFalse();
    }

    @Test
    void yearlyLunarRequiresMonthAndDay() {
        LocalDate base = LocalDate.of(2026, 9, 25);
        assertThat(RepeatSchedule.dueToday(DictConst.REPEAT_YEARLY, base, LocalDate.of(2026, 9, 25),
                DictConst.CALENDAR_LUNAR, null, 15, 0)).isFalse();
        assertThat(RepeatSchedule.dueToday(DictConst.REPEAT_YEARLY, base, LocalDate.of(2026, 9, 25),
                DictConst.CALENDAR_LUNAR, 8, null, 0)).isFalse();
    }

    @Test
    void yearlyLunarLeapMonthMatchesOnlyInThatLeapMonth() {
        LocalDate leapFirst = lunarDay(2023, 2, 1, true);
        LocalDate normalFirst = lunarDay(2023, 2, 1, false);
        assertThat(leapFirst).as("2023 年存在闰二月").isNotNull();
        assertThat(normalFirst).isNotNull();
        // 设定闰二月初一:只在闰二月初一触发
        assertThat(RepeatSchedule.dueToday(DictConst.REPEAT_YEARLY, leapFirst, leapFirst,
                DictConst.CALENDAR_LUNAR, 2, 1, 1)).isTrue();
        assertThat(RepeatSchedule.dueToday(DictConst.REPEAT_YEARLY, leapFirst, normalFirst,
                DictConst.CALENDAR_LUNAR, 2, 1, 1)).isFalse();
        // 设定普通二月初一:只在普通二月初一触发
        assertThat(RepeatSchedule.dueToday(DictConst.REPEAT_YEARLY, normalFirst, normalFirst,
                DictConst.CALENDAR_LUNAR, 2, 1, 0)).isTrue();
        assertThat(RepeatSchedule.dueToday(DictConst.REPEAT_YEARLY, normalFirst, leapFirst,
                DictConst.CALENDAR_LUNAR, 2, 1, 0)).isFalse();
    }

    @Test
    void yearlyLunarLeapMonthSkippedWhenYearHasNoSuchLeapMonth() {
        // 2026 年没有闰二月:普通二月初一当天、设定闰二月都不触发
        LocalDate normalFirst2026 = lunarDay(2026, 2, 1, false);
        assertThat(normalFirst2026).isNotNull();
        assertThat(RepeatSchedule.dueToday(DictConst.REPEAT_YEARLY, normalFirst2026, normalFirst2026,
                DictConst.CALENDAR_LUNAR, 2, 1, 1)).isFalse();
    }

    /** 找某年农历 month/day 对应的公历日(leap=true 取闰月那天);没有该日期返回 null */
    private static LocalDate lunarDay(int year, int month, int day, boolean leap) {
        for (LocalDate d = LocalDate.of(year, 1, 1); d.getYear() == year; d = d.plusDays(1)) {
            ChineseDate cd = new ChineseDate(d);
            int m = cd.isLeapMonth() ? cd.getMonth() - 1 : cd.getMonth();
            if (m == month && cd.getDay() == day && cd.isLeapMonth() == leap) {
                return d;
            }
        }
        return null;
    }
}
