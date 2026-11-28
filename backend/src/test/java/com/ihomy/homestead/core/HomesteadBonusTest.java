package com.ihomy.homestead.core;

import com.ihomy.homestead.HomesteadConst;
import com.ihomy.homestead.content.HomesteadContent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 加成聚合:工具只作用于自己的动作、装备强化每级 +50%、装备/场地按目标类别过滤、
 * 离线上限与卖价为全局项、概率类封顶、buff 按时段与类别叠加。
 */
class HomesteadBonusTest {

    private static final HomesteadContent CONTENT = new HomesteadContent();
    private static final long NOW = 1_700_000_000_000L;

    @Test
    void toolOnlyAppliesToItsOwnAction() {
        HomesteadBonus.Bonus own = HomesteadBonus.resolve(
                CONTENT, CONTENT.action("PICK_TWIG"), 3, List.of(), Map.of());
        assertEquals(1800, own.speedBp());
        HomesteadBonus.Bonus other = HomesteadBonus.resolve(
                CONTENT, CONTENT.action("PICK_BERRY"), 3, List.of(), Map.of());
        assertEquals(0, other.speedBp());
    }

    @Test
    void gearEnhanceScalesByHalf() {
        HomesteadBonus.Bonus plain = HomesteadBonus.resolve(CONTENT, CONTENT.action("PICK_TWIG"), 0,
                List.of(new HomesteadBonus.WornGear("STRAW_HAT", 0)), Map.of());
        assertEquals(800, plain.speedBp());
        HomesteadBonus.Bonus strong = HomesteadBonus.resolve(CONTENT, CONTENT.action("PICK_TWIG"), 0,
                List.of(new HomesteadBonus.WornGear("STRAW_HAT", 5)), Map.of());
        assertEquals(2800, strong.speedBp());
        // 超过上限按上限算
        HomesteadBonus.Bonus clamped = HomesteadBonus.resolve(CONTENT, CONTENT.action("PICK_TWIG"), 0,
                List.of(new HomesteadBonus.WornGear("STRAW_HAT", 99)), Map.of());
        assertEquals(2800, clamped.speedBp());
    }

    @Test
    void gearTargetFiltersBySkillKind() {
        List<HomesteadBonus.WornGear> apron = List.of(new HomesteadBonus.WornGear("CLOTH_APRON", 0));
        assertEquals(800, HomesteadBonus.resolve(CONTENT, CONTENT.action("COOK_RICE"), 0, apron, Map.of())
                .speedBp());
        assertEquals(0, HomesteadBonus.resolve(CONTENT, CONTENT.action("PICK_TWIG"), 0, apron, Map.of())
                .speedBp());
    }

    @Test
    void buildingsAccumulatePerLevel() {
        Map<String, Integer> levels = Map.of("LUMBER_YARD", 5, "WELL", 2);
        HomesteadBonus.Bonus wood = HomesteadBonus.resolve(
                CONTENT, CONTENT.action("PICK_TWIG"), 0, List.of(), levels);
        assertEquals(1250 + 240, wood.speedBp());
        assertEquals(900, wood.xpBp());
        HomesteadBonus.Bonus cook = HomesteadBonus.resolve(
                CONTENT, CONTENT.action("COOK_RICE"), 0, List.of(), levels);
        assertEquals(240, cook.speedBp());
        assertEquals(0, cook.xpBp());
    }

    @Test
    void globalEffectsApplyWithoutAction() {
        Map<String, Integer> levels = Map.of("GRANARY", 2, "DRYING_YARD", 3);
        HomesteadBonus.Bonus idle = HomesteadBonus.resolve(CONTENT, null, 0, List.of(), levels);
        assertEquals(4, idle.extraCapHours());
        assertEquals(HomesteadConst.OFFLINE_CAP_MS + 4L * 3600_000L, idle.capMs());
        assertEquals(450, idle.sellBonusBp());
        assertEquals(1045, idle.sellCoins(1000));
    }

    @Test
    void chanceAndSpeedAreCapped() {
        HomesteadBonus.Bonus wild = new HomesteadBonus.Bonus(99_999, 99_999, 99_999, 99_999, 99_999, 0L, 0);
        HomesteadBonus.Bonus capped = wild.capped();
        assertEquals(HomesteadConst.MAX_SPEED_BONUS_BP, capped.speedBp());
        assertEquals(HomesteadConst.MAX_XP_BONUS_BP, capped.xpBp());
        assertEquals(HomesteadConst.MAX_CHANCE_BP, capped.yieldBp());
        assertEquals(HomesteadConst.MAX_CHANCE_BP, capped.rareBp());
        assertEquals(HomesteadConst.MAX_CHANCE_BP, capped.efficiencyBp());
    }

    @Test
    void buffsJoinOnlyWhenActiveAndMatchingTarget() {
        List<HomesteadBonus.BuffSpan> buffs = List.of(
                new HomesteadBonus.BuffSpan("SPEED", "ALL", 1000, NOW + 60_000),
                new HomesteadBonus.BuffSpan("XP", "GATHER", 500, NOW + 60_000),
                new HomesteadBonus.BuffSpan("RARE", "ALL", 300, NOW - 1));
        HomesteadBonus.Bonus gather = HomesteadBonus.withBuffs(HomesteadBonus.NONE, "GATHER", buffs, NOW);
        assertEquals(1000, gather.speedBp());
        assertEquals(500, gather.xpBp());
        assertEquals(0, gather.rareBp(), "过期 buff 不应生效");
        HomesteadBonus.Bonus process = HomesteadBonus.withBuffs(HomesteadBonus.NONE, "PROCESS", buffs, NOW);
        assertEquals(1000, process.speedBp());
        assertEquals(0, process.xpBp(), "GATHER 经验 buff 不应作用到加工");
    }

    @Test
    void plusMergesEveryDimension() {
        HomesteadBonus.Bonus a = new HomesteadBonus.Bonus(100, 200, 300, 400, 500, 50L, 1);
        HomesteadBonus.Bonus b = new HomesteadBonus.Bonus(1, 2, 3, 4, 5, 5L, 2);
        HomesteadBonus.Bonus sum = a.plus(b);
        assertEquals(101, sum.speedBp());
        assertEquals(202, sum.xpBp());
        assertEquals(303, sum.yieldBp());
        assertEquals(404, sum.rareBp());
        assertEquals(505, sum.efficiencyBp());
        assertEquals(55, sum.sellBonusBp());
        assertEquals(3, sum.extraCapHours());
        assertTrue(sum.plus(null) == sum);
    }
}
