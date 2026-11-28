package com.ihomy.garden.core;

import com.ihomy.garden.content.SolarTermDef;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 节气事件排期与效果(核心纯函数,详细设计《节气事件与成长分段》§1/§2)。
 * 24 节气按公历近似日轮转,事件窗 = 交接日起到下一交接日;效果映射表在代码内(玩法稿 §14.4),
 * JSON 只当排期数据。no-op 事件(EQUINOX/DEW_MOIST)显式存在——机制落地时只改一行映射。
 * dayOffset 是测试夹具(settings.day_offset,正式世界不写):事件日期视图 = 真实日期 + offset,
 * 只影响成长/卖价/缺水路径,不影响便签/信件/冷却的真实时间。
 */
public final class GardenSolarEvents {

    private GardenSolarEvents() {
    }

    /**
     * 事件效果(基点):indoorBp / outdoorBp 分别是室内与室外的成长速率;
     * priceBp 收获时点乘卖价;noWater = 梅雨免浇水;hotHalf = 盛夏缺水阈值减半。
     */
    public record Effect(int indoorBp, int outdoorBp, int priceBp, boolean noWater, boolean hotHalf) {

        public static final Effect NONE = new Effect(100, 100, 100, false, false);
    }

    private static final Map<String, Effect> EFFECTS = Map.ofEntries(
            Map.entry("SPRING_PLOWING", new Effect(200, 200, 100, false, false)),
            Map.entry("SPRING_PLOWING_MINOR", new Effect(150, 150, 100, false, false)),
            Map.entry("PLUM_RAIN", new Effect(100, 100, 100, true, false)),
            Map.entry("EQUINOX", Effect.NONE),
            Map.entry("LONG_SUMMER", new Effect(100, 115, 100, false, false)),
            Map.entry("MIDSUMMER", new Effect(100, 100, 100, false, true)),
            Map.entry("HARVEST_SEASON", new Effect(100, 100, 110, false, false)),
            Map.entry("HARVEST_PEAK", new Effect(100, 100, 120, false, false)),
            Map.entry("DEW_MOIST", Effect.NONE),
            Map.entry("FROST_WARNING", new Effect(100, 100, 110, false, false)),
            Map.entry("WINTER_ENTRY", new Effect(100, 80, 100, false, false)),
            Map.entry("DORMANCY", new Effect(100, 50, 100, false, false)),
            Map.entry("COLD_WAVE", new Effect(100, 0, 100, false, false)));

    /** 事件排期视图(不可变) */
    public static final class Schedule {
        private final List<SolarTermDef> terms;
        private final int dayOffset;

        private Schedule(List<SolarTermDef> terms, int dayOffset) {
            this.terms = terms;
            this.dayOffset = dayOffset;
        }

        /** 某时刻所在事件窗的效果(真实时刻 → 显示日期 = 日期 + offset) */
        public Effect effectAt(LocalDateTime at) {
            return effectOn(at.toLocalDate());
        }

        /** 某显示日所在事件窗的效果 */
        public Effect effectOn(LocalDate realDate) {
            SolarTermDef term = termAt(realDate.plusDays(dayOffset));
            String code = term == null ? null : term.event();
            return code == null ? Effect.NONE : EFFECTS.getOrDefault(code, Effect.NONE);
        }

        /** 成长速率基点;sheltered = 室内或大棚(休眠/寒潮/长夏/入冬只打室外) */
        public int growthBp(LocalDateTime at, boolean sheltered) {
            Effect e = effectAt(at);
            return sheltered ? e.indoorBp() : e.outdoorBp();
        }

        /** 收获时点卖价基点(立秋/霜降 ×110、秋分 ×120,其余 100) */
        public int priceBp(LocalDate realToday) {
            return effectOn(realToday).priceBp();
        }

        /** 梅雨窗:天公浇水,thirsty 恒 false */
        public boolean noWater(LocalDateTime at) {
            return effectAt(at).noWater();
        }

        /** 盛夏窗:缺水阈值减半(1 = 不减) */
        public int thirstyDivisor(LocalDateTime at) {
            return effectAt(at).hotHalf() ? 2 : 1;
        }

        /** at 所在事件窗的下一个真实时刻边界(分段积分/前瞻用;恒严格晚于 at) */
        public LocalDateTime nextBoundary(LocalDateTime at) {
            LocalDate shown = at.toLocalDate().plusDays(dayOffset);
            SolarTermDef term = termAt(shown);
            if (term == null) {
                return at.plusDays(1);
            }
            LocalDate endShown = nextTermStartDate(termStartInCycle(shown, term));
            return LocalDateTime.of(endShown.minusDays(dayOffset), LocalTime.MIN);
        }

        /** 显示日落在哪个节气窗(窗 = 交接日起到下一交接日;查不到返回 null = 空窗) */
        public SolarTermDef termAt(LocalDate shown) {
            for (SolarTermDef term : terms) {
                LocalDate start = termStartInCycle(shown, term);
                if (!shown.isBefore(start) && shown.isBefore(nextTermStartDate(start))) {
                    return term;
                }
            }
            return null;
        }

        /** 该节气在本轮周期里 ≤ shown 的最近交接日 */
        private LocalDate termStartInCycle(LocalDate shown, SolarTermDef term) {
            LocalDate candidate = LocalDate.of(shown.getYear(), term.month(), term.day());
            if (candidate.isAfter(shown)) {
                return candidate.minusYears(1);
            }
            return candidate;
        }

        /** 某交接日的下一个节气交接日(同年找不到就跨年取最近的) */
        private LocalDate nextTermStartDate(LocalDate start) {
            LocalDate best = null;
            for (SolarTermDef term : terms) {
                LocalDate d = LocalDate.of(start.getYear(), term.month(), term.day());
                if (d.isAfter(start) && (best == null || d.isBefore(best))) {
                    best = d;
                }
            }
            if (best == null) {
                for (SolarTermDef term : terms) {
                    LocalDate d = LocalDate.of(start.getYear() + 1, term.month(), term.day());
                    if (best == null || d.isBefore(best)) {
                        best = d;
                    }
                }
            }
            return best == null ? start.plusDays(15) : best;
        }
    }

    /** 构建排期;节气 code 重复即失败(配置完整性优先) */
    public static Schedule schedule(List<SolarTermDef> terms, int dayOffset) {
        Map<String, Integer> seen = new HashMap<>();
        for (SolarTermDef term : terms) {
            seen.merge(term.code(), 1, Integer::sum);
        }
        if (seen.size() != terms.size()) {
            throw new IllegalStateException("solar_terms.json 节气 code 重复");
        }
        return new Schedule(List.copyOf(terms), dayOffset);
    }
}
