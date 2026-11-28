package com.ihomy.garden.core;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 天气事件分段积分单测(详细设计《天气事件与气温双轨》§2):
 * 事件速率只作用于今日 0 点起的时段(不追溯历史天气);冻结优先;AUTO_WATER 压制 thirsty;室内免疫由调用方映射
 * (weatherEventOf 对室内/大棚传 outdoor=false,核心只认 outdoor 布尔)。
 * 固定时刻、确定性,不依赖真实天气。
 */
class GardenWeatherEventsTest {

    private static final List<String> POT = List.of("POT");
    private static final List<String> BOX = List.of("BOX");
    private static final List<String> ALL_SEASONS = List.of("SPRING", "SUMMER", "AUTUMN", "WINTER");

    private static final LocalDateTime PLANTED = LocalDate.of(2026, 11, 26).atTime(9, 0); // 前天 09:00
    private static final LocalDateTime NOW = LocalDate.of(2026, 11, 28).atTime(15, 0);   // 今天 15:00(第二天)
    private static final LocalDateTime WATERED = LocalDate.of(2026, 11, 28).atTime(9, 0); // 今天 09:00 浇过

    /** CATGRASS 后院(FIELD 错配 70bp、未备 90bp、全季 100bp):54h 无 thirsty → prm 3240 → grown 2041 */
    private GardenGrowth.GrowthView eval(String condition, Integer tempMean, boolean outdoor,
                                         LocalDateTime waterRef, GardenSolarEvents.Schedule events,
                                         GardenWeatherEvents.Event weatherEvent) {
        return GardenGrowth.evaluate(24, POT, BOX, "NORMAL", ALL_SEASONS,
                100, 30, "AUTUMN", "BACKYARD", false, false, "FIELD",
                PLANTED, waterRef, 0, NOW, events, weatherEvent);
    }

    @Test
    void 无事件基线() {
        GardenGrowth.GrowthView v = eval(null, null, true, WATERED, null, null);
        assertEquals(2041L, v.progressMinutes()); // 3240 × 0.7 × 0.9
        assertFalse(v.thirsty());
    }

    @Test
    void 干旱事件只罚今日时段() {
        GardenWeatherEvents.Event drought = GardenWeatherEvents.evaluate("clear", 10, true, PLANTED, NOW);
        assertEquals("DROUGHT", drought.code());
        assertEquals(70, drought.rateBp());
        assertEquals(LocalDate.of(2026, 11, 28).atStartOfDay(), drought.since());
        // 昨天 39h 中性 + 今天 15h ×0.7 → prm = 2340 + 630 = 2970 → grown ×0.7×0.9 = 1871
        GardenGrowth.GrowthView v = eval("clear", 10, true, PLANTED, null, drought);
        assertEquals(1871L, v.progressMinutes());
    }

    @Test
    void 霜冻冻结今日时段() {
        GardenWeatherEvents.Event frost = GardenWeatherEvents.evaluate("cloud", -3, true, WATERED, NOW);
        assertEquals("FROST", frost.code());
        assertEquals(0, frost.rateBp());
        GardenGrowth.GrowthView out = eval("cloud", -3, true, WATERED, null, frost);
        // 昨天 39h 中性(prm 2340)→ grown ×0.7×0.9 = 1474;今天冻结
        assertEquals(1474L, out.progressMinutes());
    }

    @Test
    void 霜冻优先于干旱且自动补水压制thirsty() {
        GardenWeatherEvents.Event frost = GardenWeatherEvents.evaluate("clear", -1, true, PLANTED, NOW);
        assertEquals("FROST", frost.code()); // 晴 + ≤0℃:冻结优先于干旱
        GardenWeatherEvents.Event rain = GardenWeatherEvents.evaluate("rain", 8, true, PLANTED, NOW);
        assertEquals("AUTO_WATER", rain.code());
        assertTrue(rain.suppressThirsty());
        GardenGrowth.GrowthView v = eval("rain", 8, true, PLANTED, null, rain); // 54h 未浇,本该很渴
        assertFalse(v.thirsty()); // 天在浇水:thirsty 恒 false
        assertEquals(2041L, v.progressMinutes()); // 事件 bp 100 → 全速(不与内部缺水双罚)
    }

