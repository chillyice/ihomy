package com.ihomy.common;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 睡眠聚合:上报端一律传「分期区间」,服务端按间隔合并成会话(一晚一来源一行)。
 * 三端(iOS 快捷指令 / Android Health Connect / 后续原生包)共用同一口径,
 * 避免「iOS 算的和 Android 算的不一样」。纯函数,便于单测。
 */
public final class HealthSleepAggregator {

    private HealthSleepAggregator() {
    }

    /** 相邻区间间隔超过 30 分钟视为两场睡眠(中间醒来起床走动) */
    public static final int MAX_GAP_MINUTES = 30;
    /** 短于 3 分钟的会话视为误报(频繁唤醒产生的碎片) */
    public static final int MIN_SESSION_MINUTES = 3;

    public record Interval(LocalDateTime start, LocalDateTime end, String stage) {
    }

    /**
     * 零时长区间(开始 = 结束):Apple 健康在编辑/合并睡眠记录后会留下这种样本,
     * 不是用户填错,上游按「跳过」处理,不进拒收统计。
     */
    public static boolean isZeroLength(LocalDateTime start, LocalDateTime end) {
        return start != null && end != null && start.isEqual(end);
    }

    /** 聚合出的会话:start/end 为入库用的服务器时区时间,睡眠日/夜睡小睡按上报端墙钟时间判定 */
    public record Session(LocalDateTime start, LocalDateTime end,
                          java.time.LocalDate sleepDate, String kind,
                          int totalMin, Integer deepMin, Integer lightMin, Integer remMin, Integer awakeMin,
                          String stageLevel, List<Interval> stages) {
    }

    /**
     * 把区间按时间顺序合并为会话。
     * 分期分钟按「先到先得」游标裁剪累加:同一分钟只归一个分期,重叠区间不重复计时;
     * INBED/UNKNOWN 只延长在床总时长,不占分期游标(否则整夜 INBED 会把内层的 AWAKE 剪没)。
     */
    public static List<Session> aggregate(List<Interval> raw) {
        List<Session> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;

        List<Interval> list = new ArrayList<>();
        for (Interval it : raw) {
            if (it == null || it.start() == null || it.end() == null) continue;
            if (!it.end().isAfter(it.start())) continue;
            list.add(it);
        }
        list.sort(Comparator.comparing(Interval::start));

        List<Interval> group = new ArrayList<>();
        LocalDateTime groupEnd = null;
        for (Interval it : list) {
            if (groupEnd != null && Duration.between(groupEnd, it.start()).toMinutes() > MAX_GAP_MINUTES) {
                addSession(out, group);
                group = new ArrayList<>();
                groupEnd = null;
            }
            group.add(it);
            if (groupEnd == null || it.end().isAfter(groupEnd)) groupEnd = it.end();
        }
        addSession(out, group);
        return out;
    }

    /** @param group 一个会话内的区间(已按 start 排序;入参即上报端墙钟时间,睡眠日/小睡直接按它算) */
    private static void addSession(List<Session> out, List<Interval> group) {
        if (group.isEmpty()) return;
        LocalDateTime start = group.get(0).start();
        LocalDateTime end = start;
        for (Interval it : group) {
            if (it.end().isAfter(end)) end = it.end();
        }
        int totalMin = (int) Duration.between(start, end).toMinutes();
        if (totalMin < MIN_SESSION_MINUTES) return;

        int deep = 0, light = 0, rem = 0, awake = 0;
        boolean hasDeepStage = false, hasLightStage = false, hasRemStage = false, hasAwake = false;
        // 游标裁剪:同一分钟只计一次;INBED/UNKNOWN 不推进游标(只影响在床总时长)
        LocalDateTime cursor = start;
        for (Interval it : group) {
            String stage = it.stage();
            if ("INBED".equals(stage) || "UNKNOWN".equals(stage) || stage == null) continue;
            LocalDateTime from = it.start().isAfter(cursor) ? it.start() : cursor;
            LocalDateTime to = it.end().isAfter(end) ? end : it.end();
            if (!to.isAfter(from)) continue;
            int min = (int) Duration.between(from, to).toMinutes();
            switch (it.stage()) {
                case "DEEP" -> { deep += min; hasDeepStage = true; }
                case "LIGHT" -> { light += min; hasLightStage = true; }
                case "REM" -> { rem += min; hasRemStage = true; }
                case "AWAKE" -> { awake += min; hasAwake = true; }
                default -> { /* INBED/UNKNOWN 只计入在床总时长 */ }
            }
            if (it.end().isAfter(cursor)) cursor = it.end();
        }

        String level = (hasDeepStage || hasLightStage || hasRemStage) ? "FULL"
                : hasAwake ? "SUMMARY" : "NONE";

        out.add(new Session(start, end,
                HealthTime.sleepDateOf(start), HealthTime.sleepKindOf(start),
                totalMin,
                level.equals("FULL") ? deep : null,
                level.equals("FULL") ? light : null,
                level.equals("FULL") ? rem : null,
                level.equals("NONE") ? null : awake,
                level, List.copyOf(group)));
    }
}
