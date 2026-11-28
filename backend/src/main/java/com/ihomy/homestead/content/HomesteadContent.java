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
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 小院慢生活内容配置(content/homestead/*.json,classpath):
 * 技能 / 物品 / 动作 / 工具。启动即装载、校验不过即失败(内容完整性优先于可用性)。
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
    private final Map<String, ToolDef> toolBySkill;
    private final String contentVersion;

    public HomesteadContent() {
        ObjectMapper mapper = new ObjectMapper();
        SkillsFile skillsFile;
        try {
            skillsFile = load(mapper, "skills.json", SkillsFile.class);
            this.items = List.copyOf(load(mapper, "items.json", ItemsFile.class).items());
            this.actions = List.copyOf(load(mapper, "actions.json", ActionsFile.class).actions());
            this.tools = List.copyOf(load(mapper, "tools.json", ToolsFile.class).tools());
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
        Map<String, List<ActionDef>> bySkill = new HashMap<>();
        for (ActionDef action : actions) {
            bySkill.computeIfAbsent(action.skill(), k -> new java.util.ArrayList<>()).add(action);
        }
        bySkill.replaceAll((k, v) -> List.copyOf(v));
        this.actionsBySkill = Map.copyOf(bySkill);
        Map<String, ToolDef> toolPerSkill = new HashMap<>();
        for (ToolDef tool : tools) {
            toolPerSkill.put(tool.skill(), tool);
        }
        this.toolBySkill = Map.copyOf(toolPerSkill);
        validate();
        this.contentVersion = computeVersion();
        log.info("[homestead] content loaded: {} skills, {} items, {} actions, {} tools, maxLevel={}, content_version={}",
                skills.size(), items.size(), actions.size(), tools.size(), maxLevel, contentVersion);
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

    /** 内容自洽校验:引用完整、门槛单调、无自喂循环、每技能一件工具 */
    private void validate() {
        if (maxLevel < 2) {
            throw new IllegalStateException("homestead content 非法:max_level 至少 2");
        }
        Set<String> kinds = Set.of(SkillDef.KIND_GATHER, SkillDef.KIND_PROCESS);
        for (SkillDef skill : skills) {
            if (!kinds.contains(skill.kind())) {
                throw new IllegalStateException("homestead content 非法:技能 " + skill.code() + " kind=" + skill.kind());
            }
            if (!toolBySkill.containsKey(skill.code())) {
                throw new IllegalStateException("homestead content 非法:技能 " + skill.code() + " 没有工具");
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
        }
        for (ToolDef tool : tools) {
            if (!skillByCode.containsKey(tool.skill())) {
                throw new IllegalStateException("homestead content 非法:工具 " + tool.code() + " 指向未知技能 " + tool.skill());
            }
            if (tool.steps().size() != HomesteadConst.MAX_TOOL_TIER) {
                throw new IllegalStateException("homestead content 非法:工具 " + tool.code()
                        + " steps 应为 " + HomesteadConst.MAX_TOOL_TIER + " 阶");
            }
            for (ToolDef.ToolStep step : tool.steps()) {
                if (step.coins() < 0) {
                    throw new IllegalStateException("homestead content 非法:工具 " + tool.code() + " 有负花费");
                }
                requireItems(step.materials(), "工具 " + tool.code());
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
            if (skill.kind().equals(SkillDef.KIND_GATHER) && action.consumesMaterials()) {
                throw new IllegalStateException("homestead content 非法:采集动作 " + action.code() + " 不应消耗材料");
            }
            requireItems(action.inputs(), "动作 " + action.code() + " 输入");
            requireItems(action.yields(), "动作 " + action.code() + " 产出");
            if (action.rare() != null) {
                if (itemByCode.get(action.rare().item()) == null) {
                    throw new IllegalStateException("homestead content 非法:动作 " + action.code() + " 稀有物品不存在");
                }
                if (action.rare().every() < 1 || action.rare().qty() < 1) {
                    throw new IllegalStateException("homestead content 非法:动作 " + action.code() + " rare 参数越界");
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
            List<String> names = List.of("actions.json", "items.json", "skills.json", "tools.json");
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

    public ToolDef toolOfSkill(String skillCode) {
        return skillCode == null ? null : toolBySkill.get(skillCode);
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
