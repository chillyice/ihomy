package com.ihomy.common;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 记账周期模板的日期推算(纯函数,唯一来源,禁止在 Service/前端另写一套)。
 *
 * 规则:
 * <ul>
 *   <li>DAILY:从起始日起每天一次;</li>
 *   <li>WEEKLY:与起始日同一个星期几,每 7 天一次;</li>
 *   <li>MONTHLY:每月与起始日同一个日号,<b>短月取月末</b>(起始 31 号 → 2 月 28/29 号);</li>
 *   <li>YEARLY:每年与起始日同月同日,<b>2/29 在平年取 2/28</b>。</li>
 * </ul>
 *
 * 生成范围 = (已生成日, 今天] ∩ [起始日, 结束日];已生成日为空表示从未生成,从起始日起算。
 * 停机补跑最多回溯 {@link #MAX_BACKFILL_DAYS} 天:更早的期次不再补插,但已生成日仍推进到最后
 * 一期,避免以后每次都重复命中同一段历史。
 */
public final class BookRecurringSchedule {

    public static final String PERIOD_DAILY = "DAILY";
    public static final String PERIOD_WEEKLY = "WEEKLY";
    public static final String PERIOD_MONTHLY = "MONTHLY";
    public static final String PERIOD_YEARLY = "YEARLY";

    /** 停机补跑上限:最多补 90 天内的期次(含今天) */
    public static final int MAX_BACKFILL_DAYS = 90;

    /** 单次推算的安全上限,防止异常数据把定时任务拖死 */
    private static final int SAFETY_LIMIT = 10_000;

    private BookRecurringSchedule() {
    }

    /** 周期归一:未知值按每月处理(与表默认值一致) */
    public static String normalizePeriod(String period) {
        if (period == null) return PERIOD_MONTHLY;
        return switch (period.trim().toUpperCase()) {
            case PERIOD_DAILY -> PERIOD_DAILY;
            case PERIOD_WEEKLY -> PERIOD_WEEKLY;
            case PERIOD_YEARLY -> PERIOD_YEARLY;
            default -> PERIOD_MONTHLY;
        };
    }

    /** 一次推算结果:待插入的日期 + 新的「已生成到哪天」 */
    public record Occurrences(List<LocalDate> dates, LocalDate lastGeneratedDate) {

        static final Occurrences EMPTY = new Occurrences(List.of(), null);

        public boolean isEmpty() {
            return dates.isEmpty();
        }
    }

    /**
     * 推算本次应生成的期次。
     *
     * @param period            周期(未知值按每月)
     * @param startDate         起始日(周期锚点),空则无事可做
     * @param endDate           结束日(含当天),空=长期有效
     * @param lastGeneratedDate 已生成到哪一天,空=从未生成
     * @param today             今天(生成上限)
     * @return 待插入日期(已按补跑上限裁剪)与应推进到的最后期次
     */
    public static Occurrences due(String period, LocalDate startDate, LocalDate endDate,
                                  LocalDate lastGeneratedDate, LocalDate today) {
        if (startDate == null || today == null) return Occurrences.EMPTY;

        LocalDate end = endDate == null ? today : (endDate.isBefore(today) ? endDate : today);
        if (end.isBefore(startDate)) return Occurrences.EMPTY;

        // 起点:已生成日的次日;从未生成则从起始日开始
        LocalDate from = lastGeneratedDate == null
                ? startDate
                : (lastGeneratedDate.isBefore(startDate) ? startDate : lastGeneratedDate.plusDays(1));
        if (from.isAfter(end)) return Occurrences.EMPTY;

        String p = normalizePeriod(period);
        LocalDate cursor = firstOnOrAfter(p, startDate, from);
        if (cursor == null || cursor.isAfter(end)) return Occurrences.EMPTY;

        LocalDate windowStart = today.minusDays(MAX_BACKFILL_DAYS - 1L);
        List<LocalDate> all = new ArrayList<>();
        List<LocalDate> capped = new ArrayList<>();
        int guard = 0;
        while (cursor != null && !cursor.isAfter(end) && guard++ < SAFETY_LIMIT) {
            all.add(cursor);
            if (!cursor.isBefore(windowStart)) capped.add(cursor);
            cursor = next(p, startDate, cursor);
        }
        if (all.isEmpty()) return Occurrences.EMPTY;
        // 已生成日推进到最后一次命中,即使它的部分期次因超出补跑窗口而未插入
        return new Occurrences(capped, all.get(all.size() - 1));
    }

    /**
     * 下一次生成日(after 之后的下一个周期日),供列表展示「下次」;超过结束日返回 null。
     *
     * @param after 已生成日或今天(调用方传「已生成日与昨天的较晚者」即可得到下次)
     */
    public static LocalDate nextAfter(String period, LocalDate startDate, LocalDate endDate, LocalDate after) {
        if (startDate == null) return null;
        LocalDate from = (after == null || after.isBefore(startDate)) ? startDate : after.plusDays(1);
        if (endDate != null && from.isAfter(endDate)) return null;
        LocalDate cursor = firstOnOrAfter(normalizePeriod(period), startDate, from);
        if (cursor == null) return null;
        return endDate != null && cursor.isAfter(endDate) ? null : cursor;
    }

    /** 从 from 起(含)的第一个周期日;基准日已过则取基准日 */
    static LocalDate firstOnOrAfter(String period, LocalDate anchor, LocalDate from) {
        if (!from.isAfter(anchor)) return anchor;
        return switch (period) {
            case PERIOD_DAILY -> from;
            case PERIOD_WEEKLY -> {
                long days = ChronoUnit.DAYS.between(anchor, from);
                long steps = (days + 6) / 7;
                yield anchor.plusDays(steps * 7);
            }
            case PERIOD_YEARLY -> {
                LocalDate candidate = yearlyCandidate(anchor, from.getYear());
                yield candidate.isBefore(from) ? yearlyCandidate(anchor, from.getYear() + 1) : candidate;
            }
            default -> {
                LocalDate candidate = monthlyCandidate(anchor, from);
                yield candidate.isBefore(from) ? monthlyCandidate(anchor, from.plusMonths(1)) : candidate;
            }
        };
    }

    /** 下一个周期日(严格大于 current);到达上限时返回 null */
    static LocalDate next(String period, LocalDate anchor, LocalDate current) {
        return switch (period) {
            case PERIOD_DAILY -> current.plusDays(1);
            case PERIOD_WEEKLY -> current.plusDays(7);
            case PERIOD_YEARLY -> {
                LocalDate next = yearlyCandidate(anchor, current.getYear() + 1);
                yield next.isAfter(current) ? next : yearlyCandidate(anchor, current.getYear() + 2);
            }
            default -> {
                LocalDate next = monthlyCandidate(anchor, current.plusMonths(1));
                yield next.isAfter(current) ? next : monthlyCandidate(anchor, current.plusMonths(2));
            }
        };
    }

    /** 某月的周期日:日号取 min(锚点日号, 当月天数) —— 31 号在短月落到月末 */
    private static LocalDate monthlyCandidate(LocalDate anchor, LocalDate monthAnyDay) {
        int day = Math.min(anchor.getDayOfMonth(), monthAnyDay.lengthOfMonth());
        return monthAnyDay.withDayOfMonth(day);
    }

    /** 某年的周期日:月日取 min(锚点日, 当年该月天数) —— 2/29 在平年落到 2/28 */
    private static LocalDate yearlyCandidate(LocalDate anchor, int year) {
        int month = anchor.getMonthValue();
        int day = Math.min(anchor.getDayOfMonth(), java.time.YearMonth.of(year, month).lengthOfMonth());
        return LocalDate.of(year, month, day);
    }
}
