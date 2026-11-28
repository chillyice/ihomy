package com.ihomy.garden.core;

import com.ihomy.garden.GardenConst;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 成长纯函数:阶段阈值、照料加成、备盆修正、缺水减速与枯萎冻结、季节软化、产量与错季卖价。
 * 同一输入两次计算必须完全一致(NFR-02 确定性)。
 */
class GardenGrowthTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 28, 8, 0);
    private static final List<String> BOX_PREF = List.of("BOX");
    private static final List<String> SPRING_AUTUMN = List.of("SPRING", "AUTUMN");
    private static final List<String> ALL = List.of("SPRING", "SUMMER", "AUTUMN", "WINTER");

    /** 辅助:作物偏好 BOX、地块 POT → 土壤「兼容」×1.0,让倍率期望值保持可读 */
    private GardenGrowth.GrowthView grow(int durationHours, List<String> seasons, String season,
                                         boolean prepared, LocalDateTime plantedAt,
                                         LocalDateTime lastWateredAt, int careBoost, LocalDateTime now) {
        return GardenGrowth.evaluate(24, BOX_PREF, List.of("POT"), "NORMAL", seasons, 100, 30, season,
                "WINDOWSILL", true, prepared, "POT", plantedAt, lastWateredAt, careBoost, now);
    }

    @Test
    void freshSowIsStageSeedWithZeroProgress() {
        GardenGrowth.GrowthView v = grow(24, SPRING_AUTUMN, "WINTER", true, T0, null, 0, T0);
        assertEquals(0, v.stageIndex());
        assertEquals("SEED", v.stageName());
        assertEquals(0, v.progressMinutes());
        assertFalse(v.harvestable());
    }

    @Test
    void careBoostAddsMinutesDirectly() {
        GardenGrowth.GrowthView v = grow(24, SPRING_AUTUMN, "SPRING", true, T0, null, 240, T0);
        assertEquals(240, v.progressMinutes());
    }

    @Test
    void preparedIsFasterThanUnprepared() {
        LocalDateTime now = T0.plusHours(10);
        GardenGrowth.GrowthView prepared = grow(24, SPRING_AUTUMN, "SPRING", true, T0, T0, 0, now);
        GardenGrowth.GrowthView unprepared = grow(24, SPRING_AUTUMN, "SPRING", false, T0, T0, 0, now);
        // 600 分钟 × ×1.05 / ×0.90(本命季、土壤兼容)
        assertEquals(630, prepared.progressMinutes());
        assertEquals(540, unprepared.progressMinutes());
    }

    @Test
    void thirstySlowsAndWiltFreezes() {
        // 浇一次水后不再浇:48h 内全速,48–72h ×0.7,72h 后冻结(已备盆 ×1.05)
        LocalDateTime sow = T0;
        GardenGrowth.GrowthView v30 = grow(24, SPRING_AUTUMN, "SPRING", true, sow, sow, 0, sow.plusHours(30));
        assertEquals(1890, v30.progressMinutes());
        GardenGrowth.GrowthView v60 = grow(24, SPRING_AUTUMN, "SPRING", true, sow, sow, 0, sow.plusHours(60));
        // 48h 全速(2880) + 12h ×0.7(504)= 3384,再 ×1.05
        assertEquals(3553, v60.progressMinutes());
        assertTrue(v60.thirsty());
        GardenGrowth.GrowthView v100 = grow(24, SPRING_AUTUMN, "SPRING", true, sow, sow, 0, sow.plusHours(100));
        // 48h 全速 + 24h ×0.7 = 3888,再 ×1.05;72h 线后冻结
        assertEquals(4082, v100.progressMinutes());
        assertEquals(v100.progressMinutes(), grow(24, SPRING_AUTUMN, "SPRING", true, sow, sow, 0,
                sow.plusHours(200)).progressMinutes());
    }

    @Test
    void stageThresholdsFollowPlaybookRatios() {
        LocalDateTime sow = T0;
        // 24h = 1440 分钟;阈值 0/0.1/0.35/0.6/0.85/1.0
        assertEquals(0, grow(24, ALL, "WINTER", true, sow, sow, 0, sow).stageIndex());
        assertEquals(1, grow(24, ALL, "WINTER", true, sow, sow, 0, sow.plusMinutes(150)).stageIndex()); // ≥10%
        assertEquals(2, grow(24, ALL, "WINTER", true, sow, sow, 0, sow.plusMinutes(510)).stageIndex()); // ≥35%
        assertEquals(3, grow(24, ALL, "WINTER", true, sow, sow, 0, sow.plusMinutes(900)).stageIndex()); // ≥60%
        assertEquals(4, grow(24, ALL, "WINTER", true, sow, sow, 0, sow.plusMinutes(1250)).stageIndex()); // ≥85%
        GardenGrowth.GrowthView done = grow(24, ALL, "WINTER", true, sow, sow, 0, sow.plusMinutes(1440));
        assertEquals(5, done.stageIndex());
        assertEquals("FRUIT", done.stageName());
        assertTrue(done.harvestable());
        // 已备盆 ×1.05 会略过成熟线,ratioBp ≥ 10000 即可收
        assertTrue(done.ratioBp() >= 10000);
    }

    @Test
    void indoorSofteningLiftsOffSeasonFloor() {
        // 逆季 0.3 → 室内(窗台)下限 0.5
        int window = GardenGrowth.seasonFactor(SPRING_AUTUMN, 100, 30, "WINTER", "WINDOWSILL", true);
        assertEquals(50, window);
        int outdoor = GardenGrowth.seasonFactor(SPRING_AUTUMN, 100, 30, "WINTER", "BACKYARD", false);
        assertEquals(30, outdoor);
        int terrarium = GardenGrowth.seasonFactor(SPRING_AUTUMN, 100, 30, "WINTER", "TERRARIUM", true);
        assertEquals(70, terrarium);
        int greenhouse = GardenGrowth.seasonFactor(SPRING_AUTUMN, 100, 30, "WINTER", "GREENHOUSE", false);
        assertEquals(100, greenhouse);
        // 本命季不受软化
        assertEquals(100, GardenGrowth.seasonFactor(SPRING_AUTUMN, 100, 30, "SPRING", "WINDOWSILL", true));
    }

    @Test
    void soilMatchingBands() {
        assertEquals(GardenConst.SOIL_PREFERRED_BP, GardenGrowth.soilFactor(List.of("POT"), List.of(), "POT"));
        assertEquals(GardenConst.SOIL_COMPATIBLE_BP, GardenGrowth.soilFactor(List.of("BOX"), List.of("POT"), "POT"));
        assertEquals(GardenConst.SOIL_MISMATCH_BP, GardenGrowth.soilFactor(List.of("BOX"), List.of(), "POT"));
    }

    @Test
    void yieldUsesMidpointTimesSoil() {
        // (2+3) × 1.2 / 2 = 3
        assertEquals(3, GardenGrowth.yieldAmount(2, 3, GardenConst.SOIL_PREFERRED_BP));
        // (1+2) × 0.7 / 2 = 1.05 → 1
        assertEquals(1, GardenGrowth.yieldAmount(1, 2, GardenConst.SOIL_MISMATCH_BP));
    }

    @Test
    void offSeasonSellPriceIsHalved() {
        assertEquals(6, GardenGrowth.sellPrice(6, true));
        assertEquals(3, GardenGrowth.sellPrice(6, false));
        assertEquals(7, GardenGrowth.sellPrice(14, false));
    }

    @Test
    void sameInputSameOutput() {
        LocalDateTime now = T0.plusHours(13);
        GardenGrowth.GrowthView a = grow(24, SPRING_AUTUMN, "WINTER", false, T0, T0.plusHours(1), 120, now);
        GardenGrowth.GrowthView b = grow(24, SPRING_AUTUMN, "WINTER", false, T0, T0.plusHours(1), 120, now);
        assertEquals(a, b);
    }
}
