package com.ihomy.homestead.content;

import com.ihomy.homestead.HomesteadConst;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内容装载与自洽:七份 JSON 能被装载、引用完整、经验曲线长度对齐、content_version 稳定。
 * 校验逻辑本身在构造器里(非法内容启动即失败),这里只保证「当前仓库里的内容合法」。
 */
class HomesteadContentTest {

    private static final HomesteadContent CONTENT = new HomesteadContent();

    @Test
    void loadsAllSevenFiles() {
        assertEquals(11, CONTENT.skills().size());
        assertEquals(63, CONTENT.items().size());
        assertEquals(66, CONTENT.actions().size());
        assertEquals(66, CONTENT.tools().size());
        assertEquals(44, CONTENT.equipment().size());
        assertEquals(5, CONTENT.rarities().size());
        assertEquals(4, CONTENT.gearSets().size());
        assertEquals(12, CONTENT.affixPool().size());
        assertEquals(11, CONTENT.dropGear().size(), "每家族一件掉落专属");
        assertEquals(14, CONTENT.buildings().size());
        assertEquals(6, CONTENT.regions().size());
        assertEquals(7, CONTENT.shops().size());
        assertEquals(100, CONTENT.maxLevel());
        assertEquals(64, CONTENT.contentVersion().length());
    }

    @Test
    void equipmentCoversElevenFamiliesWithFourEach() {
        Map<String, Integer> perFamily = new HashMap<>();
        Map<String, Integer> craftedPerFamily = new HashMap<>();
        Set<String> codes = new HashSet<>();
        CONTENT.equipment().forEach(gear -> {
            assertTrue(codes.add(gear.code()), "装备编码重复:" + gear.code());
            assertTrue(HomesteadConst.GEAR_FAMILIES.contains(gear.slot()), "部位非法:" + gear.code());
            perFamily.merge(gear.slot(), 1, Integer::sum);
            if (gear.craftable()) {
                craftedPerFamily.merge(gear.slot(), 1, Integer::sum);
                assertNotNull(gear.craft(), "打造装缺配方:" + gear.code());
                gear.craft().materials().forEach(material -> assertNotNull(CONTENT.item(material.item())));
            } else {
                assertNull(gear.craft(), "掉落装不应有配方:" + gear.code());
            }
            assertEquals(HomesteadConst.MAX_GEAR_TIER, gear.steps().size(), "强化档数不符:" + gear.code());
            assertTrue(gear.power() > 0, "装备缺战力:" + gear.code());
            assertNotNull(CONTENT.gearSet(gear.set()), "套装未知:" + gear.code());
            assertTrue(!gear.dismantle().isEmpty(), "装备缺分解产出:" + gear.code());
            assertTrue(gear.requireLevel() > 0, "装备缺等级门槛:" + gear.code());
            assertTrue(!gear.effects().isEmpty(), "装备缺效果:" + gear.code());
            for (int tier = 0; tier < HomesteadConst.MAX_GEAR_TIER; tier++) {
                assertNotNull(gear.nextStep(tier));
            }
            assertNull(gear.nextStep(HomesteadConst.MAX_GEAR_TIER));
            gear.steps().forEach(step -> step.materials()
                    .forEach(material -> assertNotNull(CONTENT.item(material.item()))));
            gear.dismantle().forEach(material -> assertNotNull(CONTENT.item(material.item())));
        });
        assertEquals(HomesteadConst.GEAR_FAMILIES.size(), perFamily.size());
        perFamily.forEach((family, count) -> {
            assertEquals(HomesteadConst.GEAR_PER_FAMILY, count, "家族件数不符:" + family);
            assertEquals(HomesteadConst.GEAR_PER_FAMILY - 1, craftedPerFamily.getOrDefault(family, 0),
                    "每家族应 3 件打造档:" + family);
        });
    }

    @Test
    void raritiesSetsAndAffixesAreConsistent() {
        assertEquals(HomesteadConst.QUALITY_COMMON, CONTENT.rarities().get(0).code());
        for (int i = 1; i < CONTENT.rarities().size(); i++) {
            assertTrue(CONTENT.rarities().get(i).multBp() > CONTENT.rarities().get(i - 1).multBp(),
                    "品质倍率应递增:" + CONTENT.rarities().get(i).code());
            assertTrue(CONTENT.rarityIndex(CONTENT.rarities().get(i).code()) == i);
        }
        for (GearSetDef set : CONTENT.gearSets()) {
            assertTrue(!set.steps().isEmpty(), "套装缺档位:" + set.code());
            int pieces = 0;
            for (GearSetDef.SetStep step : set.steps()) {
                assertTrue(step.pieces() > pieces, "套装件数应递增:" + set.code());
                pieces = step.pieces();
                assertTrue(!step.effects().isEmpty(), "套装档位缺效果:" + set.code());
            }
            long members = CONTENT.equipment().stream().filter(g -> set.code().equals(g.set())).count();
            assertTrue(members >= pieces, "套装成员不足:" + set.code());
        }
        assertTrue(CONTENT.affixPool().size() > 0);
        CONTENT.affixPool().forEach(affix -> assertTrue(affix.target() != null && !affix.target().isBlank()));
    }

