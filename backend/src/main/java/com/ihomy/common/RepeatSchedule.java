package com.ihomy.common;

import cn.hutool.core.date.ChineseDate;

import java.time.LocalDate;

/**
 * 提醒重复规则:判断某条提醒在「某一天」是否应触发。
 * 一次性看基准日期是否当天;每日恒真;每周比星期;每月比日号;
 * 每年比阳历月日,或农历每月日(闰月按 is_leap 严格匹配)。
 * 提醒的到点通知、今日聚合与日程区间展开共用这一份口径,禁止在 Service 另写一套。
 */
public final class RepeatSchedule {

    private RepeatSchedule() {
    }

    /**
     * 旧签名(阳历口径):一次性/每日/每周/每月与阳历每年。
     *
     * @param repeatType ONCE/DAILY/WEEKLY/MONTHLY/YEARLY(空按 ONCE)
     * @param baseDate   基准日期:一次性=触发当天,每周=星期基准,每月=日号基准,每年=锚定公历日
     * @param today      被判定的当天
     * @return 该提醒今天是否应触发;基准日期缺失时返回 false
     */
    public static boolean dueToday(String repeatType, LocalDate baseDate, LocalDate today) {
        return dueToday(repeatType, baseDate, today, null, null, null, null);
    }

    /**
     * 完整签名:每年重复支持农历。
     *
     * @param repeatType ONCE/DAILY/WEEKLY/MONTHLY/YEARLY(空按 ONCE)
     * @param baseDate   基准日期(一次性/每周/每月/阳历每年用)
     * @param today      被判定的当天
     * @param calendar   solar/lunar(空或其它值按阳历);仅每年重复时有意义
     * @param lunarMonth 农历月(1-12,农历每年时必填)
     * @param lunarDay   农历日(1-30,农历每年时必填)
     * @param isLeap     农历是否闰月:1 是,其它否;为 1 而当天不在该闰月则不触发
     * @return 该提醒今天是否应触发;所需字段缺失时返回 false
     */
    public static boolean dueToday(String repeatType, LocalDate baseDate, LocalDate today,
                                  String calendar, Integer lunarMonth, Integer lunarDay, Integer isLeap) {
        if (baseDate == null || today == null) return false;
        String type = repeatType == null ? DictConst.REPEAT_ONCE : repeatType;
        return switch (type) {
            case DictConst.REPEAT_DAILY -> true;
            case DictConst.REPEAT_WEEKLY -> baseDate.getDayOfWeek() == today.getDayOfWeek();
            // 日号精确比对:基准 31 号在 30 天月份不触发(与既有行为一致)
            case DictConst.REPEAT_MONTHLY -> baseDate.getDayOfMonth() == today.getDayOfMonth();
            case DictConst.REPEAT_YEARLY -> dueYearly(baseDate, today, calendar, lunarMonth, lunarDay, isLeap);
            default -> baseDate.equals(today);
        };
    }

    /**
     * 每年重复:阳历比月日(2/29 只在闰年当天触发,不顺延到 2/28);
     * 农历比农历月日,闰月标记严格匹配(设定闰四月时只认闰四月那一天)。
     * Hutool 的 getMonth() 对闰月返回 +1 后的月号,先按 isLeapMonth 还原再比。
     */
    private static boolean dueYearly(LocalDate baseDate, LocalDate today, String calendar,
                                     Integer lunarMonth, Integer lunarDay, Integer isLeap) {
        if (DictConst.CALENDAR_LUNAR.equalsIgnoreCase(calendar)) {
            if (lunarMonth == null || lunarDay == null) return false;
            ChineseDate chinese = new ChineseDate(today);
            boolean leapToday = chinese.isLeapMonth();
            boolean wantLeap = isLeap != null && isLeap == 1;
            int monthToday = leapToday ? chinese.getMonth() - 1 : chinese.getMonth();
            return leapToday == wantLeap
                    && monthToday == lunarMonth
                    && chinese.getDay() == lunarDay;
        }
        return baseDate.getMonthValue() == today.getMonthValue()
                && baseDate.getDayOfMonth() == today.getDayOfMonth();
    }
}
