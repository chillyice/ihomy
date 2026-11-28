package com.ihomy.homestead.core;

import com.ihomy.homestead.HomesteadConst;
import com.ihomy.homestead.content.HomesteadContent;
import com.ihomy.homestead.content.SkillDef;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 穿戴折叠口径:部位 → 类别加成、离线上限、卖价,全部整数确定性。
 * 数据取仓库里的 equipment.json(内容校验另有 HomesteadContentTest)。
 */
class HomesteadGearTest {

    private static final HomesteadContent CONTENT = new HomesteadContent();

    @Test
    void noGearGivesNothing() {
        HomesteadGear.Bonuses none = HomesteadGear.of(CONTENT, Map.of());
        assertEquals(0, none.gatherBp());
        assertEquals(0, none.processBp());
        assertEquals(0, none.allBp());
        assertEquals(0L, none.sellBonusBp());
        assertEquals(HomesteadConst.OFFLINE_CAP_MS, none.capMs());
        assertEquals(0, none.speedBpFor(SkillDef.KIND_GATHER));
        assertEquals(0, none.speedBpFor(SkillDef.KIND_PROCESS));
        assertEquals(1000L, none.sellCoins(1000L));
    }

    @Test
    void hatOnlySpeedsGathering() {
        HomesteadGear.Bonuses b = HomesteadGear.of(CONTENT, Map.of("HAT", 5));
        assertEquals(750, b.gatherBp()); // 150 × 5
        assertEquals(0, b.processBp());
        assertEquals(750, b.speedBpFor(SkillDef.KIND_GATHER));
        assertEquals(0, b.speedBpFor(SkillDef.KIND_PROCESS));
    }

    @Test
    void apronOnlySpeedsProcessing() {
        HomesteadGear.Bonuses b = HomesteadGear.of(CONTENT, Map.of("APRON", 2));
        assertEquals(400, b.processBp()); // 200 × 2
        assertEquals(0, b.speedBpFor(SkillDef.KIND_GATHER));
        assertEquals(400, b.speedBpFor(SkillDef.KIND_PROCESS));
    }

    @Test
    void sleeveStacksOnTopOfBothKinds() {
        HomesteadGear.Bonuses b = HomesteadGear.of(CONTENT, Map.of("HAT", 5, "SLEEVE", 3, "APRON", 2));
        assertEquals(300, b.allBp()); // 100 × 3
        assertEquals(750 + 300, b.speedBpFor(SkillDef.KIND_GATHER));
        assertEquals(400 + 300, b.speedBpFor(SkillDef.KIND_PROCESS));
    }

    @Test
    void bootsRaiseOfflineCapOnly() {
        HomesteadGear.Bonuses b = HomesteadGear.of(CONTENT, Map.of("BOOTS", 5));
        assertEquals(20, b.extraCapHours()); // 4 × 5
        assertEquals(HomesteadConst.OFFLINE_CAP_MS + 20L * 3600_000L, b.capMs());
        assertEquals(0, b.speedBpFor(SkillDef.KIND_GATHER));
    }

    @Test
    void charmRaisesSellCoinsWithFloor() {
        HomesteadGear.Bonuses b = HomesteadGear.of(CONTENT, Map.of("CHARM", 4));
        assertEquals(800L, b.sellBonusBp()); // 200 × 4
        assertEquals(1080L, b.sellCoins(1000L));
        assertEquals(7L, b.sellCoins(7L)); // 7 × 1.08 = 7.56,向下取整
        assertEquals(0L, b.sellCoins(0L));
    }

    @Test
    void tiersAreClampedAndUnknownSlotsIgnored() {
        HomesteadGear.Bonuses over = HomesteadGear.of(CONTENT, Map.of("HAT", 99, "GHOST", 5));
        assertEquals(750, over.gatherBp()); // 99 截到 5 阶
        HomesteadGear.Bonuses negative = HomesteadGear.of(CONTENT, Map.of("HAT", -3));
        assertEquals(0, negative.gatherBp());
    }
}
