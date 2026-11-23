package com.ihomy.common;

import cn.hutool.core.date.ChineseDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 纪念日下一次日期:阳历今年已过顺延、农历今年/明年择一、闰月与非法值兜底。
 * 首页倒计时、今日页签重要日期、提前提醒调度共用这一份口径。
 */
class AnniversaryDateTest {

    private static LocalDate solarOfLunar(int lunarYear, int month, int day, boolean leap) {
        ChineseDate cd = new ChineseDate(lunarYear, month, day, leap);
        return LocalDate.of(cd.getGregorianYear(), cd.getGregorianMonth() + 1, cd.getGregorianDay());
    }

    @ParameterizedTest
    @CsvSource({
            "5, 20, 2026-05-19, 2026-05-20",
            "10, 30, 2026-10-30, 2026-10-30",
            "5, 20, 2026-05-21, 2027-05-20"
    })
    void solarPicksFirstOccurrenceOnOrAfterToday(int month, int day, LocalDate today, LocalDate expected) {
        assertThat(AnniversaryDate.nextOccurrence("solar", month, day, 0, today)).isEqualTo(expected);
    }

    @Test
    void invalidSolarDateReturnsNull() {
        // 2026 是平年,2 月 29 日不存在
        assertThat(AnniversaryDate.nextOccurrence("solar", 2, 29, 0, LocalDate.of(2026, 1, 1))).isNull();
        assertThat(AnniversaryDate.nextOccurrence("solar", 13, 1, 0, LocalDate.of(2026, 1, 1))).isNull();
    }

    @Test
    void missingMonthOrDayReturnsNull() {
        LocalDate today = LocalDate.of(2026, 10, 30);
        assertThat(AnniversaryDate.nextOccurrence("solar", null, 20, 0, today)).isNull();
        assertThat(AnniversaryDate.nextOccurrence("lunar", 8, null, 0, today)).isNull();
    }

    @ParameterizedTest
    @CsvSource({"2026-01-01, 2026", "2027-01-01, 2027"})
    void lunarPicksUpcomingMidAutumnFestival(LocalDate today, int expectedLunarYear) {
        assertThat(AnniversaryDate.nextOccurrence("lunar", 8, 15, 0, today))
                .isEqualTo(solarOfLunar(expectedLunarYear, 8, 15, false));
    }

    @Test
    void lunarLeapMonthFlagSelectsLeapOccurrence() {
        // 2025 农历闰六月:闰月与常月的公历日期不同
        LocalDate leapDay = solarOfLunar(2025, 6, 1, true);
        assertThat(AnniversaryDate.nextOccurrence("lunar", 6, 1, 1, LocalDate.of(2025, 1, 1)))
                .isEqualTo(leapDay);
        assertThat(leapDay).isNotEqualTo(solarOfLunar(2025, 6, 1, false));
    }

    @Test
    void invalidLunarDateReturnsNull() {
        assertThat(AnniversaryDate.nextOccurrence("lunar", 13, 1, 0, LocalDate.of(2026, 1, 1))).isNull();
        assertThat(AnniversaryDate.nextOccurrence("lunar", 1, 1, 0, null)).isNull();
    }
}
