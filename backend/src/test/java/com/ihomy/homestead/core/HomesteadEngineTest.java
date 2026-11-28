package com.ihomy.homestead.core;

import com.ihomy.homestead.HomesteadConst;
import com.ihomy.homestead.content.HomesteadContent;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 结算引擎:产出与经验、升级换档、材料耗尽自停、24h 封顶、时钟回拨、稀有计数、整数确定性。
 * 全部用内容表里的真实条目,避免单测自带一套假数值。
 */
class HomesteadEngineTest {

    private static final long T0 = 1_700_000_000_000L;
    private static final HomesteadContent CONTENT = new HomesteadContent();

    private HomesteadEngine.SettleInput in(String actionCode, int level, long xp, int tier,
                                          long seq, long progressMs, long elapsedMs,
                                          Map<String, Long> materials) {
        return new HomesteadEngine.SettleInput(
                CONTENT.action(actionCode), level, xp, tier, seq, progressMs,
                T0, T0 + elapsedMs, materials);
    }

    @Test
    void gatherCompletesItemsAndKeepsRemainder() {
        HomesteadEngine.SettleResult r = HomesteadEngine.settle(
                in("PICK_TWIG", 1, 0, 0, 0, 0, 10_000, Map.of()), CONTENT);
        // 3 秒一件,10 秒 → 3 件 + 余 1 秒
        assertEquals(3, r.completions());
        assertEquals(1000, r.progressMs());
        assertEquals(18, r.xpGained());
        assertEquals(18, r.xpAfter());
        assertEquals(1, r.levelAfter());
        assertEquals(0, r.levelUps());
        assertEquals(Map.of("PINE_TWIG", 3L), r.gained());
        assertTrue(r.consumed().isEmpty());
        assertFalse(r.capped());
        assertFalse(r.stopped());
    }

    @Test
    void leftoverProgressCarriesIntoNextSettle() {
        HomesteadEngine.SettleResult r = HomesteadEngine.settle(
                in("PICK_TWIG", 1, 0, 0, 0, 2500, 600, Map.of()), CONTENT);
        assertEquals(1, r.completions());
        assertEquals(100, r.progressMs());
    }

    @Test
    void levelUpShortensActionTime() {
        // 9 件 × 6 经验 = 54,跨过 1→2 级的 50
        HomesteadEngine.SettleResult r = HomesteadEngine.settle(
                in("PICK_TWIG", 1, 0, 0, 0, 0, 27_000, Map.of()), CONTENT);
        assertEquals(9, r.completions());
        assertEquals(1, r.levelUps());
        assertEquals(2, r.levelAfter());
        assertEquals(4, r.xpAfter());
        assertEquals(0, r.progressMs());
        // 2 级 +1% 速度:3000 × 10000 / 10100 向上取整
        assertEquals(2971, HomesteadEngine.msPerItem(3000, 2, 0));
    }

    @Test
    void stopsWhenMaterialsRunOut() {
        // 柴火饭:每次耗 2 松枝,库存 5 → 只能做 2 次,余下时间不入账
        HomesteadEngine.SettleResult r = HomesteadEngine.settle(
                in("COOK_RICE", 1, 0, 0, 0, 0, 100_000, Map.of("PINE_TWIG", 5L)), CONTENT);
        assertEquals(2, r.completions());
        assertEquals(HomesteadConst.STOP_MATERIALS, r.stopReason());
        assertEquals(0, r.progressMs());
        assertEquals(Map.of("PINE_TWIG", 4L), r.consumed());
        assertEquals(Map.of("RICE_MEAL", 2L), r.gained());
        assertEquals(100_000, r.elapsedMs()); // elapsedMs = 计入结算的离开时长(封顶后),与中途材料耗尽无关
    }