    @Test
    void expeditionRegionsReferenceKnownContent() {
        for (ExpeditionDef.Region region : CONTENT.regions()) {
            assertNotNull(CONTENT.skill(region.skill()), "区域指向未知技能:" + region.code());
            assertTrue(region.powerFloor() > 0, "区域应有战力下限:" + region.code());
            assertTrue(region.feeCoins() > 0, "区域应有费用:" + region.code());
            assertTrue(region.minutes() > 0, "区域应有耗时:" + region.code());
            assertTrue(!region.supplies().isEmpty(), "区域应有供给要求:" + region.code());
            region.supplies().forEach(s -> assertNotNull(CONTENT.item(s.item())));
            assertTrue(!region.events().isEmpty(), "区域应有事件:" + region.code());
            region.gearPool().forEach(code -> {
                EquipmentDef gear = CONTENT.gear(code);
                assertNotNull(gear, "区域装备池引用未知装备:" + code);
                assertTrue(!gear.craftable(), "区域装备池不应放打造装:" + code);
            });
            region.events().forEach(event -> {
                assertNotNull(CONTENT.skill(event.skill()), "事件指向未知技能:" + region.code());
                assertTrue(event.difficulty() > 0);
                assertTrue(!event.loot().isEmpty());
                event.loot().forEach(loot -> {
                    if (loot.gear()) {
                        assertNotNull(CONTENT.rarity(loot.qualityFloor() == null || loot.qualityFloor().isBlank()
                                ? region.gearQualityFloor() : loot.qualityFloor()));
                    } else {
                        assertNotNull(CONTENT.item(loot.item()), "事件掉落引用未知物品:" + loot.item());
                    }
                });
            });
        }
        for (ExpeditionDef.Shop shop : CONTENT.shops()) {
            assertTrue(shop.points() > 0, "商店价格非法:" + shop.code());
            if (shop.chest()) {
                assertTrue(!shop.pool().isEmpty(), "宝箱缺材料池:" + shop.code());
                assertNotNull(CONTENT.rarity(shop.gearQualityFloor()));
            } else {
                assertNotNull(CONTENT.item(shop.item()), "商店物品未知:" + shop.code());
            }
        }
    }

    @Test
    void xpCurveIsMonotonicAndComplete() {
        long prev = 0;
        for (int level = 1; level < CONTENT.maxLevel(); level++) {
            int need = CONTENT.xpForNextLevel(level);
            assertTrue(need > prev, "经验曲线应递增,断在 " + level);
            prev = need;
        }
        assertEquals(0, CONTENT.xpForNextLevel(CONTENT.maxLevel()));
    }

    @Test
    void everyActionHasItsOwnToolAndEverySkillHasSixActions() {
        Set<String> toolCodes = new HashSet<>();
        CONTENT.tools().forEach(tool -> {
            assertTrue(toolCodes.add(tool.code()), "工具编码重复:" + tool.code());
            assertNotNull(CONTENT.action(tool.action()), "工具指向不存在的动作:" + tool.code());
            assertEquals(HomesteadConst.MAX_TOOL_TIER, tool.steps().size(), "工具档数不符:" + tool.code());
            assertTrue(tool.requireLevel() > 0, "工具缺等级门槛:" + tool.code());
            assertTrue(!tool.effects().isEmpty(), "工具缺效果:" + tool.code());
            for (int tier = 0; tier < HomesteadConst.MAX_TOOL_TIER; tier++) {
                assertNotNull(tool.nextStep(tier));
            }
            assertNull(tool.nextStep(HomesteadConst.MAX_TOOL_TIER));
        });
        assertEquals(CONTENT.actions().size(), CONTENT.tools().size(), "一个动作一件工具");
        for (ActionDef action : CONTENT.actions()) {
            assertNotNull(CONTENT.toolOfAction(action.code()), "缺少对应工具:" + action.code());
        }
        for (SkillDef skill : CONTENT.skills()) {
            assertEquals(6, CONTENT.actionsOf(skill.code()).size(), "每技能应有 6 个动作:" + skill.code());
            assertEquals(6, CONTENT.toolsOfSkill(skill.code()).size(), "每技能应有 6 件工具:" + skill.code());
        }
    }

