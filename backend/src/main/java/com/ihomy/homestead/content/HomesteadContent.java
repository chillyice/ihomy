package com.ihomy.homestead.content;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ihomy.homestead.HomesteadConst;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 小院慢生活内容配置(content/homestead/*.json,classpath):
 * 技能 / 物品 / 动作 / 工具 / 装备 / 场地。启动即装载、校验不过即失败(内容完整性优先于可用性)。
 * content_version = sha256(按文件名排序拼接「文件名 + 内容」),写入存档用于内容升级后的口径核对。
 * 数值只在这里维护一份,引擎与前端都从这里取,禁止第二份数值来源。
 */
@Slf4j
@Component
public class HomesteadContent {

    private final int maxLevel;
    private final int[] xpToNext;
    private final List<SkillDef> skills;
    private final Map<String, SkillDef> skillByCode;
    private final List<ItemDef> items;
    private final Map<String, ItemDef> itemByCode;
    private final List<ActionDef> actions;
    private final Map<String, ActionDef> actionByCode;
    private final Map<String, List<ActionDef>> actionsBySkill;
    private final List<ToolDef> tools;
    private final Map<String, ToolDef> toolByCode;
    private final Map<String, ToolDef> toolByAction;
    private final Map<String, List<ToolDef>> toolsBySkill;
    private final List<EquipmentDef> equipment;
    private final Map<String, EquipmentDef> equipmentByCode;
    private final List<BuildingDef> buildings;
    private final Map<String, BuildingDef> buildingByCode;
    private final String contentVersion;

    public HomesteadContent() {
        ObjectMapper mapper = new ObjectMapper();
        SkillsFile skillsFile;
        try {
            skillsFile = load(mapper, "skills.json", SkillsFile.class);
            this.items = List.copyOf(load(mapper, "items.json", ItemsFile.class).items());
            this.actions = List.copyOf(load(mapper, "actions.json", ActionsFile.class).actions());
            this.tools = List.copyOf(load(mapper, "tools.json", ToolsFile.class).tools());
            this.equipment = List.copyOf(load(mapper, "equipment.json", EquipmentFile.class).slots());
            this.buildings = List.copyOf(load(mapper, "buildings.json", BuildingsFile.class).buildings());
        } catch (Exception e) {
            throw new IllegalStateException("homestead content 加载失败:检查 resources/content/homestead/*.json", e);
        }
        if (skillsFile == null || skillsFile.skills() == null || skillsFile.skills().isEmpty()) {
            throw new IllegalStateException("homestead content 非法:skills.json 缺少 skills");
        }
        this.skills = List.copyOf(skillsFile.skills());
        this.maxLevel = skillsFile.maxLevel();
        List<Long> xpList = skillsFile.xpToNext();
        if (xpList == null || xpList.size() != Math.max(0, maxLevel - 1)) {
            throw new IllegalStateException("homestead content 非法:xp_to_next 长度应为 max_level-1");
        }
        this.xpToNext = new int[xpList.size()];
        for (int i = 0; i < xpList.size(); i++) {
            long v = xpList.get(i);
            if (v <= 0 || v > Integer.MAX_VALUE) {
                throw new IllegalStateException("homestead content 非法:xp_to_next 第 " + (i + 1) + " 项越界");
            }
            this.xpToNext[i] = (int) v;
        }
        this.skillByCode = index(skills, SkillDef::code, "skill");
        this.itemByCode = index(items, ItemDef::code, "item");
        this.actionByCode = index(actions, ActionDef::code, "action");
        this.toolByCode = index(tools, ToolDef::code, "tool");
        this.equipmentByCode = index(equipment, EquipmentDef::code, "equipment");
        this.buildingByCode = index(buildings, BuildingDef::code, "building");

        Map<String, List<ActionDef>> bySkill = new HashMap<>();
        for (ActionDef action : actions) {
            bySkill.computeIfAbsent(action.skill(), k -> new ArrayList<>()).add(action);
        }
        bySkill.replaceAll((k, v) -> List.copyOf(v));
        this.actionsBySkill = Map.copyOf(bySkill);

        Map<String, ToolDef> perAction = new HashMap<>();
        Map<String, List<ToolDef>> toolBySkillMap = new HashMap<>();
        for (ToolDef tool : tools) {
            perAction.put(tool.action(), tool);
            toolBySkillMap.computeIfAbsent(tool.skill(), k -> new ArrayList<>()).add(tool);
        }
        this.toolByAction = Map.copyOf(perAction);
        toolBySkillMap.replaceAll((k, v) -> List.copyOf(v));
        this.toolsBySkill = Map.copyOf(toolBySkillMap);

        validate();
        this.contentVersion = computeVersion();
        log.info("[homestead] content loaded: {} skills, {} items, {} actions, {} tools, {} gear, {} buildings, maxLevel={}, content_version={}",
                skills.size(), items.size(), actions.size(), tools.size(), equipment.size(), buildings.size(),
                maxLevel, contentVersion);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SkillsFile(
            @JsonProperty("max_level") int maxLevel,
            @JsonProperty("xp_to_next") List<Long> xpToNext,
            @JsonProperty("skills") List<SkillDef> skills) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ItemsFile(@JsonProperty("items") List<ItemDef> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ActionsFile(@JsonProperty("actions") List<ActionDef> actions) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ToolsFile(@JsonProperty("tools") List<ToolDef> tools) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EquipmentFile(@JsonProperty("slots") List<EquipmentDef> slots) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BuildingsFile(@JsonProperty("buildings") List<BuildingDef> buildings) {
    }

    private <T> T load(ObjectMapper mapper, String name, Class<T> type) throws Exception {
        try (var in = new ClassPathResource("content/homestead/" + name).getInputStream()) {
            return mapper.readValue(in, type);
        }
    }

    private static <T> Map<String, T> index(List<T> list, Function<T, String> codeOf, String what) {
        Map<String, T> map = new HashMap<>();
        for (T item : list) {
            String code = codeOf.apply(item);
            if (code == null || code.isBlank()) {
                throw new IllegalStateException("homestead content 非法:" + what + " 缺少 code");
            }
            if (map.put(code, item) != null) {
                throw new IllegalStateException("homestead content 非法:" + what + " code 重复 " + code);
            }
        }
        return Map.copyOf(map);
    }

    /** 内容自洽校验:引用完整、门槛单调、无自喂循环、每动作一件工具、每技能一座工坊 */
    private void validate() {
        if (maxLevel < 2) {
            throw new IllegalStateException("homestead content 非法:max_level 至少 2");
        }
        Set<String> kinds = Set.of(SkillDef.KIND_GATHER, SkillDef.KIND_PROCESS);
        for (SkillDef skill : skills) {
            if (!kinds.contains(skill.kind())) {
                throw new IllegalStateException("homestead content 非法:技能 " + skill.code() + " kind=" + skill.kind());
            }
            if (actionsOf(skill.code()).isEmpty()) {
                throw new IllegalStateException("homestead content 非法:技能 " + skill.code() + " 没有动作");
            }
        }
        Set<String> categories = Set.of(ItemDef.CAT_MATERIAL, ItemDef.CAT_PRODUCT, ItemDef.CAT_RARE);
        for (ItemDef item : items) {
            if (!categories.contains(item.category())) {
                throw new IllegalStateException("homestead content 非法:物品 " + item.code() + " category=" + item.category());
            }
            if (item.sellPrice() < 0) {
                throw new IllegalStateException("homestead content 非法:物品 " + item.code() + " sell_price < 0");
            }
            if (item.buff() != null) {
                validateEffect("物品 " + item.code() + " buff", item.buff().kind(), item.buff().target(),
                        item.buff().bp(), 0);
                if (item.buff().minutes() <= 0) {
                    throw new IllegalStateException("homestead content 非法:物品 " + item.code() + " buff minutes 非法");
                }
            }
        }

        for (ActionDef action : actions) {
            SkillDef skill = skillByCode.get(action.skill());
            if (skill == null) {
                throw new IllegalStateException("homestead content 非法:动作 " + action.code() + " 指向未知技能");
            }
            if (action.level() < 1 || action.level() > maxLevel) {
                throw new IllegalStateException("homestead content 非法:动作 " + action.code() + " level 越界");
            }
            if (action.seconds() < 1 || action.xp() < 0) {
                throw new IllegalStateException("homestead content 非法:动作 " + action.code() + " 耗时/经验非法");
            }
            if (action.yields().isEmpty()) {
                throw new IllegalStateException("homestead content 非法:动作 " + action.code() + " 没有产出");
            }
            // 采集技能只有野外动作不耗材;工坊动作(需场地)可以做「整理/提炼」这类转换
            if (skill.kind().equals(SkillDef.KIND_GATHER) && action.consumesMaterials() && !action.needsBuilding()) {
                throw new IllegalStateException("homestead content 非法:采集动作 " + action.code() + " 不应消耗材料");
            }
            if (skill.kind().equals(SkillDef.KIND_PROCESS) && !action.consumesMaterials()) {
                throw new IllegalStateException("homestead content 非法:加工动作 " + action.code() + " 应消耗材料");
            }
            requireItems(action.inputs(), "动作 " + action.code() + " 输入");
            requireItems(action.yields(), "动作 " + action.code() + " 产出");
            if (action.rare() != null) {
                if (itemByCode.get(action.rare().item()) == null) {
                    throw new IllegalStateException("homestead content 非法:动作 " + action.code() + " 稀有物品不存在");
                }
                if (action.rare().chanceBp() < 1 || action.rare().chanceBp() > HomesteadConst.BP_BASE
                        || action.rare().qty() < 1) {
                    throw new IllegalStateException("homestead content 非法:动作 " + action.code() + " rare 参数越界");
                }
            }
            if (action.needsBuilding()) {
                BuildingDef building = buildingByCode.get(action.requireBuilding());
                if (building == null) {
                    throw new IllegalStateException("homestead content 非法:动作 " + action.code() + " 指向未知场地");
                }
                if (!action.code().equals(building.unlockAction())) {
                    throw new IllegalStateException("homestead content 非法:场地 " + building.code() + " 的解锁动作不匹配");
                }
                if (action.requireBuildingLevel() < 1 || action.requireBuildingLevel() > building.maxLevel()) {
                    throw new IllegalStateException("homestead content 非法:动作 " + action.code() + " 建筑门槛越界");
                }
                if (building.skill() != null && !skill.code().equals(building.skill())) {
                    throw new IllegalStateException("homestead content 非法:动作 " + action.code() + " 与场地技能不一致");
                }
            }
            Set<String> yieldCodes = new HashSet<>();
            action.yields().forEach(y -> yieldCodes.add(y.item()));
            for (ActionDef.ItemAmount input : action.inputs()) {
                if (yieldCodes.contains(input.item())) {
                    throw new IllegalStateException("homestead content 非法:动作 " + action.code() + " 产出回流自身输入");
                }
            }
        }

        for (ToolDef tool : tools) {
            if (!skillByCode.containsKey(tool.skill())) {
                throw new IllegalStateException("homestead content 非法:工具 " + tool.code() + " 指向未知技能 " + tool.skill());
            }
            ActionDef action = actionByCode.get(tool.action());
            if (action == null) {
                throw new IllegalStateException("homestead content 非法:工具 " + tool.code() + " 指向未知动作 " + tool.action());
            }
            if (!action.skill().equals(tool.skill())) {
                throw new IllegalStateException("homestead content 非法:工具 " + tool.code() + " 技能与动作不一致");
            }
            if (tool.requireLevel() != action.level()) {
                throw new IllegalStateException("homestead content 非法:工具 " + tool.code() + " 门槛应等于动作门槛");
            }
            if (tool.steps().size() != HomesteadConst.MAX_TOOL_TIER) {
                throw new IllegalStateException("homestead content 非法:工具 " + tool.code()
                        + " steps 应为 " + HomesteadConst.MAX_TOOL_TIER + " 阶");
            }
            if (tool.effects().isEmpty()) {
                throw new IllegalStateException("homestead content 非法:工具 " + tool.code() + " 没有效果");
            }
            for (ToolDef.ToolEffect effect : tool.effects()) {
                validateEffect("工具 " + tool.code(), effect.kind(), EffectKind.TARGET_ALL, effect.bp(), 0);
            }
            for (ToolDef.ToolStep step : tool.steps()) {
                if (step.coins() < 0) {
                    throw new IllegalStateException("homestead content 非法:工具 " + tool.code() + " 有负花费");
                }
                requireItems(step.materials(), "工具 " + tool.code());
            }
        }
        // 动作 ↔ 工具一一对应
        for (ActionDef action : actions) {
            if (!toolByAction.containsKey(action.code())) {
                throw new IllegalStateException("homestead content 非法:动作 " + action.code() + " 没有对应工具");
            }
        }

        if (equipment.size() != HomesteadConst.SLOT_COUNT * HomesteadConst.GEAR_PER_SLOT) {
            throw new IllegalStateException("homestead content 非法:装备数量应为 5 部位 × 3 件");
        }
        Set<String> slotsSeen = new HashSet<>();
        Map<String, Integer> perSlot = new HashMap<>();
        for (EquipmentDef gear : equipment) {
            if (!HomesteadConst.GEAR_SLOTS.contains(gear.slot())) {
                throw new IllegalStateException("homestead content 非法:装备 " + gear.code() + " 部位未知 " + gear.slot());
            }
            slotsSeen.add(gear.slot());
            perSlot.merge(gear.slot(), 1, Integer::sum);
            SkillDef skill = skillByCode.get(gear.skill());
            if (skill == null) {
                throw new IllegalStateException("homestead content 非法:装备 " + gear.code() + " 指向未知技能");
            }
            if (gear.requireLevel() < 1 || gear.requireLevel() > maxLevel) {
                throw new IllegalStateException("homestead content 非法:装备 " + gear.code() + " 等级门槛越界");
            }
            if (gear.effects().isEmpty()) {
                throw new IllegalStateException("homestead content 非法:装备 " + gear.code() + " 没有效果");
            }
            for (EquipmentDef.EffectDef effect : gear.effects()) {
                validateEffect("装备 " + gear.code(), effect.kind(), effect.target(), effect.bp(), effect.hours());
            }
            if (gear.craft() == null || gear.craft().coins() < 0) {
                throw new IllegalStateException("homestead content 非法:装备 " + gear.code() + " 缺少制造花费");
            }
            requireItems(gear.craft().materials(), "装备 " + gear.code() + " 制造");
            if (gear.steps().size() != HomesteadConst.MAX_GEAR_TIER) {
                throw new IllegalStateException("homestead content 非法:装备 " + gear.code()
                        + " steps 应为 " + HomesteadConst.MAX_GEAR_TIER + " 级");
            }
            for (EquipmentDef.EquipmentStep step : gear.steps()) {
                if (step.coins() < 0) {
                    throw new IllegalStateException("homestead content 非法:装备 " + gear.code() + " 有负花费");
                }
                requireItems(step.materials(), "装备 " + gear.code());
            }
        }
        if (!slotsSeen.equals(HomesteadConst.GEAR_SLOTS)) {
            throw new IllegalStateException("homestead content 非法:装备部位不全 " + slotsSeen);
        }
        perSlot.forEach((slot, count) -> {
            if (count != HomesteadConst.GEAR_PER_SLOT) {
                throw new IllegalStateException("homestead content 非法:部位 " + slot + " 装备数量应为 3");
            }
        });

        for (BuildingDef building : buildings) {
            if (building.maxLevel() < 1 || building.steps().size() != building.maxLevel()) {
                throw new IllegalStateException("homestead content 非法:场地 " + building.code() + " max_level/steps 不一致");
            }
            if (building.effects().isEmpty()) {
                throw new IllegalStateException("homestead content 非法:场地 " + building.code() + " 没有效果");
            }
            for (BuildingDef.EffectDef effect : building.effects()) {
                validateEffect("场地 " + building.code(), effect.kind(), effect.target(), effect.bp(), effect.hours());
            }
            for (BuildingDef.BuildingStep step : building.steps()) {
                if (step.coins() < 0) {
                    throw new IllegalStateException("homestead content 非法:场地 " + building.code() + " 有负花费");
                }
                requireItems(step.materials(), "场地 " + building.code());
            }
            if (building.skill() != null && !skillByCode.containsKey(building.skill())) {
                throw new IllegalStateException("homestead content 非法:场地 " + building.code() + " 指向未知技能");
            }
            if (building.unlockAction() != null) {
                ActionDef action = actionByCode.get(building.unlockAction());
                if (action == null) {
                    throw new IllegalStateException("homestead content 非法:场地 " + building.code() + " 指向未知动作");
                }
                if (!building.code().equals(action.requireBuilding())) {
                    throw new IllegalStateException("homestead content 非法:场地 " + building.code() + " 与动作门槛不匹配");
                }
            }
        }
    }

    private void validateEffect(String where, String kind, String target, int bp, int hours) {
        Set<String> effectKinds = Set.of(EffectKind.SPEED, EffectKind.XP, EffectKind.YIELD, EffectKind.RARE,
                EffectKind.EFFICIENCY, EffectKind.OFFLINE_CAP, EffectKind.SELL_BONUS);
        Set<String> targets = Set.of(EffectKind.TARGET_GATHER, EffectKind.TARGET_PROCESS, EffectKind.TARGET_ALL);
        if (!effectKinds.contains(kind)) {
            throw new IllegalStateException("homestead content 非法:" + where + " kind 未知 " + kind);
        }
        if (!targets.contains(target)) {
            throw new IllegalStateException("homestead content 非法:" + where + " target 未知 " + target);
        }
        switch (kind) {
            case EffectKind.OFFLINE_CAP -> {
                if (hours <= 0) {
                    throw new IllegalStateException("homestead content 非法:" + where + " OFFLINE_CAP hours 非法");
                }
            }
            default -> {
                if (bp <= 0) {
                    throw new IllegalStateException("homestead content 非法:" + where + " bp 非法");
                }
            }
        }
    }

    private void requireItems(List<ActionDef.ItemAmount> amounts, String where) {
        for (ActionDef.ItemAmount amount : amounts) {
            if (itemByCode.get(amount.item()) == null) {
                throw new IllegalStateException("homestead content 非法:" + where + " 引用未知物品 " + amount.item());
            }
            if (amount.qty() < 1) {
                throw new IllegalStateException("homestead content 非法:" + where + " 数量非法 " + amount.qty());
            }
        }
    }

    /** sha256 十六进制(64 字符) */
    private String computeVersion() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            List<String> names = List.of("actions.json", "buildings.json", "equipment.json",
                    "items.json", "skills.json", "tools.json");
            for (String name : names.stream().sorted().toList()) {
                byte[] bytes;
                try (var in = new ClassPathResource("content/homestead/" + name).getInputStream()) {
                    bytes = in.readAllBytes();
                }
                digest.update(name.getBytes(StandardCharsets.UTF_8));
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new IllegalStateException("content_version 计算失败", e);
        }
    }

    public int maxLevel() {
        return maxLevel;
    }

    /** 从 level 升到 level+1 所需经验;已满级返回 0 */
    public int xpForNextLevel(int level) {
        return level >= 1 && level <= xpToNext.length ? xpToNext[level - 1] : 0;
    }

    public List<SkillDef> skills() {
        return skills;
    }

    public SkillDef skill(String code) {
        return code == null ? null : skillByCode.get(code);
    }

    public List<ItemDef> items() {
        return items;
    }

    public ItemDef item(String code) {
        return code == null ? null : itemByCode.get(code);
    }

    public List<ActionDef> actions() {
        return actions;
    }

    public ActionDef action(String code) {
        return code == null ? null : actionByCode.get(code);
    }

    public List<ActionDef> actionsOf(String skillCode) {
        return actionsBySkill.getOrDefault(skillCode, List.of());
    }

    public List<ToolDef> tools() {
        return tools;
    }

    public ToolDef tool(String code) {
        return code == null ? null : toolByCode.get(code);
    }

    public ToolDef toolOfAction(String actionCode) {
        return actionCode == null ? null : toolByAction.get(actionCode);
    }

    public List<ToolDef> toolsOfSkill(String skillCode) {
        return toolsBySkill.getOrDefault(skillCode, List.of());
    }

    public List<EquipmentDef> equipment() {
        return equipment;
    }

    public EquipmentDef gear(String code) {
        return code == null ? null : equipmentByCode.get(code);
    }

    public List<BuildingDef> buildings() {
        return buildings;
    }

    public BuildingDef building(String code) {
        return code == null ? null : buildingByCode.get(code);
    }

    public String contentVersion() {
        return contentVersion;
    }

    /** 物品卖价(未知物品按 0,调用方先校验存在性) */
    public long sellPrice(String itemCode) {
        ItemDef item = item(itemCode);
        return item == null ? 0L : item.sellPrice();
    }
}