    @Test
    void 室内免疫由调用方映射核心只认outdoor布尔() {
        assertNull(GardenWeatherEvents.evaluate("clear", -2, false, WATERED, NOW)); // outdoor=false → 无事件
        // greenhouse 的免疫在 weatherEventOf 里映射为 outdoor=false;核心对 outdoor=true 一律冻
        GardenWeatherEvents.Event frost = GardenWeatherEvents.evaluate("cloud", -3, true, WATERED, NOW);
        assertEquals("FROST", frost.code());
    }

    @Test
    void 无天气数据无事件() {
        assertNull(GardenWeatherEvents.evaluate(null, 8, true, WATERED, NOW));
    }

    // ---- 气温双轨:逐作物耐温区间(玩法稿 §8.1)----

    /** 与 eval 同参,但带气温胁迫乘子(24h 作物 / 后院 / 错配 / 未备 / 全季) */
    private GardenGrowth.GrowthView evalWithStress(int stressBp) {
        return GardenGrowth.evaluate(24, POT, BOX, "NORMAL", ALL_SEASONS,
                100, 30, "AUTUMN", "BACKYARD", false, false, "FIELD",
                PLANTED, WATERED, 0, NOW, null, null, stressBp);
    }

    @Test
    void 气温胁迫乘子与胁迫码() {
        // 越界两侧
        assertEquals(60, GardenWeatherEvents.tempStressBp(2, 5, 30, true));
        assertEquals("COLD_STRESS", GardenWeatherEvents.stressCode(60));
        assertEquals(70, GardenWeatherEvents.tempStressBp(35, 5, 30, true));
        assertEquals("HEAT_STRESS", GardenWeatherEvents.stressCode(70));
        assertNull(GardenWeatherEvents.stressCode(100));
        // 区间内(含端点)不罚
        assertEquals(100, GardenWeatherEvents.tempStressBp(5, 5, 30, true));
        assertEquals(100, GardenWeatherEvents.tempStressBp(30, 5, 30, true));
        // 室内/大棚免疫由调用方传 outdoor=false
        assertEquals(100, GardenWeatherEvents.tempStressBp(2, 5, 30, false));
        assertEquals(100, GardenWeatherEvents.tempStressBp(35, 5, 30, false));
        // 日均温 ≤0 一律交 FROST(冻结比 ×0.6 更狠,不叠加);无温度数据不判
        assertEquals(100, GardenWeatherEvents.tempStressBp(0, 5, 30, true));
        assertEquals(100, GardenWeatherEvents.tempStressBp(-3, 5, 30, true));
        assertEquals(100, GardenWeatherEvents.tempStressBp(null, 5, 30, true));
        // 只配一侧也能判(另一侧 null 不判)
        assertEquals(60, GardenWeatherEvents.tempStressBp(1, 2, null, true));
        assertEquals(100, GardenWeatherEvents.tempStressBp(99, null, null, true));
    }

    @Test
    void 气温胁迫乘在成长上并拉长成熟时间() {
        GardenGrowth.GrowthView none = evalWithStress(100);
        GardenGrowth.GrowthView cold = evalWithStress(60);
        assertEquals(2041L, none.progressMinutes());          // 3240 × 0.7 × 0.9
        assertEquals(1224L, cold.progressMinutes());          // 3240 ×0.6 × 0.7 × 0.9(整数分步取整)
        // 24h 作物在 54h 内两档都已成熟;换 96h 作物看「还要长多久」按 1/0.6 拉长
        GardenGrowth.GrowthView slowNone = GardenGrowth.evaluate(96, POT, BOX, "NORMAL", ALL_SEASONS,
                100, 30, "AUTUMN", "BACKYARD", false, false, "FIELD",
                PLANTED, WATERED, 0, NOW, null, null, 100);
        GardenGrowth.GrowthView slowCold = GardenGrowth.evaluate(96, POT, BOX, "NORMAL", ALL_SEASONS,
                100, 30, "AUTUMN", "BACKYARD", false, false, "FIELD",
                PLANTED, WATERED, 0, NOW, null, null, 60);
        assertEquals(5760L - 2041L, slowNone.minutesToHarvest());
        assertEquals((5760L - 1224L) * 100 / 60, slowCold.minutesToHarvest()); // 7560
        // 传 0/负数按「无胁迫」兜底(不把地冻住)
        assertEquals(2041L, evalWithStress(0).progressMinutes());
    }
}
