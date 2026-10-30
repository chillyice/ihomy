package com.ihomy.service;

import com.ihomy.common.BizException;
import com.ihomy.common.CalendarDay;
import com.ihomy.entity.SysHoliday;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 日历基础数据:农历(1-based,闰月月号需还原)、节气、传统节日白名单与别名归一、
 * 法定节假日/调休挂载、周末标记。只测纯函数,不碰数据库。
 * 日期取自 Hutool ChineseDate 实测(清明只在 getTerm(),不在节日里)。
 */
class CalendarServiceTest {

    private static SysHoliday holiday(String date, String name, String type) {
        SysHoliday h = new SysHoliday();
        h.setYear(LocalDate.parse(date).getYear());
        h.setHolidayDate(LocalDate.parse(date));
        h.setName(name);
        h.setType(type);
        return h;
    }

    private static CalendarDay day(String date) {
        return CalendarService.buildDay(LocalDate.parse(date), null);
    }

    @Test
    void springFestivalIsLunarNewYear() {
        CalendarDay d = day("2026-02-17");
        assertThat(d.lunarMonth()).isEqualTo(1);
        assertThat(d.lunarDay()).isEqualTo(1);
        assertThat(d.lunarText()).isEqualTo("初一");
        assertThat(d.lunarMonthText()).isEqualTo("正月");
        assertThat(d.leapMonth()).isFalse();
        assertThat(d.festivals()).containsExactly("春节");
        assertThat(d.term()).isEmpty();
    }

    @Test
    void lanternFestivalAliasesAreMerged() {
        // Hutool 同时给出「元宵节 上元节」,只保留规范名且不重复
        CalendarDay d = day("2026-03-03");
        assertThat(d.lunarMonth()).isEqualTo(1);
        assertThat(d.lunarDay()).isEqualTo(15);
        assertThat(d.festivals()).containsExactly("元宵节");
    }

    @Test
    void qingmingOnlyComesFromSolarTerm() {
        CalendarDay d = day("2026-04-05");
        assertThat(d.term()).isEqualTo("清明");
        assertThat(d.festivals()).isEmpty();
    }

    @Test
    void dragonBoatFestival() {
        CalendarDay d = day("2026-06-19");
        assertThat(d.lunarMonth()).isEqualTo(5);
        assertThat(d.lunarDay()).isEqualTo(5);
        assertThat(d.festivals()).containsExactly("端午节");
    }

    @Test
    void qixiFestival() {
        CalendarDay d = day("2026-08-19");
        assertThat(d.lunarMonth()).isEqualTo(7);
        assertThat(d.lunarDay()).isEqualTo(7);
        assertThat(d.festivals()).containsExactly("七夕");
    }

    @Test
    void midAutumnFestival() {
        CalendarDay d = day("2026-09-25");
        assertThat(d.lunarMonth()).isEqualTo(8);
        assertThat(d.lunarDay()).isEqualTo(15);
        assertThat(d.festivals()).containsExactly("中秋节");
    }

    @Test
    void doubleNinthFestival() {
        CalendarDay d = day("2026-10-18");
        assertThat(d.lunarMonth()).isEqualTo(9);
        assertThat(d.lunarDay()).isEqualTo(9);
        assertThat(d.festivals()).containsExactly("重阳节");
        assertThat(d.weekend()).isTrue();
    }

    @Test
    void newYearsEveOnLastDayOfTwelfthMonth() {
        CalendarDay d = day("2027-02-05");
        assertThat(d.lunarMonth()).isEqualTo(12);
        assertThat(d.lunarDay()).isEqualTo(29);
        assertThat(d.lunarMonthText()).isEqualTo("腊月");
        assertThat(d.festivals()).containsExactly("除夕");
    }

    @Test
    void leapMonthRestoresMonthNumber() {
        // 2025 农历闰六月初一:Hutool getMonth() 返回 7,展示口径要还原成「闰六月」
        CalendarDay d = day("2025-07-25");
        assertThat(d.leapMonth()).isTrue();
        assertThat(d.lunarMonth()).isEqualTo(6);
        assertThat(d.lunarDay()).isEqualTo(1);
        assertThat(d.lunarMonthText()).isEqualTo("闰六月");
        assertThat(d.festivals()).isEmpty();
    }

    @Test
    void ordinaryYearEndHasNoFestivalOrTerm() {
        CalendarDay d = day("2026-12-31");
        assertThat(d.lunarMonth()).isEqualTo(11);
        assertThat(d.lunarDay()).isEqualTo(23);
        assertThat(d.term()).isEmpty();
        assertThat(d.festivals()).isEmpty();
        assertThat(d.weekend()).isFalse();
    }

    @Test
    void holidayAndWorkdayAreAttachedByDate() {
        CalendarDay off = CalendarService.buildDay(LocalDate.parse("2026-10-01"),
                holiday("2026-10-01", "国庆节", "HOLIDAY"));
        assertThat(off.holiday()).isNotNull();
        assertThat(off.holiday().type()).isEqualTo("HOLIDAY");
        assertThat(off.holiday().name()).isEqualTo("国庆节");

        CalendarDay work = CalendarService.buildDay(LocalDate.parse("2026-10-10"),
                holiday("2026-10-10", "国庆节", "WORKDAY"));
        assertThat(work.holiday().type()).isEqualTo("WORKDAY");
        assertThat(work.weekend()).isTrue(); // 补班日照日历仍是周六
    }

    @Test
    void buildMonthCoversEveryDayAndAppliesHolidays() {
        Map<LocalDate, SysHoliday> holidays = new HashMap<>();
        holidays.put(LocalDate.parse("2026-02-17"), holiday("2026-02-17", "春节", "HOLIDAY"));

        List<CalendarDay> feb = CalendarService.buildMonth(YearMonth.of(2026, 2), holidays);
        assertThat(feb).hasSize(28);
        assertThat(feb.get(16).festivals()).containsExactly("春节");
        assertThat(feb.get(16).holiday()).isNotNull();

        // 2028 是闰年
        assertThat(CalendarService.buildMonth(YearMonth.of(2028, 2), null)).hasSize(29);
    }

    @Test
    void invalidMonthIsRejected() {
        CalendarService service = new CalendarService(null);
        assertThatThrownBy(() -> service.month("2026-13")).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.month("2026/10")).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.month("1899-12")).isInstanceOf(BizException.class);
    }
}
