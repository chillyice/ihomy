package com.ihomy.homestead.core;

import com.ihomy.homestead.HomesteadConst;
import com.ihomy.homestead.content.HomesteadContent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 掉落实例的确定性掷点:同种子同结果、品质下限生效、词缀条数=品质定义、词缀串可回读、分解按品质放大。
 */
class HomesteadLootTest {

    private static final HomesteadContent CONTENT = new HomesteadContent();

    @Test
    void sameSeedGivesSameInstance() {
        HomesteadLoot.Instance a = HomesteadLoot.roll(CONTENT, "FOREST_CROWN", 7L, "HILL_PATH#1", 0, "FINE");
        HomesteadLoot.Instance b = HomesteadLoot.roll(CONTENT, "FOREST_CROWN", 7L, "HILL_PATH#1", 0, "FINE");
        assertNotNull(a);
        assertEquals(a, b);
        assertEquals("FOREST_CROWN", a.code());
    }

    @Test
    void qualityNeverBelowFloor() {
        int floorIndex = CONTENT.rarityIndex("RARE");
        for (int seq = 0; seq < 40; seq++) {
            HomesteadLoot.Instance inst = HomesteadLoot.roll(CONTENT, "FOREST_CROWN", 11L, "CHEST_IRON", seq, "RARE");
            assertTrue(CONTENT.rarityIndex(inst.quality()) >= floorIndex,
                    "品质低于下限:" + inst.quality());
        }
        // 下限已是最高档时不会越界
        HomesteadLoot.Instance top = HomesteadLoot.roll(CONTENT, "FOREST_CROWN", 11L, "CHEST_IRON", 0, "LEGENDARY");
        assertEquals("LEGENDARY", top.quality());
    }

    @Test
    void affixCountMatchesRarity() {
        for (String floor : List.of("COMMON", "FINE", "RARE", "EPIC", "LEGENDARY")) {
            HomesteadLoot.Instance inst = HomesteadLoot.roll(CONTENT, "FOREST_CROWN", 3L, "SALT", 1, floor);
            List<HomesteadBonus.Affix> affixes = HomesteadLoot.parseAffixes(inst.affixes());
            int expected = Math.min(CONTENT.rarity(inst.quality()).affixes(), CONTENT.affixPool().size());
            assertEquals(expected, affixes.size(), "词缀条数不符:" + inst.quality());
            if (!affixes.isEmpty()) {
                int min = CONTENT.rarity(inst.quality()).minBp();
                int max = CONTENT.rarity(inst.quality()).maxBp();
                affixes.forEach(affix -> assertTrue(affix.bp() >= min && affix.bp() <= max,
                        "词缀数值越界:" + affix));
            }
        }
    }

    @Test
    void affixStringRoundTrips() {
        List<HomesteadBonus.Affix> affixes = List.of(
                new HomesteadBonus.Affix("SPEED", "ALL", 120),
                new HomesteadBonus.Affix("XP", "FARM", 80));
        String raw = HomesteadLoot.formatAffixes(affixes);
        assertEquals("SPEED:ALL:120,XP:FARM:80", raw);
        assertEquals(affixes, HomesteadLoot.parseAffixes(raw));
        assertTrue(HomesteadLoot.parseAffixes("").isEmpty());
        assertTrue(HomesteadLoot.parseAffixes(null).isEmpty());
        assertTrue(HomesteadLoot.parseAffixes("BAD").isEmpty());
        assertEquals("", HomesteadLoot.formatAffixes(List.of()));
    }

    @Test
    void dismantleScalesWithQuality() {
        List<com.ihomy.homestead.content.ActionDef.ItemAmount> common =
                HomesteadLoot.dismantle(CONTENT, "WOOL_CLOAK", "COMMON");
        List<com.ihomy.homestead.content.ActionDef.ItemAmount> legendary =
                HomesteadLoot.dismantle(CONTENT, "WOOL_CLOAK", "LEGENDARY");
        assertFalse(common.isEmpty());
        assertEquals(common.size(), legendary.size());
        for (int i = 0; i < common.size(); i++) {
            assertEquals(common.get(i).item(), legendary.get(i).item());
            assertTrue(legendary.get(i).qty() > common.get(i).qty(), "高品质分解应更多材料");
        }
        assertTrue(HomesteadLoot.dismantle(CONTENT, "NOPE", "COMMON").isEmpty());
    }

    @Test
    void craftedGearIsCommonByConvention() {
        HomesteadBonus.WornGear crafted = HomesteadBonus.WornGear.plain("WOOL_CLOAK", 0);
        assertEquals(HomesteadConst.QUALITY_COMMON, crafted.quality());
        assertTrue(crafted.affixes().isEmpty());
    }
}
