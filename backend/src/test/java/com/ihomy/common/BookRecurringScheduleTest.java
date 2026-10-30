package com.ihomy.common;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 记账周期模板的日期推算:月末取短月尾声、每年 2/29 平年取 2/28、结束日含当天、
 * 停机补跑最多 90 天、已生成日推进后同日重复跑不再产出(幂等的纯逻辑部分)。
 */
class BookRecurringScheduleTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 30);

    @Test
    void dailyGeneratesEveryDayFromStartThroughToday() {
        BookRecurringSchedule.Occurrences occ = BookRecurringSchedule.due(
                BookRecurringSchedule.PERIOD_DAILY, TODAY.minusDays(2), null, null, TODAY);

        assertThat(occ.dates()).containsExactly(TODAY.minusDays(2), TODAY.minusDays(1), TODAY);
        assertThat(occ.lastGeneratedDate()).isEqualTo(TODAY);
    }

    @Test
    void weeklyKeepsTheSameWeekday() {
        LocalDate start = TODAY.minusWeeks(2); // 与今天同一个星期几
        BookRecurringSchedule.Occurrences occ = BookRecurringSchedule.due(
                BookRecurringSchedule.PERIOD_WEEKLY, start, null, null, TODAY);

        assertThat(occ.dates()).containsExactly(start, start.plusWeeks(1), start.plusWeeks(2));
    }

    @Test
    void monthlyOnThe31stFallsBackToTheEndOfShortMonths() {
        LocalDate start = LocalDate.of(2026, 1, 31);
        BookRecurringSchedule.Occurrences occ = BookRecurringSchedule.due(
                BookRecurringSchedule.PERIOD_MONTHLY, start, null, start, LocalDate.of(2026, 3, 31));

        // 2 月没有 31 号 → 2/28;3 月回到 31 号
        assertThat(occ.dates()).containsExactly(LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31));
    }

    @Test
    void yearlyOnLeapDayFallsBackToFeb28InCommonYears() {
        // 起始 2/29:平年落到 2/28,闰年回到 2/29
        assertThat(BookRecurringSchedule.nextAfter(BookRecurringSchedule.PERIOD_YEARLY,
                LocalDate.of(2024, 2, 29), null, LocalDate.of(2024, 2, 29)))
                .isEqualTo(LocalDate.of(2025, 2, 28));
        assertThat(BookRecurringSchedule.nextAfter(BookRecurringSchedule.PERIOD_YEARLY,
                LocalDate.of(2024, 2, 29), null, LocalDate.of(2025, 2, 28)))
                .isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(BookRecurringSchedule.nextAfter(BookRecurringSchedule.PERIOD_YEARLY,
                LocalDate.of(2024, 2, 29), null, LocalDate.of(2027, 2, 28)))
                .isEqualTo(LocalDate.of(2028, 2, 29));

        BookRecurringSchedule.Occurrences occ = BookRecurringSchedule.due(
                BookRecurringSchedule.PERIOD_YEARLY, LocalDate.of(2024, 2, 29), null,
                LocalDate.of(2024, 2, 29), LocalDate.of(2025, 3, 1));
        assertThat(occ.dates()).containsExactly(LocalDate.of(2025, 2, 28));
    }

    @Test
    void endDateIsInclusiveAndStopsGeneration() {
        BookRecurringSchedule.Occurrences occ = BookRecurringSchedule.due(
                BookRecurringSchedule.PERIOD_MONTHLY, LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 1), null, LocalDate.of(2026, 3, 15));

        assertThat(occ.dates()).containsExactly(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1));
    }

    @Test
    void backfillIsCappedAt90DaysButLastGeneratedAdvances() {
        LocalDate start = LocalDate.of(2025, 1, 1);
        BookRecurringSchedule.Occurrences occ = BookRecurringSchedule.due(
                BookRecurringSchedule.PERIOD_DAILY, start, null, null, TODAY);

        assertThat(occ.dates()).hasSize(BookRecurringSchedule.MAX_BACKFILL_DAYS);
        assertThat(occ.dates().get(0)).isEqualTo(TODAY.minusDays(BookRecurringSchedule.MAX_BACKFILL_DAYS - 1L));
        assertThat(occ.dates().get(occ.dates().size() - 1)).isEqualTo(TODAY);
        // 已生成日推进到今天,避免下次又落回同一段历史
        assertThat(occ.lastGeneratedDate()).isEqualTo(TODAY);
    }

    @Test
    void rerunOnTheSameDayProducesNothing() {
        BookRecurringSchedule.Occurrences first = BookRecurringSchedule.due(
                BookRecurringSchedule.PERIOD_MONTHLY, LocalDate.of(2026, 9, 1), null, null, TODAY);
        assertThat(first.dates()).containsExactly(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1));

        BookRecurringSchedule.Occurrences again = BookRecurringSchedule.due(
                BookRecurringSchedule.PERIOD_MONTHLY, LocalDate.of(2026, 9, 1), null,
                first.lastGeneratedDate(), TODAY);
        assertThat(again.isEmpty()).isTrue();
        assertThat(again.lastGeneratedDate()).isNull();
    }

    @Test
    void futureStartDateGeneratesNothing() {
        BookRecurringSchedule.Occurrences occ = BookRecurringSchedule.due(
                BookRecurringSchedule.PERIOD_MONTHLY, TODAY.plusDays(1), null, null, TODAY);
        assertThat(occ.isEmpty()).isTrue();
    }

    @Test
    void lastGeneratedBeforeStartDoesNotDoubleCountStart() {
        BookRecurringSchedule.Occurrences occ = BookRecurringSchedule.due(
                BookRecurringSchedule.PERIOD_MONTHLY, LocalDate.of(2026, 10, 1), null,
                LocalDate.of(2026, 9, 1), TODAY);
        assertThat(occ.dates()).containsExactly(LocalDate.of(2026, 10, 1));
    }

    @Test
    void nextAfterSkipsGeneratedDateAndRespectsEndDate() {
        assertThat(BookRecurringSchedule.nextAfter(BookRecurringSchedule.PERIOD_MONTHLY,
                LocalDate.of(2026, 1, 15), null, LocalDate.of(2026, 1, 15)))
                .isEqualTo(LocalDate.of(2026, 2, 15));
        assertThat(BookRecurringSchedule.nextAfter(BookRecurringSchedule.PERIOD_MONTHLY,
                LocalDate.of(2026, 1, 15), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 15)))
                .isNull();
        assertThat(BookRecurringSchedule.nextAfter(BookRecurringSchedule.PERIOD_YEARLY,
                LocalDate.of(2026, 2, 28), null, LocalDate.of(2026, 3, 1)))
                .isEqualTo(LocalDate.of(2027, 2, 28));
    }

    @Test
    void normalizePeriodFallsBackToMonthly() {
        assertThat(BookRecurringSchedule.normalizePeriod(null)).isEqualTo(BookRecurringSchedule.PERIOD_MONTHLY);
        assertThat(BookRecurringSchedule.normalizePeriod("weekly")).isEqualTo(BookRecurringSchedule.PERIOD_WEEKLY);
        assertThat(BookRecurringSchedule.normalizePeriod("SOMETHING")).isEqualTo(BookRecurringSchedule.PERIOD_MONTHLY);
    }

    @Test
    void datesAreAscendingWithoutDuplicates() {
        List<LocalDate> dates = BookRecurringSchedule.due(
                BookRecurringSchedule.PERIOD_MONTHLY, LocalDate.of(2025, 12, 31), null, null, TODAY).dates();
        assertThat(dates).isSorted();
        assertThat(dates).doesNotHaveDuplicates();
    }
}
