package com.ihomy.garden.core;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 周挑战的周窗口纯函数(玩法稿 §15「周挑战复用任务悬赏机制」):
 * 周一 00:00 起 → 周日夜里结束,领取凭证按 ISO 周编号;轮换取哪几条挑战只由「周一序号」决定,不用随机。
 * 零存储:窗口与轮换都从日历现算,不落表、不落 settings。
 */
public final class GardenWeek {

    /** 1970-01-05(周一)的 epochDay;用它把任意周一映射为连续周序号(floorDiv 对 1970 前也成立) */
    private static final long MONDAY_EPOCH = LocalDate.of(1970, 1, 5).toEpochDay();
    private static final WeekFields ISO = WeekFields.ISO;

    private GardenWeek() {
    }

    /** 本周一(含今天) */
    public static LocalDate start(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** 本周日(= 周一 + 6,与 start 成对下发,前端不必再算) */
    public static LocalDate end(LocalDate date) {
        return start(date).plusDays(6);
    }

    /** 领取凭证键:`2026-W48`(ISO 周年 + 两位补零周号;12-29 属次年第 1 周) */
    public static String key(LocalDate date) {
        int year = date.get(ISO.weekBasedYear());
        int week = date.get(ISO.weekOfWeekBasedYear());
        return String.format(Locale.ROOT, "%d-W%02d", year, week);
    }

    /** 连续周序号(周一为界)——同一周内恒定,轮换与测试夹具都以此为准 */
    public static long index(LocalDate date) {
        return Math.floorDiv(start(date).toEpochDay() - MONDAY_EPOCH, 7);
    }

    /** 本周在窗的挑战:pool 已按 order 排好,从 (index mod size) 起环形连续取 take 个 */
    public static <T> List<T> pick(List<T> pool, LocalDate date, int take) {
        if (pool == null || pool.isEmpty() || take <= 0) {
            return List.of();
        }
        int size = pool.size();
        int n = Math.min(take, size);
        int base = (int) Math.floorMod(index(date), size);
        List<T> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(pool.get((base + i) % size));
        }
        return out;
    }
}
