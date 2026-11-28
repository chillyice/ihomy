package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.PointsRuleConst;
import com.ihomy.common.ResultCode;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.ihomy.entity.HomesteadBuff;
import com.ihomy.entity.HomesteadBuilding;
import com.ihomy.entity.HomesteadExpedition;
import com.ihomy.entity.HomesteadGear;
import com.ihomy.entity.HomesteadItem;
import com.ihomy.entity.HomesteadLoadout;
import com.ihomy.entity.HomesteadPlayer;
import com.ihomy.entity.HomesteadSkill;
import com.ihomy.entity.HomesteadTool;
import com.ihomy.entity.HomesteadWear;
import com.ihomy.homestead.HomesteadConst;
import com.ihomy.homestead.content.ActionDef;
import com.ihomy.homestead.content.BuildingDef;
import com.ihomy.homestead.content.EquipmentDef;
import com.ihomy.homestead.content.ExpeditionDef;
import com.ihomy.homestead.content.GearSetDef;
import com.ihomy.homestead.content.HomesteadContent;
import com.ihomy.homestead.content.ItemDef;
import com.ihomy.homestead.content.RarityDef;
import com.ihomy.homestead.content.SkillDef;
import com.ihomy.homestead.content.ToolDef;
import com.ihomy.homestead.core.HomesteadBonus;
import com.ihomy.homestead.core.HomesteadEngine;
import com.ihomy.homestead.core.HomesteadIndex;
import com.ihomy.homestead.core.HomesteadLoot;
import com.ihomy.homestead.core.HomesteadMarket;
import com.ihomy.mapper.HomesteadBuffMapper;
import com.ihomy.mapper.HomesteadBuildingMapper;
import com.ihomy.mapper.HomesteadExpeditionMapper;
import com.ihomy.mapper.HomesteadGearMapper;
import com.ihomy.mapper.HomesteadItemMapper;
import com.ihomy.mapper.HomesteadLoadoutMapper;
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
 *   <li>概率产出(省料 / 多收 / 稀有 / 掉落品质与词缀)由引擎用「玩家 + 来源 + 序号」确定性掷点,
 *       同一进度重读结果不变;探险手记落库后不再重算。</li>
 *   <li>加成唯一口径 {@link HomesteadBonus}:工具(按动作) + 穿戴实例(品质·强化·词缀) + 套装 + 场地
 *       + 生效中的料理 buff。</li>
 *   <li>并发:事务内 SELECT ... FOR UPDATE 锁玩家行,同一账号的多标签页/多设备串行入账。</li>
 *   <li>幂等:写操作带 Idempotency-Key 时用 Redis SETNX 去重(10 分钟);Redis 不可用则放行(家庭内互信)。</li>
 *   <li>积分:技能升级与探险得分由服务端直接发放,客户端没有任何上报收益或积分的入口。</li>
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
    /** 状态里带最近几份探险手记(更早的留在库里,不随轮询下发) */
    private static final int TRIP_HISTORY = 3;
    /** 一次请求最多结算几趟探险(攒着多趟没看的兜底,正常一天 3 趟) */
    private static final int TRIP_SETTLE_LIMIT = 10;
    /** 积分上限:防极端存档把值推到溢出 */
    private static final long MAX_EXPLORE_POINTS = 100_000_000L;
    /** 词缀重铸消耗的稀有材质(松脂:最早能稳定拿到的稀有副产) */
    private static final String REROLL_ITEM = "PINE_RESIN";
    /** 一键最优支持的追求项(对应装备加成类型) */
    private static final Set<String> AUTO_FOCUS = Set.of("SPEED", "XP", "RARE", "YIELD");
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
    private final HomesteadLoadoutMapper loadoutMapper;
    private final HomesteadExpeditionMapper expeditionMapper;
    private final StringRedisTemplate redis;
    private final PointsService pointsService;
    private final ObjectMapper objectMapper;

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

    /**
     * 打造装备:产出「实例」(固定普通品质、无词缀、0 级强化),同一蓝本可以反复打造、
     * 造多了可以分解换材料;只能打造的蓝本(source=DROP)不在这里出现。
     */
    @Transactional
    public Map<String, Object> craftGear(Long userId, Long familyId, String gearCode, String opId) {
        Profile profile = load(userId, familyId);
        HomesteadEngine.SettleResult settle = advance(profile, familyId);
        EquipmentDef def = content.gear(gearCode);
        if (def == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "没有这件装备");
        }
        if (!def.craftable()) {
            throw new BizException(ResultCode.BAD_REQUEST, "这件装备只能从探险或宝箱里获得");
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
            insertGear(profile, userId, new HomesteadLoot.Instance(gearCode,
                    HomesteadConst.QUALITY_COMMON, ""));
            playerMapper.updateProgress(profile.player);
        }
        return snapshot(profile, settle);
    }

    /** 强化某一件实例:0→5,每级效果 +50%,材料逐阶换更高档 */
    @Transactional
    public Map<String, Object> enhanceGear(Long userId, Long familyId, Long gearId, String opId) {
        Profile profile = load(userId, familyId);
        HomesteadEngine.SettleResult settle = advance(profile, familyId);
        HomesteadGear row = requireGear(profile, gearId);
        EquipmentDef def = requireGearDef(row);
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

    /**
     * 穿 / 脱:gearId 为空即脱下该部位;穿上按实例所在的槽位家族判格(戒指两格都能穿),
     * 同一件实例只能穿一格(穿到新格会自动从旧格脱下),要求技能等级达标。
     */
    @Transactional
    public Map<String, Object> wear(Long userId, Long familyId, String slotCode, Long gearId, String opId) {
        Profile profile = load(userId, familyId);
        HomesteadEngine.SettleResult settle = advance(profile, familyId);
        String slot;
        Long target = null;
        if (gearId == null) {
            if (slotCode == null || !HomesteadConst.GEAR_SLOTS.contains(slotCode)) {
                throw new BizException(ResultCode.BAD_REQUEST, "没有这个部位");
            }
            slot = slotCode;
        } else {
            HomesteadGear row = requireGear(profile, gearId);
            EquipmentDef def = requireGearDef(row);
            requireSkillLevel(profile, def.skill(), def.requireLevel(), "穿这件装备");
            if (slotCode != null && !slotCode.isBlank()) {
                if (!HomesteadConst.GEAR_SLOTS.contains(slotCode)
                        || !familyOf(slotCode).equals(def.slot())) {
                    throw new BizException(ResultCode.BAD_REQUEST, "这件装备穿不到这个部位");
                }
                slot = slotCode;
            } else {
                slot = freeSlotOf(profile, def.slot());
            }
            target = row.getId();
        }
        if (allowOp(userId, opId)) {
            setWear(profile, userId, slot, target);
            playerMapper.updateProgress(profile.player);
        }
        return snapshot(profile, settle);
    }

    /** 分解实例:返还蓝本材料 × 品质系数;锁定或穿在身上的不能分解 */
    @Transactional
    public Map<String, Object> dismantleGear(Long userId, Long familyId, Long gearId, String opId) {
        Profile profile = load(userId, familyId);
        HomesteadEngine.SettleResult settle = advance(profile, familyId);
        HomesteadGear row = requireGear(profile, gearId);
        EquipmentDef def = requireGearDef(row);
        if (nzInt(row.getLocked()) > 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "这件装备锁着呢,先解锁再分解");
        }
        if (wornSlotOf(profile, row.getId()) != null) {
            throw new BizException(ResultCode.BAD_REQUEST, "先把这件装备脱下来再分解");
        }
        List<ActionDef.ItemAmount> output = HomesteadLoot.dismantle(content, def.code(), row.getQuality());
        if (allowOp(userId, opId)) {
            Map<String, Long> gained = new HashMap<>();
            for (ActionDef.ItemAmount amount : output) {
                gained.merge(amount.item(), amount.qty(), Long::sum);
            }
            applyItems(profile, gained, Map.of());
            gearMapper.deleteById(row.getId());
            profile.gear.remove(row);
            playerMapper.updateProgress(profile.player);
        }
        return snapshot(profile, settle);
    }

    /** 锁定 / 解锁(防误分解与误重铸) */
    @Transactional
    public Map<String, Object> lockGear(Long userId, Long familyId, Long gearId, boolean locked, String opId) {
        Profile profile = load(userId, familyId);
        HomesteadEngine.SettleResult settle = advance(profile, familyId);
        HomesteadGear row = requireGear(profile, gearId);
        if (allowOp(userId, opId)) {
            row.setLocked(locked ? 1 : 0);
            gearMapper.updateById(row);
        }
        return snapshot(profile, settle);
    }

    /** 重铸词缀:花铜板 + 稀有材质把词缀整组重掷(品质不变);没有词缀的打造装不用重铸 */
    @Transactional
    public Map<String, Object> rerollGear(Long userId, Long familyId, Long gearId, String opId) {
        Profile profile = load(userId, familyId);
        HomesteadEngine.SettleResult settle = advance(profile, familyId);
        HomesteadGear row = requireGear(profile, gearId);
        if (nzInt(row.getLocked()) > 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "这件装备锁着呢,先解锁再重铸");
        }
        RarityDef rarity = content.rarity(row.getQuality());
        if (rarity == null || rarity.affixes() <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "这件装备没有词缀,不用重铸");
        }
        Map<String, Long> need = checkCost(profile, HomesteadConst.REROLL_COINS,
                List.of(new ActionDef.ItemAmount(REROLL_ITEM, HomesteadConst.REROLL_RARE_QTY)));
        if (allowOp(userId, opId)) {
            applyItems(profile, Map.of(), need);
            profile.player.setCoins(clampCoins(nz(profile.player.getCoins()) - HomesteadConst.REROLL_COINS));
            int seq = nzInt(row.getRerollSeq());
            long base = HomesteadLoot.base(userId, "REROLL:" + row.getId(), seq);
            String affixes = HomesteadLoot.formatAffixes(HomesteadLoot.affixes(content, row.getQuality(), base));
            row.setAffixes(affixes == null ? "" : affixes);
            row.setRerollSeq(seq + 1);
            gearMapper.updateById(row);
            playerMapper.updateProgress(profile.player);
        }
        return snapshot(profile, settle);
    }

    /**
     * 一键最优穿戴:按「战力 + 追求项加成」给每个格子挑最好的实例(戒指两格取前二),
     * 穿不上的(技能等级不够)跳过;不强求换满,没有可用实例的格子保持原样。
     */
    @Transactional
    public Map<String, Object> autoWear(Long userId, Long familyId, String focus, String opId) {
        Profile profile = load(userId, familyId);
        HomesteadEngine.SettleResult settle = advance(profile, familyId);
        String want = focus == null || focus.isBlank() ? "SPEED" : focus.trim().toUpperCase(java.util.Locale.ROOT);
        if (!AUTO_FOCUS.contains(want)) {
            throw new BizException(ResultCode.BAD_REQUEST, "没有这种追求方向");
        }
        List<HomesteadGear> candidates = new ArrayList<>();
        for (HomesteadGear row : profile.gear) {
            EquipmentDef def = content.gear(row.getGearCode());
            if (def == null) {
                continue;
            }
            if (skillOf(profile, def.skill()).getLevel() < def.requireLevel()) {
                continue;
            }
            candidates.add(row);
        }
        Map<String, Long> chosen = new LinkedHashMap<>();
        Set<Long> used = new HashSet<>();
        for (String slot : HomesteadConst.GEAR_SLOTS) {
            String family = familyOf(slot);
            HomesteadGear best = null;
            long bestScore = Long.MIN_VALUE;
            for (HomesteadGear row : candidates) {
                EquipmentDef def = content.gear(row.getGearCode());
                if (def == null || !family.equals(def.slot()) || used.contains(row.getId())) {
                    continue;
                }
                long score = autoScore(row, def, want);
                if (score > bestScore) {
                    bestScore = score;
                    best = row;
                }
            }
            if (best != null) {
                used.add(best.getId());
                chosen.put(slot, best.getId());
            }
        }
        if (allowOp(userId, opId)) {
            for (String slot : HomesteadConst.GEAR_SLOTS) {
                Long gearId = chosen.get(slot);
                if (gearId != null) {
                    setWear(profile, userId, slot, gearId);
                }
            }
            playerMapper.updateProgress(profile.player);
        }
        return snapshot(profile, settle);
    }

    /** 保存预设:把当前穿戴快照写进指定预设(只存实例 id,实例分解后该格自动跳过) */
    @Transactional
    public Map<String, Object> saveLoadout(Long userId, Long familyId, int index, String name, String opId) {
        Profile profile = load(userId, familyId);
        HomesteadEngine.SettleResult settle = advance(profile, familyId);
        HomesteadLoadout row = requireLoadout(profile, index);
        String payload = payloadOf(profile);
        if (allowOp(userId, opId)) {
            row.setPayload(payload);
            row.setName(name == null ? "" : name.trim());
            loadoutMapper.updateById(row);
        }
        return snapshot(profile, settle);
    }

    /** 应用预设:按快照整套换上(快照里没有的格子脱下),实例已分解或等级不够则跳过 */
    @Transactional
    public Map<String, Object> applyLoadout(Long userId, Long familyId, int index, String opId) {
        Profile profile = load(userId, familyId);
        HomesteadEngine.SettleResult settle = advance(profile, familyId);
        HomesteadLoadout row = requireLoadout(profile, index);
        Map<String, Long> payload = parsePayload(row.getPayload());
        if (allowOp(userId, opId)) {
            for (String slot : HomesteadConst.GEAR_SLOTS) {
                Long target = payload.get(slot);
                if (target != null) {
                    HomesteadGear gear = gearById(profile, target);
                    EquipmentDef def = gear == null ? null : content.gear(gear.getGearCode());
                    if (def == null || !familyOf(slot).equals(def.slot())
                            || skillOf(profile, def.skill()).getLevel() < def.requireLevel()) {
                        target = null;
                    }
                }
                setWear(profile, userId, slot, target);
            }
            playerMapper.updateProgress(profile.player);
        }
        return snapshot(profile, settle);
    }

    /** 买第 3~5 套预设(前两套建号即有) */
    @Transactional
    public Map<String, Object> buyLoadout(Long userId, Long familyId, int index, String opId) {
        Profile profile = load(userId, familyId);
        HomesteadEngine.SettleResult settle = advance(profile, familyId);
        if (index < 1 || index > HomesteadConst.MAX_LOADOUTS) {
            throw new BizException(ResultCode.BAD_REQUEST, "没有这个预设位");
        }
        if (findLoadout(profile, index) != null) {
            throw new BizException(ResultCode.BAD_REQUEST, "这个预设位已经有了");
        }
        long price = HomesteadConst.LOADOUT_PRICES[index];
        if (price <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "这个预设位不用买");
        }
        if (nz(profile.player.getCoins()) < price) {
            throw new BizException(ResultCode.BAD_REQUEST, "铜板不够");
        }
        if (allowOp(userId, opId)) {
            profile.player.setCoins(clampCoins(nz(profile.player.getCoins()) - price));
            HomesteadLoadout fresh = new HomesteadLoadout();
            fresh.setUserId(userId);
            fresh.setSlotIndex(index);
            fresh.setName("");
            fresh.setPayload("");
            loadoutMapper.insert(fresh);
            profile.loadouts.add(fresh);
            playerMapper.updateProgress(profile.player);
        }
        return snapshot(profile, settle);
    }

    /**
     * 派遣探险:与挂机并行,但每天限 {@value HomesteadConst#EXPEDITION_PER_DAY} 趟、同时只允许一趟在途;
     * 前置条件 = 技能等级 + 战力下限 + 铜板费用 + 供给品,出发即扣费扣料,到点由 advance() 惰性结算。
     */
    @Transactional
    public Map<String, Object> startExpedition(Long userId, Long familyId, String regionCode, String opId) {
        Profile profile = load(userId, familyId);
        HomesteadEngine.SettleResult settle = advance(profile, familyId);
        ExpeditionDef.Region region = content.region(regionCode);
        if (region == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "没有这个探险区域");
        }
        LocalDate today = LocalDate.now(ZONE);
        int used = expeditionUsed(profile.player, today);
        if (used >= HomesteadConst.EXPEDITION_PER_DAY) {
            throw new BizException(ResultCode.BAD_REQUEST, "今天的探险次数用完了");
        }
        if (activeTrip(profile) != null) {
            throw new BizException(ResultCode.BAD_REQUEST, "已经有一趟在路上了,等它回来");
        }
        requireSkillLevel(profile, region.skill(), region.requireLevel(), "去" + region.name());
        int power = HomesteadBonus.powerOf(content, wornList(profile));
        if (power < region.powerFloor()) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "战力不够,至少 " + region.powerFloor() + "(现在 " + power + ")");
        }
        Map<String, Long> need = checkCost(profile, region.feeCoins(), region.supplies());
        if (allowOp(userId, opId)) {
            applyItems(profile, Map.of(), need);
            profile.player.setCoins(clampCoins(nz(profile.player.getCoins()) - region.feeCoins()));
            profile.player.setExpeditionDate(today);
            profile.player.setExpeditionUsed(used + 1);
            long startedMs = Instant.now().toEpochMilli();
            HomesteadExpedition trip = new HomesteadExpedition();
            trip.setUserId(userId);
            trip.setRegionCode(region.code());
            trip.setStatus(HomesteadConst.TRIP_ACTIVE);
            trip.setStartedAt(fromMillis(startedMs));
            trip.setEndsAt(fromMillis(startedMs + region.minutes() * 60_000L));
            expeditionMapper.insert(trip);
            profile.trips.add(0, trip);
            playerMapper.updateProgress(profile.player);
        }
        return snapshot(profile, settle);
    }

    /**
     * 积分兑换:宝箱按「材料掷点 + 装备掷点」开箱(装备从掉落专属蓝本里抽,品质有下限),
     * 直接换物品比开箱贵;每次兑换把 shop_seq 推一格,保证同样的点数不会连开同一个结果。
     */
    @Transactional
    public Map<String, Object> exchange(Long userId, Long familyId, String shopCode, String opId) {
        Profile profile = load(userId, familyId);
        HomesteadEngine.SettleResult settle = advance(profile, familyId);
        ExpeditionDef.Shop shop = content.shop(shopCode);
        if (shop == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "没有这个兑换项");
        }
        long points = nz(profile.player.getExplorePoints());
        if (points < shop.points()) {
            throw new BizException(ResultCode.BAD_REQUEST, "积分不够");
        }
        long seqBase = nz(profile.player.getShopSeq());
        if (allowOp(userId, opId)) {
            Map<String, Long> items = new LinkedHashMap<>();
            List<HomesteadLoot.Instance> drops = new ArrayList<>();
            openShop(shop, userId, seqBase, items, drops);
            profile.player.setExplorePoints(Math.max(0L,
                    Math.min(MAX_EXPLORE_POINTS, points - shop.points())));
            profile.player.setShopSeq(seqBase + 1);
            applyItems(profile, items, Map.of());
            for (HomesteadLoot.Instance inst : drops) {
                insertGear(profile, userId, inst);
            }
            playerMapper.updateProgress(profile.player);
            profile.lastExchange = Map.of("shop", shop.code(), "items", items, "gear", drops);
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
            Object raw = member.get("userId");
            if (raw instanceof Number number) {
                userIds.add(number.longValue());
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
        List<HomesteadLoadout> loadouts;
        /** 最近若干趟探险(含在途那一趟) */
        List<HomesteadExpedition> trips;
        /** 本次请求新结算完的探险(前端据此弹「探险手记」) */
        final List<HomesteadExpedition> settledTrips = new ArrayList<>();
        /** 本次请求兑换开出的东西(前端据此弹开箱结果) */
        Map<String, Object> lastExchange;
    }

    /** 锁行装载;首次访问建号并补齐技能/工具/12 格穿戴/场地/免费预设(内容扩表后老存档也能直接用) */
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
        profile.loadouts = new ArrayList<>(loadoutMapper.selectList(
                new LambdaQueryWrapper<HomesteadLoadout>().eq(HomesteadLoadout::getUserId, userId)));
        profile.trips = new ArrayList<>(expeditionMapper.selectList(
                new LambdaQueryWrapper<HomesteadExpedition>()
                        .eq(HomesteadExpedition::getUserId, userId)
                        .orderByDesc(HomesteadExpedition::getId)
                        .last("LIMIT 30")));
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
        Set<Integer> owned = new HashSet<>();
        profile.loadouts.forEach(l -> owned.add(nzInt(l.getSlotIndex())));
        for (int index = 1; index <= HomesteadConst.LOADOUT_FREE; index++) {
            if (owned.add(index)) {
                HomesteadLoadout fresh = new HomesteadLoadout();
                fresh.setUserId(userId);
                fresh.setSlotIndex(index);
                fresh.setName("");
                fresh.setPayload("");
                loadoutMapper.insert(fresh);
                profile.loadouts.add(fresh);
            }
        }
    }

    /**
     * 结算当前动作并把结果落库;空闲时只把锚点前移(空闲时长不入账)。返回本次结算摘要(可能为 null)。
     * 同一次结算里顺手收尾到点的探险(手记落库、奖励入账),两者共用一个事务与一次玩家行回写。
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
        settleExpeditions(profile, nowMs);
        // 过期 buff 顺手清掉(读取时不写库,只在真正的写路径删)
        purgeExpiredBuffs(profile, nowMs);
        profile.player.setFamilyId(familyId);
        profile.player.setContentVersion(content.contentVersion());
        profile.player.setLastSettleAt(nowAt);
        playerMapper.updateProgress(profile.player);
        return settle;
    }

    /**
     * 到点的探险就地结算:手记按「玩家 + 趟次」确定性算出(战力取结算时刻的穿戴),
     * 物品/装备/积分当场入账,状态置 DONE 后不再重算。已在 profile.trips 里按 id 倒序,取最新几趟。
     */
    private void settleExpeditions(Profile profile, long nowMs) {
        int settled = 0;
        for (HomesteadExpedition trip : profile.trips) {
            if (settled >= TRIP_SETTLE_LIMIT) {
                break;
            }
            if (!HomesteadConst.TRIP_ACTIVE.equals(trip.getStatus())
                    || toMillis(trip.getEndsAt()) > nowMs) {
                continue;
            }
            settled++;
            trip.setStatus(HomesteadConst.TRIP_DONE);
            ExpeditionDef.Region region = content.region(trip.getRegionCode());
            if (region == null) {
                // 区域被内容表下掉了:直接收尾,不发奖也不报错
                expeditionMapper.updateById(trip);
                continue;
            }
            com.ihomy.homestead.core.HomesteadExpedition.Report report =
                    com.ihomy.homestead.core.HomesteadExpedition.resolve(content, region,
                            nz(profile.player.getUserId()), trip.getId(), levelMap(profile.skills),
                            wornList(profile));
            applyItems(profile, report.items(), Map.of());
            for (HomesteadLoot.Instance inst : report.gear()) {
                insertGear(profile, profile.player.getUserId(), inst);
            }
            profile.player.setExplorePoints(clampPoints(
                    nz(profile.player.getExplorePoints()) + report.points()));
            trip.setReport(writeReport(report));
            expeditionMapper.updateById(trip);
            profile.settledTrips.add(trip);
        }
    }

    private String writeReport(com.ihomy.homestead.core.HomesteadExpedition.Report report) {
        try {
            return objectMapper.writeValueAsString(report);
        } catch (Exception e) {
            log.warn("[homestead] 探险手记序列化失败,region={}, err={}", report.region(), e.getMessage());
            return null;
        }
    }

    private Map<String, Object> readReport(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("[homestead] 探险手记解析失败,按无手记处理, err={}", e.getMessage());
            return null;
        }
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
        playerView.put("explorePoints", nz(player.getExplorePoints()));
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

        List<HomesteadBonus.WornGear> worn = wornList(profile);
        List<Map<String, Object>> gear = new ArrayList<>();
        for (HomesteadGear row : profile.gear) {
            if (content.gear(row.getGearCode()) == null) {
                continue;
            }
            gear.add(gearView(profile, row));
        }

        List<Map<String, Object>> wear = new ArrayList<>();
        for (String slot : HomesteadConst.GEAR_SLOTS) {
            HomesteadWear row = wearRow(profile, slot);
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("slot", slot);
            view.put("gearId", row == null ? null : row.getGearId());
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
        out.put("power", HomesteadBonus.powerOf(content, worn));
        out.put("sets", setViews(worn));
        out.put("loadouts", loadoutViews(profile));
        out.put("expedition", expeditionView(profile, nowMs));
        out.put("opened", profile.lastExchange);
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
        out.put("contentVersion", content.contentVersion());
        return out;
    }

    /** 一件实例的下发视图:蓝本 + 品质 + 词缀 + 强化 + 锁定 + 单体战力 + 穿在哪格 */
    private Map<String, Object> gearView(Profile profile, HomesteadGear row) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", row.getId());
        view.put("code", row.getGearCode());
        view.put("quality", row.getQuality() == null ? HomesteadConst.QUALITY_COMMON : row.getQuality());
        view.put("enhance", nzInt(row.getEnhance()));
        view.put("locked", nzInt(row.getLocked()) > 0);
        List<Map<String, Object>> affixes = new ArrayList<>();
        for (HomesteadBonus.Affix affix : HomesteadLoot.parseAffixes(row.getAffixes())) {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("kind", affix.kind());
            one.put("target", affix.target());
            one.put("bp", affix.bp());
            affixes.add(one);
        }
        view.put("affixes", affixes);
        view.put("power", HomesteadBonus.powerOf(content, List.of(worn(row))));
        view.put("wornSlot", wornSlotOf(profile, row.getId()));
        return view;
    }

    /** 套装进度:件数是当前穿戴的,active 标出已触发的档位 */
    private List<Map<String, Object>> setViews(List<HomesteadBonus.WornGear> worn) {
        Map<String, Integer> counts = HomesteadBonus.setCounts(content, worn);
        List<Map<String, Object>> out = new ArrayList<>();
        for (GearSetDef set : content.gearSets()) {
            int pieces = counts.getOrDefault(set.code(), 0);
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("code", set.code());
            view.put("pieces", pieces);
            List<Map<String, Object>> steps = new ArrayList<>();
            for (GearSetDef.SetStep step : set.steps()) {
                Map<String, Object> one = new LinkedHashMap<>();
                one.put("pieces", step.pieces());
                one.put("active", pieces >= step.pieces());
                one.put("effects", effectViews(step.effects()));
                steps.add(one);
            }
            view.put("steps", steps);
            out.add(view);
        }
        return out;
    }

    private List<Map<String, Object>> loadoutViews(Profile profile) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (int index = 1; index <= HomesteadConst.MAX_LOADOUTS; index++) {
            HomesteadLoadout row = findLoadout(profile, index);
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("index", index);
            view.put("owned", row != null);
            view.put("name", row == null || row.getName() == null ? "" : row.getName());
            view.put("price", HomesteadConst.LOADOUT_PRICES[index]);
            view.put("items", row == null ? Map.of() : parsePayload(row.getPayload()));
            out.add(view);
        }
        return out;
    }

    /** 探险:每日余次 + 在途一趟 + 最近手记 + 本次新结算的手记 */
    private Map<String, Object> expeditionView(Profile profile, long nowMs) {
        LocalDate today = LocalDate.now(ZONE);
        int used = expeditionUsed(profile.player, today);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("limit", HomesteadConst.EXPEDITION_PER_DAY);
        out.put("used", used);
        out.put("resetAt", today.plusDays(1).atStartOfDay(ZONE).toInstant().toEpochMilli());
        HomesteadExpedition active = activeTrip(profile);
        if (active == null) {
            out.put("active", null);
        } else {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("id", active.getId());
            view.put("region", active.getRegionCode());
            view.put("endsAt", toMillis(active.getEndsAt()));
            view.put("remainingMs", Math.max(0L, toMillis(active.getEndsAt()) - nowMs));
            out.put("active", view);
        }
        List<Map<String, Object>> history = new ArrayList<>();
        List<Map<String, Object>> fresh = new ArrayList<>();
        for (HomesteadExpedition trip : profile.trips) {
            if (!HomesteadConst.TRIP_DONE.equals(trip.getStatus())) {
                continue;
            }
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("id", trip.getId());
            view.put("region", trip.getRegionCode());
            view.put("endedAt", toMillis(trip.getEndsAt()));
            view.put("report", readReport(trip.getReport()));
            if (history.size() < TRIP_HISTORY) {
                history.add(view);
            }
            if (profile.settledTrips.contains(trip)) {
                fresh.add(view);
            }
        }
        out.put("history", history);
        out.put("fresh", fresh);
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
        Map<String, Integer> levels = new HashMap<>();
        for (HomesteadBuilding row : profile.buildings) {
            if (nzInt(row.getLevel()) > 0) {
                levels.put(row.getBuildingCode(), nzInt(row.getLevel()));
            }
        }
        int toolTier = action == null ? 0 : toolTier(profile, action);
        return HomesteadBonus.resolve(content, action, toolTier, wornList(profile), levels);
    }

    /** 穿戴实例 → 引擎口径(自动跳过已不存在的实例与内容表里被删的蓝本) */
    private List<HomesteadBonus.WornGear> wornList(Profile profile) {
        List<HomesteadBonus.WornGear> out = new ArrayList<>();
        for (HomesteadWear row : profile.wear) {
            if (row.getGearId() == null) {
                continue;
            }
            HomesteadGear gear = gearById(profile, row.getGearId());
            if (gear == null || content.gear(gear.getGearCode()) == null) {
                continue;
            }
            out.add(worn(gear));
        }
        return out;
    }

    private HomesteadBonus.WornGear worn(HomesteadGear row) {
        return new HomesteadBonus.WornGear(row.getGearCode(), row.getQuality(),
                nzInt(row.getEnhance()), HomesteadLoot.parseAffixes(row.getAffixes()));
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

    /** 当天已派遣次数:日期不是今天就当 0(读的时候不写库,真正派遣时才落日期) */
    private int expeditionUsed(HomesteadPlayer player, LocalDate today) {
        if (player == null || player.getExpeditionDate() == null || !today.equals(player.getExpeditionDate())) {
            return 0;
        }
        return player.getExpeditionUsed() == null ? 0 : Math.max(0, player.getExpeditionUsed());
    }

    private HomesteadExpedition activeTrip(Profile profile) {
        for (HomesteadExpedition trip : profile.trips) {
            if (HomesteadConst.TRIP_ACTIVE.equals(trip.getStatus())) {
                return trip;
            }
        }
        return null;
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

    /**
     * 内容表整体下发(单独端点):名称/价格/耗时/配方/区域/商店只有一份来源(后端 JSON)。
     * /state 只回版本号,前端版本不符时再拉一次,避免 60 秒轮询拖着 44 件蓝本与区域事件。
     */
    public Map<String, Object> contentView() {
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
            view.put("source", def.source());
            view.put("power", def.power());
            view.put("set", def.set());
            view.put("name", def.name());
            view.put("nameEn", def.nameEn());
            view.put("desc", def.desc());
            view.put("descEn", def.descEn());
            view.put("effects", effectViews(def.effects()));
            view.put("craft", stepView(def.craft()));
            view.put("dismantle", amountViews(def.dismantle()));
            List<Map<String, Object>> steps = new ArrayList<>();
            for (EquipmentDef.EquipmentStep step : def.steps()) {
                steps.add(stepView(step));
            }
            view.put("steps", steps);
            equipment.add(view);
        }
        out.put("equipment", equipment);

        List<Map<String, Object>> rarities = new ArrayList<>();
        for (RarityDef def : content.rarities()) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("code", def.code());
            view.put("name", def.name());
            view.put("nameEn", def.nameEn());
            view.put("multBp", def.multBp());
            view.put("affixes", def.affixes());
            view.put("minBp", def.minBp());
            view.put("maxBp", def.maxBp());
            rarities.add(view);
        }
        out.put("rarities", rarities);

        List<Map<String, Object>> affixes = new ArrayList<>();
        for (com.ihomy.homestead.content.AffixDef def : content.affixPool()) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("kind", def.kind());
            view.put("target", def.target());
            affixes.add(view);
        }
        out.put("affixes", affixes);

        List<Map<String, Object>> sets = new ArrayList<>();
        for (GearSetDef def : content.gearSets()) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("code", def.code());
            view.put("name", def.name());
            view.put("nameEn", def.nameEn());
            List<Map<String, Object>> steps = new ArrayList<>();
            for (GearSetDef.SetStep step : def.steps()) {
                Map<String, Object> one = new LinkedHashMap<>();
                one.put("pieces", step.pieces());
                one.put("effects", effectViews(step.effects()));
                steps.add(one);
            }
            view.put("steps", steps);
            sets.add(view);
        }
        out.put("sets", sets);

        List<Map<String, Object>> regions = new ArrayList<>();
        for (ExpeditionDef.Region def : content.regions()) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("code", def.code());
            view.put("name", def.name());
            view.put("nameEn", def.nameEn());
            view.put("skill", def.skill());
            view.put("requireLevel", def.requireLevel());
            view.put("powerFloor", def.powerFloor());
            view.put("feeCoins", def.feeCoins());
            view.put("supplies", amountViews(def.supplies()));
            view.put("minutes", def.minutes());
            view.put("gearQualityFloor", def.gearQualityFloor());
            view.put("gearPool", def.gearPool());
            List<Map<String, Object>> events = new ArrayList<>();
            for (ExpeditionDef.Event event : def.events()) {
                Map<String, Object> one = new LinkedHashMap<>();
                one.put("kind", event.kind());
                one.put("skill", event.skill());
                one.put("difficulty", event.difficulty());
                one.put("points", event.points());
                one.put("failPoints", event.failPoints());
                List<Map<String, Object>> loot = new ArrayList<>();
                for (ExpeditionDef.Loot one2 : event.loot()) {
                    loot.add(lootView(one2));
                }
                one.put("loot", loot);
                events.add(one);
            }
            view.put("events", events);
            regions.add(view);
        }
        out.put("regions", regions);

        List<Map<String, Object>> shops = new ArrayList<>();
        for (ExpeditionDef.Shop def : content.shops()) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("code", def.code());
            view.put("kind", def.kind());
            view.put("name", def.name());
            view.put("nameEn", def.nameEn());
            view.put("desc", def.desc());
            view.put("descEn", def.descEn());
            view.put("points", def.points());
            view.put("item", def.item());
            view.put("qty", def.qty());
            view.put("materials", def.materials());
            view.put("gearRolls", def.gearRolls());
            view.put("gearQualityFloor", def.gearQualityFloor());
            List<Map<String, Object>> pool = new ArrayList<>();
            for (ExpeditionDef.Loot one : def.pool()) {
                pool.add(lootView(one));
            }
            view.put("pool", pool);
            shops.add(view);
        }
        out.put("shops", shops);

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
        out.put("gearFamilies", new ArrayList<>(HomesteadConst.GEAR_FAMILIES));
        out.put("expeditionPerDay", HomesteadConst.EXPEDITION_PER_DAY);
        out.put("loadoutFree", HomesteadConst.LOADOUT_FREE);
        out.put("loadoutPrices", loadoutPrices());
        out.put("rerollCoins", HomesteadConst.REROLL_COINS);
        out.put("rerollItem", REROLL_ITEM);
        out.put("rerollQty", HomesteadConst.REROLL_RARE_QTY);
        return out;
    }

    private Map<String, Object> lootView(ExpeditionDef.Loot loot) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("item", loot.item());
        view.put("qty", loot.qty());
        view.put("weight", loot.weight());
        view.put("gear", loot.gear());
        view.put("qualityFloor", loot.qualityFloor());
        return view;
    }

    private List<Long> loadoutPrices() {
        List<Long> prices = new ArrayList<>();
        for (int index = 1; index <= HomesteadConst.MAX_LOADOUTS; index++) {
            prices.add(HomesteadConst.LOADOUT_PRICES[index]);
        }
        return prices;
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

    // ------------------------------------------------------------------ 装备与预设

    /** 按实例 id 取自己名下的装备;别人的 / 不存在的统一当「没有这件装备」 */
    private HomesteadGear requireGear(Profile profile, Long gearId) {
        HomesteadGear row = gearById(profile, gearId);
        if (row == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "没有这件装备");
        }
        return row;
    }

    private EquipmentDef requireGearDef(HomesteadGear row) {
        EquipmentDef def = content.gear(row.getGearCode());
        if (def == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "这件装备已经不在图鉴里了");
        }
        return def;
    }

    private HomesteadGear insertGear(Profile profile, Long userId, HomesteadLoot.Instance inst) {
        HomesteadGear fresh = new HomesteadGear();
        fresh.setUserId(userId);
        fresh.setGearCode(inst.code());
        fresh.setQuality(inst.quality() == null ? HomesteadConst.QUALITY_COMMON : inst.quality());
        fresh.setAffixes(inst.affixes() == null ? "" : inst.affixes());
        fresh.setEnhance(0);
        fresh.setLocked(0);
        fresh.setRerollSeq(0);
        gearMapper.insert(fresh);
        profile.gear.add(fresh);
        return fresh;
    }

    /** 写穿戴格:gearId 为空即脱下(必须用 set 显式写 NULL,MP 默认会跳过 null) */
    private void setWear(Profile profile, Long userId, String slot, Long gearId) {
        HomesteadWear row = wearRow(profile, slot);
        if (gearId != null) {
            // 同一件实例只能穿一格:先从别的格子脱下
            for (HomesteadWear other : profile.wear) {
                if (other != row && gearId.equals(other.getGearId())) {
                    other.setGearId(null);
                    wearMapper.update(null, new LambdaUpdateWrapper<HomesteadWear>()
                            .eq(HomesteadWear::getUserId, userId)
                            .eq(HomesteadWear::getSlotCode, other.getSlotCode())
                            .set(HomesteadWear::getGearId, null));
                }
            }
        }
        if (row == null) {
            HomesteadWear fresh = new HomesteadWear();
            fresh.setUserId(userId);
            fresh.setSlotCode(slot);
            fresh.setGearId(gearId);
            wearMapper.insert(fresh);
            profile.wear.add(fresh);
        } else {
            row.setGearId(gearId);
            wearMapper.update(null, new LambdaUpdateWrapper<HomesteadWear>()
                    .eq(HomesteadWear::getUserId, userId)
                    .eq(HomesteadWear::getSlotCode, slot)
                    .set(HomesteadWear::getGearId, gearId));
        }
    }

    private String familyOf(String slot) {
        String family = HomesteadConst.SLOT_FAMILY.get(slot);
        return family == null ? slot : family;
    }

    /** 没指定格子时:优先空着的那一格,都满就换掉同家族的第一格(戒指两格走同一条规则) */
    private String freeSlotOf(Profile profile, String family) {
        String fallback = null;
        for (String slot : HomesteadConst.GEAR_SLOTS) {
            if (!family.equals(familyOf(slot))) {
                continue;
            }
            HomesteadWear row = wearRow(profile, slot);
            if (row != null && row.getGearId() == null) {
                return slot;
            }
            if (fallback == null) {
                fallback = slot;
            }
        }
        return fallback == null ? HomesteadConst.GEAR_SLOTS.get(0) : fallback;
    }

    private String wornSlotOf(Profile profile, Long gearId) {
        if (gearId == null) {
            return null;
        }
        for (HomesteadWear row : profile.wear) {
            if (gearId.equals(row.getGearId())) {
                return row.getSlotCode();
            }
        }
        return null;
    }

    /** 一键最优的打分:战力为主,追求项(速度/经验/稀有/多收)按 3 倍权重 */
    private long autoScore(HomesteadGear row, EquipmentDef def, String focus) {
        long score = (long) HomesteadBonus.powerOf(content, List.of(worn(row))) * 10L;
        for (EquipmentDef.EffectDef effect : def.effects()) {
            if (focus.equals(effect.kind())) {
                score += Math.max(0, effect.bp()) * 3L;
            }
        }
        for (HomesteadBonus.Affix affix : HomesteadLoot.parseAffixes(row.getAffixes())) {
            if (focus.equals(affix.kind())) {
                score += Math.max(0, affix.bp()) * 3L;
            }
        }
        return score;
    }

    /** 当前穿戴 → 预设快照串(HEAD:12,RING_A:15;空着的格子不写) */
    private String payloadOf(Profile profile) {
        StringBuilder sb = new StringBuilder();
        for (String slot : HomesteadConst.GEAR_SLOTS) {
            HomesteadWear row = wearRow(profile, slot);
            if (row == null || row.getGearId() == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(slot).append(':').append(row.getGearId());
        }
        return sb.toString();
    }

    private Map<String, Long> parsePayload(String raw) {
        Map<String, Long> out = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String part : raw.split(",")) {
            int at = part.indexOf(':');
            if (at <= 0) {
                continue;
            }
            String slot = part.substring(0, at).trim();
            if (!HomesteadConst.GEAR_SLOTS.contains(slot)) {
                continue;
            }
            try {
                out.put(slot, Long.parseLong(part.substring(at + 1).trim()));
            } catch (NumberFormatException ignored) {
                // 坏数据跳过
            }
        }
        return out;
    }

    private HomesteadLoadout findLoadout(Profile profile, int index) {
        for (HomesteadLoadout row : profile.loadouts) {
            if (nzInt(row.getSlotIndex()) == index) {
                return row;
            }
        }
        return null;
    }

    private HomesteadLoadout requireLoadout(Profile profile, int index) {
        HomesteadLoadout row = findLoadout(profile, index);
        if (row == null) {
            throw new BizException(ResultCode.BAD_REQUEST, index <= HomesteadConst.LOADOUT_FREE
                    ? "没有这个预设位" : "这个预设位还没解锁");
        }
        return row;
    }

    /** 开箱 / 换物:宝箱按材料次数抽物品、按装备次数抽掉落专属蓝本(品质有下限) */
    private void openShop(ExpeditionDef.Shop shop, Long userId, long seqBase,
                          Map<String, Long> items, List<HomesteadLoot.Instance> drops) {
        if (!shop.chest()) {
            if (shop.item() != null) {
                items.merge(shop.item(), (long) shop.qty(), Long::sum);
            }
            return;
        }
        String salt = "SHOP:" + shop.code();
        for (int i = 0; i < shop.materials(); i++) {
            long seq = seqBase * 64 + i;
            ExpeditionDef.Loot loot = com.ihomy.homestead.core.HomesteadExpedition.pick(
                    shop.pool(), HomesteadLoot.base(userId, salt + ":item", seq));
            if (loot == null || loot.gear() || loot.item() == null) {
                continue;
            }
            items.merge(loot.item(), (long) loot.qty(), Long::sum);
        }
        List<EquipmentDef> pool = content.dropGear();
        if (pool.isEmpty()) {
            return;
        }
        for (int i = 0; i < shop.gearRolls(); i++) {
            long seq = seqBase * 64 + shop.materials() + i;
            long roll = HomesteadLoot.base(userId, salt + ":gear", seq);
            EquipmentDef pick = pool.get((int) Long.remainderUnsigned(roll >>> 23, pool.size()));
            HomesteadLoot.Instance inst = HomesteadLoot.roll(content, pick.code(), userId,
                    salt + ":inst", seq, shop.gearQualityFloor());
            if (inst != null) {
                drops.add(inst);
            }
        }
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

    private HomesteadGear gearById(Profile profile, Long gearId) {
        if (gearId == null) {
            return null;
        }
        for (HomesteadGear row : profile.gear) {
            if (gearId.equals(row.getId())) {
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

    private static long clampPoints(long points) {
        return Math.max(0L, Math.min(MAX_EXPLORE_POINTS, points));
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
