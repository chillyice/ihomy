package com.ihomy.common;

/**
 * 睡眠统计口径:只做「一个月每晚平均睡多久」这一件事,便于单测钉住。
 * 夜睡(kind=NIGHT)与小睡同表,平均应以夜睡为准;某月只有小睡记录时才退回全部会话,
 * 否则「白天补觉」会把月平均拉到几十分钟,看着像数据丢了。
 */
public final class HealthSleepStats {

    private HealthSleepStats() {
    }

    /**
     * @param nightSumMin 夜睡总分钟
     * @param nightCount  夜睡条数
     * @param allSumMin   全部会话总分钟
     * @param allCount    全部会话条数
     * @return 每晚平均分钟(四舍五入);完全没有数据返回 null,调用方按「没数据」处理
     */
    public static Integer nightlyAvg(Integer nightSumMin, Integer nightCount,
                                     Integer allSumMin, Integer allCount) {
        int nSum = nz(nightSumMin);
        int nCnt = nz(nightCount);
        if (nCnt > 0) return Math.round((float) nSum / nCnt);
        int aSum = nz(allSumMin);
        int aCnt = nz(allCount);
        if (aCnt > 0) return Math.round((float) aSum / aCnt);
        return null;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
