package com.ihomy.common;

import cn.hutool.core.date.ChineseDate;

import java.time.DateTimeException;
import java.time.LocalDate;

/**
 * 纪念日日期计算:算出每年重复纪念日「下一次」落在的公历日期。
 * 阳历今年已过则顺延下一年;农历今年/明年各试一次(闰月该年缺失则跳过)。
 * 首页倒计时、今日页签重要日期、提前提醒调度共用这一份口径,别在前端或别处重算。
 */
public final class AnniversaryDate {

    private AnniversaryDate() {
    }

    /**
     * @param calendar solar/lunar(空或其它值按阳历)
     * @param month    月份(农历为农历月)
     * @param day      日期
     * @param isLeap   农历是否闰月:1 是,其它否
     * @param today    基准日
     * @return 不早于 today 的下一次日期;月份/日期缺失或历法无法换算时返回 null
     */
    public static LocalDate nextOccurrence(String calendar, Integer month, Integer day, Integer isLeap, LocalDate today) {
        if (today == null) return null;
        if ("lunar".equalsIgnoreCase(calendar)) {
            return nextLunar(month, day, isLeap, today);
        }
        return nextSolar(month, day, today);
    }

    /** 阳历日期:今年已过则顺延到下一年;非法日期(如平年 2/29)返回 null */
    private static LocalDate nextSolar(Integer month, Integer day, LocalDate today) {
        if (month == null || day == null || month < 1 || month > 12 || day < 1 || day > 31) return null;
        LocalDate candidate;
        try {
            candidate = LocalDate.of(today.getYear(), month, day);
        } catch (DateTimeException ex) {
            return null;
        }
        if (candidate.isBefore(today)) {
            candidate = candidate.plusYears(1);
        }
        return candidate;
    }

    /** 农历日期:今年与明年各尝试一次,取首个不早于今天的公历日期;闰月无此年则跳过 */
    private static LocalDate nextLunar(Integer month, Integer day, Integer isLeap, LocalDate today) {
        if (month == null || day == null || month < 1 || month > 12 || day < 1 || day > 30) return null;
        boolean leap = isLeap != null && isLeap == 1;
        for (int lunarYear = today.getYear(); lunarYear <= today.getYear() + 1; lunarYear++) {
            try {
                ChineseDate cd = new ChineseDate(lunarYear, month, day, leap);
                LocalDate solar = LocalDate.of(cd.getGregorianYear(), cd.getGregorianMonth() + 1, cd.getGregorianDay());
                if (!solar.isBefore(today)) {
                    return solar;
                }
            } catch (Exception ex) {
                // 该农历年无此闰月或日期非法,尝试下一年
            }
        }
        return null;
    }
}