    @Test
    void noMaterialsMeansImmediateStop() {
        HomesteadEngine.SettleResult r = HomesteadEngine.settle(
                in("COOK_RICE", 1, 0, 0, 0, 0, 60_000, Map.of()), CONTENT);
        assertEquals(0, r.completions());
        assertEquals(HomesteadConst.STOP_MATERIALS, r.stopReason());
        assertEquals(0, r.progressMs());
    }

    @Test
    void offlineTimeIsCappedAtTwentyFourHours() {
        long thirtyHours = 30L * 3600L * 1000L;
        HomesteadEngine.SettleResult r = HomesteadEngine.settle(
                in("PICK_TWIG", 1, 0, 0, 0, 0, thirtyHours, Map.of()), CONTENT);
        assertTrue(r.capped());
        assertEquals(HomesteadConst.OFFLINE_CAP_MS, r.elapsedMs());
        // 等级涨了会更快,所以一定多于匀速理论值
        assertTrue(r.completions() > HomesteadConst.OFFLINE_CAP_MS / 3000);
        assertTrue(r.levelAfter() > 1);
    }

    @Test
    void clockRollbackGainsNothingButKeepsProgress() {
        HomesteadEngine.SettleInput rollback = new HomesteadEngine.SettleInput(
                CONTENT.action("PICK_TWIG"), 1, 0, 0, 0, 1200, T0, T0 - 60_000, Map.of());
        HomesteadEngine.SettleResult r = HomesteadEngine.settle(rollback, CONTENT);
        assertEquals(0, r.completions());
        assertEquals(0, r.elapsedMs());
        assertEquals(1200, r.progressMs());
        assertTrue(r.gained().isEmpty());
        assertNull(r.stopReason());
    }

    @Test
    void rareDropFollowsActionCounter() {
        // 松枝:每 30 次给 1 份松脂;seq 28 → 做 2 次正好第 30 次
        HomesteadEngine.SettleResult r = HomesteadEngine.settle(
                in("PICK_TWIG", 1, 0, 0, 28, 0, 6_000, Map.of()), CONTENT);
        assertEquals(2, r.completions());
        assertEquals(30, r.actionSeqAfter());
        assertEquals(Map.of("PINE_TWIG", 2L, "PINE_RESIN", 1L), r.gained());
    }

    @Test
    void rareDropDoesNotFireBeforeInterval() {
        HomesteadEngine.SettleResult r = HomesteadEngine.settle(
                in("PICK_TWIG", 1, 0, 0, 28, 0, 3_000, Map.of()), CONTENT);
        assertEquals(1, r.completions());
        assertEquals(Map.of("PINE_TWIG", 1L), r.gained());
    }

    @Test
    void toolTierShortensActionTime() {
        assertEquals(2143, HomesteadEngine.msPerItem(3000, 1, 5));
        HomesteadEngine.SettleResult r = HomesteadEngine.settle(
                in("PICK_TWIG", 1, 0, 5, 0, 0, 3000, Map.of()), CONTENT);
        assertEquals(1, r.completions());
        assertEquals(857, r.progressMs());
    }

    @Test
    void sameInputAlwaysProducesSameResult() {
        HomesteadEngine.SettleInput input = in("PICK_BERRY", 3, 120, 2, 17, 700, 3_600_000, Map.of());
        HomesteadEngine.SettleResult a = HomesteadEngine.settle(input, CONTENT);
        HomesteadEngine.SettleResult b = HomesteadEngine.settle(input, CONTENT);
        assertEquals(a, b);
    }

    @Test
    void pendingXpIsPromotedEvenWithNoElapsedTime() {
        // 存档里经验已够但等级没推上去(旧内容曲线变更等):结算先补升级,再按新等级算
        HomesteadEngine.SettleResult r = HomesteadEngine.settle(
                in("PICK_TWIG", 1, 60, 0, 0, 0, 0, Map.of()), CONTENT);
        assertEquals(0, r.completions());
        assertEquals(2, r.levelAfter());
        assertEquals(10, r.xpAfter());
    }
}
