package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.PointsRuleConst;
import com.ihomy.common.ResultCode;
import com.ihomy.entity.HomesteadBuff;
import com.ihomy.entity.HomesteadBuilding;
import com.ihomy.entity.HomesteadGear;
import com.ihomy.entity.HomesteadItem;
import com.ihomy.entity.HomesteadPlayer;
import com.ihomy.entity.HomesteadSkill;
import com.ihomy.entity.HomesteadTool;
import com.ihomy.entity.HomesteadWear;
import com.ihomy.homestead.HomesteadConst;
import com.ihomy.homestead.content.ActionDef;
import com.ihomy.homestead.content.BuildingDef;
import com.ihomy.homestead.content.EquipmentDef;
import com.ihomy.homestead.content.HomesteadContent;
import com.ihomy.homestead.content.ItemDef;
import com.ihomy.homestead.content.SkillDef;
import com.ihomy.homestead.content.ToolDef;
import com.ihomy.homestead.core.HomesteadBonus;
import com.ihomy.homestead.core.HomesteadEngine;
import com.ihomy.homestead.core.HomesteadIndex;
import com.ihomy.homestead.core.HomesteadMarket;
import com.ihomy.mapper.HomesteadBuffMapper;
import com.ihomy.mapper.HomesteadBuildingMapper;
import com.ihomy.mapper.HomesteadGearMapper;
import com.ihomy.mapper.HomesteadItemMapper;
import com.ihomy.mapper.HomesteadPlayerMapper;
import com.ihomy.mapper.HomesteadSkillMapper;
import com.ihomy.mapper.HomesteadToolMapper;
import com.ihomy.mapper.HomesteadWearMapper;
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
 *   <li>每次读状态都先结算一次(离线时长按 last_settle_at 补算,上限 24 小时 + 仓房/鞋加成)。</li>
 *   <li>概率产出(省料 / 多收 / 稀有)由引擎用「玩家 + 动作 + 次数」确定性掷点,同一进度重读结果不变。</li>
 *   <li>加成唯一口径 {@link HomesteadBonus}:工具(按动作) + 已穿装备(含强化) + 场地 + 生效中的料理 buff。</li>
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
    /** 一次最多吃几份料理(份数只用来延长同一份 buff 的时长) */
    private static final int MAX_CONSUME_QTY = 5;
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final HomesteadContent content;
    private final HomesteadPlayerMapper playerMapper;
    private final HomesteadSkillMapper skillMapper;
    private final HomesteadItemMapper itemMapper;
    private final HomesteadToolMapper toolMapper;
    private final HomesteadGearMapper gearMapper;
    private final HomesteadWearMapper wearMapper;
    private final HomesteadBuildingMapper buildingMapper;
    private final HomesteadBuffMapper buffMapper;
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

    /** 切换挂机动作:先结算旧动作,再落新动作(同一动作不清零掷点计数与余量) */
    @Transactional
    public Map<String, Object> startAction(Long userId, Long familyId, String actionCode, String opId) {
        Profile profile = load(userId, familyId);
        HomesteadEngine.SettleResult settle = advance(profile, familyId);
        ActionDef action = content.action(actionCode);
        if (action == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "没有这个动作");
        }
        requireActionUnlocked(profile, action);
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
        long have = row == null ? 0L : nz(row.getQty());
        if (have < qty) {
            throw new BizException(ResultCode.BAD_REQUEST, "背包里没有这么多");
        }
        LocalDate today = LocalDate.now(ZONE);
        HomesteadMarket.Sale sale = HomesteadMarket.plan(
                HomesteadMarket.orders(today, content), marketUsed(profile.player, today), item, qty);
        if (allowOp(userId, opId)) {
            applyItems(profile, Map.of(), Map.of(itemCode, qty));
            // 行情价算完再乘卖价加成(晒场/挂饰),两段都向下取整,便于对账
            long coins = bonusOf(profile, null).sellCoins(sale.coins());
            profile.player.setCoins(clampCoins(nz(profile.player.getCoins()) + coins));
            profile.player.setMarketDate(today);
            profile.player.setMarketUsed(sale.usedAfter());
            playerMapper.updateProgress(profile.player);
        }
        return snapshot(profile, settle);
    }

    /** 制造 / 升级工具:每个动作一件,tier 0→5,配方逐阶换更高档材料 */
    @Transactional
    public Map<String, Object> craftTool(Long userId, Long familyId, String toolCode, String opId) {
        Profile profile = load(userId, familyId);
        HomesteadEngine.SettleResult settle = advance(profile, familyId);
        ToolDef tool = content.tool(toolCode);
        if (tool == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "没有这个工具");
        }
        HomesteadTool row = toolRow(profile, toolCode);
        int tier = row == null ? 0 : nzInt(row.getTier());
        if (tier >= HomesteadConst.MAX_TOOL_TIER) {
            throw new BizException(ResultCode.BAD_REQUEST, "这件工具已经升到顶了");
        }
        requireSkillLevel(profile, tool.skill(), tool.requireLevel(), "做这件工具");
        ToolDef.ToolStep step = tool.nextStep(tier);
        if (step == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "这件工具已经升到顶了");
        }
        Map<String, Long> need = checkCost(profile, step.coins(), step.materials());
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

    /** 制造装备:做出来是 0 级强化,之后才能用 / 强化 */
    @Transactional
    public Map<String, Object> craftGear(Long userId, Long familyId, String gearCode, String opId) {
        Profile profile = load(userId, familyId);
        HomesteadEngine.SettleResult settle = advance(profile, familyId);
        EquipmentDef def = content.gear(gearCode);
        if (def == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "没有这件装备");
        }
        if (gearRow(profile, gearCode) != null) {
            throw new BizException(ResultCode.BAD_REQUEST, "这件装备已经做好了");
        }
        requireSkillLevel(profile, def.skill(), def.requireLevel(), "做这件装备");
        EquipmentDef.EquipmentStep step = def.craft();
        if (step == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "这件装备没有制造配方");
        }
        Map<String, Long> need = checkCost(profile, step.coins(), step.materials());
        if (allowOp(userId, opId)) {
            applyItems(profile, Map.of(), need);
            profile.player.setCoins(clampCoins(nz(profile.player.getCoins()) - step.coins()));
            HomesteadGear fresh = new HomesteadGear();
            fresh.setUserId(userId);
            fresh.setGearCode(gearCode);
            fresh.setEnhance(0);
            gearMapper.insert(fresh);
            profile.gear.add(fresh);
            playerMapper.updateProgress(profile.player);
        }
        return snapshot(profile, settle);
    }

    /** 强化装备:0→5,每级效果 +50%,材料逐阶换更高档 */
    @Transactional
    public Map<String, Object> enhanceGear(Long userId, Long familyId, String gearCode, String opId) {
        Profile profile = load(userId, familyId);
        HomesteadEngine.SettleResult settle = advance(profile, familyId);
        EquipmentDef def = content.gear(gearCode);
        if (def == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "没有这件装备");
        }
        HomesteadGear row = gearRow(profile, gearCode);
        if (row == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "还没做这件装备");
        }
        int enhance = nzInt(row.getEnhance());
        if (enhance >= HomesteadConst.MAX_GEAR_TIER) {
            throw new BizException(ResultCode.BAD_REQUEST, "这件装备已经强化到顶了");
        }
        EquipmentDef.EquipmentStep step = def.nextStep(enhance);
        if (step == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "这件装备已经强化到顶了");
        }
        Map<String, Long> need = checkCost(profile, step.coins(), step.materials());
        if (allowOp(userId, opId)) {
            applyItems(profile, Map.of(), need);
            profile.player.setCoins(clampCoins(nz(profile.player.getCoins()) - step.coins()));
            row.setEnhance(enhance + 1);
            gearMapper.updateById(row);
            playerMapper.updateProgress(profile.player);
        }
        return snapshot(profile, settle);
    }

    /** 穿 / 脱:gearCode 为空即脱下该部位;穿上要求已制造且等级达标 */
    @Transactional
    public Map<String, Object> wear(Long userId, Long familyId, String slotCode, String gearCode, String opId) {
        Profile profile = load(userId, familyId);
        HomesteadEngine.SettleResult settle = advance(profile, familyId);
        String slot;
        String targetGear = null;
        if (gearCode == null || gearCode.isBlank()) {
            if (slotCode == null || !HomesteadConst.GEAR_SLOTS.contains(slotCode)) {
                throw new BizException(ResultCode.BAD_REQUEST, "没有这个部位");
            }
            slot = slotCode;
        } else {
            EquipmentDef def = content.gear(gearCode);
            if (def == null) {
                throw new BizException(ResultCode.BAD_REQUEST, "没有这件装备");
            }
            if (gearRow(profile, gearCode) == null) {
                throw new BizException(ResultCode.BAD_REQUEST, "还没做这件装备");
            }
            requireSkillLevel(profile, def.skill(), def.requireLevel(), "穿这件装备");
            slot = def.slot();
            targetGear = def.code();
        }
        if (allowOp(userId, opId)) {
            HomesteadWear row = wearRow(profile, slot);
            if (row == null) {
                HomesteadWear fresh = new HomesteadWear();
                fresh.setUserId(userId);
                fresh.setSlotCode(slot);
                fresh.setGearCode(targetGear);
                wearMapper.insert(fresh);
                profile.wear.add(fresh);
            } else {
                row.setGearCode(targetGear);
                wearMapper.update(null, new LambdaUpdateWrapper<HomesteadWear>()
                        .eq(HomesteadWear::getUserId, userId)
                        .eq(HomesteadWear::getSlotCode, slot)
                        .set(HomesteadWear::getGearCode, targetGear));
            }
            playerMapper.updateProgress(profile.player);
        }
        return snapshot(profile, settle);
    }

    /** 造 / 升场地:0→10 级,效果按级线性叠加,工坊 Lv5 解锁该技能的门槛 70 动作 */
    @Transactional
    public Map<String, Object> upgradeBuilding(Long userId, Long familyId, String buildingCode, String opId) {
        Profile profile = load(userId, familyId);
        HomesteadEngine.SettleResult settle = advance(profile, familyId);
        BuildingDef def = content.building(buildingCode);
        if (def == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "没有这栋场地");
        }
        HomesteadBuilding row = buildingRow(profile, buildingCode);
        int level = row == null ? 0 : nzInt(row.getLevel());
        if (level >= def.maxLevel()) {
            throw new BizException(ResultCode.BAD_REQUEST, "这栋场地已经升到顶了");
        }
        BuildingDef.BuildingStep step = def.nextStep(level);
        if (step == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "这栋场地已经升到顶了");
        }
        Map<String, Long> need = checkCost(profile, step.coins(), step.materials());
        if (allowOp(userId, opId)) {
            applyItems(profile, Map.of(), need);
            profile.player.setCoins(clampCoins(nz(profile.player.getCoins()) - step.coins()));
            if (row == null) {
                HomesteadBuilding fresh = new HomesteadBuilding();
                fresh.setUserId(userId);
                fresh.setBuildingCode(buildingCode);
                fresh.setLevel(level + 1);
                buildingMapper.insert(fresh);
                profile.buildings.add(fresh);
            } else {
                row.setLevel(level + 1);
                buildingMapper.updateById(row);
            }
            playerMapper.updateProgress(profile.player);
        }
        return snapshot(profile, settle);
    }

    /**
     * 吃料理:消耗物品换一条限时 buff。同一种料理再吃只延长到期时刻;
     * 同时最多 {@value HomesteadConst#MAX_ACTIVE_BUFFS} 种(第四种要等最早那份到期或手动等它过)。
     */
    @Transactional
    public Map<String, Object> consume(Long userId, Long familyId, String itemCode, int qty, String opId) {
        Profile profile = load(userId, familyId);
        HomesteadEngine.SettleResult settle = advance(profile, familyId);
        ItemDef item = content.item(itemCode);
        if (item == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "没有这个物品");
        }
        ItemDef.BuffDef buff = item.buff();
        if (buff == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "这个物品不能吃");
        }
        if (qty <= 0 || qty > MAX_CONSUME_QTY) {
            throw new BizException(ResultCode.BAD_REQUEST, "一次最多吃 " + MAX_CONSUME_QTY + " 份");
        }
        HomesteadItem row = itemRow(profile, itemCode);
        if (row == null || nz(row.getQty()) < qty) {
            throw new BizException(ResultCode.BAD_REQUEST, "背包里没有这么多");
        }
        long nowMs = Instant.now().toEpochMilli();
        LocalDateTime expireAt = fromMillis(nowMs + buff.minutes() * 60_000L * qty);
        HomesteadBuff existing = activeBuffOf(profile, itemCode, nowMs);
        if (existing == null) {
            long activeKinds = profile.buffs.stream()
                    .filter(b -> b.getExpireAt() != null && toMillis(b.getExpireAt()) > nowMs)
                    .count();
            if (activeKinds >= HomesteadConst.MAX_ACTIVE_BUFFS) {
                throw new BizException(ResultCode.BAD_REQUEST,
                        "同时最多 " + HomesteadConst.MAX_ACTIVE_BUFFS + " 种料理加成,先等一份到期");
            }
        }
        if (allowOp(userId, opId)) {
            applyItems(profile, Map.of(), Map.of(itemCode, (long) qty));
            if (existing == null) {
                HomesteadBuff fresh = new HomesteadBuff();
                fresh.setUserId(userId);
                fresh.setItemCode(itemCode);
                fresh.setKind(buff.kind());
                fresh.setTarget(buff.target());
                fresh.setBp(buff.bp());
                fresh.setExpireAt(expireAt);
                buffMapper.insert(fresh);
                profile.buffs.add(fresh);
            } else {
                long base = Math.max(nowMs, toMillis(existing.getExpireAt()));
                LocalDateTime extended = fromMillis(base + buff.minutes() * 60_000L * qty);
                existing.setExpireAt(extended);
                buffMapper.updateById(existing);
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
        List<HomesteadGear> gear;
        List<HomesteadWear> wear;
        List<HomesteadBuilding> buildings;
        List<HomesteadBuff> buffs;
    }

    /** 锁行装载;首次访问建号并补齐技能/工具/部位/场地行(内容扩表后老存档也能直接用) */
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
        profile.gear = new ArrayList<>(gearMapper.selectList(
                new LambdaQueryWrapper<HomesteadGear>().eq(HomesteadGear::getUserId, userId)));
        profile.wear = new ArrayList<>(wearMapper.selectList(
                new LambdaQueryWrapper<HomesteadWear>().eq(HomesteadWear::getUserId, userId)));
        profile.buildings = new ArrayList<>(buildingMapper.selectList(
                new LambdaQueryWrapper<HomesteadBuilding>().eq(HomesteadBuilding::getUserId, userId)));
        profile.buffs = new ArrayList<>(buffMapper.selectList(
                new LambdaQueryWrapper<HomesteadBuff>().eq(HomesteadBuff::getUserId, userId)));
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
        profile.wear.forEach(w -> slotCodes.add(w.getSlotCode()));
        for (String slot : HomesteadConst.GEAR_SLOTS) {
            if (slotCodes.add(slot)) {
                HomesteadWear wear = new HomesteadWear();
                wear.setUserId(userId);
                wear.setSlotCode(slot);
                wearMapper.insert(wear);
                profile.wear.add(wear);
            }
        }
        Set<String> buildingCodes = new HashSet<>();
        profile.buildings.forEach(b -> buildingCodes.add(b.getBuildingCode()));
        for (BuildingDef def : content.buildings()) {
            if (buildingCodes.add(def.code())) {
                HomesteadBuilding building = new HomesteadBuilding();
                building.setUserId(userId);
                building.setBuildingCode(def.code());
                building.setLevel(0);
                buildingMapper.insert(building);
                profile.buildings.add(building);
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
            HomesteadBonus.Bonus base = bonusOf(profile, action);
            HomesteadEngine.SettleInput input = new HomesteadEngine.SettleInput(
                    action,
                    skill.getLevel(),
                    skill.getXp(),
                    nz(profile.player.getActionSeq()),
                    nz(profile.player.getActionProgressMs()),
                    toMillis(profile.player.getLastSettleAt()),
                    nowMs,
                    qtyMap(profile.items),
                    base,
                    activeBuffSpans(profile, nowMs),
                    base.capMs(),
                    nz(profile.player.getUserId()));
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
        // 过期 buff 顺手清掉(读取时不写库,只在真正的写路径删)
        purgeExpiredBuffs(profile, nowMs);
        profile.player.setFamilyId(familyId);
        profile.player.setContentVersion(content.contentVersion());
        profile.player.setLastSettleAt(nowAt);
        playerMapper.updateProgress(profile.player);
        return settle;
    }

    private void purgeExpiredBuffs(Profile profile, long nowMs) {
        List<HomesteadBuff> expired = new ArrayList<>();
        for (HomesteadBuff buff : profile.buffs) {
            if (buff.getExpireAt() == null || toMillis(buff.getExpireAt()) <= nowMs) {
                expired.add(buff);
            }
        }
        if (expired.isEmpty()) {
            return;
        }
        List<Long> ids = new ArrayList<>(expired.size());
        expired.forEach(buff -> ids.add(buff.getId()));
        buffMapper.deleteBatchIds(ids);
        profile.buffs.removeAll(expired);
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
        long nowMs = Instant.now().toEpochMilli();
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
            view.put("xpToNext", row.getLevel() >= content.maxLevel() ? 0 : content.xpForNextLevel(row.getLevel()));
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
            view.put("tier", toolTier(profile, def.code()));
            tools.add(view);
        }

        List<Map<String, Object>> gear = new ArrayList<>();
        for (EquipmentDef def : content.equipment()) {
            HomesteadGear row = gearRow(profile, def.code());
            if (row == null) {
                continue;
            }
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("code", def.code());
            view.put("enhance", nzInt(row.getEnhance()));
            gear.add(view);
        }

        List<Map<String, Object>> wear = new ArrayList<>();
        for (String slot : HomesteadConst.GEAR_SLOTS) {
            HomesteadWear row = wearRow(profile, slot);
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("slot", slot);
            view.put("gearCode", row == null ? null : row.getGearCode());
            wear.add(view);
        }

        List<Map<String, Object>> buildings = new ArrayList<>();
        for (BuildingDef def : content.buildings()) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("code", def.code());
            view.put("level", buildingLevel(profile, def.code()));
            buildings.add(view);
        }

        ActionDef current = content.action(player.getActionCode());
        HomesteadBonus.Bonus bonus = effectiveBonus(profile, current, nowMs);
        Map<String, Object> bonusView = new LinkedHashMap<>();
        bonusView.put("speedBp", bonus.speedBp());
        bonusView.put("xpBp", bonus.xpBp());
        bonusView.put("yieldBp", bonus.yieldBp());
        bonusView.put("rareBp", bonus.rareBp());
        bonusView.put("efficiencyBp", bonus.efficiencyBp());
        bonusView.put("sellBonusBp", bonus.sellBonusBp());
        bonusView.put("capHours", HomesteadConst.OFFLINE_CAP_HOURS + bonus.extraCapHours());

        List<Map<String, Object>> buffs = new ArrayList<>();
        for (HomesteadBuff buff : profile.buffs) {
            long expireAt = toMillis(buff.getExpireAt());
            if (expireAt <= nowMs) {
                continue;
            }
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("item", buff.getItemCode());
            view.put("kind", buff.getKind());
            view.put("target", buff.getTarget());
            view.put("bp", nzInt(buff.getBp()));
            view.put("expireAt", expireAt);
            view.put("remainingMs", expireAt - nowMs);
            buffs.add(view);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("player", playerView);
        out.put("skills", skills);
        out.put("items", items);
        out.put("tools", tools);
        out.put("gear", gear);
        out.put("wear", wear);
        out.put("buildings", buildings);
        out.put("buffs", buffs);
        out.put("bonus", bonusView);
        out.put("index", HomesteadIndex.compute(levelMap(profile.skills), nz(player.getCoins()),
                qtyMap(profile.items), content));
        out.put("serverNow", nowMs);
        out.put("msPerItem", currentMsPerItem(profile, current, bonus));
        out.put("speedBp", current == null
                ? HomesteadConst.SPEED_BASE_BP
                : HomesteadEngine.speedBp(skillOf(profile, current.skill()).getLevel(), bonus.speedBp()));
        out.put("market", marketView(profile));
        out.put("offline", offlineView(settle));
        out.put("content", contentView());
        return out;
    }

    private long currentMsPerItem(Profile profile, ActionDef action, HomesteadBonus.Bonus bonus) {
        if (action == null) {
            return 0L;
        }
        return HomesteadEngine.msPerItem(action.baseMs(),
                skillOf(profile, action.skill()).getLevel(), bonus.speedBp());
    }

    /** 当前动作的生效加成(静态 + buff),空闲时只有全局项(离线上限/卖价) */
    private HomesteadBonus.Bonus effectiveBonus(Profile profile, ActionDef action, long nowMs) {
        HomesteadBonus.Bonus base = bonusOf(profile, action);
        String kind = action == null ? "ALL" : skillKind(action.skill());
        return HomesteadBonus.withBuffs(base, kind, activeBuffSpans(profile, nowMs), nowMs);
    }

    private HomesteadBonus.Bonus bonusOf(Profile profile, ActionDef action) {
        List<HomesteadBonus.WornGear> worn = new ArrayList<>();
        for (HomesteadWear row : profile.wear) {
            if (row.getGearCode() == null || row.getGearCode().isBlank()) {
                continue;
            }
            HomesteadGear gear = gearRow(profile, row.getGearCode());
            worn.add(new HomesteadBonus.WornGear(row.getGearCode(),
                    gear == null ? 0 : nzInt(gear.getEnhance())));
        }
        Map<String, Integer> levels = new HashMap<>();
        for (HomesteadBuilding row : profile.buildings) {
            if (nzInt(row.getLevel()) > 0) {
                levels.put(row.getBuildingCode(), nzInt(row.getLevel()));
            }
        }
        int toolTier = action == null ? 0 : toolTier(profile, action);
        return HomesteadBonus.resolve(content, action, toolTier, worn, levels);
    }

    private List<HomesteadBonus.BuffSpan> activeBuffSpans(Profile profile, long nowMs) {
        List<HomesteadBonus.BuffSpan> spans = new ArrayList<>(profile.buffs.size());
        for (HomesteadBuff buff : profile.buffs) {
            long expireAt = toMillis(buff.getExpireAt());
            if (expireAt > nowMs) {
                spans.add(new HomesteadBonus.BuffSpan(buff.getKind(), buff.getTarget(),
                        nzInt(buff.getBp()), expireAt));
            }
        }
        return spans;
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

    /** 内容表整体下发给前端,保证名称/价格/耗时/配方只有一份来源(后端 JSON) */
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
            if (def.buff() == null) {
                view.put("buff", null);
            } else {
                Map<String, Object> buff = new LinkedHashMap<>();
                buff.put("kind", def.buff().kind());
                buff.put("target", def.buff().target());
                buff.put("bp", def.buff().bp());
                buff.put("minutes", def.buff().minutes());
                view.put("buff", buff);
            }
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
                rare.put("chanceBp", def.rare().chanceBp());
                view.put("rare", rare);
            }
            view.put("requireBuilding", def.requireBuilding());
            view.put("requireBuildingLevel", def.requireBuildingLevel());
            actions.add(view);
        }
        out.put("actions", actions);

        List<Map<String, Object>> tools = new ArrayList<>();
        for (ToolDef def : content.tools()) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("code", def.code());
            view.put("action", def.action());
            view.put("skill", def.skill());
            view.put("name", def.name());
            view.put("nameEn", def.nameEn());
            view.put("desc", def.desc());
            view.put("descEn", def.descEn());
            view.put("requireLevel", def.requireLevel());
            view.put("effects", effectViews(def.effects()));
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
            view.put("slot", def.slot());
            view.put("skill", def.skill());
            view.put("requireLevel", def.requireLevel());
            view.put("name", def.name());
            view.put("nameEn", def.nameEn());
            view.put("desc", def.desc());
            view.put("descEn", def.descEn());
            view.put("effects", effectViews(def.effects()));
            view.put("craft", stepView(def.craft()));
            List<Map<String, Object>> steps = new ArrayList<>();
            for (EquipmentDef.EquipmentStep step : def.steps()) {
                steps.add(stepView(step));
            }
            view.put("steps", steps);
            equipment.add(view);
        }
        out.put("equipment", equipment);

        List<Map<String, Object>> buildings = new ArrayList<>();
        for (BuildingDef def : content.buildings()) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("code", def.code());
            view.put("skill", def.skill());
            view.put("unlockAction", def.unlockAction());
            view.put("name", def.name());
            view.put("nameEn", def.nameEn());
            view.put("icon", def.icon());
            view.put("desc", def.desc());
            view.put("descEn", def.descEn());
            view.put("maxLevel", def.maxLevel());
            view.put("effects", effectViews(def.effects()));
            List<Map<String, Object>> steps = new ArrayList<>();
            for (BuildingDef.BuildingStep step : def.steps()) {
                Map<String, Object> stepView = new LinkedHashMap<>();
                stepView.put("coins", step.coins());
                stepView.put("materials", amountViews(step.materials()));
                steps.add(stepView);
            }
            view.put("steps", steps);
            buildings.add(view);
        }
        out.put("buildings", buildings);
        out.put("gearSlots", new ArrayList<>(HomesteadConst.GEAR_SLOTS));
        return out;
    }

    private List<Map<String, Object>> effectViews(List<?> effects) {
        List<Map<String, Object>> out = new ArrayList<>(effects.size());
        for (Object raw : effects) {
            Map<String, Object> view = new LinkedHashMap<>();
            if (raw instanceof ToolDef.ToolEffect effect) {
                view.put("kind", effect.kind());
                view.put("target", "ACTION");
                view.put("bp", effect.bp());
                view.put("hours", 0);
            } else if (raw instanceof EquipmentDef.EffectDef effect) {
                view.put("kind", effect.kind());
                view.put("target", effect.target());
                view.put("bp", effect.bp());
                view.put("hours", effect.hours());
            } else if (raw instanceof BuildingDef.EffectDef effect) {
                view.put("kind", effect.kind());
                view.put("target", effect.target());
                view.put("bp", effect.bp());
                view.put("hours", effect.hours());
            }
            out.add(view);
        }
        return out;
    }

    private Map<String, Object> stepView(EquipmentDef.EquipmentStep step) {
        if (step == null) {
            return null;
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("coins", step.coins());
        view.put("materials", amountViews(step.materials()));
        return view;
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

    /** 动作解锁:技能等级 + (工坊动作)对应场地等级 */
    private void requireActionUnlocked(Profile profile, ActionDef action) {
        HomesteadSkill skill = skillOf(profile, action.skill());
        if (skill.getLevel() < action.level()) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "这个动作要 " + action.level() + " 级才能做");
        }
        if (action.needsBuilding()) {
            int level = buildingLevel(profile, action.requireBuilding());
            if (level < action.requireBuildingLevel()) {
                BuildingDef building = content.building(action.requireBuilding());
                String name = building == null ? "对应场地" : building.name();
                throw new BizException(ResultCode.BAD_REQUEST,
                        "要先把" + name + "升到 " + action.requireBuildingLevel() + " 级");
            }
        }
    }

    private void requireSkillLevel(Profile profile, String skillCode, int level, String what) {
        if (level <= 1) {
            return;
        }
        if (skillOf(profile, skillCode).getLevel() < level) {
            throw new BizException(ResultCode.BAD_REQUEST, what + "要 " + level + " 级");
        }
    }

    /** 校验铜板与材料够不够,返回材料总需求(同物品合并) */
    private Map<String, Long> checkCost(Profile profile, long coins, List<ActionDef.ItemAmount> materials) {
        if (nz(profile.player.getCoins()) < coins) {
            throw new BizException(ResultCode.BAD_REQUEST, "铜板不够");
        }
        Map<String, Long> need = new HashMap<>();
        Map<String, Long> stock = qtyMap(profile.items);
        for (ActionDef.ItemAmount material : materials) {
            if (stock.getOrDefault(material.item(), 0L) < material.qty()) {
                throw new BizException(ResultCode.BAD_REQUEST, "材料不够");
            }
            need.merge(material.item(), material.qty(), Long::sum);
        }
        return need;
    }

    private String skillKind(String skillCode) {
        SkillDef def = content.skill(skillCode);
        return def == null || def.kind() == null ? "ALL" : def.kind();
    }

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
            if (skillCode != null && skillCode.equals(skill.getSkillCode())) {
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

    private int toolTier(Profile profile, ActionDef action) {
        ToolDef def = content.toolOfAction(action.code());
        return def == null ? 0 : toolTier(profile, def.code());
    }

    private int toolTier(Profile profile, String toolCode) {
        HomesteadTool row = toolRow(profile, toolCode);
        return row == null ? 0 : nzInt(row.getTier());
    }

    private HomesteadTool toolRow(Profile profile, String toolCode) {
        for (HomesteadTool tool : profile.tools) {
            if (toolCode != null && toolCode.equals(tool.getToolCode())) {
                return tool;
            }
        }
        return null;
    }

    private HomesteadGear gearRow(Profile profile, String gearCode) {
        for (HomesteadGear row : profile.gear) {
            if (gearCode != null && gearCode.equals(row.getGearCode())) {
                return row;
            }
        }
        return null;
    }

    private HomesteadWear wearRow(Profile profile, String slotCode) {
        for (HomesteadWear row : profile.wear) {
            if (slotCode != null && slotCode.equals(row.getSlotCode())) {
                return row;
            }
        }
        return null;
    }

    private HomesteadBuilding buildingRow(Profile profile, String buildingCode) {
        for (HomesteadBuilding row : profile.buildings) {
            if (buildingCode != null && buildingCode.equals(row.getBuildingCode())) {
                return row;
            }
        }
        return null;
    }

    private int buildingLevel(Profile profile, String buildingCode) {
        HomesteadBuilding row = buildingRow(profile, buildingCode);
        return row == null ? 0 : nzInt(row.getLevel());
    }

    private HomesteadBuff activeBuffOf(Profile profile, String itemCode, long nowMs) {
        for (HomesteadBuff buff : profile.buffs) {
            if (itemCode.equals(buff.getItemCode()) && buff.getExpireAt() != null
                    && toMillis(buff.getExpireAt()) > nowMs) {
                return buff;
            }
        }
        return null;
    }

    private HomesteadItem itemRow(Profile profile, String itemCode) {
        for (HomesteadItem item : profile.items) {
            if (itemCode != null && itemCode.equals(item.getItemCode())) {
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

    private static int nzInt(Integer value) {
        return value == null ? 0 : value;
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
