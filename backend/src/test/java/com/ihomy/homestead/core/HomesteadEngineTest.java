package com.ihomy.homestead.core;

import com.ihomy.homestead.HomesteadConst;
import com.ihomy.homestead.content.HomesteadContent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 结算引擎:产出与经验、升级换档、材料耗尽自停、24h 封顶、时钟回拨、
 * 确定性掷点(稀有/多收/省料)、buff 到期分段、100 级封顶、整数确定性。
 * 全部用内容表里的真实条目,避免单测自带一套假数值。
 */
class HomesteadEngineTest {

    private static final long T0 = 1_700_000_000_000L;
    private static final HomesteadContent CONTENT = new HomesteadContent();
    /** 一次完成在掷点里的切片位置(与引擎一致) */
    private static final int SHIFT_RARE = 0;
    private static final int SHIFT_YIELD = 13;
    private static final int SHIFT_EFFICIENCY = 29;

    private HomesteadEngine.SettleInput in(String actionCode, int level, long xp, int tier,
                                          long seq, long progressMs, long elapsedMs,
                                          Map<String, Long> materials) {
        HomesteadBonus.Bonus bonus = HomesteadBonus.resolve(
                CONTENT, CONTENT.action(actionCode), tier, List.of(), Map.of());
        return new HomesteadEngine.SettleInput(
                CONTENT.action(actionCode), level, xp, seq, progressMs,
                T0, T0 + elapsedMs, materials, bonus);
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
        assertEquals(3L, r.gained().getOrDefault("PINE_TWIG", 0L));
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
        // 柴火饭:每次耗 2 豆,库存 5 → 只能做 2 次,余下时间不入账
        HomesteadEngine.SettleResult r = HomesteadEngine.settle(
                in("COOK_RICE", 1, 0, 0, 0, 0, 100_000, Map.of("BEAN", 5L)), CONTENT);
        assertEquals(2, r.completions());
        assertEquals(HomesteadConst.STOP_MATERIALS, r.stopReason());
        assertEquals(0, r.progressMs());
        assertEquals(Map.of("BEAN", 4L), r.consumed());
        assertEquals(Map.of("RICE_MEAL", 2L), r.gained());
        assertEquals(100_000, r.elapsedMs());
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
                CONTENT.action("PICK_TWIG"), 1, 0, 0, 1200, T0, T0 - 60_000, Map.of());
        HomesteadEngine.SettleResult r = HomesteadEngine.settle(rollback, CONTENT);
        assertEquals(0, r.completions());
        assertEquals(0, r.elapsedMs());
        assertEquals(1200, r.progressMs());
        assertTrue(r.gained().isEmpty());
        assertNull(r.stopReason());
    }

    @Test
    void rareDropsFollowDeterministicRolls() {
        // 稀有次数必须精确等于「掷点落在 320bp 内」的次数(离线重算可复现)
        HomesteadEngine.SettleResult r = HomesteadEngine.settle(
                in("PICK_TWIG", 1, 0, 0, 0, 0, 600_000, Map.of()), CONTENT);
        long n = r.completions();
        assertTrue(n >= 200);
        long expect = hits("PICK_TWIG", 0, n, SHIFT_RARE, 320);
        assertEquals(expect, r.gained().getOrDefault("PINE_RESIN", 0L));
    }

    @Test
    void yieldBonusAddsOneExtraPerHit() {
        HomesteadBonus.Bonus bonus = new HomesteadBonus.Bonus(0, 0, 5000, 0, 0, 0L, 0);
        HomesteadEngine.SettleInput input = new HomesteadEngine.SettleInput(
                CONTENT.action("PICK_TWIG"), 1, 0, 0, 0, T0, T0 + 300_000, Map.of(), bonus);
        HomesteadEngine.SettleResult r = HomesteadEngine.settle(input, CONTENT);
        long n = r.completions();
        long extra = hits("PICK_TWIG", 0, n, SHIFT_YIELD, 5000);
        assertEquals(n + extra, r.gained().getOrDefault("PINE_TWIG", 0L));
    }

    @Test
    void efficiencySavesMaterialsDeterministically() {
        HomesteadBonus.Bonus bonus = new HomesteadBonus.Bonus(0, 0, 0, 0, 5000, 0L, 0);
        HomesteadEngine.SettleInput input = new HomesteadEngine.SettleInput(
                CONTENT.action("COOK_RICE"), 1, 0, 0, 0, T0, T0 + 60_000,
                Map.of("BEAN", 100L), bonus);
        HomesteadEngine.SettleResult r = HomesteadEngine.settle(input, CONTENT);
        long n = r.completions();
        long saved = hits("COOK_RICE", 0, n, SHIFT_EFFICIENCY, 5000);
        // 柴火饭每次 2 颗豆:省料的那几次不扣
        assertEquals(2 * (n - saved), r.consumed().getOrDefault("BEAN", 0L));
        assertEquals(n, r.gained().getOrDefault("RICE_MEAL", 0L));
    }

    @Test
    void buffExpirySplitsSettlement() {
        // 前 6 秒 +100% 速度(1500ms 一件),之后回到 3000ms:6 秒内 4 件 + 剩下 6 秒 2 件
        HomesteadBonus.BuffSpan buff = new HomesteadBonus.BuffSpan("SPEED", "ALL", 10_000, T0 + 6_000);
        HomesteadEngine.SettleInput input = new HomesteadEngine.SettleInput(
                CONTENT.action("PICK_TWIG"), 1, 0, 0, 0, T0, T0 + 12_000, Map.of(),
                HomesteadBonus.NONE, List.of(buff), HomesteadConst.OFFLINE_CAP_MS, 0L);
        HomesteadEngine.SettleResult r = HomesteadEngine.settle(input, CONTENT);
        assertEquals(6, r.completions());
        assertEquals(0, r.progressMs());
    }

    @Test
    void expiredBuffDoesNotApply() {
        HomesteadBonus.BuffSpan buff = new HomesteadBonus.BuffSpan("SPEED", "ALL", 10_000, T0 - 1);
        HomesteadEngine.SettleInput input = new HomesteadEngine.SettleInput(
                CONTENT.action("PICK_TWIG"), 1, 0, 0, 0, T0, T0 + 12_000, Map.of(),
                HomesteadBonus.NONE, List.of(buff), HomesteadConst.OFFLINE_CAP_MS, 0L);
        HomesteadEngine.SettleResult r = HomesteadEngine.settle(input, CONTENT);
        assertEquals(4, r.completions());
    }

    @Test
    void toolTierShortensActionTime() {
        // 柴刀 5 阶 = +3000bp:3000 × 10000 / 13000 向上取整 = 2308
        assertEquals(2308, HomesteadEngine.msPerItem(3000, 1, 3000));
        HomesteadEngine.SettleResult r = HomesteadEngine.settle(
                in("PICK_TWIG", 1, 0, 5, 0, 0, 3000, Map.of()), CONTENT);
        assertEquals(1, r.completions());
        assertEquals(692, r.progressMs());
    }

    @Test
    void sameInputAlwaysProducesSameResult() {
        HomesteadEngine.SettleInput input = in("PICK_BERRY", 3, 120, 2, 17, 700, 3_600_000, Map.of());
        HomesteadEngine.SettleResult a = HomesteadEngine.settle(input, CONTENT);
        HomesteadEngine.SettleResult b = HomesteadEngine.settle(input, CONTENT);
        assertEquals(a, b);
    }

    @Test
    void repeatedSettleChunksMatchOneLongSettle() {
        // 分两次结算(模拟页面刷新)= 一次长结算,证明掷点只依赖 (玩家, 动作, 次数)
        HomesteadEngine.SettleResult first = HomesteadEngine.settle(
                in("PICK_TWIG", 1, 0, 0, 0, 0, 12_000, Map.of()), CONTENT);
        HomesteadEngine.SettleResult second = HomesteadEngine.settle(
                in("PICK_TWIG", first.levelAfter(), first.xpAfter(), 0,
                        first.actionSeqAfter(), first.progressMs(), 33_000, Map.of()), CONTENT);
        HomesteadEngine.SettleResult once = HomesteadEngine.settle(
                in("PICK_TWIG", 1, 0, 0, 0, 0, 45_000, Map.of()), CONTENT);
        assertEquals(once.completions(), first.completions() + second.completions());
        assertEquals(once.gained(), merge(first.gained(), second.gained()));
        assertEquals(once.actionSeqAfter(), second.actionSeqAfter());
        assertEquals(once.xpAfter(), second.xpAfter());
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

    @Test
    void levelStopsAtOneHundred() {
        HomesteadEngine.SettleResult r = HomesteadEngine.settle(
                in("PICK_TWIG", 100, 999_999, 0, 0, 0, 0, Map.of()), CONTENT);
        assertEquals(100, r.levelAfter());
        assertEquals(999_999, r.xpAfter());
        assertEquals(0, r.levelUps());
    }

    @Test
    void extraCapExtendsOfflineWindow() {
        long fiftyHours = 50L * 3600_000L;
        long cap44h = HomesteadConst.OFFLINE_CAP_MS + 20L * 3600_000L;
        HomesteadEngine.SettleInput geared = new HomesteadEngine.SettleInput(
                CONTENT.action("PICK_TWIG"), 1, 0, 0, 0, T0, T0 + fiftyHours, Map.of(),
                HomesteadBonus.NONE, List.of(), cap44h, 0L);
        HomesteadEngine.SettleResult withBarn = HomesteadEngine.settle(geared, CONTENT);
        HomesteadEngine.SettleResult withoutBarn = HomesteadEngine.settle(
                in("PICK_TWIG", 1, 0, 0, 0, 0, fiftyHours, Map.of()), CONTENT);
        assertTrue(withBarn.capped());
        assertEquals(cap44h, withBarn.elapsedMs());
        assertEquals(HomesteadConst.OFFLINE_CAP_MS, withoutBarn.elapsedMs());
        assertTrue(withBarn.completions() > withoutBarn.completions());
    }

    /** 与引擎同口径的掷点计数(seq 从 1 数到 upto) */
    private static long hits(String actionCode, long seed, long upto, int shift, int chanceBp) {
        int hash = HomesteadEngine.actionHash(actionCode);
        long n = 0;
        for (long seq = 1; seq <= upto; seq++) {
            long roll = HomesteadEngine.roll(seed, hash, seq - 1);
            int bp = (int) Long.remainderUnsigned(roll >>> shift, 10_000);
            if (bp < chanceBp) {
                n++;
            }
        }
        return n;
    }

    private static Map<String, Long> merge(Map<String, Long> a, Map<String, Long> b) {
        Map<String, Long> out = new java.util.HashMap<>(a);
        b.forEach((k, v) -> out.merge(k, v, Long::sum));
        return out;
    }
}
