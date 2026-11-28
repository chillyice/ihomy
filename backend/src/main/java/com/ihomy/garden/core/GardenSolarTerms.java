package com.ihomy.garden.core;

import com.ihomy.garden.content.SolarTermDef;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 节气推导(纯函数,详细设计 §1):24 节气是公历固定日子,第 k 个节气 =
 * 建号日之后遇到的第 k 个交接点(严格晚于 cycle_start_at),由同一张 24 节气表轮转推出,
 * 不另造数据、无任何一处依赖公历月份或「1 月开始」。日期取常年近似代表日,不做天文计算。
 */
public final class GardenSolarTerms {

    private GardenSolarTerms() {
    }

    /** 节气交接点:def + 日期 */
    public record Occurrence(SolarTermDef term, LocalDate date) {
    }

    /**
     * 自 after(不含)之后的按时间升序交接点序列,取前 limit 个。
     * 每个节气的下一次交接 = 其 (月,日) 在 after 之后的首个出现;跨年由取模轮转覆盖。
     */
    public static List<Occurrence> occurrences(List<SolarTermDef> terms, LocalDate after, int limit) {
        List<Occurrence> out = new ArrayList<>();
        LocalDate cursor = after;
        for (int i = 0; i < limit; i++) {
            Occurrence next = null;
            for (SolarTermDef term : terms) {
                LocalDate candidate = nextOccurrence(term, cursor);
                if (next == null || candidate.isBefore(next.date())) {
                    next = new Occurrence(term, candidate);
                }
            }
            if (next == null) {
                break;
            }
            out.add(next);
            cursor = next.date();
        }
        return out;
    }

    /** 建号后第 k(1-based)个节气之前已交接的个数:occurrence.date <= today */
    public static int termCount(List<Occurrence> occurrences, LocalDate today) {
        int count = 0;
        for (Occurrence o : occurrences) {
            if (!o.date().isAfter(today)) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    /** 单个节气的下一次交接(严格晚于 after) */
    private static LocalDate nextOccurrence(SolarTermDef term, LocalDate after) {
        MonthDay md = MonthDay.of(term.month(), term.day());
        LocalDate sameYear = md.atYear(after.getYear());
        if (sameYear.isAfter(after)) {
            return sameYear;
        }
        return md.atYear(after.getYear() + 1);
    }

    /** 内容自检:24 条且 (月,日) 无重复(启动即失败,配置完整性优先) */
    public static void validate(List<SolarTermDef> terms) {
        if (terms == null || terms.size() != 24) {
            throw new IllegalStateException("solar_terms.json 必须 24 个节气,实际 " + (terms == null ? 0 : terms.size()));
        }
        long distinct = terms.stream().map(t -> MonthDay.of(t.month(), t.day())).distinct().count();
        if (distinct != 24) {
            throw new IllegalStateException("solar_terms.json 节气日期有重复");
        }
        terms.stream().sorted(Comparator.comparing(t -> MonthDay.of(t.month(), t.day())))
                .reduce((a, b) -> b).ifPresent(last -> {
                    // 按 (月,日) 升序:1 月的两个节气排最前,最末是冬至 12-22(跨年由轮转覆盖)
                    if (!"DONGZHI".equals(last.code())) {
                        throw new IllegalStateException("solar_terms.json 排序锚点异常:按 (月,日) 升序最末应为冬至");
                    }
                });
    }
}
