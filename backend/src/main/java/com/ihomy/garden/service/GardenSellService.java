package com.ihomy.garden.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.garden.GardenConst;
import com.ihomy.garden.content.CropDef;
import com.ihomy.garden.content.GardenContent;
import com.ihomy.garden.core.GardenGrowth;
import com.ihomy.garden.core.GardenSeason;
import com.ihomy.garden.core.Ulid;
import com.ihomy.garden.entity.GardenInventory;
import com.ihomy.garden.entity.GardenLog;
import com.ihomy.garden.entity.GardenMember;
import com.ihomy.garden.entity.GardenWallet;
import com.ihomy.garden.mapper.GardenInventoryMapper;
import com.ihomy.garden.mapper.GardenLogMapper;
import com.ihomy.garden.mapper.GardenPlotMapper;
import com.ihomy.garden.mapper.GardenStatMapper;
import com.ihomy.garden.mapper.GardenWalletMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 卖出(系统收购,玩法稿 §12.2):把仓库里的果实按参考价卖给系统——设计原文点名的保底出口
 * (「否则挂单没人买会卡死经济」),也是满仓禁收(§4.13)的唯一 P1 仓库出口。
 * 参考价 = 单果价 × 错季 0.5 × 节气卖价事件 bp(与收获入账同模型);园币进卖出者钱包。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GardenSellService {

    private final GardenContent content;
    private final GardenInventoryMapper inventoryMapper;
    private final GardenWalletMapper walletMapper;
    private final GardenStatMapper statMapper;
    private final GardenLogMapper logMapper;
    private final GardenPlotMapper plotMapper;
    private final GardenGrowthService growth;

    /** 卖出果实给系统(§12.2 系统收购):库存条件扣减、参考价入账、SELL 流水;Idempotency-Key 幂等 */
    @Transactional
    public Map<String, Object> sell(Long familyId, GardenMember me, String itemCode, Integer qty, String opIdIn) {
        if (itemCode == null || itemCode.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "要卖什么,得说清楚");
        }
        if (qty == null || qty < 1) {
            throw new BizException(ResultCode.BAD_REQUEST, "要卖多少,得说清楚");
        }
        CropDef crop = content.crop(itemCode);
        if (crop == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "这不是能卖的东西");
        }
        GardenInventory row = inventoryMapper.selectOne(new LambdaQueryWrapper<GardenInventory>()
                .eq(GardenInventory::getFamilyId, familyId)
                .eq(GardenInventory::getItemCode, itemCode));
        if (row == null || !GardenConst.ITEM_TYPE_FRUIT.equals(row.getItemType())) {
            throw new BizException(ResultCode.BAD_REQUEST, "种子不是收成,系统不回收");
        }
        if (row.getQty() == null || row.getQty() < qty) {
            throw new BizException(ResultCode.CONFLICT,
                    "仓库里只有 " + (row.getQty() == null ? 0 : row.getQty()) + " 个,不够卖 " + qty + " 个");
        }
        // 断线重试:同 Idempotency-Key 已落账 → 回执当前状态(契约 §0)
        String opId = Ulid.orNext(opIdIn);
        GardenLog prev = logMapper.selectOne(new LambdaQueryWrapper<GardenLog>()
                .eq(GardenLog::getFamilyId, familyId)
                .eq(GardenLog::getOpId, opId));
        if (prev != null) {
            return receipt(familyId, me, itemCode, qty, true, null, null, null, null, null, null);
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        String season = GardenSeason.seasonOf(today);
        boolean inSeason = GardenSeason.inSeason(crop.seasons(), season);
        int unitPrice = GardenGrowth.sellPrice(crop.fruitPrice(), inSeason);
        int priceBp = growth.scheduleOf(familyId).priceBp(today);
        // 整批出货(§4.2.4):同种一次卖 ≥ 10 份,回收额 ×1.05
        int batchBp = qty >= GardenConst.MARKET_BATCH_MIN_QTY ? GardenConst.MARKET_BATCH_BONUS_BP : 100;
        int coinsGranted = (unitPrice * qty * priceBp * batchBp + 5000) / 10000;

        // 条件扣减:库存不足即 0 行(并发/库存变动安全);归零即删行(与种子包同口径)
        if (inventoryMapper.deduct(familyId, itemCode, qty) == 0) {
            throw new BizException(ResultCode.CONFLICT, "仓库里的库存刚被别的成员动了,刷新后再试");
        }
        inventoryMapper.deleteIfZero(familyId, itemCode);
        walletMapper.insertIgnore(familyId, me.getPlayerUid());
        walletMapper.add(familyId, me.getPlayerUid(), coinsGranted);
        statMapper.upsertDeltas(familyId, today, 0, 0, 0, coinsGranted, 0, 0);
        GardenLog logRow = new GardenLog();
        logRow.setFamilyId(familyId);
        logRow.setActorUid(me.getPlayerUid());
        logRow.setOpId(opId);
        logRow.setLamport(0L);
        logRow.setOrigin("IHOMY");
        logRow.setAction("SELL");
        logRow.setCropCode(itemCode);
        logRow.setDetail("把 " + itemCode + " ×" + qty + " 卖给了系统,进账 " + coinsGranted + " 园币");
        logMapper.insert(logRow);
        log.info("[garden] sell family={} item={} qty={} coins={} priceBp={} opId={}",
                familyId, itemCode, qty, coinsGranted, priceBp, opId);
        return receipt(familyId, me, itemCode, qty, false, unitPrice, coinsGranted,
                crop.seedPrice() * qty, inSeason ? 1 : 0, priceBp, batchBp);
    }

    // ---------- 内部 ----------

    private Map<String, Object> receipt(Long familyId, GardenMember me, String itemCode, Integer qty,
                                        boolean alreadyDone, Integer unitPrice, Integer coinsGranted,
                                        Integer seedCost, Integer inSeason, Integer priceBp, Integer batchBp) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("itemCode", itemCode);
        out.put("qty", qty);
        out.put("alreadyDone", alreadyDone);
        out.put("inventoryUsed", inventoryMapper.sumQty(familyId));
        out.put("inventoryCap", GardenConst.WAREHOUSE_BASE
                + GardenConst.WAREHOUSE_PER_PLOT * plotMapper.countAll(familyId)
                + growth.warehouseBonus(familyId));
        GardenWallet wallet = walletMapper.selectOne(new LambdaQueryWrapper<GardenWallet>()
                .eq(GardenWallet::getFamilyId, familyId)
                .eq(GardenWallet::getPlayerUid, me.getPlayerUid()));
        out.put("walletCoins", wallet == null ? 0L : wallet.getCoins());
        if (unitPrice != null) {
            out.put("unitPrice", unitPrice);        // 参考单价(核本)
            out.put("coinsGranted", coinsGranted);
            out.put("seedCost", seedCost);          // 种子参考成本(核本:这批的种子价)
            out.put("inSeason", inSeason != null && inSeason == 1);
            out.put("priceBp", priceBp == null ? 100 : priceBp);
            out.put("batchBp", batchBp == null ? 100 : batchBp);  // 整批出货加成(≥10 份 ×1.05,§4.2.4;数字不藏)
        }
        return out;
    }
}
