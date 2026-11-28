package com.ihomy.homestead.content;

import com.ihomy.homestead.HomesteadConst;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内容装载与自洽:四份 JSON 能被装载、引用完整、经验曲线长度对齐、content_version 稳定。
 * 校验逻辑本身在构造器里(非法内容启动即失败),这里只保证「当前仓库里的内容合法」。
 */
class HomesteadContentTest {

    private static final HomesteadContent CONTENT = new HomesteadContent();

    @Test
    void loadsAllFourFiles() {
        assertEquals(7, CONTENT.skills().size());
        assertEquals(32, CONTENT.items().size());
        assertEquals(28, CONTENT.actions().size());
        assertEquals(7, CONTENT.tools().size());
        assertEquals(5, CONTENT.equipment().size());
        assertEquals(50, CONTENT.maxLevel());
        assertEquals(64, CONTENT.contentVersion().length());
    }

    @Test
    void equipmentHasFiveStableSlots() {
        Set<String> codes = new HashSet<>();
        CONTENT.equipment().forEach(slot -> {
            assertTrue(codes.add(slot.code()), "部位编码重复:" + slot.code());
            assertEquals(HomesteadConst.MAX_GEAR_TIER, slot.steps().size(), "穿戴阶数不符:" + slot.code());
            for (int tier = 0; tier < HomesteadConst.MAX_GEAR_TIER; tier++) {
                assertNotNull(slot.nextStep(tier));
            }
            assertNull(slot.nextStep(HomesteadConst.MAX_GEAR_TIER));
            assertNotNull(slot.effect(), "穿戴缺效果:" + slot.code());
            slot.steps().forEach(s -> s.materials().forEach(m -> assertNotNull(CONTENT.item(m.item()))));
        });
        assertEquals(Set.of("HAT", "APRON", "SLEEVE", "BOOTS", "CHARM"), codes);
        assertNotNull(CONTENT.equipmentSlot("HAT"));
        assertNull(CONTENT.equipmentSlot("NOPE"));
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
    void everySkillHasOneToolAndFourActions() {
        Set<String> toolSkills = new HashSet<>();
        CONTENT.tools().forEach(t -> {
            assertTrue(toolSkills.add(t.skill()), "每个技能只能有一件工具:" + t.skill());
            assertEquals(HomesteadConst.MAX_TOOL_TIER, t.steps().size());
            for (int tier = 0; tier < HomesteadConst.MAX_TOOL_TIER; tier++) {
                assertNotNull(t.nextStep(tier));
            }
            assertNull(t.nextStep(HomesteadConst.MAX_TOOL_TIER));
        });
        for (SkillDef skill : CONTENT.skills()) {
            assertNotNull(CONTENT.toolOfSkill(skill.code()), "缺少工具:" + skill.code());
            assertEquals(4, CONTENT.actionsOf(skill.code()).size(), "动作档位应为 4:" + skill.code());
        }
    }

    @Test
    void gatherSkillsNeverConsumeMaterials() {
        for (SkillDef skill : CONTENT.skills()) {
            for (ActionDef action : CONTENT.actionsOf(skill.code())) {
                if (SkillDef.KIND_GATHER.equals(skill.kind())) {
                    assertTrue(action.inputs().isEmpty(), "采集动作不应耗材:" + action.code());
                } else {
                    assertTrue(action.consumesMaterials(), "加工动作应耗材:" + action.code());
                }
            }
        }
    }

    @Test
    void actionLevelGatesAreAscendingWithinSkill() {
        for (SkillDef skill : CONTENT.skills()) {
            int prev = 0;
            for (ActionDef action : CONTENT.actionsOf(skill.code())) {
                assertTrue(action.level() >= prev, "档位门槛应递增:" + action.code());
                prev = action.level();
            }
        }
    }

    @Test
    void everyYieldAndInputPointsToKnownItem() {
        CONTENT.actions().forEach(a -> {
            a.inputs().forEach(i -> assertNotNull(CONTENT.item(i.item())));
            a.yields().forEach(y -> assertNotNull(CONTENT.item(y.item())));
            if (a.rare() != null) {
                assertNotNull(CONTENT.item(a.rare().item()));
            }
        });
        CONTENT.tools().forEach(t -> t.steps().forEach(s ->
                s.materials().forEach(m -> assertNotNull(CONTENT.item(m.item())))));
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
        assertNull(CONTENT.toolOfSkill("NOPE"));
        assertEquals(0, CONTENT.sellPrice("NOPE"));
    }
}
