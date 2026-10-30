package com.ihomy.common;

import java.time.LocalDate;
import java.util.List;

/**
 * 日历单日信息(纯值对象):公历日期 + 农历 + 节气 + 传统节日 + 法定节假日/调休。
 * 由 CalendarService 按月计算后随 /public/calendar 下发,前端不做任何农历换算。
 *
 * @param date           公历日期
 * @param lunarMonth     农历月(1-12,1-based)
 * @param lunarDay       农历日(1-30,1-based)
 * @param lunarText      农历日文案(初一…三十)
 * @param lunarMonthText 农历月文案(正月…腊月,闰月带「闰」前缀)
 * @param leapMonth      是否闰月
 * @param term           节气名(非节气日为空串)
 * @param festivals      传统节日(规范化后的名称,已去重)
 * @param holiday        法定节假日/调休信息(无数据为 null)
 * @param weekend        是否周六/周日(补班日仍为 true,由 holiday.type 表达「班」)
 */
public record CalendarDay(
        LocalDate date,
        int lunarMonth,
        int lunarDay,
        String lunarText,
        String lunarMonthText,
        boolean leapMonth,
        String term,
        List<String> festivals,
        HolidayInfo holiday,
        boolean weekend) {

    /**
     * 法定节假日/调休信息。
     *
     * @param type HOLIDAY 放假 / WORKDAY 补班
     * @param name 名称(如 国庆节、春节补班)
     */
    public record HolidayInfo(String type, String name) {
    }
}
