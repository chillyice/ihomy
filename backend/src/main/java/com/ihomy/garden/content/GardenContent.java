package com.ihomy.garden.content;

import com.ihomy.garden.GardenConst;
import com.ihomy.garden.core.GardenSeason;
import com.ihomy.garden.core.GardenSolarTerms;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 内容配置装载(content/garden/*.json,classpath):作物 / 场域 / 信件文案。
 * 唯一真相源是 garden 仓 content/,构建期拷贝到 backend resources(需求设计说明书 §5.2、架构分册 §16.3)。
 * content_version = sha256(按文件名排序拼接「文件名 + 文件内容」),是存档闸门与 meta 协商的依据(§25.3)。
 * 启动即装载、缺失即失败(配置完整性优先)。
 */
@Slf4j
@Component
public class GardenContent {

    private final List<CropDef> crops;
    private final Map<String, CropDef> cropByCode;
    private final List<VenueDef> venues;
    private final Map<String, VenueDef> venueByCode;
    private final MailDef mail;
    private final List<SolarTermDef> solarTerms;
    private final List<TaskDef> tasks;
    private final Map<String, TaskDef> taskByCode;
    private final List<SkillDefs.SkillTreeDef> skillTrees;
    private final Map<String, SkillDefs.SkillNodeDef> skillNodeByTree;
    private final Map<String, SkillDefs.SkillTreeDef> skillTreeByCode;
    private final String contentVersion;

    public GardenContent() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            this.crops = load(mapper, "crops.json", CropsFile.class).crops();
            this.venues = load(mapper, "venues.json", VenuesFile.class).venues();
            this.mail = load(mapper, "mail.json", MailDef.class);
            this.solarTerms = load(mapper, "solar_terms.json", TermsFile.class).terms();
            this.tasks = load(mapper, "tasks.json", TasksFile.class).tasks();
            this.skillTrees = load(mapper, "skills.json", SkillDefs.SkillFile.class).trees();
        } catch (Exception e) {
            throw new IllegalStateException("garden content 加载失败:检查 resources/content/garden/*.json", e);
        }
        GardenSolarTerms.validate(solarTerms);
        validateTaskTitles(tasks);
        validateCropTemps(crops);
        validateWeekly(tasks);
        this.cropByCode = crops.stream().collect(Collectors.toUnmodifiableMap(CropDef::code, Function.identity()));
        this.venueByCode = venues.stream().collect(Collectors.toUnmodifiableMap(VenueDef::code, Function.identity()));
        this.taskByCode = tasks.stream().collect(Collectors.toUnmodifiableMap(TaskDef::code, Function.identity()));
        Map<String, SkillDefs.SkillNodeDef> nodeByTree = new HashMap<>();
        Map<String, SkillDefs.SkillTreeDef> treeByCode = new HashMap<>();
        for (SkillDefs.SkillTreeDef tree : skillTrees) {
            treeByCode.put(tree.code(), tree);
            for (SkillDefs.SkillNodeDef node : tree.nodes()) {
                nodeByTree.put(tree.code() + "/" + node.code(), node);
            }
        }
        this.skillNodeByTree = Map.copyOf(nodeByTree);
        this.skillTreeByCode = Map.copyOf(treeByCode);
        this.contentVersion = computeVersion();
        log.info("[garden] content loaded: {} crops, {} venues, {} terms, {} tasks, {} skill trees, content_version={}",
                crops.size(), venues.size(), solarTerms.size(), tasks.size(), skillTrees.size(), contentVersion);
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private record CropsFile(@com.fasterxml.jackson.annotation.JsonProperty("crops") List<CropDef> crops) {
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private record VenuesFile(@com.fasterxml.jackson.annotation.JsonProperty("venues") List<VenueDef> venues) {
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private record TermsFile(@com.fasterxml.jackson.annotation.JsonProperty("terms") List<SolarTermDef> terms) {
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private record TasksFile(@com.fasterxml.jackson.annotation.JsonProperty("tasks") List<TaskDef> tasks) {
    }

    private <T> T load(ObjectMapper mapper, String name, Class<T> type) throws Exception {
        try (var in = new ClassPathResource("content/garden/" + name).getInputStream()) {
            return mapper.readValue(in, type);
        }
    }

    /**
     * 四季标题完整性(tasks.json 内容轮):needsSeasonVariants=true 的主线 / 年终链必须四季齐全且非空,
     * 否则启动即失败——宁可起不来,也不要玩家在某一季看到空标题。节气支线(TERM_SIDE)按设计不做变体。
     */
    private static void validateTaskTitles(List<TaskDef> tasks) {
        List<String> seasons = List.of(GardenSeason.SPRING, GardenSeason.SUMMER, GardenSeason.AUTUMN, GardenSeason.WINTER);
        int variants = 0;
        for (TaskDef t : tasks) {
            Map<String, String> bySeason = t.titlesBySeason();
            if (bySeason != null) {
                for (Map.Entry<String, String> e : bySeason.entrySet()) {
                    if (!seasons.contains(e.getKey())) {
                        throw new IllegalStateException("garden content 任务 " + t.code() + " titlesBySeason 含未知季节 " + e.getKey());
                    }
                    if (e.getValue() == null || e.getValue().isBlank()) {
                        throw new IllegalStateException("garden content 任务 " + t.code() + " titlesBySeason." + e.getKey() + " 为空");
                    }
                }
            }
            if (t.needsSeasonVariants()) {
                if (bySeason == null) {
                    throw new IllegalStateException("garden content 任务 " + t.code() + " 标记 needsSeasonVariants 但缺 titlesBySeason");
                }
                for (String s : seasons) {
                    if (!bySeason.containsKey(s)) {
                        throw new IllegalStateException("garden content 任务 " + t.code() + " titlesBySeason 缺 " + s);
                    }
                }
                variants++;
            }
        }
        log.info("[garden] task season variants ok: {} tasks × 4 seasons", variants);
    }

    /**
     * 作物耐温区间自检(玩法稿 §8.1):区间必须成序,且落在可信的摄氏度范围内。
     * 未配置不算错——按 hardiness 兜底(NORMAL 5–30 / TOLERANT 2–33 / EXTREME 0–35),
     * 这样新增作物漏写 temp_min_c / temp_max_c 也不会被 0 默认值判成「整片地受冻」。
     */
    private static void validateCropTemps(List<CropDef> crops) {
        int configured = 0;
        for (CropDef c : crops) {
            if (c.tempMinC() != null) {
                configured++;
                if (c.tempMinC() < -30 || c.tempMinC() > 30) {
                    throw new IllegalStateException("garden content 作物 " + c.code() + " temp_min_c=" + c.tempMinC()
                            + " 超出可信范围 [-30, 30]");
                }
            }
            if (c.tempMaxC() != null) {
                if (c.tempMaxC() < 0 || c.tempMaxC() > 50) {
                    throw new IllegalStateException("garden content 作物 " + c.code() + " temp_max_c=" + c.tempMaxC()
                            + " 超出可信范围 [0, 50]");
                }
            }
            if (c.effectiveTempMinC() >= c.effectiveTempMaxC()) {
                throw new IllegalStateException("garden content 作物 " + c.code() + " 耐温区间不成立:min="
                        + c.effectiveTempMinC() + " ≥ max=" + c.effectiveTempMaxC());
            }
        }
        log.info("[garden] crop temp ranges ok: {}/{} crops configured", configured, crops.size());
    }

    /**
     * 周挑战池自检(tasks.json,layer=WEEKLY):每周从池里轮换取 3 条,所以池要 ≥3 条、
     * 每条必须 window=WEEK 且带条件;order 唯一(轮换顺序即面板顺序)。周挑战不挂在阶段/节气上。
     */
    private static void validateWeekly(List<TaskDef> tasks) {
        List<TaskDef> weekly = tasks.stream().filter(t -> "WEEKLY".equals(t.layer())).toList();
        if (weekly.isEmpty()) {
            log.info("[garden] weekly challenge pool empty (content 未配 WEEKLY)");
            return;
        }
        if (weekly.size() < 3) {
            throw new IllegalStateException("garden content 周挑战池只有 " + weekly.size() + " 条(每周取 3 条,至少配 3 条)");
        }
        java.util.Set<Integer> orders = new java.util.HashSet<>();
        for (TaskDef t : weekly) {
            if (t.condition() == null || !"WEEK".equals(t.condition().window())) {
                throw new IllegalStateException("garden content 周挑战 " + t.code() + " 的 condition.window 必须是 WEEK");
            }
            if (t.stage() != null || t.term() != null) {
                throw new IllegalStateException("garden content 周挑战 " + t.code() + " 不挂阶段/节气(stage/term 必须留空)");
            }
            if (t.order() == null || !orders.add(t.order())) {
                throw new IllegalStateException("garden content 周挑战 " + t.code() + " order 缺失或重复");
            }
            if (t.isAuto()) {
                throw new IllegalStateException("garden content 周挑战 " + t.code() + " 不能是 auto 任务");
            }
        }
        log.info("[garden] weekly challenge pool ok: {} defs, rotation 3/week", weekly.size());
    }

    public List<CropDef> crops() {
        return crops;
    }

    public CropDef crop(String code) {
        return code == null ? null : cropByCode.get(code);
    }

    public List<VenueDef> venues() {
        return venues;
    }

    public VenueDef venue(String code) {
        return code == null ? null : venueByCode.get(code);
    }

    public MailDef mail() {
        return mail;
    }

    public String contentVersion() {
        return contentVersion;
    }

    /** 当前阶段已解锁的作物(最小闭环 = 阶段 1 的 8 种) */
    public List<CropDef> unlockedCrops() {
        return crops.stream().filter(c -> c.unlockStage() <= GardenConst.WORLD_STAGE).toList();
    }

    /** 24 节气表(轮转/推导见 GardenSolarTerms) */
    public List<SolarTermDef> solarTerms() {
        return solarTerms;
    }

    public List<TaskDef> tasks() {
        return tasks;
    }

    public TaskDef task(String code) {
        return code == null ? null : taskByCode.get(code);
    }

    /** 周挑战池(按 order 排好,轮换直接用 GardenWeek.pick) */
    public List<TaskDef> weeklyPool() {
        return tasks.stream()
                .filter(t -> "WEEKLY".equals(t.layer()))
                .sorted(java.util.Comparator.comparing(t -> t.order() == null ? Integer.MAX_VALUE : t.order()))
                .toList();
    }

    public List<SkillDefs.SkillTreeDef> skillTrees() {
        return skillTrees;
    }

    public SkillDefs.SkillTreeDef skillTree(String code) {
        return code == null ? null : skillTreeByCode.get(code);
    }

    /** 按 treeCode + nodeCode 取节点(键 = 树码/节点码) */
    public SkillDefs.SkillNodeDef skillNode(String treeCode, String nodeCode) {
        return treeCode == null || nodeCode == null ? null : skillNodeByTree.get(treeCode + "/" + nodeCode);
    }

    /** sha256 十六进制(64 字符) */
    private String computeVersion() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            List<String> names = List.of("crops.json", "mail.json", "solar_terms.json", "skills.json", "tasks.json", "venues.json");
            for (String name : names.stream().sorted().toList()) {
                byte[] bytes;
                try (var in = new ClassPathResource("content/garden/" + name).getInputStream()) {
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
}