    @Test
    void gatherSkillsNeverConsumeMaterials() {
        for (SkillDef skill : CONTENT.skills()) {
            for (ActionDef action : CONTENT.actionsOf(skill.code())) {
                if (!SkillDef.KIND_GATHER.equals(skill.kind())) {
                    assertTrue(action.consumesMaterials(), "加工动作应耗材:" + action.code());
                } else if (!action.needsBuilding()) {
                    assertTrue(action.inputs().isEmpty(), "野外采集动作不应耗材:" + action.code());
                }
            }
        }
    }

    @Test
    void actionLevelGatesAreAscendingWithinSkill() {
        for (SkillDef skill : CONTENT.skills()) {
            // 工坊动作单独挂在 70 级,不参与「档位递增」这条
            int prev = 0;
            for (ActionDef action : CONTENT.actionsOf(skill.code())) {
                if (action.needsBuilding()) {
                    continue;
                }
                assertTrue(action.level() >= prev, "档位门槛应递增:" + action.code());
                prev = action.level();
            }
        }
    }

    @Test
    void workshopActionsAreGatedByBuilding() {
        int gated = 0;
        for (ActionDef action : CONTENT.actions()) {
            if (action.needsBuilding()) {
                gated++;
                assertNotNull(CONTENT.building(action.requireBuilding()), "场地不存在:" + action.code());
                assertTrue(action.requireBuildingLevel() > 0, "场地等级门槛缺失:" + action.code());
                assertEquals(70, action.level(), "工坊动作门槛应为 70:" + action.code());
            }
        }
        assertEquals(11, gated, "每门技能一个工坊动作");
    }

    @Test
    void buildingsAreCompleteAndUnlockKnownActions() {
        Set<String> codes = new HashSet<>();
        for (BuildingDef building : CONTENT.buildings()) {
            assertTrue(codes.add(building.code()), "场地编码重复:" + building.code());
            assertEquals(10, building.maxLevel(), "场地上限应为 10:" + building.code());
            assertEquals(building.maxLevel(), building.steps().size(), "场地档数应等于上限:" + building.code());
            assertNull(building.nextStep(building.maxLevel()));
            assertTrue(!building.effects().isEmpty(), "场地缺效果:" + building.code());
            building.steps().forEach(step -> step.materials()
                    .forEach(material -> assertNotNull(CONTENT.item(material.item()))));
            if (building.unlockAction() != null) {
                ActionDef action = CONTENT.action(building.unlockAction());
                assertNotNull(action, "场地解锁的动作不存在:" + building.code());
                assertEquals(building.code(), action.requireBuilding(), "动作与场地门槛不一致:" + action.code());
            }
        }
    }

    @Test
    void everyYieldInputRareAndRecipePointsToKnownItem() {
        CONTENT.actions().forEach(a -> {
            a.inputs().forEach(i -> assertNotNull(CONTENT.item(i.item())));
            a.yields().forEach(y -> assertNotNull(CONTENT.item(y.item())));
            if (a.rare() != null) {
                assertNotNull(CONTENT.item(a.rare().item()));
                assertTrue(a.rare().chanceBp() > 0, "稀有掉落应有概率:" + a.code());
            }
        });
        CONTENT.tools().forEach(t -> t.steps().forEach(s ->
                s.materials().forEach(m -> assertNotNull(CONTENT.item(m.item())))));
    }

    @Test
    void consumablesCarrySaneBuff() {
        int consumables = 0;
        for (ItemDef item : CONTENT.items()) {
            if (!item.consumable()) {
                continue;
            }
            consumables++;
            ItemDef.BuffDef buff = item.buff();
            assertTrue(buff.bp() > 0, "buff 数值应为正:" + item.code());
            assertTrue(buff.minutes() > 0, "buff 时长应为正:" + item.code());
            assertTrue(Set.of(ItemDef.BuffDef.KIND_SPEED, ItemDef.BuffDef.KIND_XP,
                            ItemDef.BuffDef.KIND_YIELD, ItemDef.BuffDef.KIND_RARE).contains(buff.kind()),
                    "buff 类型非法:" + item.code());
            assertTrue(Set.of(EffectKind.TARGET_ALL, EffectKind.TARGET_GATHER, EffectKind.TARGET_PROCESS)
                    .contains(buff.target()), "buff 目标非法:" + item.code());
        }
        assertTrue(consumables >= 8, "料理种类偏少:" + consumables);
    }

    @Test
    void contentVersionIsStableAcrossInstances() {
        assertEquals(CONTENT.contentVersion(), new HomesteadContent().contentVersion());
    }

    @Test
    void unknownCodesReturnNull() {
        assertNull(CONTENT.skill("NOPE"));
        assertNull(CONTENT.item("NOPE"));
        assertNull(CONTENT.action("NOPE"));
        assertNull(CONTENT.tool("NOPE"));
        assertNull(CONTENT.toolOfAction("NOPE"));
        assertNull(CONTENT.gear("NOPE"));
        assertNull(CONTENT.building("NOPE"));
        assertEquals(0, CONTENT.sellPrice("NOPE"));
    }
}
