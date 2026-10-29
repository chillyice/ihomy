package com.ihomy.common;

import java.time.LocalDate;

/**
 * 提醒重复规则:判断某条提醒在「今天」是否应触发。
 * 一次性看基准日期是否当天;每日恒真;每周比星期;每月比日号。
 * 提醒的到点通知与「今日」聚合共用这一份口径,避免两套判断。
 */
public final class RepeatSchedule {

    private RepeatSchedule() {
    }

    /**
     * @param repeatType ONCE/DAILY/WEEKLY/MONTHLY(空按 ONCE)
     * @param baseDate   基准日期:一次性=触发当天,每周=星期基准,每月=日号基准
     * @param today      被判定的当天
     * @return 该提醒今天是否应触发;基准日期缺失时返回 false
     */
    public static boolean dueToday(String repeatType, LocalDate baseDate, LocalDate today) {
        if (baseDate == null || today == null) return false;
        return switch (repeatType == null ? DictConst.REPEAT_ONCE : repeatType) {
            case DictConst.REPEAT_DAILY -> true;
            case DictConst.REPEAT_WEEKLY -> baseDate.getDayOfWeek() == today.getDayOfWeek();
            // 日号精确比对:基准 31 号在 30 天月份不触发(与既有行为一致)
            case DictConst.REPEAT_MONTHLY -> baseDate.getDayOfMonth() == today.getDayOfMonth();
            default -> baseDate.equals(today);
        };
    }
}
