package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.PointsRuleConst;
import com.ihomy.common.ResultCode;
import com.ihomy.entity.HomesteadEquipment;
import com.ihomy.entity.HomesteadItem;
import com.ihomy.entity.HomesteadPlayer;
import com.ihomy.entity.HomesteadSkill;
import com.ihomy.entity.HomesteadTool;
import com.ihomy.homestead.HomesteadConst;
import com.ihomy.homestead.content.ActionDef;
import com.ihomy.homestead.content.EquipmentDef;
import com.ihomy.homestead.content.HomesteadContent;
import com.ihomy.homestead.content.ItemDef;
import com.ihomy.homestead.content.SkillDef;
import com.ihomy.homestead.content.ToolDef;
import com.ihomy.homestead.core.HomesteadEngine;
import com.ihomy.homestead.core.HomesteadGear;
import com.ihomy.homestead.core.HomesteadIndex;
import com.ihomy.homestead.core.HomesteadMarket;
import com.ihomy.mapper.HomesteadEquipmentMapper;
import com.ihomy.mapper.HomesteadItemMapper;
import com.ihomy.mapper.HomesteadPlayerMapper;
import com.ihomy.mapper.HomesteadSkillMapper;
import com.ihomy.mapper.HomesteadToolMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 小院慢生活:服务端权威的放置游戏。
 * <p>
 * 口径:
 * <ul>
 *   <li>进度只由服务端结算;客户端只渲染状态并插值进度环,不做本地收益模拟(避免两套引擎漂移)。</li>
 *   <li>每次读状态都先结算一次(离线时长按 last_settle_at 补算,上限 24 小时;时钟回拨记 0 并把锚点拉回现在)。</li>
 *   <li>并发:事务内 SELECT ... FOR UPDATE 锁玩家行,同一账号的多标签页/多设备串行入账。</li>
 *   <li>幂等:写操作带 Idempotency-Key 时用 Redis SETNX 去重(10 分钟);Redis 不可用则放行(家庭内互信)。</li>
 *   <li>积分:技能升级由服务端直接发放(每天最多 {@value #LEVEL_AWARD_DAILY_LIMIT} 次),
 *       客户端没有任何上报收益或积分的入口。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HomesteadService {

    /** 榜单条数(家庭人数通常远小于此) */
    private static final int RANKING_LIMIT = 20;
    /** 离开超过 5 分钟才在状态里带离线结算摘要,避免 60 秒轮询每次都弹「离线收益」 */
    private static final long OFFLINE_SUMMARY_MIN_MS = 5 * 60_000L;
    private static final Duration OP_TTL = Duration.ofMinutes(10);
    private static final String OP_KEY_PREFIX = "ihomy:homestead:op:";
    /** 技能升级发积分的每日上限(次),防止离线回来一次升几十级刷爆积分 */
    private static final int LEVEL_AWARD_DAILY_LIMIT = 5;
    private static final String LEVEL_AWARD_KEY_PREFIX = "ihomy:homestead:levelup:";
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final HomesteadContent content;
    private final HomesteadPlayerMapper playerMapper;
    private final HomesteadSkillMapper skillMapper;
    private final HomesteadItemMapper itemMapper;
    private final HomesteadToolMapper toolMapper;
    private final HomesteadEquipmentMapper equipmentMapper;
    private final StringRedisTemplate redis;
    private final PointsService pointsService;

    // ------------------------------------------------------------------ 对外接口

    /** 读状态(顺带结算);首次访问建号 */
    @Transactional
    public Map<String, Object> state(Long userId, Long familyId) {
        Profile profile = load(userId, familyId);
        HomesteadEngine.SettleResult settle = advance(profile, familyId);
        return snapshot(profile, settle);
    }

    /** 切换挂机动作:先结算旧动作,再落新动作(同一动作不清零稀有计数与余量) */
    @Transactional
    public Map<String, Object> startAction(Long userId, Long familyId, String actionCode, String opId) {
        Profile profile = load(userId, familyId);
        HomesteadEngine.SettleResult settle = advance(profile, familyId);
        ActionDef action = content.action(actionCode);
        if (action == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "没有这个动作");
        }
        HomesteadSkill skill = skillOf(profile, action.skill());
        if (skill.getLevel() < action.level()) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "这个动作要 " + action.level() + " 级才能做");
        }
        if (allowOp(userId, opId)) {
            if (!action.code().equals(profile.player.getActionCode())) {
                profile.player.setActionCode(action.code());
                profile.player.setActionSeq(0L);
                profile.player.setActionProgressMs(0L);
                profile.player.setLastSettleAt(now());
                playerMapper.updateProgress(profile.player);
            }
        }
        return snapshot(profile, settle);
    }

    /** 收工:结算后置为空闲(空闲时间不入账,last_settle_at 每读一次就前移) */
    @Transactional
    public Map<String, Object> stop(Long userId, Long familyId, String opId) {
        Profile profile = load(userId, familyId);
        HomesteadEngine.SettleResult settle = advance(profile, familyId);
        if (allowOp(userId, opId)) {
            profile.player.setActionCode(null);
            profile.player.setActionSeq(0L);
            profile.player.setActionProgressMs(0L);
            profile.player.setLastSettleAt(now());
            playerMapper.updateProgress(profile.player);
        }
        return snapshot(profile, settle);
    }

    /** 卖物品换铜板:命中当天赶集行情且还有额度的部分按行情价,其余按内容表原价 */
    @Transactional
    public Map<String, Object> sell(Long userId, Long familyId, String itemCode, long qty, String opId) {
        Profile profile = load(userId, familyId);
        HomesteadEngine.SettleResult settle = advance(profile, familyId);
        ItemDef item = content.item(itemCode);
        if (item == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "没有这个物品");
        }
        if (qty <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "数量至少为 1");
        }
        HomesteadItem row = itemRow(profile, itemCode);
        long have = row == null ? 0L : row.getQty();
        if (have < qty) {
            throw new BizException(ResultCode.BAD_REQUEST, "背包里没有这么多");
        }
        LocalDate today = LocalDate.now(ZONE);
        HomesteadMarket.Sale sale = HomesteadMarket.plan(
                HomesteadMarket.orders(today, content), marketUsed(profile.player, today), item, qty);
        if (allowOp(userId, opId)) {
            applyItems(profile, Map.of(), Map.of(itemCode, qty));
            // 行情价算完再乘穿戴卖价加成(挂饰),两段都向下取整,便于对账
            long coins = gearBonuses(profile).sellCoins(sale.coins());
            profile.player.setCoins(clampCoins(nz(profile.player.getCoins()) + coins));
            profile.player.setMarketDate(today);
            profile.player.setMarketUsed(sale.usedAfter());
            playerMapper.updateProgress(profile.player);
        }
        return snapshot(profile, settle);
    }

    /** 工具升一阶:扣铜板与材料,速度加成由引擎按阶数算 */
    @Transactional
    public Map<String, Object> upgradeTool(Long userId, Long familyId, String toolCode, String opId) {
        Profile profile = load(userId, familyId);
        HomesteadEngine.SettleResult settle = advance(profile, familyId);
        ToolDef tool = content.tool(toolCode);
        if (tool == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "没有这个工具");
        }
        HomesteadTool row = toolRow(profile, toolCode);
        int tier = row == null ? 0 : row.getTier();
        ToolDef.ToolStep step = tool.nextStep(tier);
        if (step == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "这件工具已经升到顶了");
        }
        if (nz(profile.player.getCoins()) < step.coins()) {
            throw new BizException(ResultCode.BAD_REQUEST, "铜板不够");
        }
        Map<String, Long> need = new HashMap<>();
        Map<String, Long> stock = qtyMap(profile.items);
        for (ActionDef.ItemAmount material : step.materials()) {
            if (stock.getOrDefault(material.item(), 0L) < material.qty()) {
                throw new BizException(ResultCode.BAD_REQUEST, "材料不够");
            }
            need.merge(material.item(), material.qty(), Long::sum);
        }
        if (allowOp(userId, opId)) {
            applyItems(profile, Map.of(), need);
            profile.player.setCoins(clampCoins(nz(profile.player.getCoins()) - step.coins()));
            if (row == null) {
                HomesteadTool fresh = new HomesteadTool();
                fresh.setUserId(userId);
                fresh.setToolCode(toolCode);
                fresh.setTier(tier + 1);
                toolMapper.insert(fresh);
                profile.tools.add(fresh);
            } else {
                row.setTier(tier + 1);
                toolMapper.updateById(row);
            }
            playerMapper.updateProgress(profile.player);
        }
        return snapshot(profile, settle);
    }

    /**
     * 穿戴升一阶:扣铜板与材料,加成由 HomesteadGear 折叠(采集/加工/全部加速、离线上限、卖价)。
     * 与工具一样「一人一部位一件」,升阶即替换,不做装备背包。
     */
    @Transactional
    public Map<String, Object> upgradeGear(Long userId, Long familyId, String slotCode, String opId) {
        Profile profile = load(userId, familyId);
        HomesteadEngine.SettleResult settle = advance(profile, familyId);
        EquipmentDef slot = content.equipmentSlot(slotCode);
        if (slot == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "没有这个部位");
        }
        HomesteadEquipment row = equipmentRow(profile, slotCode);
        int tier = row == null ? 0 : row.getTier();
        EquipmentDef.EquipmentStep step = slot.nextStep(tier);
        if (step == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "这件已经升到顶了");
        }
        if (nz(profile.player.getCoins()) < step.coins()) {
            throw new BizException(ResultCode.BAD_REQUEST, "铜板不够");
        }
        Map<String, Long> need = new HashMap<>();
        Map<String, Long> stock = qtyMap(profile.items);
        for (ActionDef.ItemAmount material : step.materials()) {
            if (stock.getOrDefault(material.item(), 0L) < material.qty()) {
                throw new BizException(ResultCode.BAD_REQUEST, "材料不够");
            }
            need.merge(material.item(), material.qty(), Long::sum);
        }
        if (allowOp(userId, opId)) {
            applyItems(profile, Map.of(), need);
            profile.player.setCoins(clampCoins(nz(profile.player.getCoins()) - step.coins()));
            if (row == null) {
                HomesteadEquipment fresh = new HomesteadEquipment();
                fresh.setUserId(userId);
                fresh.setSlotCode(slotCode);
                fresh.setTier(tier + 1);
                equipmentMapper.insert(fresh);
                profile.gear.add(fresh);
            } else {
                row.setTier(tier + 1);
                equipmentMapper.updateById(row);
            }
            playerMapper.updateProgress(profile.player);
        }
        return snapshot(profile, settle);
    }

    /**
     * 家庭小院指数榜:指数由服务端从自己的表现算(等级×1000 + 铜板 + 库存半价),
     * 不接受客户端上报;成员口径取 sys_user_role 的当前绑定,退家庭即消失。
     */
    public Map<String, Object> ranking(Long familyId, Long userId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ranking", List.of());
        out.put("me", null);
        if (familyId == null) {
            return out;
        }
        List<Map<String, Object>> members = playerMapper.selectFamilyPlayers(familyId);
        if (members.isEmpty()) {
            return out;
        }
        List<Long> userIds = new ArrayList<>(members.size());
        for (Map<String, Object> member : members) {
            Long id = asLong(member.get("userId"));
            if (id != null) {
                userIds.add(id);
            }
        }
        if (userIds.isEmpty()) {
            return out;
        }
        Map<Long, Map<String, Integer>> levels = groupLevels(
                skillMapper.selectList(new LambdaQueryWrapper<HomesteadSkill>().in(HomesteadSkill::getUserId, userIds)));
        Map<Long, Map<String, Long>> stock = groupQty(
                itemMapper.selectList(new LambdaQueryWrapper<HomesteadItem>().in(HomesteadItem::getUserId, userIds)));

        List<Map<String, Object>> rows = new ArrayList<>(members.size());
        for (Map<String, Object> member : members) {
            Long id = asLong(member.get("userId"));
            long score = HomesteadIndex.compute(
                    levels.getOrDefault(id, Map.of()),
                    asLong(member.get("coins")),
                    stock.getOrDefault(id, Map.of()),
                    content);
            Map<String, Object> row = new LinkedHashMap<>(member);
            row.put("score", score);
            row.put("isMe", userId != null && userId.equals(id));
            rows.add(row);
        }
        // 指数降序;同分按最近动静在前(updatedAt 越新越靠前)
        rows.sort(Comparator
                .comparingLong((Map<String, Object> r) -> (Long) r.get("score")).reversed()
                .thenComparing((Map<String, Object> r) -> String.valueOf(r.get("updatedAt")), Comparator.reverseOrder()));

        List<Map<String, Object>> top = new ArrayList<>();
        Map<String, Object> me = null;
        int rank = 0;
        for (Map<String, Object> row : rows) {
            rank++;
            Map<String, Object> view = new LinkedHashMap<>(row);
            view.put("rank", rank);
            if (rank <= RANKING_LIMIT) {
                top.add(view);
            }
            if (Boolean.TRUE.equals(row.get("isMe"))) {
                me = view;
            }
        }
        out.put("ranking", top);
        out.put("me", me);
        return out;
    }

    // ------------------------------------------------------------------ 装载与结算

    private static final class Profile {
        HomesteadPlayer player;
        List<HomesteadSkill> skills;
        List<HomesteadItem> items;
        List<HomesteadTool> tools;
        List<HomesteadEquipment> gear;
    }

    /** 锁行装载;首次访问建号并补齐技能/工具行(内容新增技能后老存档也能直接用) */
    private Profile load(Long userId, Long familyId) {
        HomesteadPlayer player = playerMapper.selectByUserForUpdate(userId);
        if (player == null) {
            HomesteadPlayer fresh = new HomesteadPlayer();
            fresh.setUserId(userId);
            fresh.setFamilyId(familyId);
            fresh.setCoins(0L);
            fresh.setActionSeq(0L);
            fresh.setActionProgressMs(0L);
            fresh.setTotalActions(0L);
            fresh.setMarketUsed(0);
            fresh.setLastSettleAt(now());
            fresh.setContentVersion(content.contentVersion());
            playerMapper.insertPlayerIfAbsent(fresh);
            player = playerMapper.selectByUserForUpdate(userId);
            if (player == null) {
                throw new IllegalStateException("小院存档建立失败:userId=" + userId);
            }
        }
        Profile profile = new Profile();
        profile.player = player;
        profile.skills = new ArrayList<>(skillMapper.selectList(
                new LambdaQueryWrapper<HomesteadSkill>().eq(HomesteadSkill::getUserId, userId)));
        profile.items = new ArrayList<>(itemMapper.selectList(
                new LambdaQueryWrapper<HomesteadItem>().eq(HomesteadItem::getUserId, userId)));
        profile.tools = new ArrayList<>(toolMapper.selectList(
                new LambdaQueryWrapper<HomesteadTool>().eq(HomesteadTool::getUserId, userId)));
        profile.gear = new ArrayList<>(equipmentMapper.selectList(
                new LambdaQueryWrapper<HomesteadEquipment>().eq(HomesteadEquipment::getUserId, userId)));
        ensureRows(profile, userId);
        return profile;
    }

    private void ensureRows(Profile profile, Long userId) {
        Set<String> skillCodes = new HashSet<>();
        profile.skills.forEach(s -> skillCodes.add(s.getSkillCode()));
        for (SkillDef def : content.skills()) {
            if (skillCodes.add(def.code())) {
                HomesteadSkill skill = new HomesteadSkill();
                skill.setUserId(userId);
                skill.setSkillCode(def.code());
                skill.setLevel(1);
                skill.setXp(0L);
                skillMapper.insert(skill);
                profile.skills.add(skill);
            }
        }
        Set<String> toolCodes = new HashSet<>();
        profile.tools.forEach(t -> toolCodes.add(t.getToolCode()));
        for (ToolDef def : content.tools()) {
            if (toolCodes.add(def.code())) {
                HomesteadTool tool = new HomesteadTool();
                tool.setUserId(userId);
                tool.setToolCode(def.code());
                tool.setTier(0);
                toolMapper.insert(tool);
                profile.tools.add(tool);
            }
        }
        Set<String> slotCodes = new HashSet<>();
        profile.gear.forEach(g -> slotCodes.add(g.getSlotCode()));
        for (EquipmentDef def : content.equipment()) {
            if (slotCodes.add(def.code())) {
                HomesteadEquipment gear = new HomesteadEquipment();
                gear.setUserId(userId);
                gear.setSlotCode(def.code());
                gear.setTier(0);
                equipmentMapper.insert(gear);
                profile.gear.add(gear);
            }
        }
    }

    /**
     * 结算当前动作并把结果落库;空闲时只把锚点前移(空闲时长不入账)。返回本次结算摘要(可能为 null)。
     * last_settle_at 无论是否入账都推进到 now,时钟回拨后也不会把「未来时间」反复重算。
     */
    private HomesteadEngine.SettleResult advance(Profile profile, Long familyId) {
        long nowMs = Instant.now().toEpochMilli();
        LocalDateTime nowAt = fromMillis(nowMs);
        ActionDef action = content.action(profile.player.getActionCode());
        HomesteadEngine.SettleResult settle = null;
        if (action == null) {
            profile.player.setActionSeq(0L);
            profile.player.setActionProgressMs(0L);
        } else {
            HomesteadSkill skill = skillOf(profile, action.skill());
            HomesteadGear.Bonuses gear = gearBonuses(profile);
            SkillDef skillDef = content.skill(action.skill());
            HomesteadEngine.SettleInput input = new HomesteadEngine.SettleInput(
                    action,
                    skill.getLevel(),
                    skill.getXp(),
                    tierOf(profile, action.skill()),
                    nz(profile.player.getActionSeq()),
                    nz(profile.player.getActionProgressMs()),
                    toMillis(profile.player.getLastSettleAt()),
                    nowMs,
                    qtyMap(profile.items),
                    gear.speedBpFor(skillDef == null ? null : skillDef.kind()),
                    gear.capMs());
            settle = HomesteadEngine.settle(input, content);
            if (settle.completions() > 0) {
                applyItems(profile, settle.gained(), settle.consumed());
                skill.setLevel(settle.levelAfter());
                skill.setXp(settle.xpAfter());
                skillMapper.updateById(skill);
                profile.player.setTotalActions(nz(profile.player.getTotalActions()) + settle.completions());
                awardLevelUps(profile.player.getUserId(), familyId, settle.levelUps());
            }
            profile.player.setActionSeq(settle.actionSeqAfter());
            profile.player.setActionProgressMs(settle.progressMs());
        }
        profile.player.setFamilyId(familyId);
        profile.player.setContentVersion(content.contentVersion());
        profile.player.setLastSettleAt(nowAt);
        playerMapper.updateProgress(profile.player);
        return settle;
    }

    /**
     * 技能升级发积分:按积分规则的「生活技能升级」分值发一次,每天最多 5 次。
     * 只认服务端结算出的 levelUps(离线补算也算),Redis 计数跨设备共享;
     * Redis 不可用时不做日限(家庭内互信),但不能阻断结算。
     */
    private void awardLevelUps(Long userId, Long familyId, int levelUps) {
        if (levelUps <= 0 || !pointsService.ruleEnabled(familyId, PointsRuleConst.GAME_HOMESTEAD)) {
            return;
        }
        int perLevel = pointsService.rulePoints(familyId, PointsRuleConst.GAME_HOMESTEAD);
        if (perLevel <= 0) {
            return;
        }
        String key = LEVEL_AWARD_KEY_PREFIX + userId + ":" + LocalDate.now();
        long usedBefore;
        try {
            long used = redis.opsForValue().increment(key, levelUps);
            if (used == levelUps) {
                redis.expire(key, Duration.ofSeconds(secondsUntilMidnight()));
            }
            usedBefore = used - levelUps;
        } catch (Exception e) {
            log.warn("小院升级积分计数失败,按未使用处理,userId={}", userId, e);
            usedBefore = 0L;
        }
        long allowed = Math.min(levelUps, Math.max(0L, LEVEL_AWARD_DAILY_LIMIT - usedBefore));
        if (allowed <= 0) {
            return;
        }
        pointsService.addRecord(userId, familyId, "REWARD", (int) (perLevel * allowed),
                "小院生活技能升级 " + allowed + " 次");
    }

    private long secondsUntilMidnight() {
        LocalDateTime now = LocalDateTime.now();
        return Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).getSeconds();
    }

    /** 背包增减:行不存在则插入(数量为 0 的行保留,不删行) */
    private void applyItems(Profile profile, Map<String, Long> gained, Map<String, Long> consumed) {
        Map<String, HomesteadItem> byCode = new HashMap<>();
        profile.items.forEach(item -> byCode.put(item.getItemCode(), item));
        for (Map.Entry<String, Long> entry : gained.entrySet()) {
            long delta = entry.getValue() == null ? 0 : entry.getValue();
            if (delta <= 0) {
                continue;
            }
            HomesteadItem row = byCode.get(entry.getKey());
            if (row == null) {
                HomesteadItem fresh = new HomesteadItem();
                fresh.setUserId(profile.player.getUserId());
                fresh.setItemCode(entry.getKey());
                fresh.setQty(Math.min(delta, HomesteadConst.MAX_ITEM_QTY));
                itemMapper.insert(fresh);
                profile.items.add(fresh);
                byCode.put(fresh.getItemCode(), fresh);
            } else {
                row.setQty(Math.min(HomesteadConst.MAX_ITEM_QTY, nz(row.getQty()) + delta));
                itemMapper.updateById(row);
            }
        }
        for (Map.Entry<String, Long> entry : consumed.entrySet()) {
            long delta = entry.getValue() == null ? 0 : entry.getValue();
            HomesteadItem row = byCode.get(entry.getKey());
            if (delta <= 0 || row == null) {
                continue;
            }
            row.setQty(Math.max(0L, nz(row.getQty()) - delta));
            itemMapper.updateById(row);
        }
    }

    // ------------------------------------------------------------------ 状态组装

    private Map<String, Object> snapshot(Profile profile, HomesteadEngine.SettleResult settle) {
        HomesteadPlayer player = profile.player;
        Map<String, Object> playerView = new LinkedHashMap<>();
        playerView.put("coins", nz(player.getCoins()));
        playerView.put("actionCode", player.getActionCode());
        playerView.put("actionSeq", nz(player.getActionSeq()));
        playerView.put("progressMs", nz(player.getActionProgressMs()));
        playerView.put("totalActions", nz(player.getTotalActions()));
        playerView.put("lastSettleAt", toMillis(player.getLastSettleAt()));

        List<Map<String, Object>> skills = new ArrayList<>();
        for (SkillDef def : content.skills()) {
            HomesteadSkill row = skillOf(profile, def.code());
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("code", def.code());
            view.put("level", row.getLevel());
            view.put("xp", nz(row.getXp()));
            view.put("xpToNext", content.xpForNextLevel(row.getLevel()));
            skills.add(view);
        }

        List<Map<String, Object>> items = new ArrayList<>();
        for (HomesteadItem row : profile.items) {
            if (nz(row.getQty()) <= 0) {
                continue;
            }
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("code", row.getItemCode());
            view.put("qty", row.getQty());
            items.add(view);
        }

        List<Map<String, Object>> tools = new ArrayList<>();
        for (ToolDef def : content.tools()) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("code", def.code());
            view.put("tier", tierOf(profile, def.skill()));
            tools.add(view);
        }

        HomesteadGear.Bonuses bonus = gearBonuses(profile);
        List<Map<String, Object>> gear = new ArrayList<>();
        for (EquipmentDef def : content.equipment()) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("code", def.code());
            view.put("tier", gearTier(profile, def.code()));
            gear.add(view);
        }
        Map<String, Object> gearBonus = new LinkedHashMap<>();
        gearBonus.put("gatherBp", bonus.gatherBp());
        gearBonus.put("processBp", bonus.processBp());
        gearBonus.put("allBp", bonus.allBp());
        gearBonus.put("sellBp", bonus.sellBonusBp());
        gearBonus.put("extraCapHours", bonus.extraCapHours());
        gearBonus.put("capHours", HomesteadConst.OFFLINE_CAP_HOURS + bonus.extraCapHours());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("player", playerView);
        out.put("skills", skills);
        out.put("items", items);
        out.put("tools", tools);
        out.put("gear", gear);
        out.put("gearBonus", gearBonus);
        out.put("index", HomesteadIndex.compute(levelMap(profile.skills), nz(player.getCoins()),
                qtyMap(profile.items), content));
        out.put("serverNow", Instant.now().toEpochMilli());
        out.put("msPerItem", currentMsPerItem(profile));
        out.put("speedBp", currentSpeedBp(profile));
        out.put("market", marketView(profile));
        out.put("offline", offlineView(settle));
        out.put("content", contentView());
        return out;
    }

    private long currentMsPerItem(Profile profile) {
        ActionDef action = content.action(profile.player.getActionCode());
        if (action == null) {
            return 0L;
        }
        return HomesteadEngine.msPerItem(action.baseMs(),
                skillOf(profile, action.skill()).getLevel(), tierOf(profile, action.skill()),
                speedBonusOf(profile, action.skill()));
    }

    private long currentSpeedBp(Profile profile) {
        ActionDef action = content.action(profile.player.getActionCode());
        if (action == null) {
            return HomesteadConst.SPEED_BASE_BP;
        }
        return HomesteadEngine.speedBp(skillOf(profile, action.skill()).getLevel(), tierOf(profile, action.skill()),
                speedBonusOf(profile, action.skill()));
    }

    /** 某个技能的穿戴加速:采集/加工分开算,袖套给全部技能 */
    private int speedBonusOf(Profile profile, String skillCode) {
        SkillDef def = content.skill(skillCode);
        return gearBonuses(profile).speedBpFor(def == null ? null : def.kind());
    }

    private HomesteadGear.Bonuses gearBonuses(Profile profile) {
        Map<String, Integer> tiers = new HashMap<>();
        for (HomesteadEquipment row : profile.gear) {
            tiers.put(row.getSlotCode(), row.getTier() == null ? 0 : row.getTier());
        }
        return HomesteadGear.of(content, tiers);
    }

    /** 赶集日行情:当天两单 + 共享额度 + 下次换货时刻(前端据此显示剩余与倒计时) */
    private Map<String, Object> marketView(Profile profile) {
        LocalDate today = LocalDate.now(ZONE);
        int used = marketUsed(profile.player, today);
        List<Map<String, Object>> orders = new ArrayList<>();
        for (HomesteadMarket.Order order : HomesteadMarket.orders(today, content)) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("code", order.itemCode());
            view.put("price", order.price());
            view.put("basePrice", order.basePrice());
            view.put("remaining", order.remaining(used));
            orders.add(view);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("date", today.toString());
        out.put("bonusBp", HomesteadConst.MARKET_BONUS_BP);
        out.put("quota", HomesteadConst.MARKET_QUOTA);
        out.put("used", used);
        out.put("resetAt", today.plusDays(1).atStartOfDay(ZONE).toInstant().toEpochMilli());
        out.put("orders", orders);
        return out;
    }

    /** 当天已用额度:存档日期不是今天就当 0(读的时候不写库,真正卖货时才落日期) */
    private int marketUsed(HomesteadPlayer player, LocalDate today) {
        if (player == null || player.getMarketDate() == null || !today.equals(player.getMarketDate())) {
            return 0;
        }
        return player.getMarketUsed() == null ? 0 : Math.max(0, player.getMarketUsed());
    }

    /** 离线摘要只在离开够久且有实际产出时返回,前端据此弹一次结算卡片 */
    private Map<String, Object> offlineView(HomesteadEngine.SettleResult settle) {
        if (settle == null || settle.completions() <= 0 || settle.elapsedMs() < OFFLINE_SUMMARY_MIN_MS) {
            return null;
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("completions", settle.completions());
        view.put("xpGained", settle.xpGained());
        view.put("levelUps", settle.levelUps());
        view.put("elapsedMs", settle.elapsedMs());
        view.put("capped", settle.capped());
        view.put("stopped", settle.stopped());
        view.put("stopReason", settle.stopReason());
        List<Map<String, Object>> gained = new ArrayList<>();
        settle.gained().forEach((code, qty) -> {
            if (qty != null && qty > 0) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("code", code);
                row.put("qty", qty);
                gained.add(row);
            }
        });
        view.put("items", gained);
        return view;
    }

    /** 内容表整体下发给前端,保证名称/价格/耗时只有一份来源(后端 JSON) */
    private Map<String, Object> contentView() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("version", content.contentVersion());
        out.put("maxLevel", content.maxLevel());
        List<Integer> xp = new ArrayList<>();
        for (int level = 1; level < content.maxLevel(); level++) {
            xp.add(content.xpForNextLevel(level));
        }
        out.put("xpToNext", xp);

        List<Map<String, Object>> skills = new ArrayList<>();
        for (SkillDef def : content.skills()) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("code", def.code());
            view.put("name", def.name());
            view.put("nameEn", def.nameEn());
            view.put("kind", def.kind());
            view.put("icon", def.icon());
            view.put("desc", def.desc());
            view.put("descEn", def.descEn());
            skills.add(view);
        }
        out.put("skills", skills);

        List<Map<String, Object>> items = new ArrayList<>();
        for (ItemDef def : content.items()) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("code", def.code());
            view.put("name", def.name());
            view.put("nameEn", def.nameEn());
            view.put("category", def.category());
            view.put("sellPrice", def.sellPrice());
            items.add(view);
        }
        out.put("items", items);

        List<Map<String, Object>> actions = new ArrayList<>();
        for (ActionDef def : content.actions()) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("code", def.code());
            view.put("skill", def.skill());
            view.put("name", def.name());
            view.put("nameEn", def.nameEn());
            view.put("level", def.level());
            view.put("seconds", def.seconds());
            view.put("xp", def.xp());
            view.put("inputs", amountViews(def.inputs()));
            view.put("yields", amountViews(def.yields()));
            if (def.rare() == null) {
                view.put("rare", null);
            } else {
                Map<String, Object> rare = new LinkedHashMap<>();
                rare.put("item", def.rare().item());
                rare.put("qty", def.rare().qty());
                rare.put("every", def.rare().every());
                view.put("rare", rare);
            }
            actions.add(view);
        }
        out.put("actions", actions);

        List<Map<String, Object>> tools = new ArrayList<>();
        for (ToolDef def : content.tools()) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("code", def.code());
            view.put("skill", def.skill());
            view.put("name", def.name());
            view.put("nameEn", def.nameEn());
            view.put("desc", def.desc());
            view.put("descEn", def.descEn());
            List<Map<String, Object>> steps = new ArrayList<>();
            for (ToolDef.ToolStep step : def.steps()) {
                Map<String, Object> stepView = new LinkedHashMap<>();
                stepView.put("coins", step.coins());
                stepView.put("materials", amountViews(step.materials()));
                steps.add(stepView);
            }
            view.put("steps", steps);
            tools.add(view);
        }
        out.put("tools", tools);

        List<Map<String, Object>> equipment = new ArrayList<>();
        for (EquipmentDef def : content.equipment()) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("code", def.code());
            view.put("name", def.name());
            view.put("nameEn", def.nameEn());
            view.put("desc", def.desc());
            view.put("descEn", def.descEn());
            Map<String, Object> effect = new LinkedHashMap<>();
            effect.put("kind", def.effect().kind());
            effect.put("target", def.effect().target());
            effect.put("bp", def.effect().bp());
            effect.put("hours", def.effect().hours());
            view.put("effect", effect);
            List<Map<String, Object>> steps = new ArrayList<>();
            for (EquipmentDef.EquipmentStep step : def.steps()) {
                Map<String, Object> stepView = new LinkedHashMap<>();
                stepView.put("coins", step.coins());
                stepView.put("materials", amountViews(step.materials()));
                steps.add(stepView);
            }
            view.put("steps", steps);
            equipment.add(view);
        }
        out.put("equipment", equipment);
        return out;
    }

    private List<Map<String, Object>> amountViews(List<ActionDef.ItemAmount> amounts) {
        List<Map<String, Object>> out = new ArrayList<>(amounts.size());
        for (ActionDef.ItemAmount amount : amounts) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("item", amount.item());
            view.put("qty", amount.qty());
            out.add(view);
        }
        return out;
    }

    // ------------------------------------------------------------------ 小工具

    /** 写操作幂等:同一 opId 只生效一次;Redis 异常时放行(可用性优先于去重) */
    private boolean allowOp(Long userId, String opId) {
        if (opId == null || opId.isBlank()) {
            return true;
        }
        String key = OP_KEY_PREFIX + userId + ":" + opId.trim();
        try {
            Boolean first = redis.opsForValue().setIfAbsent(key, "1", OP_TTL);
            return !Boolean.FALSE.equals(first);
        } catch (Exception e) {
            log.warn("[homestead] 幂等键写入失败,按未重放处理,userId={}, err={}", userId, e.getMessage());
            return true;
        }
    }

    private HomesteadSkill skillOf(Profile profile, String skillCode) {
        for (HomesteadSkill skill : profile.skills) {
            if (skillCode.equals(skill.getSkillCode())) {
                return skill;
            }
        }
        // ensureRows 已补齐,走到这里说明内容表被运行时改动,按 1 级兜底而不是抛错
        HomesteadSkill fallback = new HomesteadSkill();
        fallback.setUserId(profile.player.getUserId());
        fallback.setSkillCode(skillCode);
        fallback.setLevel(1);
        fallback.setXp(0L);
        profile.skills.add(fallback);
        return fallback;
    }

    private int tierOf(Profile profile, String skillCode) {
        ToolDef def = content.toolOfSkill(skillCode);
        if (def == null) {
            return 0;
        }
        HomesteadTool row = toolRow(profile, def.code());
        return row == null ? 0 : row.getTier();
    }

    private HomesteadTool toolRow(Profile profile, String toolCode) {
        for (HomesteadTool tool : profile.tools) {
            if (toolCode.equals(tool.getToolCode())) {
                return tool;
            }
        }
        return null;
    }

    private int gearTier(Profile profile, String slotCode) {
        HomesteadEquipment row = equipmentRow(profile, slotCode);
        return row == null || row.getTier() == null ? 0 : row.getTier();
    }

    private HomesteadEquipment equipmentRow(Profile profile, String slotCode) {
        for (HomesteadEquipment row : profile.gear) {
            if (slotCode.equals(row.getSlotCode())) {
                return row;
            }
        }
        return null;
    }

    private HomesteadItem itemRow(Profile profile, String itemCode) {
        for (HomesteadItem item : profile.items) {
            if (itemCode.equals(item.getItemCode())) {
                return item;
            }
        }
        return null;
    }

    private Map<String, Long> qtyMap(List<HomesteadItem> items) {
        Map<String, Long> out = new HashMap<>();
        for (HomesteadItem item : items) {
            if (nz(item.getQty()) > 0) {
                out.put(item.getItemCode(), item.getQty());
            }
        }
        return out;
    }

    private Map<String, Integer> levelMap(List<HomesteadSkill> skills) {
        Map<String, Integer> out = new HashMap<>();
        for (HomesteadSkill skill : skills) {
            out.put(skill.getSkillCode(), skill.getLevel());
        }
        return out;
    }

    private Map<Long, Map<String, Integer>> groupLevels(List<HomesteadSkill> skills) {
        Map<Long, Map<String, Integer>> out = new HashMap<>();
        for (HomesteadSkill skill : skills) {
            out.computeIfAbsent(skill.getUserId(), k -> new HashMap<>())
                    .put(skill.getSkillCode(), skill.getLevel());
        }
        return out;
    }

    private Map<Long, Map<String, Long>> groupQty(List<HomesteadItem> items) {
        Map<Long, Map<String, Long>> out = new HashMap<>();
        for (HomesteadItem item : items) {
            if (nz(item.getQty()) <= 0) {
                continue;
            }
            out.computeIfAbsent(item.getUserId(), k -> new HashMap<>())
                    .put(item.getItemCode(), item.getQty());
        }
        return out;
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }

    private static long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static long clampCoins(long coins) {
        return Math.max(0L, Math.min(HomesteadConst.MAX_COINS, coins));
    }

    private static long toMillis(LocalDateTime time) {
        return time == null ? 0L : time.atZone(ZONE).toInstant().toEpochMilli();
    }

    private static LocalDateTime fromMillis(long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZONE);
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }
}
