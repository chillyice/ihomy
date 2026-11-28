package com.ihomy.homestead.core;

import com.ihomy.homestead.HomesteadConst;
import com.ihomy.homestead.content.ExpeditionDef;
import com.ihomy.homestead.content.HomesteadContent;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 探险事件判定:分数口径(等级×权重 + 战力,±10% 抖动)、同趟可复算、
 * 通过给积分与掉落、失败只给保底积分、装备掉落遵守区域品质下限。
 */
class HomesteadExpeditionTest {

    private static final HomesteadContent CONTENT = new HomesteadContent();
    private static final ExpeditionDef.Region HILL = CONTENT.region("HILL_PATH");

    private static Map<String, Integer> levels(int level) {
        Map<String, Integer> map = new HashMap<>();
        CONTENT.skills().forEach(skill -> map.put(skill.code(), level));
        return map;
    }

    @Test
    void sameTripRecomputesIdentically() {
        HomesteadExpedition.Report a = HomesteadExpedition.resolve(CONTENT, HILL, 42L, 9L, levels(30), List.of());
        HomesteadExpedition.Report b = HomesteadExpedition.resolve(CONTENT, HILL, 42L, 9L, levels(30), List.of());
        assertEquals(a, b);
        assertEquals(HILL.events().size(), a.steps().size());
        assertEquals("HILL_PATH", a.region());
    }

    @Test
    void scoreFollowsLevelWeightAndJitterBand() {
        HomesteadExpedition.Report report = HomesteadExpedition.resolve(CONTENT, HILL, 5L, 1L, levels(30), List.of());
        for (HomesteadExpedition.Step step : report.steps()) {
            int base = 30 * HomesteadConst.EXPEDITION_LEVEL_WEIGHT;
            int low = base * (HomesteadConst.BP_BASE - HomesteadConst.EXPEDITION_JITTER_BP) / HomesteadConst.BP_BASE;
            int high = base * (HomesteadConst.BP_BASE + HomesteadConst.EXPEDITION_JITTER_BP) / HomesteadConst.BP_BASE;
            assertTrue(step.score() >= low && step.score() <= high, "分数越出抖动区间:" + step.score());
        }
    }

    @Test
    void highLevelPassesAndEarnsPoints() {
        HomesteadExpedition.Report report = HomesteadExpedition.resolve(CONTENT, HILL, 1L, 2L, levels(99), List.of());
        assertEquals(HILL.events().size(), report.passedCount());
        int expected = 0;
        for (ExpeditionDef.Event event : HILL.events()) {
            expected += event.points();
        }
        assertEquals(expected, report.points());
        assertTrue(report.points() > 0);
    }

    @Test
    void zeroLevelFailsAndKeepsOnlyFailPoints() {
        HomesteadExpedition.Report report = HomesteadExpedition.resolve(CONTENT, HILL, 1L, 3L, Map.of(), List.of());
        assertEquals(0, report.passedCount());
        assertTrue(report.items().isEmpty());
        assertTrue(report.gear().isEmpty());
        int expected = 0;
        for (ExpeditionDef.Event event : HILL.events()) {
            expected += event.failPoints();
        }
        assertEquals(expected, report.points());
    }

    @Test
    void gearDropsRespectRegionQualityFloor() {
        boolean dropped = false;
        for (long trip = 0; trip < 60 && !dropped; trip++) {
            HomesteadExpedition.Report report = HomesteadExpedition.resolve(
                    CONTENT, HILL, 8L, trip, levels(99), List.of());
            for (HomesteadLoot.Instance gear : report.gear()) {
                dropped = true;
                assertTrue(HILL.gearPool().contains(gear.code()), "装备不在区域池:" + gear.code());
                assertTrue(CONTENT.rarityIndex(gear.quality()) >= CONTENT.rarityIndex(HILL.gearQualityFloor()),
                        "品质低于区域下限:" + gear.quality());
            }
        }
        assertTrue(dropped, "连续 60 趟应至少掉出一件装备");
    }

    @Test
    void wornPowerLiftsScore() {
        List<HomesteadBonus.WornGear> full = List.of(
                new HomesteadBonus.WornGear("FOREST_CROWN", "LEGENDARY", 5, List.of()),
                new HomesteadBonus.WornGear("MOSS_SANDALS", "LEGENDARY", 5, List.of()));
        HomesteadExpedition.Report bare = HomesteadExpedition.resolve(CONTENT, HILL, 6L, 4L, levels(0), List.of());
        HomesteadExpedition.Report armed = HomesteadExpedition.resolve(CONTENT, HILL, 6L, 4L, levels(0), full);
        assertEquals(0, bare.power());
        assertTrue(armed.power() > 0);
        assertTrue(armed.steps().get(0).score() > bare.steps().get(0).score(), "战力应提高判定分");
    }

    @Test
    void reportStepsCarryLootPayload() {
        HomesteadExpedition.Report report = HomesteadExpedition.resolve(CONTENT, HILL, 9L, 5L, levels(99), List.of());
        for (HomesteadExpedition.Step step : report.steps()) {
            assertTrue(step.passed());
            assertNotNull(step.kind());
            assertTrue(step.gear() != null || step.item() != null, "通过的事件应有掉落");
            assertTrue(step.qty() >= 0);
            assertTrue(step.score() >= step.difficulty());
        }
        assertSame(HILL, CONTENT.region(HILL.code()));
    }
}
