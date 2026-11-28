package com.ihomy.garden.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.garden.GardenConst;
import com.ihomy.garden.content.GardenContent;
import com.ihomy.garden.content.TaskDef;
import com.ihomy.garden.core.GardenSolarTerms;
import com.ihomy.garden.core.GardenWeek;
import com.ihomy.garden.entity.GardenCodex;
import com.ihomy.garden.entity.GardenInventory;
import com.ihomy.garden.entity.GardenLog;
import com.ihomy.garden.entity.GardenMember;
import com.ihomy.garden.entity.GardenWallet;
import com.ihomy.garden.entity.GardenWorld;
import com.ihomy.garden.mapper.GardenCodexMapper;
import com.ihomy.garden.mapper.GardenInventoryMapper;
import com.ihomy.garden.mapper.GardenLogMapper;
import com.ihomy.garden.mapper.GardenMemberMapper;
import com.ihomy.garden.mapper.GardenPlotMapper;
import com.ihomy.garden.mapper.GardenStatMapper;
import com.ihomy.garden.mapper.GardenWalletMapper;
import com.ihomy.garden.mapper.GardenWorldMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 任务链侧(详细设计 §4/§5):五层任务的可见性 / 完成度推导(不落库)/ 领取 / 推阶。
 * 条件只用 7 类可增量求和的计数;领取与推阶走确定性合成 op_id(uk_op_id 兜底并发,不建表);
 * 闸门三要素 = 主线链(当前阶)+ 节气到 + 园币(只对新场域收费)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GardenTaskService {

    private final GardenGrowthService growth;
    private final GardenContent content;
    private final GardenLogMapper logMapper;
    private final GardenStatMapper statMapper;
    private final GardenInventoryMapper inventoryMapper;
    private final GardenCodexMapper codexMapper;
    private final GardenPlotMapper plotMapper;
    private final GardenMemberMapper memberMapper;
    private final GardenWalletMapper walletMapper;
    private final GardenWorldMapper worldMapper;
    private final ObjectMapper objectMapper;

    // ---------- 面板 ----------

    /** GET /garden/tasks:推进面板 + 五层任务(可见性/完成度/领取状态,详细设计 §5.1) */
    public Map<String, Object> panel(Long familyId, String settings, String playerUid) {
        String season = com.ihomy.garden.core.GardenSeason.seasonOf(LocalDate.now());
        int stage = growth.stageOf(familyId, settings);
        int termCount = growth.termCount(settings);
        GardenSolarTerms.Occurrence term = growth.currentTerm(settings);
        final String currentTermCode = term == null ? null : term.term().code();
        Set<String> claimed = claimedKeys(familyId);
        List<TaskDef> weeklyActive = weeklyActive(settings);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("stage", stagePanel(familyId, settings, stage, termCount, playerUid));
        out.put("starter", taskViews(tasksOf("STARTER"), familyId, settings, stage, termCount, claimed, season,
                t -> starterVisible(t, claimed)));
        out.put("main", taskViews(tasksOf("MAIN"), familyId, settings, stage, termCount, claimed, season,
                t -> t.stage() != null && t.stage() == stage));
        out.put("mid", taskViews(tasksOf("MID"), familyId, settings, stage, termCount, claimed, season,
                t -> t.stage() != null && t.stage() == stage));
        out.put("termSide", taskViews(tasksOf("TERM_SIDE"), familyId, settings, stage, termCount, claimed, season,
                t -> currentTermCode != null && currentTermCode.equals(t.term())));
        out.put("side", taskViews(tasksOf("SIDE"), familyId, settings, stage, termCount, claimed, season, t -> true));
        out.put("final", taskViews(tasksOf("FINAL"), familyId, settings, stage, termCount, claimed, season,
                t -> stage == 9 && termCount >= GardenConst.FINAL_STAGE_MIN_TERM));
        out.put("weekly", taskViews(weeklyActive, familyId, settings, stage, termCount, claimed, season, t -> true));
        out.put("week", weekInfo());
        out.put("world", Map.of("termCount", termCount, "termCode", currentTermCode == null ? "" : currentTermCode,
                "season", season));
        return out;
    }

    /** 本周窗口(前端直接显示,不必再算周一;key 与领取凭证同源,便于对日志) */
    private Map<String, Object> weekInfo() {
        LocalDate today = LocalDate.now();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("key", GardenWeek.key(today));
        out.put("start", GardenWeek.start(today).toString());
        out.put("end", GardenWeek.end(today).toString());
        out.put("perWeek", GardenConst.WEEKLY_PER_WEEK);
        return out;
    }

    /** 推进面板:亮「可解锁」或写明缺什么(§5.1);canAdvance 按发起人钱包算(推阶由发起人付币) */
    private Map<String, Object> stagePanel(Long familyId, String settings, int stage, int termCount, String playerUid) {
        int target = Math.min(stage + 1, GardenConst.FINAL_STAGE);
        int requiredTerm = targetTermRequirement(target);
        List<GardenSolarTerms.Occurrence> occ = growth.occurrences(settings, Math.max(30, requiredTerm + 1));
        String termDate = requiredTerm <= occ.size() ? occ.get(requiredTerm - 1).date().toString() : null;
        List<String> missing = missingMain(familyId, settings, stage, target);
        int coins = GardenConst.STAGE_COINS.getOrDefault(target, 0);
        long wallet = walletOf(familyId, playerUid);
        boolean termOk = termCount >= requiredTerm;
        boolean tasksOk = missing.isEmpty();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("current", stage);
        out.put("next", target);
        out.put("termRequired", requiredTerm);
        out.put("termCount", termCount);
        out.put("termDate", termDate);
        out.put("coinsRequired", coins);
        out.put("coinsShort", Math.max(0, coins - (int) wallet));
        out.put("missingMain", missing);
        out.put("canAdvance", termOk && tasksOk && wallet >= coins && stage < GardenConst.FINAL_STAGE);
        return out;
    }

    // ---------- 领取 ----------

    /** POST /garden/tasks/{code}/claim:校可见+已完+未领 → 奖励(币进领取人钱包、种子进家庭仓);幂等 */
    @Transactional
    public Map<String, Object> claim(Long familyId, GardenMember me, String settings, String taskCode) {
        TaskDef task = content.task(taskCode);
        if (task == null || task.isAuto()) {
            throw new BizException(ResultCode.NOT_FOUND, "没有这个任务");
        }
        int stage = growth.stageOf(familyId, settings);
        int termCount = growth.termCount(settings);
        Set<String> claimed = claimedKeys(familyId);
        LocalDate today = LocalDate.now();
        Set<String> weeklyCodes = weeklyActive(settings).stream().map(TaskDef::code)
                .collect(java.util.stream.Collectors.toSet());
        if (!visible(task, stage, termCount, currentTermCode(settings), claimed, weeklyCodes)) {
            throw new BizException(ResultCode.NOT_FOUND, "这个任务还没出现在桌上");
        }
        if (claimed.contains(claimKey(task, today))) {
            return receipt(familyId, taskCode, true, null, null);
        }
        LocalDate windowStart = windowStart(familyId, settings, task, stage, termCount);
        int progress = progress(task, familyId, windowStart);
        if (progress < task.condition().value()) {
            throw new BizException(ResultCode.CONFLICT,
                    "还差一点:" + task.condition().type() + " " + progress + "/" + task.condition().value());
        }
        // 确定性 op_id(节气支线带年份,次年同节气可再做;周挑战带 ISO 周键,下周可再做;其余一次性)
        String windowKey = claimWindow(task, today);
        GardenLog row = new GardenLog();
        row.setFamilyId(familyId);
        row.setActorUid(me.getPlayerUid());
        row.setOpId(GardenConst.synthOpId('C', "task-claim", familyId, taskCode, windowKey));
        row.setLamport(0L);
        row.setOrigin("IHOMY");
        row.setAction("TASK_CLAIM");
        row.setCropCode(taskCode);
        if (!"ALL".equals(windowKey)) {
            row.setVariant(windowKey);
        }
        row.setDetail("领了任务奖励:" + task.title());
        try {
            logMapper.insert(row);
        } catch (DuplicateKeyException e) {
            return receipt(familyId, taskCode, true, null, null);
        }
        Integer coins = task.reward() == null ? null : task.reward().coins();
        String seed = task.reward() == null ? null : task.reward().seed();
        if (coins != null && coins > 0) {
            walletMapper.insertIgnore(familyId, me.getPlayerUid());
            walletMapper.add(familyId, me.getPlayerUid(), coins);
            statMapper.upsertDeltas(familyId, LocalDate.now(), 0, 0, 0, coins, 0, 0);
        }
        if (seed != null && !seed.isBlank()) {
            inventoryMapper.upsertAdd(familyId, seed, GardenConst.ITEM_TYPE_CONSUMABLE, 1);
        }
        memberMapper.touchActive(familyId, me.getPlayerUid(), LocalDate.now());
        log.info("[garden] task claim family={} task={} coins={} seed={} opId={}",
                familyId, taskCode, coins, seed, row.getOpId());
        return receipt(familyId, taskCode, false, coins, seed);
    }

    // ---------- 推阶(详细设计 §2.2)----------

    /** POST /garden/stage/advance:三闸门(主线链 + 节气 + 园币)→ STAGE_ADVANCE;新地块随引导补行 */
    @Transactional
    public Map<String, Object> advance(Long familyId, GardenMember me, String settings) {
        worldMapper.lockById(familyId);
        int stage = growth.stageOf(familyId, settings);
        if (stage >= GardenConst.FINAL_STAGE) {
            throw new BizException(ResultCode.CONFLICT, "大棚都开过了,先把这一园种好");
        }
        int target = stage + 1;
        int requiredTerm = targetTermRequirement(target);
        int termCount = growth.termCount(settings);
        if (termCount < requiredTerm) {
            List<GardenSolarTerms.Occurrence> occ = growth.occurrences(settings, Math.max(30, requiredTerm + 1));
            String date = requiredTerm <= occ.size() ? occ.get(requiredTerm - 1).date().toString() : "?";
            throw new BizException(ResultCode.CONFLICT,
                    "第 " + requiredTerm + " 个节气(" + date + " 前后)才开,现在还早");
        }
        List<String> missing = missingMain(familyId, settings, stage, target);
        if (!missing.isEmpty()) {
            throw new BizException(ResultCode.CONFLICT, "还差 " + missing.size() + " 件主线任务:" + String.join("、", missing));
        }
        int coins = GardenConst.STAGE_COINS.getOrDefault(target, 0);
        // 确定性 op_id:同阶并发双推只有一个成功;先落账再扣币,扣币失败整体回滚
        GardenLog row = new GardenLog();
        row.setFamilyId(familyId);
        row.setActorUid(me.getPlayerUid());
        row.setOpId(GardenConst.synthOpId('S', "stage-advance", familyId, target));
        row.setLamport(0L);
        row.setOrigin("IHOMY");
        row.setAction("STAGE_ADVANCE");
        row.setCropCode(String.valueOf(target));
        row.setDetail("推到了第 " + target + " 阶,新地方和新作物一起来了");
        try {
            logMapper.insert(row);
        } catch (DuplicateKeyException e) {
            Map<String, Object> out = advanceReceipt(familyId, settings, stage, target, coins, true);
            out.put("alreadyDone", true);
            return out;
        }
        if (coins > 0) {
            chargeCoins(familyId, me.getPlayerUid(), coins);
            statMapper.upsertDeltas(familyId, LocalDate.now(), 0, 0, 0, 0, coins, 0);
        }
        memberMapper.touchActive(familyId, me.getPlayerUid(), LocalDate.now());
        log.info("[garden] stage advance family={} -> {} coins={} opId={}", familyId, target, coins, row.getOpId());
        Map<String, Object> out = advanceReceipt(familyId, settings, stage, target, coins, false);
        if (target == GardenConst.FINAL_STAGE) {
            out.put("finalRite", true);   // 年终仪式:⑤立架随推阶完成(详细设计 §5.4)
        }
        return out;
    }

    // ---------- 内部 ----------

    private Map<String, Object> advanceReceipt(Long familyId, String settings, int stage, int target, int coins, boolean alreadyDone) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("stage", target);
        out.put("previous", stage);
        out.put("coinsSpent", coins);
        out.put("plotsAdded", plotsAddedAt(target, familyId));
        out.put("worldStage", growth.stageOf(familyId, settings));
        out.put("alreadyDone", alreadyDone);
        return out;
    }

    private List<TaskDef> tasksOf(String layer) {
        return content.tasks().stream().filter(t -> layer.equals(t.layer())).toList();
    }

    private List<Map<String, Object>> taskViews(List<TaskDef> tasks, Long familyId, String settings, int stage,
                                                int termCount, Set<String> claimed, String season,
                                                java.util.function.Predicate<TaskDef> visibleFn) {
        List<Map<String, Object>> out = new ArrayList<>();
        String termCode = currentTermCode(settings);
        LocalDate today = LocalDate.now();
        for (TaskDef t : tasks) {
            boolean visible = visibleFn.test(t);
            boolean isAuto = t.isAuto();
            LocalDate windowStart = windowStart(familyId, settings, t, stage, termCount);
            int progress = isAuto ? 0 : progress(t, familyId, windowStart);
            boolean completed;
            if (isAuto) {
                completed = "STAGE_ADVANCE_10".equals(t.auto())
                        && logMapper.selectCount(new LambdaQueryWrapper<GardenLog>()
                        .eq(GardenLog::getFamilyId, familyId)
                        .eq(GardenLog::getAction, "STAGE_ADVANCE")
                        .eq(GardenLog::getCropCode, String.valueOf(GardenConst.FINAL_STAGE))) > 0;
            } else {
                completed = progress >= t.condition().value();
            }
            boolean claimedFlag = claimed.contains(claimKey(t, today));
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("code", t.code());
            v.put("layer", t.layer());
            v.put("stage", t.stage());
            v.put("term", t.term());
            v.put("title", t.titleFor(season));
            v.put("condition", Map.of("type", t.condition().type(), "value", t.condition().value(),
                    "window", t.condition().window() == null ? "" : t.condition().window()));
            v.put("progress", progress);
            v.put("completed", completed);
            v.put("claimed", claimedFlag);
            v.put("visible", visible);
            v.put("auto", isAuto);
            v.put("reward", t.reward() == null ? Map.of() : Map.of(
                    "coins", t.reward().coins() == null ? 0 : t.reward().coins(),
                    "seed", t.reward().seed() == null ? "" : t.reward().seed()));
            v.put("card", t.card() == null ? "" : t.card());
            v.put("needsSeasonVariants", t.needsSeasonVariants());
            out.add(v);
        }
        return out;
    }

    private boolean visible(TaskDef t, int stage, int termCount, String termCode, Set<String> claimed,
                            Set<String> weeklyActive) {
        return switch (t.layer()) {
            case "STARTER" -> starterVisible(t, claimed);
            case "MAIN", "MID" -> t.stage() != null && t.stage() == stage;
            case "TERM_SIDE" -> termCode != null && termCode.equals(t.term());
            case "WEEKLY" -> weeklyActive.contains(t.code());
            case "FINAL" -> stage == 9 && termCount >= GardenConst.FINAL_STAGE_MIN_TERM;
            default -> true;
        };
    }

    /** 起步任务按序解锁:可见第 N 个 ⇔ 前 N−1 个已领 */
    private boolean starterVisible(TaskDef t, Set<String> claimed) {
        int order = t.order() == null ? 0 : t.order();
        int done = (int) content.tasks().stream()
                .filter(x -> "STARTER".equals(x.layer()) && x.order() != null && x.order() < order)
                .filter(x -> claimed.contains(claimKey(x, LocalDate.now())))
                .count();
        return done >= order - 1;
    }

    /** 推阶闸门缺件:target=10 查年终链(FINAL 非自动件),否则查当前阶主线链(链压在阶首,推的是离开这一阶) */
    private List<String> missingMain(Long familyId, String settings, int stage, int target) {
        List<String> missing = new ArrayList<>();
        if (target == GardenConst.FINAL_STAGE && stage != 9) {
            throw new BizException(ResultCode.CONFLICT, "先推到第 9 阶,年终收尾才开");
        }
        String layer = target == GardenConst.FINAL_STAGE ? "FINAL" : "MAIN";
        Integer gateStage = target == GardenConst.FINAL_STAGE ? null : stage;
        Set<String> claimed = claimedKeys(familyId);
        int termCount = growth.termCount(settings);
        for (TaskDef t : content.tasks()) {
            if (!layer.equals(t.layer()) || t.isAuto()) {
                continue;
            }
            if (gateStage != null && !gateStage.equals(t.stage())) {
                continue;
            }
            LocalDate windowStart = windowStart(familyId, settings, t, stage, termCount);
            int progress = progress(t, familyId, windowStart);
            if (progress < t.condition().value() && !claimed.contains(claimKey(t, LocalDate.now()))) {
                missing.add(t.code());
            }
        }
        return missing;
    }

    private String settingsOf(Long familyId) {
        GardenWorld world = worldMapper.selectById(familyId);
        return world == null ? null : world.getSettings();
    }

    /** 7 类条件 → 数据源(详细设计 §4.2);windowed 条件查 stat 自窗口起点起 */
    private int progress(TaskDef t, Long familyId, LocalDate windowStart) {
        TaskDef.Condition c = t.condition();
        if (c == null) {
            return 0;
        }
        boolean windowed = c.window() != null && !c.window().isBlank();
        if (windowed && windowStart == null) {
            return 0;
        }
        switch (c.type()) {
            case "PLANT_N", "CARE_N", "HARVEST_N", "SELL_COINS" -> {
                Map<String, Object> s = windowed
                        ? statMapper.sumSince(familyId, windowStart)
                        : statMapper.sumByFamily(familyId);
                return switch (c.type()) {
                    case "PLANT_N" -> (int) num(s.get("plantCnt"));
                    case "CARE_N" -> (int) num(s.get("careCnt"));
                    case "HARVEST_N" -> (int) num(s.get("harvestCnt"));
                    default -> (int) num(s.get("coinIn"));
                };
            }
            case "HOLD_KINDS" -> {
                Set<String> kinds = new HashSet<>();
                for (GardenInventory r : inventoryMapper.selectList(new LambdaQueryWrapper<GardenInventory>()
                        .eq(GardenInventory::getFamilyId, familyId))) {
                    if (r.getQty() == null || r.getQty() <= 0 || r.getItemCode() == null) {
                        continue;
                    }
                    kinds.add(r.getItemCode().startsWith(GardenConst.SEED_PREFIX)
                            ? r.getItemCode().substring(GardenConst.SEED_PREFIX.length())
                            : r.getItemCode());
                }
                return kinds.size();
            }
            case "FACILITY_N" -> {
                return 0; // 设施系统(P2)落地前恒 0(详细设计 §8.2)
            }
            case "CODEX_STAGE" -> {
                int gateStage = c.stage() != null ? c.stage() : (t.stage() == null ? 1 : t.stage());
                Set<String> kinds = new HashSet<>();
                for (GardenCodex row : codexMapper.selectList(new LambdaQueryWrapper<GardenCodex>()
                        .eq(GardenCodex::getFamilyId, familyId))) {
                    var crop = content.crop(row.getCropCode());
                    if (crop != null && crop.unlockStage() == gateStage) {
                        kinds.add(row.getCropCode());
                    }
                }
                return kinds.size();
            }
            default -> {
                return 0;
            }
        }
    }

    /** 条件窗口起点:STAGE = 本阶 STAGE_ADVANCE 日(阶段 1 = 建号日);TERM = 当前节气交接日;WEEK = 本周一 */
    private LocalDate windowStart(Long familyId, String settings, TaskDef t, int stage, int termCount) {
        if (t.condition() == null || t.condition().window() == null) {
            return null;
        }
        return switch (t.condition().window()) {
            case "WEEK" -> GardenWeek.start(LocalDate.now());
            case "STAGE" -> {
                if (stage <= 1) {
                    yield growth.cycleStart(settings);
                }
                GardenLog adv = logMapper.selectOne(new LambdaQueryWrapper<GardenLog>()
                        .eq(GardenLog::getFamilyId, familyId)
                        .eq(GardenLog::getAction, "STAGE_ADVANCE")
                        .eq(GardenLog::getCropCode, String.valueOf(stage))
                        .orderByDesc(GardenLog::getCreatedAt)
                        .last("LIMIT 1"));
                yield adv == null ? growth.cycleStart(settings) : adv.getCreatedAt().toLocalDate();
            }
            case "TERM" -> {
                if (termCount == 0) {
                    yield null;
                }
                yield growth.currentTerm(settings).date();
            }
            default -> null;
        };
    }

    /**
     * 已领集合:`任务码|窗口键`。窗口键从 TASK_CLAIM 行的 variant 取(领取时写入):
     * 节气支线 = 年份、周挑战 = ISO 周键、其余 = ALL。层从 content 反查——别再从 variant 猜层,
     * 否则按年/按周领取的任务在面板上永远显示「未领」(踩坑:领取凭证必须与 claimKey 同源)。
     */
    private Set<String> claimedKeys(Long familyId) {
        Set<String> out = new HashSet<>();
        LocalDate today = LocalDate.now();
        for (GardenLog row : logMapper.selectList(new LambdaQueryWrapper<GardenLog>()
                .eq(GardenLog::getFamilyId, familyId)
                .eq(GardenLog::getAction, "TASK_CLAIM"))) {
            TaskDef t = content.task(row.getCropCode());
            String windowKey = "ALL";
            if (t != null && "WEEKLY".equals(t.layer())) {
                windowKey = row.getVariant() == null ? GardenWeek.key(today) : row.getVariant();
            } else if (t != null && "TERM_SIDE".equals(t.layer())) {
                windowKey = row.getVariant() == null ? String.valueOf(today.getYear()) : row.getVariant();
            }
            out.add(row.getCropCode() + "|" + windowKey);
        }
        return out;
    }

    private String claimKey(TaskDef t, LocalDate today) {
        return t.code() + "|" + claimWindow(t, today);
    }

    /** 领取/凭证的窗口键:周挑战 = ISO 周键(下周可再做)、节气支线 = 年(次年同节气可再做)、其余 ALL */
    private String claimWindow(TaskDef t, LocalDate today) {
        return switch (t.layer()) {
            case "WEEKLY" -> GardenWeek.key(today);
            case "TERM_SIDE" -> String.valueOf(today.getYear());
            default -> "ALL";
        };
    }

    /**
     * 本周在窗的挑战:pool 按 order 轮换取 3 条(周一序号决定起点,同一周内恒定);
     * `settings.weekly_fixture` 夹具(仅测试)直接指定任务码,验证用,不参与轮换。
     */
    private List<TaskDef> weeklyActive(String settings) {
        List<TaskDef> pool = content.weeklyPool();
        if (pool.isEmpty()) {
            return List.of();
        }
        LocalDate today = LocalDate.now();
        String fixture = weeklyFixture(settings);
        if (fixture != null && !fixture.isBlank()) {
            Set<String> codes = new HashSet<>();
            for (String s : fixture.split(",")) {
                if (!s.isBlank()) {
                    codes.add(s.trim());
                }
            }
            List<TaskDef> picked = pool.stream().filter(t -> codes.contains(t.code())).toList();
            if (!picked.isEmpty()) {
                return picked;
            }
            log.warn("[garden] weekly_fixture 未命中任何周挑战(settings={}),回退轮换", fixture);
        }
        return GardenWeek.pick(pool, today, GardenConst.WEEKLY_PER_WEEK);
    }

    /** settings JSON 的 weekly_fixture(逗号分隔任务码;仅测试,正式世界不写) */
    private String weeklyFixture(String settings) {
        if (settings == null || settings.isBlank()) {
            return null;
        }
        try {
            Map<?, ?> map = objectMapper.readValue(settings, Map.class);
            Object v = map.get(GardenConst.SETTINGS_WEEKLY_FIXTURE_KEY);
            return v == null ? null : String.valueOf(v);
        } catch (Exception e) {
            log.warn("[garden] settings weekly_fixture 解析失败 settings={}", settings);
            return null;
        }
    }

    private String currentTermCode(String settings) {
        GardenSolarTerms.Occurrence term = growth.currentTerm(settings);
        return term == null ? null : term.term().code();
    }

    private int targetTermRequirement(int target) {
        return target == GardenConst.FINAL_STAGE
                ? GardenConst.FINAL_STAGE_MIN_TERM
                : GardenConst.TERMS_PER_STAGE * (target - 1);
    }

    /** 推阶新增地块行数(回执用,按 unlock_stage==target 现算;个人场域 × 成员数) */
    private int plotsAddedAt(int target, Long familyId) {
        long members = memberMapper.selectCount(new LambdaQueryWrapper<GardenMember>()
                .eq(GardenMember::getFamilyId, familyId));
        int rows = 0;
        for (var venue : content.venues()) {
            long slots = venue.slots().stream().filter(s -> s.unlockStage() == target).count();
            if (slots == 0) {
                continue;
            }
            rows += (int) (GardenConst.isPersonalVenue(venue.code()) ? slots * Math.max(1, members) : slots);
        }
        return rows;
    }

    private long walletOf(Long familyId, String playerUid) {
        if (playerUid == null) {
            return 0;
        }
        GardenWallet wallet = walletMapper.selectOne(new LambdaQueryWrapper<GardenWallet>()
                .eq(GardenWallet::getFamilyId, familyId)
                .eq(GardenWallet::getPlayerUid, playerUid));
        return wallet == null ? 0 : wallet.getCoins();
    }

    private Map<String, Object> receipt(Long familyId, String taskCode, boolean alreadyDone, Integer coins, String seed) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("taskCode", taskCode);
        out.put("alreadyDone", alreadyDone);
        out.put("coinsGranted", coins == null ? 0 : coins);
        out.put("seed", seed == null ? "" : seed);
        return out;
    }

    /** 条件扣款;余额不足 → 1008 带差额(与 GardenPlotService 同口径) */
    private void chargeCoins(Long familyId, String playerUid, int amount) {
        walletMapper.insertIgnore(familyId, playerUid);
        if (walletMapper.deduct(familyId, playerUid, amount) > 0) {
            return;
        }
        GardenWallet wallet = walletMapper.selectOne(new LambdaQueryWrapper<GardenWallet>()
                .eq(GardenWallet::getFamilyId, familyId)
                .eq(GardenWallet::getPlayerUid, playerUid));
        long balance = wallet == null ? 0 : wallet.getCoins();
        throw new BizException(ResultCode.INSUFFICIENT_POINTS,
                "园币不够,还差 " + Math.max(0, amount - balance) + " 枚");
    }

    private long num(Object v) {
        return v == null ? 0 : Long.parseLong(String.valueOf(v));
    }
}
