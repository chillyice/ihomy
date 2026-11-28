package com.ihomy.garden.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.garden.GardenConst;
import com.ihomy.garden.content.CropDef;
import com.ihomy.garden.content.GardenContent;
import com.ihomy.garden.content.SkillDefs;
import com.ihomy.garden.core.GardenSeason;
import com.ihomy.garden.core.GardenWeatherEvents;
import com.ihomy.garden.core.GardenSolarEvents;
import com.ihomy.garden.core.GardenSolarTerms;
import com.ihomy.garden.entity.GardenCodex;
import com.ihomy.garden.entity.GardenLog;
import com.ihomy.garden.entity.GardenMember;
import com.ihomy.garden.entity.GardenPlot;
import com.ihomy.garden.entity.GardenStat;
import com.ihomy.entity.Family;
import com.ihomy.garden.entity.GardenWorld;
import com.ihomy.garden.mapper.GardenCodexMapper;
import com.ihomy.garden.mapper.GardenInventoryMapper;
import com.ihomy.garden.mapper.GardenLogMapper;
import com.ihomy.garden.mapper.GardenMemberMapper;
import com.ihomy.garden.mapper.GardenPlotMapper;
import com.ihomy.garden.mapper.GardenStatMapper;
import com.ihomy.garden.mapper.GardenWorldMapper;
import com.ihomy.mapper.FamilyMapper;
import com.ihomy.service.WeatherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 成长侧:节气/阶段推导 + 经验→技能点 + 技能分配/重置 + P1 三处数值效果钩子
 * (详细设计 §1/§2/§6)。全部从 garden_log / garden_stat / 仓库 / 图鉴 / cycle_start_at 推导,
 * 不新增表(§0-1);推阶/领取在 GardenTaskService,本服务只管「点数经济」。
 * 世界阶段:settings.stage 夹具优先(正式世界不写),否则 = 1 + STAGE_ADVANCE 次数(受日历上限约束)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GardenGrowthService {

    private final GardenLogMapper logMapper;
    private final GardenStatMapper statMapper;
    private final GardenInventoryMapper inventoryMapper;
    private final GardenCodexMapper codexMapper;
    private final GardenPlotMapper plotMapper;
    private final GardenMemberMapper memberMapper;
    private final GardenWorldMapper worldMapper;
    private final GardenContent content;
    private final ObjectMapper objectMapper;
    private final FamilyMapper familyMapper;
    private final WeatherService weatherService;

    // ---------- 家庭天气(玩法稿 §8 照料倍率;阳光量 = 天气 × 季节)----------

    /**
     * 家庭所在城市的当前天气:{condition(clear/cloud/rain/snow/fog/thunder), text, temp, ...}。
     * 取数模式照 FamilyPlantService(家庭 weatherLat/Lng/City → WeatherService,Redis 缓存);
     * 无凭证/无坐标/请求失败一律 null——倍率回退 1.0 基线,不阻断游戏。
     */
    public Map<String, Object> weatherOf(Long familyId) {
        try {
            Family f = familyMapper.selectById(familyId);
            if (f == null || f.getWeatherLat() == null || f.getWeatherLng() == null) {
                return null;
            }
            String[] loc = new String[]{f.getWeatherLat().toPlainString(),
                    f.getWeatherLng().toPlainString(), f.getWeatherCity()};
            return weatherService.getWeather(null, loc);
        } catch (Exception e) {
            log.warn("[garden] weather 获取失败 familyId={}", familyId);
            return null;
        }
    }

    /**
     * 今日日均温((tempMax+tempMin)/2,§8.1 日内恒定);取不到返回 null(FROST 不判定)。
     * `settings.temp_fixture_c` 夹具优先(仅测试;正式世界不写)。
     */
    @SuppressWarnings("unchecked")
    public Integer tempMeanToday(Long familyId) {
        Integer fixture = tempFixture(familyId);
        if (fixture != null) {
            return fixture;
        }
        try {
            Family f = familyMapper.selectById(familyId);
            if (f == null || f.getWeatherLat() == null || f.getWeatherLng() == null) {
                return null;
            }
            String[] loc = new String[]{f.getWeatherLat().toPlainString(),
                    f.getWeatherLng().toPlainString(), f.getWeatherCity()};
            Map<String, Object> detail = weatherService.getDetail(null, loc);
            List<Map<String, Object>> daily = (List<Map<String, Object>>) detail.get("daily");
            String today = LocalDate.now().toString();
            if (daily != null) {
                for (Map<String, Object> d : daily) {
                    if (today.equals(String.valueOf(d.get("fxDate")))) {
                        int mx = Integer.parseInt(String.valueOf(d.get("tempMax")));
                        int mn = Integer.parseInt(String.valueOf(d.get("tempMin")));
                        return (mx + mn) / 2;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[garden] 日均温获取失败 familyId={}", familyId);
        }
        return null;
    }

    /**
     * 地块天气事件(FROST/DROUGHT/AUTO_WATER,null = 无事件):读时推导,零存储(详细设计《天气事件与气温双轨》§1)。
     */
    public GardenWeatherEvents.Event weatherEventOf(Long familyId, String venueCode, boolean indoor,
                                                    LocalDateTime lastWateredAt, LocalDateTime plantedAt,
                                                    LocalDateTime now) {
        Map<String, Object> weather = weatherOf(familyId);
        if (weather == null || weather.get("condition") == null) {
            return null;
        }
        boolean outdoor = !indoor && !"GREENHOUSE".equals(venueCode);
        LocalDateTime waterRef = lastWateredAt != null ? lastWateredAt : plantedAt;
        return GardenWeatherEvents.evaluate(conditionOf(weather), tempMeanToday(familyId),
                outdoor, waterRef, now);
    }

    private String conditionOf(Map<String, Object> weather) {
        Object c = weather.get("condition");
        return c == null ? null : String.valueOf(c);
    }

    /**
     * 气温胁迫乘子(玩法稿 §8.1,仅室外):日均温 < 作物耐寒下限 → 60、> 耐热上限 → 70、否则 100。
     * 室内/大棚免疫(传 sheltered=true 直接 100);无温度数据/无作物 → 100(不判,也不惩罚没配城市的家庭)。
     * 与天气事件是两根轴:同一块地可以既干旱又高温,成长两条都扣,面板分别显示。
     */
    public int tempStressBpOf(Long familyId, String cropCode, boolean sheltered) {
        if (sheltered || cropCode == null) {
            return 100;
        }
        CropDef crop = content.crop(cropCode);
        if (crop == null) {
            return 100;
        }
        return GardenWeatherEvents.tempStressBp(tempMeanToday(familyId),
                crop.effectiveTempMinC(), crop.effectiveTempMaxC(), true);
    }

    /** getDetail 的日均温夹具(settings.temp_fixture_c;仅测试) */
    private Integer tempFixture(Long familyId) {
        try {
            GardenWorld world = worldMapper.selectById(familyId);
            if (world == null || world.getSettings() == null || world.getSettings().isBlank()) {
                return null;
            }
            Map<String, Object> map = objectMapper.readValue(world.getSettings(), Map.class);
            Object v = map.get(GardenConst.SETTINGS_TEMP_FIXTURE_KEY);
            if (v instanceof Number n) {
                return n.intValue();
            }
            if (v != null) {
                return Integer.parseInt(String.valueOf(v).trim());
            }
        } catch (Exception e) {
            log.warn("[garden] temp_fixture_c 解析失败 familyId={}", familyId);
        }
        return null;
    }

    // ---------- 节气事件(详细设计《节气事件与成长分段》§5)----------

    /** 事件排期:settings.day_offset 夹具(缺省 0,正式世界不写)移动事件日期视图,只进成长/卖价/缺水路径 */
    public GardenSolarEvents.Schedule scheduleOf(Long familyId) {
        int offset = 0;
        try {
            GardenWorld world = worldMapper.selectById(familyId);
            if (world != null && world.getSettings() != null && !world.getSettings().isBlank()) {
                Map<String, Object> map = objectMapper.readValue(world.getSettings(), Map.class);
                Object v = map.get(GardenConst.SETTINGS_DAY_OFFSET_KEY);
                if (v instanceof Number n) {
                    offset = n.intValue();
                } else if (v != null) {
                    offset = Integer.parseInt(String.valueOf(v).trim());
                }
            }
        } catch (Exception e) {
            log.warn("[garden] day_offset 解析失败 familyId={}", familyId);
        }
        return GardenSolarEvents.schedule(content.solarTerms(), offset);
    }

    // ---------- 节气(详细设计 §1)----------

    /** 建号后节气交接点序列(前 limit 个,严格晚于 cycle_start_at) */
    public List<GardenSolarTerms.Occurrence> occurrences(String settings, int limit) {
        return GardenSolarTerms.occurrences(content.solarTerms(), parseCycleStart(settings), limit);
    }

    /** 已交接的节气个数(建号当天 = 0) */
    public int termCount(String settings) {
        return GardenSolarTerms.termCount(occurrences(settings, 30), LocalDate.now());
    }

    /** 当前节气(第 termCount 个;termCount=0 = 建号后还没过第 1 个交接点) */
    public GardenSolarTerms.Occurrence currentTerm(String settings) {
        List<GardenSolarTerms.Occurrence> occ = occurrences(settings, 30);
        int count = GardenSolarTerms.termCount(occ, LocalDate.now());
        return count == 0 ? null : occ.get(count - 1);
    }

    /** 下一个节气(提示「第 N 个节气(X/X 前后)开」用) */
    public GardenSolarTerms.Occurrence nextTerm(String settings) {
        List<GardenSolarTerms.Occurrence> occ = occurrences(settings, 30);
        int count = GardenSolarTerms.termCount(occ, LocalDate.now());
        return occ.get(count);
    }

    // ---------- 阶段(详细设计 §2.1)----------

    /** 世界阶段:settings.stage 夹具优先;否则 = 1 + STAGE_ADVANCE 次数,受日历上限约束 */
    public int stageOf(Long familyId, String settings) {
        Integer fixture = parseStageFixture(settings);
        if (fixture != null) {
            return fixture;
        }
        int advanced = Math.toIntExact(logMapper.selectCount(new LambdaQueryWrapper<GardenLog>()
                .eq(GardenLog::getFamilyId, familyId)
                .eq(GardenLog::getAction, "STAGE_ADVANCE")));
        int termCount = termCount(settings);
        int calendarMax = termCount >= GardenConst.FINAL_STAGE_MIN_TERM
                ? GardenConst.FINAL_STAGE
                : Math.min(9, 1 + termCount / GardenConst.TERMS_PER_STAGE);
        return Math.min(1 + advanced, calendarMax);
    }

    // ---------- 经验与技能点(详细设计 §6.1/§6.2)----------

    /** 累计经验:只读快照 Σ stat.xp_gain(写时落账;§15.2 判定禁读日志,清理不动 XP) */
    public long xp(Long familyId) {
        return statMapper.sumXp(familyId);
    }

    /** 经验 → 点数阶梯(起步/成长/平台三段,封顶 132;玩法稿 §4.2.5) */
    public int pointsFromXp(long xp) {
        int[] starterCost = {30, 60, 100, 150, 220, 300, 420, 560, 740, 950};
        int points = 0;
        long left = xp;
        for (int cost : starterCost) {
            if (left < cost) {
                return points;
            }
            left -= cost;
            points++;
        }
        for (int k = 11; k <= 60; k++) {
            int cost = 1100 + 50 * (k - 11);
            if (left < cost) {
                return points;
            }
            left -= cost;
            points++;
        }
        while (points < 132) {
            if (left < 3600) {
                return points;
            }
            left -= 3600;
            points++;
        }
        return points;
    }

    /** 当前已学级数(最近一次 SKILL_RESET 之后的 SKILL_ALLOCATE 计数;nodeCode → level) */
    public Map<String, Integer> levels(Long familyId) {
        GardenLog lastReset = logMapper.selectOne(new LambdaQueryWrapper<GardenLog>()
                .eq(GardenLog::getFamilyId, familyId)
                .eq(GardenLog::getAction, "SKILL_RESET")
                .orderByDesc(GardenLog::getCreatedAt)
                .last("LIMIT 1"));
        LambdaQueryWrapper<GardenLog> qw = new LambdaQueryWrapper<GardenLog>()
                .eq(GardenLog::getFamilyId, familyId)
                .eq(GardenLog::getAction, "SKILL_ALLOCATE")
                .orderByAsc(GardenLog::getCreatedAt);
        if (lastReset != null) {
            qw.gt(GardenLog::getCreatedAt, lastReset.getCreatedAt());
        }
        Map<String, Integer> out = new HashMap<>();
        for (GardenLog row : logMapper.selectList(qw)) {
            out.merge(row.getCropCode(), 1, Integer::sum);
        }
        return out;
    }

    /** 已投入点数 = Σ 已学级数 × 层价 */
    public int pointsSpent(Map<String, Integer> levels) {
        int spent = 0;
        for (Map.Entry<String, Integer> e : levels.entrySet()) {
            SkillDefs.SkillNodeDef node = nodeByCode(e.getKey());
            if (node != null) {
                spent += tierCost(node.tier()) * e.getValue();
            }
        }
        return spent;
    }

    /** 一次性 18 点奖励(按家庭去重,全推导;条目可换总额不变,详细设计 §6.2) */
    public Map<String, Boolean> oneTime(Long familyId) {
        Map<String, Object> sums = statMapper.sumByFamily(familyId);
        long harvest = num(sums.get("harvestCnt"));
        long coinIn = num(sums.get("coinIn"));
        List<GardenStat> careDays = statMapper.selectList(new LambdaQueryWrapper<GardenStat>()
                .eq(GardenStat::getFamilyId, familyId)
                .gt(GardenStat::getCareCnt, 0)
                .orderByAsc(GardenStat::getStatDate));

        Map<String, Boolean> out = new LinkedHashMap<>();
        out.put("firstHarvest", harvest >= 1);
        out.put("firstSell", coinIn >= 1);
        out.put("firstVariant", false);                  // 变体系统未落地
        out.put("firstCook", false);                     // 烹饪未落地
        out.put("codexHalf", codexCount(familyId) >= 43);
        out.put("allVariantsOfOne", false);              // 变体系统未落地
        out.put("halfRecipes", false);                   // 食谱系统未落地
        out.put("everyMemberPlanted", everyMemberPlanted(familyId));
        out.put("care30Days", maxCareStreak(careDays) >= 30);
        out.put("harvest10Day", statMapper.maxHarvestDay(familyId) >= 10);
        out.put("venueComplete", anyVenueComplete(familyId));
        out.put("dormancyHarvest", dormancyHarvest(familyId));
        out.put("starterDone", claimedCount(familyId, "STARTER_") >= 12);
        out.put("firstFinalRite", logMapper.selectCount(new LambdaQueryWrapper<GardenLog>()
                .eq(GardenLog::getFamilyId, familyId)
                .eq(GardenLog::getAction, "STAGE_ADVANCE")
                .eq(GardenLog::getCropCode, String.valueOf(GardenConst.FINAL_STAGE))) > 0);
        out.put("codexSets3", false);                    // 套装系统未落地
        out.put("sold2000", coinIn >= 2000);
        out.put("pointsExchanged100", false);            // ihomy 积分随平台能力开放
        out.put("allVenuesComplete", allVenuesComplete(familyId));
        return out;
    }

    /** 可用点数总览(§4.2.6:GET /garden 返回可用点数和已投入级数) */
    public Map<String, Object> pointsView(Long familyId) {
        Map<String, Integer> lv = levels(familyId);
        int spent = pointsSpent(lv);
        long xp = xp(familyId);
        int fromXp = pointsFromXp(xp);
        Map<String, Boolean> oneTime = oneTime(familyId);
        int oneTimePoints = (int) oneTime.values().stream().filter(Boolean::booleanValue).count();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("xp", xp);
        out.put("pointsFromXp", fromXp);
        out.put("oneTimePoints", oneTimePoints);
        out.put("oneTime", oneTime);
        out.put("pointsSpent", spent);
        out.put("pointsAvailable", fromXp + oneTimePoints - spent);
        out.put("levels", lv);
        return out;
    }

    // ---------- 技能分配 / 重置(详细设计 §6.3)----------

    /** GET /garden/skills:六树配置(P1 节点,P2+ 隐藏)+ 已学级数 + 点数(§4.2.6) */
    public Map<String, Object> skillsView(Long familyId) {
        Map<String, Integer> lv = levels(familyId);
        List<Map<String, Object>> trees = new ArrayList<>();
        for (SkillDefs.SkillTreeDef tree : content.skillTrees()) {
            Map<String, Object> tv = new LinkedHashMap<>();
            tv.put("code", tree.code());
            tv.put("name", tree.name());
            tv.put("root", tree.root());
            List<Map<String, Object>> nodes = new ArrayList<>();
            for (SkillDefs.SkillNodeDef node : tree.nodes()) {
                if (!node.isP1()) {
                    continue; // §4.2.2:没有可用玩法时隐藏相应节点
                }
                Map<String, Object> nv = new LinkedHashMap<>();
                nv.put("code", node.code());
                nv.put("branch", node.branch());
                nv.put("tier", node.tier());
                nv.put("cost", tierCost(node.tier()));
                nv.put("prereq", node.prereq());
                nv.put("levels", node.levels());
                nv.put("learned", lv.getOrDefault(node.code(), 0));
                nv.put("live", node.live());
                nv.put("effectText", node.effectText());
                if (node.pending() != null) {
                    nv.put("pending", node.pending());
                }
                nodes.add(nv);
            }
            tv.put("nodes", nodes);
            trees.add(tv);
        }
        Map<String, Object> out = pointsView(familyId);
        out.put("trees", trees);
        out.put("levels", lv);
        return out;
    }

    /** 提升一级:校验 release/live/前置/满级/点数;确定性 op_id 兜底并发(u k_op_id) */
    @Transactional
    public Map<String, Object> allocate(Long familyId, GardenMember me, String treeCode, String nodeCode) {
        worldMapper.lockById(familyId);
        SkillDefs.SkillNodeDef node = content.skillNode(treeCode, nodeCode);
        if (node == null) {
            throw new BizException(ResultCode.NOT_FOUND, "没有这项手艺");
        }
        if (!node.isP1()) {
            throw new BizException(ResultCode.FORBIDDEN, "这项手艺还没到开放的季节");
        }
        if (!node.live()) {
            throw new BizException(ResultCode.FORBIDDEN,
                    "这项手艺的用场还没开放" + (node.pending() == null ? "" : "(等 " + node.pending() + ")"));
        }
        if (node.venue() != null && plotMapper.selectCount(new LambdaQueryWrapper<GardenPlot>()
                .eq(GardenPlot::getFamilyId, familyId)
                .eq(GardenPlot::getVenue, node.venue())) == 0) {
            throw new BizException(ResultCode.FORBIDDEN, "先开" + node.venue() + "再学它");
        }
        Map<String, Integer> lv = levels(familyId);
        int cur = lv.getOrDefault(nodeCode, 0);
        if (cur >= node.levels()) {
            throw new BizException(ResultCode.CONFLICT, "这项已经学到头了");
        }
        for (String pre : node.prereq()) {
            if (lv.getOrDefault(pre, 0) < 1) {
                throw new BizException(ResultCode.CONFLICT, "还没学会前置:" + nameOf(pre));
            }
        }
        int cost = tierCost(node.tier());
        Map<String, Object> view = pointsView(familyId);
        int available = (int) view.get("pointsAvailable");
        if (available < cost) {
            throw new BizException(ResultCode.CONFLICT, "技能点不够,还差 " + (cost - available) + " 点");
        }
        GardenLog row = new GardenLog();
        row.setFamilyId(familyId);
        row.setActorUid(me.getPlayerUid());
        row.setOpId(GardenConst.synthOpId('A', "skill-alloc", familyId, nodeCode, cur + 1));
        row.setLamport(0L);
        row.setOrigin("IHOMY");
        row.setAction("SKILL_ALLOCATE");
        row.setCropCode(nodeCode);
        row.setDetail("学了「" + nameOf(nodeCode) + "」第 " + (cur + 1) + " 级");
        try {
            logMapper.insert(row);
        } catch (DuplicateKeyException e) {
            // 同级并发双跑:第二个拿到 alreadyDone 回执,不重复扣点
            return receipt(familyId, treeCode, nodeCode, cur + 1, true);
        }
        log.info("[garden] skill allocate family={} node={} level={} cost={} opId={}",
                familyId, nodeCode, cur + 1, cost, row.getOpId());
        return receipt(familyId, treeCode, nodeCode, cur + 1, false);
    }

    /** 每季免费重置一次(自然季);重置后已投入点数由推导口径自动退回 */
    @Transactional
    public Map<String, Object> reset(Long familyId, GardenMember me) {
        worldMapper.lockById(familyId);
        String season = GardenSeason.seasonOf(LocalDate.now());
        GardenLog last = logMapper.selectOne(new LambdaQueryWrapper<GardenLog>()
                .eq(GardenLog::getFamilyId, familyId)
                .eq(GardenLog::getAction, "SKILL_RESET")
                .eq(GardenLog::getCropCode, season)
                .last("LIMIT 1"));
        if (last != null) {
            throw new BizException(ResultCode.CONFLICT, "本季已经重置过一次,下个季节再来");
        }
        Map<String, Integer> lv = levels(familyId);
        int refund = pointsSpent(lv);
        GardenLog row = new GardenLog();
        row.setFamilyId(familyId);
        row.setActorUid(me.getPlayerUid());
        row.setOpId(GardenConst.synthOpId('R', "skill-reset", familyId, season));
        row.setLamport(0L);
        row.setOrigin("IHOMY");
        row.setAction("SKILL_RESET");
        row.setCropCode(season);
        row.setDetail("把 " + refund + " 点技能重新收回了篮子");
        try {
            logMapper.insert(row);
        } catch (DuplicateKeyException e) {
            throw new BizException(ResultCode.CONFLICT, "本季已经重置过一次,下个季节再来");
        }
        log.info("[garden] skill reset family={} season={} refund={} opId={}", familyId, season, refund, row.getOpId());
        Map<String, Integer> after = levels(familyId);
        Map<String, Object> out = pointsView(familyId);
        out.put("refunded", refund);
        out.put("season", season);
        out.put("levels", after);
        return out;
    }

    // ---------- P1 数值效果钩子(详细设计 §6.4)----------

    /** 轻锄:备盆费 5 → 4 → 3 园币(首盆免单判定在前,不受影响) */
    public int prepareCost(Long familyId) {
        return Math.max(3, GardenConst.PREPARE_COST - levels(familyId).getOrDefault("QINGZHU", 0));
    }

    /** 试水:浇水地块冷却 12h → 11h/10h(晒太阳/施肥不变) */
    public int waterCooldownMinutes(Long familyId) {
        return GardenConst.CARE_COOLDOWN_MINUTES - 60 * Math.min(2, levels(familyId).getOrDefault("SHISHUI", 0));
    }

    /** 仓廪:家庭仓容 +50/+100 */
    public int warehouseBonus(Long familyId) {
        return 50 * Math.min(2, levels(familyId).getOrDefault("CANGLIN", 0));
    }

    // ---------- 内部 ----------

    private Map<String, Object> receipt(Long familyId, String treeCode, String nodeCode, int level, boolean alreadyDone) {
        Map<String, Integer> lv = levels(familyId);
        Map<String, Object> out = pointsView(familyId);
        out.put("tree", treeCode);
        out.put("node", nodeCode);
        out.put("level", level);
        out.put("alreadyDone", alreadyDone);
        out.put("levels", lv);
        return out;
    }

    private SkillDefs.SkillNodeDef nodeByCode(String nodeCode) {
        for (SkillDefs.SkillTreeDef tree : content.skillTrees()) {
            for (SkillDefs.SkillNodeDef node : tree.nodes()) {
                if (node.code().equals(nodeCode)) {
                    return node;
                }
            }
        }
        return null;
    }

    private String nameOf(String nodeCode) {
        return nodeCode; // 节点中文名随 i18n 走,后端先回节点码
    }

    private int tierCost(int tier) {
        return GardenConst.SKILL_TIER_COST[Math.max(1, Math.min(5, tier)) - 1];
    }

    private long codexCount(Long familyId) {
        return codexMapper.selectCount(new LambdaQueryWrapper<GardenCodex>()
                .eq(GardenCodex::getFamilyId, familyId));
    }

    private boolean everyMemberPlanted(Long familyId) {
        Long members = memberMapper.selectCount(new LambdaQueryWrapper<GardenMember>()
                .eq(GardenMember::getFamilyId, familyId));
        if (members == null || members == 0) {
            return false;
        }
        Set<String> planters = new HashSet<>();
        for (GardenLog row : logMapper.selectList(new LambdaQueryWrapper<GardenLog>()
                .eq(GardenLog::getFamilyId, familyId)
                .eq(GardenLog::getAction, "PLANT"))) {
            planters.add(row.getActorUid());
        }
        return planters.size() >= members;
    }

    private int maxCareStreak(List<GardenStat> careDays) {
        int best = 0;
        int streak = 0;
        LocalDate prev = null;
        for (GardenStat row : careDays) {
            if (prev != null && row.getStatDate().equals(prev.plusDays(1))) {
                streak++;
            } else {
                streak = 1;
            }
            best = Math.max(best, streak);
            prev = row.getStatDate();
        }
        return best;
    }

    /** 一个场域满编:该场域全部已解锁地块都有作物(个人 + 共享合并看) */
    private boolean anyVenueComplete(Long familyId) {
        return venueCompleteRatios(familyId).values().stream().anyMatch(v -> v);
    }

    private boolean allVenuesComplete(Long familyId) {
        Map<String, Boolean> ratios = venueCompleteRatios(familyId);
        return !ratios.isEmpty() && ratios.values().stream().allMatch(v -> v);
    }

    private Map<String, Boolean> venueCompleteRatios(Long familyId) {
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (var venue : content.venues()) {
            List<GardenPlot> rows = plotMapper.selectList(new LambdaQueryWrapper<GardenPlot>()
                    .eq(GardenPlot::getFamilyId, familyId)
                    .eq(GardenPlot::getVenue, venue.code()));
            if (rows.isEmpty()) {
                continue;
            }
            long planted = rows.stream().filter(p -> p.getCropCode() != null).count();
            out.put(venue.code(), planted == rows.size());
        }
        return out;
    }

    /** 休眠期(大雪 12-07 起到年底,近似)仍有收获 */
    private boolean dormancyHarvest(Long familyId) {
        for (GardenLog row : logMapper.selectList(new LambdaQueryWrapper<GardenLog>()
                .eq(GardenLog::getFamilyId, familyId)
                .eq(GardenLog::getAction, "HARVEST"))) {
            LocalDateTime at = row.getCreatedAt();
            if (at.getMonth().getValue() == 12 && at.getDayOfMonth() >= 7) {
                return true;
            }
        }
        return false;
    }

    private long claimedCount(Long familyId, String prefix) {
        long count = 0;
        for (GardenLog row : logMapper.selectList(new LambdaQueryWrapper<GardenLog>()
                .eq(GardenLog::getFamilyId, familyId)
                .eq(GardenLog::getAction, "TASK_CLAIM"))) {
            if (row.getCropCode() != null && row.getCropCode().startsWith(prefix)) {
                count++;
            }
        }
        return count;
    }

    private long num(Object v) {
        return v == null ? 0 : Long.parseLong(String.valueOf(v));
    }

    /** settings JSON 的 cycle_start_at(缺省按今天,worldService 建号必写,防御用;窗口推导共用) */
    public LocalDate cycleStart(String settings) {
        try {
            if (settings != null && !settings.isBlank()) {
                Map<String, Object> map = objectMapper.readValue(settings, Map.class);
                Object v = map.get(GardenConst.SETTINGS_CYCLE_START_KEY);
                if (v != null) {
                    return LocalDate.parse(String.valueOf(v));
                }
            }
        } catch (Exception e) {
            log.warn("[garden] settings cycle_start_at 解析失败 settings={}", settings);
        }
        return LocalDate.now();
    }

    private LocalDate parseCycleStart(String settings) {
        return cycleStart(settings);
    }

    /** settings JSON 的 stage 夹具(正式世界不写) */
    @SuppressWarnings("unchecked")
    public Integer parseStageFixture(String settings) {
        if (settings == null || settings.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(settings, Map.class);
            Object v = map.get(GardenConst.SETTINGS_STAGE_KEY);
            if (v instanceof Number n) {
                return Math.max(1, n.intValue());
            }
            if (v != null) {
                return Math.max(1, Integer.parseInt(String.valueOf(v).trim()));
            }
        } catch (Exception e) {
            log.warn("[garden] settings stage 解析失败 settings={}", settings);
        }
        return null;
    }
}
