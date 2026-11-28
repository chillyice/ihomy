package com.ihomy.garden.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.garden.GardenConst;
import com.ihomy.garden.content.CropDef;
import com.ihomy.garden.content.GardenContent;
import com.ihomy.garden.content.VenueDef;
import com.ihomy.garden.core.GardenGrowth;
import com.ihomy.garden.core.GardenSeason;
import com.ihomy.garden.core.GardenSolarEvents;
import com.ihomy.garden.core.GardenWeatherEvents;
import com.ihomy.garden.core.Ulid;
import com.ihomy.garden.dto.BatchCareRequest;
import com.ihomy.garden.dto.BatchSowRequest;
import com.ihomy.garden.dto.PlotActionVO;
import com.ihomy.garden.dto.PlotVO;
import com.ihomy.garden.dto.SowRequest;
import com.ihomy.garden.entity.GardenLog;
import com.ihomy.garden.entity.GardenMember;
import com.ihomy.garden.entity.GardenPlot;
import com.ihomy.garden.entity.GardenWallet;
import com.ihomy.garden.entity.GardenWorld;
import com.ihomy.garden.mapper.GardenCodexMapper;
import com.ihomy.garden.mapper.GardenInventoryMapper;
import com.ihomy.garden.mapper.GardenLogMapper;
import com.ihomy.garden.mapper.GardenMemberMapper;
import com.ihomy.garden.mapper.GardenPlotMapper;
import com.ihomy.garden.mapper.GardenStatMapper;
import com.ihomy.garden.mapper.GardenWalletMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 地块写动作:备盆 / 播种 / 浇水 / 晒太阳 / 施肥 / 收获(FR-04/04a/07/08/12)。
 * 统一纪律(架构分册 §17.1):① 归属是统一前置(共享放行、本人放行、其余 403);
 * ② 所有扣减与状态变更都是条件 UPDATE + 0 行即失败,不用「先查后写」;
 * ③ 收获与入仓同事务;④ 全部写动作带 op_id(Idempotency-Key)兜底重放。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GardenPlotService {

    private final GardenWorldService worldService;
    private final GardenContent content;
    private final GardenGrowthService growth;
    private final GardenPlotMapper plotMapper;
    private final GardenInventoryMapper inventoryMapper;
    private final GardenWalletMapper walletMapper;
    private final GardenStatMapper statMapper;
    private final GardenCodexMapper codexMapper;
    private final GardenLogMapper logMapper;
    private final GardenMemberMapper memberMapper;
    private final GardenPlotAssembler assembler;

    /** 播种(FR-04):校归属 → 场域已解锁 → 作物阶段已解锁 → 占坑(条件 UPDATE)→ 扣 1 包种子(包优先、无包扣园币) */
    @Transactional
    public PlotActionVO sow(Long familyId, Long userId, SowRequest req, String opIdIn) {
        GardenMember me = worldService.requireMember(familyId, userId);
        // 顺带引导世界:世界/成员/地块缺行都补齐(按世界阶段;测试夹具 settings.stage 可为后院等共享场域补行)
        GardenWorld world = worldService.ensureWorld(familyId);
        int stage = worldService.stageOf(world);
        String opId = Ulid.orNext(opIdIn);
        if (req == null || req.getVenue() == null || req.getSlotNo() == null || req.getCropCode() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "要种哪块地、种什么,得说清楚");
        }
        CropDef crop = content.crop(req.getCropCode());
        if (crop == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "还不认识这种作物");
        }
        if (crop.unlockStage() > stage) {
            throw new BizException(ResultCode.FORBIDDEN, "这一阶还没解锁,先看看当期的任务");
        }
        VenueDef venue = content.venue(req.getVenue());
        if (venue == null) {
            throw new BizException(ResultCode.NOT_FOUND, "没有这个场域");
        }
        VenueDef.SlotDef slot = venue.slots().stream()
                .filter(s -> s.slotNo() == req.getSlotNo()).findFirst().orElse(null);
        if (slot == null) {
            throw new BizException(ResultCode.NOT_FOUND, "这一格不存在");
        }
        if (slot.unlockStage() > stage) {
            throw new BizException(ResultCode.NOT_FOUND, "这一格还没解锁");
        }
        String ownerUid = GardenConst.isPersonalVenue(venue.code()) ? me.getPlayerUid() : GardenConst.OWNER_SHARED;
        GardenPlot plot = plotMapper.selectOne(new LambdaQueryWrapper<GardenPlot>()
                .eq(GardenPlot::getFamilyId, familyId)
                .eq(GardenPlot::getOwnerUid, ownerUid)
                .eq(GardenPlot::getVenue, venue.code())
                .eq(GardenPlot::getSlotNo, req.getSlotNo()));
        if (plot == null) {
            throw new BizException(ResultCode.NOT_FOUND, "这块地还没开垦出来");
        }
        requireOperable(plot, familyId, me);
        // 断线重试:同 Idempotency-Key 已落账 → 回执当前状态,不重复占坑/扣费(契约 §0)
        GardenLog prevOp = seenOp(familyId, opId);
        if (prevOp != null) {
            return replay(familyId, plot.getId(), me, prevOp, "PLANT");
        }

        LocalDateTime now = LocalDateTime.now();
        // 占坑:仅空地可种(0 行 = 已有植物,FR-04 ①)
        if (plotMapper.sow(plot.getId(), familyId, crop.code(), now) == 0) {
            throw new BizException(ResultCode.CONFLICT, "这块地已经有植物在长了");
        }
        // 付费:有包用包(免费),没包按种子价扣园币(§11:种子不入仓,播种直接扣 1 包种子价)
        final String seedUsed;
        final int coinsSpent;
        if (inventoryMapper.deduct(familyId, GardenConst.seedItemCode(crop.code()), 1) > 0) {
            inventoryMapper.deleteIfZero(familyId, GardenConst.seedItemCode(crop.code()));
            seedUsed = "PACK";
            coinsSpent = 0;
        } else {
            seedUsed = "COINS";
            coinsSpent = crop.seedPrice();
            chargeCoins(familyId, me.getPlayerUid(), coinsSpent);
        }
        LocalDate today = now.toLocalDate();
        statMapper.upsertDeltas(familyId, today, 0, 1, 0, 0, coinsSpent, 0);
        memberMapper.touchActive(familyId, me.getPlayerUid(), today);
        insertLog(familyId, me.getPlayerUid(), plot.getId(), opId, "PLANT", crop.code(), trim(req.getMessage()),
                "种下了" + crop.name() + (seedUsed.equals("PACK") ? "(用了 1 包种子)" : "(买了 1 包种子)"));
        log.info("[garden] sow familyId={} plot={} crop={} seedUsed={} opId={}",
                familyId, plot.getId(), crop.code(), seedUsed, opId);
        return actionVO(familyId, plot.getId(), me, vo -> {
            vo.setSeedUsed(seedUsed);
            vo.setCoinsSpent(coinsSpent);
        });
    }

    /** 备盆(FR-04a):一次性 5 园币,首盆免单;重复调用幂等不重复扣费 */
    @Transactional
    public PlotActionVO prepare(Long familyId, Long userId, Long plotId, String opIdIn) {
        GardenMember me = worldService.requireMember(familyId, userId);
        String opId = Ulid.orNext(opIdIn);
        // 断线重试:同 Idempotency-Key 已落账 → 回执当前状态,不重复扣费(契约 §0)
        GardenLog prevOp = seenOp(familyId, opId);
        if (prevOp != null) {
            return replay(familyId, plotId, me, prevOp, "PREPARE");
        }
        GardenPlot plot = requirePlot(familyId, plotId);
        requireOperable(plot, familyId, me);
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();

        if (plot.getPrepared() != null && plot.getPrepared() == 1) {
            // 幂等:已备盆直接成功,不重复扣费
            return actionVO(familyId, plot.getId(), me, vo -> vo.setCoinsSpent(0));
        }
        int cost = growth.prepareCost(familyId);   // 轻锄:5 → 4 → 3 园币(详细设计 §6.4)
        boolean freeFirst = plotMapper.countPrepared(familyId) == 0;
        if (!freeFirst) {
            chargeCoins(familyId, me.getPlayerUid(), cost);
        }
        if (plotMapper.prepare(plot.getId(), familyId) == 0) {
            // 并发:他人刚备好;若本事务扣了钱,抛异常整体回滚
            throw new BizException(ResultCode.CONFLICT, "这只盆刚备好了");
        }
        if (!freeFirst) {
            statMapper.upsertDeltas(familyId, today, 0, 0, 0, 0, cost, 0);
        }
        memberMapper.touchActive(familyId, me.getPlayerUid(), today);
        insertLog(familyId, me.getPlayerUid(), plot.getId(), opId, "PREPARE", null, null,
                freeFirst ? "备好了第一只盆(免单)" : "备好了盆(花了 " + cost + " 园币)");
        PlotActionVO vo = actionVO(familyId, plot.getId(), me, null);
        vo.setCoinsSpent(freeFirst ? 0 : cost);
        return vo;
    }

    /** 浇水(FR-07):12h 地块冷却(试水 11h/10h),+120 分钟加成;0 行 = 冷却中(带剩余秒数) */
    @Transactional
    public PlotActionVO water(Long familyId, Long userId, Long plotId, String opIdIn) {
        return care(familyId, userId, plotId, "WATER", GardenConst.WATER_BOOST_MINUTES, 0, opIdIn, "浇了水");
    }

    /** 晒太阳(FR-07) */
    @Transactional
    public PlotActionVO sun(Long familyId, Long userId, Long plotId, String opIdIn) {
        return care(familyId, userId, plotId, "SUN", GardenConst.SUN_BOOST_MINUTES, 0, opIdIn, "晒了太阳");
    }

    /** 施肥(FR-08):15 园币,12h 冷却,+360 分钟加成;扣款与写状态同事务 */
    @Transactional
    public PlotActionVO fertilize(Long familyId, Long userId, Long plotId, String opIdIn) {
        return care(familyId, userId, plotId, "FERTILIZE", GardenConst.FERTILIZE_BOOST_MINUTES,
                GardenConst.FERTILIZE_COST, opIdIn, "施了肥");
    }

    /** 收获(FR-12/14/15/18):锁定地块 + 入仓 + 图鉴 + 入账,同一事务 */
    @Transactional
    public PlotActionVO harvest(Long familyId, Long userId, Long plotId, String opIdIn) {
        GardenMember me = worldService.requireMember(familyId, userId);
        String opId = Ulid.orNext(opIdIn);
        // 断线重试:同 Idempotency-Key 已落账 → 回执当前状态(空盆照回),不重复入仓/入账/记图鉴(契约 §3)
        GardenLog prevOp = seenOp(familyId, opId);
        if (prevOp != null) {
            PlotActionVO vo = replay(familyId, plotId, me, prevOp, "HARVEST");
            vo.setItemCode(prevOp.getCropCode());
            return vo;
        }
        GardenPlot plot = requirePlot(familyId, plotId);
        requireOperable(plot, familyId, me);
        if (plot.getCropCode() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "这块地还没种东西");
        }
        CropDef crop = content.crop(plot.getCropCode());
        if (crop == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "这种作物已下架,联系运维处理");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        String season = GardenSeason.seasonOf(today);
        VenueDef venue = content.venue(plot.getVenue());
        GardenSolarEvents.Schedule events = this.growth.scheduleOf(familyId);
        GardenWeatherEvents.Event weatherEvent = this.growth.weatherEventOf(familyId, plot.getVenue(),
                venue != null && venue.indoor(), plot.getLastWateredAt(), plot.getPlantedAt(), now);
        GardenGrowth.GrowthView growth = GardenGrowth.evaluate(
                crop.durationHours(), crop.soilPreference(), crop.soilCompatible(), crop.hardiness(),
                crop.seasons(), crop.seasonMultBp(), crop.offSeasonMultBp(), season, plot.getVenue(),
                venue != null && venue.indoor(),
                plot.getPrepared() != null && plot.getPrepared() == 1, plot.getSoilType(),
                plot.getPlantedAt(), plot.getLastWateredAt(), plot.getCareBoostMinutes(), now, events, weatherEvent,
                // 气温胁迫(§8.1):与全景同口径,否则面板显示「可收」而收获报「还没熟」
                this.growth.tempStressBpOf(familyId, crop.code(),
                        (venue != null && venue.indoor()) || "GREENHOUSE".equals(plot.getVenue())));
        if (!growth.harvestable()) {
            long remainMin = growth.minutesToHarvest();
            throw new BizException(ResultCode.CONFLICT,
                    "还没成熟,还要长 " + humanDuration(remainMin));
        }
        int yieldQty = GardenGrowth.yieldAmount(crop.yieldMin(), crop.yieldMax(), growth.soilBp());
        // 满仓禁收(FR-13:容量现算,含仓廪技能加成)
        int used = inventoryMapper.sumQty(familyId);
        int cap = GardenConst.WAREHOUSE_BASE + GardenConst.WAREHOUSE_PER_PLOT * plotMapper.countAll(familyId)
                + this.growth.warehouseBonus(familyId);
        if (used + yieldQty > cap) {
            throw new BizException(ResultCode.CONFLICT, "仓库满了,先处理一些收成再摘");
        }
        // 锁定地块(0 行 = 并发重复收获)
        if (plotMapper.harvest(plot.getId(), familyId, crop.code()) == 0) {
            throw new BizException(ResultCode.CONFLICT, "这块地刚被收获过了");
        }
        inventoryMapper.upsertAdd(familyId, crop.code(), GardenConst.ITEM_TYPE_FRUIT, yieldQty);
        int codexRows = codexMapper.upsertHarvest(familyId, crop.code(), now);
        boolean firstCodex = codexRows == 1;
        // 收获入账:错季 ×0.5 后再乘节气卖价事件(立秋/霜降 ×1.1、秋分 ×1.2,四舍五入一次)
        int baseCoins = GardenGrowth.sellPrice(crop.fruitPrice(), growth.seasonBp() == crop.seasonMultBp()) * yieldQty;
        int priceBp = events.priceBp(today);
        int coinsGranted = (baseCoins * priceBp + 50) / 100;
        if (coinsGranted > 0) {
            walletMapper.insertIgnore(familyId, me.getPlayerUid());
            walletMapper.add(familyId, me.getPlayerUid(), coinsGranted);
        }
        int xpGain = GardenConst.harvestStageXp(crop.unlockStage()) + (firstCodex ? GardenConst.XP_FIRST_CROP : 0);
        statMapper.upsertDeltas(familyId, today, 0, 0, 1, coinsGranted, 0, xpGain);
        memberMapper.touchActive(familyId, me.getPlayerUid(), today);
        insertLog(familyId, me.getPlayerUid(), plot.getId(), opId, "HARVEST", crop.code(), null,
                "收获了" + crop.name() + " ×" + yieldQty + (firstCodex ? ",记进图鉴了" : ""));
        log.info("[garden] harvest familyId={} plot={} crop={} yield={} coins={} firstCodex={} opId={}",
                familyId, plot.getId(), crop.code(), yieldQty, coinsGranted, firstCodex, opId);
        return actionVO(familyId, plot.getId(), me, vo -> {
            vo.setItemCode(crop.code());
            vo.setYieldQty(yieldQty);
            vo.setCoinsGranted(coinsGranted);
            vo.setFirstCodex(firstCodex);
        });
    }

    /** 批量上限(约束单事务规模;36 块地上限远用不满) */
    public static final int BATCH_MAX = 40;

    /**
     * 批量播种(一畦齐播,§4.2.4):一次确认播同种,逐盆校验归属/占用/解锁,分别扣费、分别记流水(§4.2.3)。
     * 子动作确定性 op_id = synth('B', batch-sow, family, 批次键, plotId)——同键重放逐盆 alreadyDone,不重复扣费。
     * 个人场域只认本人空盆(共享放行),防止把它翻译成自己的同号盆;逐盆失败不互相拖累(自调用不过代理,异常被接住不污染事务)。
     */
    @Transactional
    public Map<String, Object> batchSow(Long familyId, Long userId, BatchSowRequest req, String opIdIn) {
        GardenMember me = worldService.requireMember(familyId, userId);
        if (growth.levels(familyId).getOrDefault("YIQI", 0) < 1) {
            throw new BizException(ResultCode.FORBIDDEN, "还没学会「一畦齐播」,先去技能树点它");
        }
        if (req == null || req.getCropCode() == null || req.getCropCode().isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "要种什么,得说清楚");
        }
        List<Long> ids = normalizeBatchIds(req.getPlotIds());
        if (ids.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "要种哪几块,得说清楚");
        }
        String batchKey = Ulid.orNext(opIdIn);
        Map<String, String> nicknames = worldService.nicknames(familyId);
        List<Map<String, Object>> results = new ArrayList<>();
        int succeeded = 0;
        int coinsSpent = 0;
        for (Long plotId : ids) {
            try {
                GardenPlot plot = requirePlot(familyId, plotId);
                if (GardenConst.isPersonalVenue(plot.getVenue()) && !plot.getOwnerUid().equals(me.getPlayerUid())) {
                    throw new BizException(ResultCode.FORBIDDEN, "这是 "
                            + nicknames.getOrDefault(plot.getOwnerUid(), "家人") + " 的房间");
                }
                SowRequest single = new SowRequest();
                single.setVenue(plot.getVenue());
                single.setSlotNo(plot.getSlotNo());
                single.setCropCode(req.getCropCode());
                single.setMessage(trim(req.getMessage()));
                PlotActionVO vo = sow(familyId, userId, single,
                        GardenConst.synthOpId('B', "batch-sow", familyId, batchKey, plotId));
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("plotId", plotId);
                r.put("venue", plot.getVenue());
                r.put("slotNo", plot.getSlotNo());
                r.put("code", 0);
                r.put("seedUsed", vo.getSeedUsed());
                r.put("coinsSpent", vo.getCoinsSpent());
                r.put("alreadyDone", Boolean.TRUE.equals(vo.getAlreadyDone()));
                results.add(r);
                succeeded++;
                coinsSpent += vo.getCoinsSpent() == null ? 0 : vo.getCoinsSpent();
            } catch (BizException e) {
                results.add(perPlotFailure(plotId, e));
            }
        }
        return batchOut(req.getCropCode(), ids.size(), succeeded, coinsSpent, 0, results, familyId, me);
    }

    /** 批量照料(巡园,§4.2.4):同一动作(WATER/SUN/FERTILIZE)一次确认,逐盆校验冷却与费用;缸内补水随生态缸端点开放 */
    @Transactional
    public Map<String, Object> batchCare(Long familyId, Long userId, BatchCareRequest req, String opIdIn) {
        GardenMember me = worldService.requireMember(familyId, userId);
        if (growth.levels(familyId).getOrDefault("XUNYUAN", 0) < 1) {
            throw new BizException(ResultCode.FORBIDDEN, "还没学会「巡园」,先去技能树点它");
        }
        List<Long> ids = normalizeBatchIds(req == null ? null : req.getPlotIds());
        String action = req == null ? null : req.getAction();
        boolean known = "WATER".equals(action) || "SUN".equals(action) || "FERTILIZE".equals(action);
        if (ids.isEmpty() || !known) {
            throw new BizException(ResultCode.BAD_REQUEST, "照料哪几块、做什么(浇水/晒太阳/施肥),得说清楚");
        }
        int boost = "WATER".equals(action) ? GardenConst.WATER_BOOST_MINUTES
                : "SUN".equals(action) ? GardenConst.SUN_BOOST_MINUTES : GardenConst.FERTILIZE_BOOST_MINUTES;
        int cost = "FERTILIZE".equals(action) ? GardenConst.FERTILIZE_COST : 0;
        String detail = "WATER".equals(action) ? "浇了水" : "SUN".equals(action) ? "晒了太阳" : "施了肥";
        String batchKey = Ulid.orNext(opIdIn);
        List<Map<String, Object>> results = new ArrayList<>();
        int succeeded = 0;
        int coinsSpent = 0;
        int coinsEarned = 0;
        for (Long plotId : ids) {
            try {
                PlotActionVO vo = care(familyId, userId, plotId, action, boost, cost,
                        GardenConst.synthOpId('B', "batch-care", familyId, batchKey, action, plotId), detail);
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("plotId", plotId);
                r.put("code", 0);
                r.put("careBoostMinutes", vo.getPlot() == null ? null : vo.getPlot().getCareBoostMinutes());
                r.put("coinsEarned", vo.getCoinsEarned() == null ? 0 : vo.getCoinsEarned());
                r.put("alreadyDone", Boolean.TRUE.equals(vo.getAlreadyDone()));
                results.add(r);
                succeeded++;
                coinsSpent += vo.getCoinsSpent() == null ? 0 : vo.getCoinsSpent();
                coinsEarned += vo.getCoinsEarned() == null ? 0 : vo.getCoinsEarned();
            } catch (BizException e) {
                results.add(perPlotFailure(plotId, e));
            }
        }
        return batchOut(action, ids.size(), succeeded, coinsSpent, coinsEarned, results, familyId, me);
    }

    private List<Long> normalizeBatchIds(List<Long> plotIds) {
        if (plotIds != null && plotIds.size() > BATCH_MAX) {
            throw new BizException(ResultCode.BAD_REQUEST, "一次最多 " + BATCH_MAX + " 块");
        }
        return plotIds == null ? List.of()
                : plotIds.stream().filter(Objects::nonNull).distinct().toList();
    }

    private Map<String, Object> perPlotFailure(Long plotId, BizException e) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("plotId", plotId);
        r.put("code", e.getCode());
        r.put("message", e.getMessage());
        return r;
    }

    private Map<String, Object> batchOut(String subject, int total, int succeeded, int coinsSpent, int coinsEarned,
                                         List<Map<String, Object>> results, Long familyId, GardenMember me) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("subject", subject);
        out.put("total", total);
        out.put("succeeded", succeeded);
        out.put("failed", total - succeeded);
        out.put("coinsSpent", coinsSpent);
        out.put("coinsEarned", coinsEarned);
        out.put("results", results);
        GardenWallet wallet = walletMapper.selectOne(new LambdaQueryWrapper<GardenWallet>()
                .eq(GardenWallet::getFamilyId, familyId)
                .eq(GardenWallet::getPlayerUid, me.getPlayerUid()));
        out.put("walletCoins", wallet == null ? 0L : wallet.getCoins());
        return out;
    }

    // ---------- 内部 ----------

    private PlotActionVO care(Long familyId, Long userId, Long plotId, String action, int boost, int cost,
                              String opIdIn, String detail) {
        GardenMember me = worldService.requireMember(familyId, userId);
        String opId = Ulid.orNext(opIdIn);
        // 断线重试:同 Idempotency-Key 已落账 → 回执当前状态(冷却秒数照实),不重复加成/扣费(契约 §3)
        GardenLog prevOp = seenOp(familyId, opId);
        if (prevOp != null) {
            return replay(familyId, plotId, me, prevOp, action);
        }
        GardenPlot plot = requirePlot(familyId, plotId);
        requireOperable(plot, familyId, me);
        if (plot.getCropCode() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "这块地还没种东西");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDate today0 = now.toLocalDate();
        // 冷却边界:试水只缩浇水的地块冷却(11h/10h),晒太阳/施肥沿用 12h(详细设计 §6.4)
        int cooldownMinutes = "WATER".equals(action)
                ? growth.waterCooldownMinutes(familyId)
                : GardenConst.CARE_COOLDOWN_MINUTES;
        LocalDateTime boundary = now.minusMinutes(cooldownMinutes);
        // 照料倍率(玩法稿 §8 表):晒太阳 = 天气 × 季节(「当日阳光量」);浇水 = 天气;施肥不乘。
        // 无天气数据一律回退 100 基线,不阻断游戏。
        int appliedBoost = boost;
        if (!"FERTILIZE".equals(action)) {
            Map<String, Object> weather = growth.weatherOf(familyId);
            String condition = weather == null ? null : (String) weather.get("condition");
            int weatherBp = condition == null ? 100
                    : ("SUN".equals(action)
                        ? GardenConst.WEATHER_SUN_BP.getOrDefault(condition, 100)
                        : GardenConst.WEATHER_WATER_BP.getOrDefault(condition, 100));
            appliedBoost = (boost * weatherBp + 50) / 100;
            if ("SUN".equals(action)) {
                int seasonBp = GardenConst.SUN_SEASON_BP.getOrDefault(GardenSeason.seasonOf(today0), 100);
                appliedBoost = (appliedBoost * seasonBp + 50) / 100;
            }
        }
        int rows = switch (action) {
            case "WATER" -> plotMapper.water(plot.getId(), familyId, now, boundary, appliedBoost);
            case "SUN" -> plotMapper.sun(plot.getId(), familyId, now, boundary, appliedBoost);
            default -> plotMapper.fertilize(plot.getId(), familyId, now, boundary, appliedBoost);
        };
        if (rows == 0) {
            GardenPlot fresh = plotMapper.selectById(plot.getId());
            LocalDateTime last = switch (action) {
                case "WATER" -> fresh.getLastWateredAt();
                case "SUN" -> fresh.getLastSunAt();
                default -> fresh.getLastFertilizedAt();
            };
            long remainSec = GardenPlotAssembler.remainingSeconds(last, now, cooldownMinutes);
            throw new BizException(ResultCode.CONFLICT,
                    switch (action) {
                        case "WATER" -> "浇水冷却中,还需 " + remainSec + " 秒";
                        case "SUN" -> "晒太阳冷却中,还需 " + remainSec + " 秒";
                        default -> "施肥冷却中,还需 " + remainSec + " 秒";
                    });
        }
        LocalDate today = now.toLocalDate();
        // 照料 XP / 园币:每地块每日只计第一次(写时判定,§15.2 判定禁读日志;园币口径见契约 §13)
        int firstCareToday = plotMapper.grantCareXpDay(plot.getId(), familyId, today);
        int xpGain = firstCareToday > 0 ? GardenConst.XP_PER_CARE_DAY : 0;
        int coinsEarned = firstCareToday > 0 ? GardenConst.CARE_COINS_PER_DAY : 0;
        if (cost > 0) {
            chargeCoins(familyId, me.getPlayerUid(), cost);
            statMapper.upsertDeltas(familyId, today, 1, 0, 0, coinsEarned, cost, xpGain);
        } else {
            statMapper.upsertDeltas(familyId, today, 1, 0, 0, coinsEarned, 0, xpGain);
        }
        if (coinsEarned > 0) {
            // 园币进操作者个人钱包(与卖出一致),同时写 stat.coin_in——该列口径是
            // 「累计入账园币(含首封礼金/收获/任务奖励)」,SELL_COINS 类任务文案写的是
            // 「攒下 X 园币」(任务链详设 §4.2),照料这份小额入账同走 stat 才与口径一致
            walletMapper.insertIgnore(familyId, me.getPlayerUid());
            walletMapper.add(familyId, me.getPlayerUid(), coinsEarned);
        }
        memberMapper.touchActive(familyId, me.getPlayerUid(), today);
        insertLog(familyId, me.getPlayerUid(), plot.getId(), opId, action,
                plot.getCropCode(), null, detail + ",这块地更有劲了");
        return actionVO(familyId, plot.getId(), me,
                cost > 0 || coinsEarned > 0 ? vo -> {
                    if (cost > 0) {
                        vo.setCoinsSpent(cost);
                    }
                    if (coinsEarned > 0) {
                        vo.setCoinsEarned(coinsEarned);
                    }
                } : null);
    }

    private GardenPlot requirePlot(Long familyId, Long plotId) {
        GardenPlot plot = plotId == null ? null : plotMapper.selectById(plotId);
        // 跨家庭一律 404,不泄露存在性(项目口径)
        if (plot == null || !plot.getFamilyId().equals(familyId)) {
            throw new BizException(ResultCode.NOT_FOUND, "这块地不存在");
        }
        return plot;
    }

    /** 归属统一前置(架构分册 §17.1):共享放行、本人放行、其余 403「这是 {昵称} 的房间」 */
    private void requireOperable(GardenPlot plot, Long familyId, GardenMember me) {
        if (GardenConst.mayOperate(plot.getOwnerUid(), me.getPlayerUid())) {
            return;
        }
        Map<String, String> nicknames = worldService.nicknames(familyId);
        String ownerName = nicknames.getOrDefault(plot.getOwnerUid(), "家人");
        throw new BizException(ResultCode.FORBIDDEN, "这是 " + ownerName + " 的房间");
    }

    /** 按 op_id 查已落账动作(garden_log.uk_op_id 兜底幂等,限本家庭防跨家重放) */
    private GardenLog seenOp(Long familyId, String opId) {
        return logMapper.selectOne(new LambdaQueryWrapper<GardenLog>()
                .eq(GardenLog::getFamilyId, familyId)
                .eq(GardenLog::getOpId, opId));
    }

    /**
     * 断线重试回执(契约 §0「重试/重放返回当前状态、不重复生效」):
     * 同 Idempotency-Key → 当前地块/钱包/仓库 + alreadyDone,结算全部跳过;
     * 键被其他动作占用 → 400。404/403 归属口径照常把关。
     */
    private PlotActionVO replay(Long familyId, Long plotId, GardenMember me, GardenLog prevOp, String expectAction) {
        if (!expectAction.equals(prevOp.getAction())) {
            throw new BizException(ResultCode.BAD_REQUEST, "这个操作编号已被别的动作用掉了");
        }
        GardenPlot plot = requirePlot(familyId, plotId);
        requireOperable(plot, familyId, me);
        PlotActionVO vo = actionVO(familyId, plot.getId(), me, null);
        vo.setAlreadyDone(true);
        return vo;
    }

    /** 条件扣款;余额不足 → 1008 带差额(FR-23) */
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

    private PlotActionVO actionVO(Long familyId, Long plotId, GardenMember me,
                                  java.util.function.Consumer<PlotActionVO> extra) {
        PlotActionVO vo = new PlotActionVO();
        LocalDateTime now = LocalDateTime.now();
        GardenPlot plot = plotMapper.selectById(plotId);
        VenueDef av = content.venue(plot.getVenue());
        vo.setPlot(assembler.toVO(plot, now, me.getPlayerUid(),
                worldService.nicknames(familyId), GardenSeason.seasonOf(now.toLocalDate()),
                this.growth.scheduleOf(familyId),
                this.growth.weatherEventOf(familyId, plot.getVenue(),
                        av != null && av.indoor(), plot.getLastWateredAt(), plot.getPlantedAt(), now)));
        GardenWallet wallet = walletMapper.selectOne(new LambdaQueryWrapper<GardenWallet>()
                .eq(GardenWallet::getFamilyId, familyId)
                .eq(GardenWallet::getPlayerUid, me.getPlayerUid()));
        vo.setWalletCoins(wallet == null ? 0L : wallet.getCoins());
        vo.setInventoryUsed(inventoryMapper.sumQty(familyId));
        vo.setInventoryCap(GardenConst.WAREHOUSE_BASE
                + GardenConst.WAREHOUSE_PER_PLOT * plotMapper.countAll(familyId)
                + growth.warehouseBonus(familyId));
        if (extra != null) {
            extra.accept(vo);
        }
        return vo;
    }

    private void insertLog(Long familyId, String playerUid, Long plotId, String opId, String action,
                           String cropCode, String message, String detail) {
        GardenLog logRow = new GardenLog();
        logRow.setFamilyId(familyId);
        logRow.setActorUid(playerUid);
        logRow.setOpId(opId);
        logRow.setLamport(0L);
        logRow.setOrigin("IHOMY");
        logRow.setAction(action);
        logRow.setCropCode(cropCode);
        logRow.setPlotId(plotId);
        logRow.setMessage(message);
        logRow.setDetail(detail);
        try {
            logMapper.insert(logRow);
        } catch (DataIntegrityViolationException e) {
            // 同键并发双跑:条件 UPDATE 已保证状态只动一次,此处只把撞 uk_op_id 的脏 500 变成干净 409
            throw new BizException(ResultCode.CONFLICT, "这个动作刚被处理过,请刷新查看");
        }
    }

    private String trim(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String m = message.trim();
        return m.length() > 200 ? m.substring(0, 200) : m;
    }

    private String humanDuration(long minutes) {
        if (minutes < 60) {
            return minutes + " 分钟";
        }
        long h = minutes / 60;
        long m = minutes % 60;
        return m == 0 ? h + " 小时" : h + " 小时 " + m + " 分";
    }
}
