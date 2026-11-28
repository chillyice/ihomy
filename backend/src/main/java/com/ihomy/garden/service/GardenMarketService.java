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
import com.ihomy.garden.entity.GardenListing;
import com.ihomy.garden.entity.GardenLog;
import com.ihomy.garden.entity.GardenMember;
import com.ihomy.garden.entity.GardenWallet;
import com.ihomy.garden.mapper.GardenInventoryMapper;
import com.ihomy.garden.mapper.GardenListingMapper;
import com.ihomy.garden.mapper.GardenLogMapper;
import com.ihomy.garden.mapper.GardenStatMapper;
import com.ihomy.garden.mapper.GardenWalletMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 家庭挂单(玩法稿 §12.2 成员互市)+ 系统代售(园币买种子)。
 * 库存口径:挂单即从家庭仓扣走(防双卖);售出 → 货回家庭仓 + 买家币 → 卖家;下架/过期 → 货回仓——
 * 净效果:仓内数量不变,币从买家到卖家(「家庭就是市场」)。价格上限 = 参考价 ×3(家庭保护)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GardenMarketService {

    private final GardenContent content;
    private final GardenInventoryMapper inventoryMapper;
    private final GardenWalletMapper walletMapper;
    private final GardenStatMapper statMapper;
    private final GardenLogMapper logMapper;
    private final GardenListingMapper listingMapper;
    private final GardenGrowthService growth;

    // ---------- 上架 ----------

    /** 上架:条件扣库存(挂单即占仓)、价格上限、48h 过期 */
    @Transactional
    public Map<String, Object> list(Long familyId, GardenMember me, String itemCode, Integer qty,
                                    Integer unitPrice, String opIdIn) {
        if (itemCode == null || itemCode.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "要卖什么,得说清楚");
        }
        if (qty == null || qty < 1) {
            throw new BizException(ResultCode.BAD_REQUEST, "要挂多少,得说清楚");
        }
        if (unitPrice == null || unitPrice < 1) {
            throw new BizException(ResultCode.BAD_REQUEST, "挂牌单价得是正整数园币");
        }
        CropDef crop = content.crop(itemCode);
        if (crop == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "这不是能挂的东西");
        }
        GardenInventory row = inventoryMapper.selectOne(new LambdaQueryWrapper<GardenInventory>()
                .eq(GardenInventory::getFamilyId, familyId)
                .eq(GardenInventory::getItemCode, itemCode));
        if (row == null || !GardenConst.ITEM_TYPE_FRUIT.equals(row.getItemType())) {
            throw new BizException(ResultCode.BAD_REQUEST, "种子不是收成,不上市集");
        }
        if (row.getQty() == null || row.getQty() < qty) {
            throw new BizException(ResultCode.CONFLICT,
                    "仓库里只有 " + (row.getQty() == null ? 0 : row.getQty()) + " 个,不够挂 " + qty + " 个");
        }
        int cap = referencePriceCap(crop, familyId);
        if (unitPrice > cap) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "挂单价不能超过参考价的 3 倍(参考价 " + cap / 3 + " 园币)");
        }
        if (inventoryMapper.deduct(familyId, itemCode, qty) == 0) {
            throw new BizException(ResultCode.CONFLICT, "仓库里的库存刚被别的成员动了,刷新后再试");
        }
        inventoryMapper.deleteIfZero(familyId, itemCode);
        String opId = Ulid.orNext(opIdIn);
        GardenListing listing = new GardenListing();
        listing.setFamilyId(familyId);
        listing.setSellerUid(me.getPlayerUid());
        listing.setItemCode(itemCode);
        listing.setItemName(crop.name());
        listing.setQty(qty);
        listing.setUnitPrice(unitPrice);
        listing.setStatus("ACTIVE");
        listing.setExpiresAt(LocalDateTime.now().plusHours(48));
        listingMapper.insert(listing);
        insertLog(familyId, me.getPlayerUid(), opId, "MARKET_LIST", itemCode,
                "把 " + itemCode + " ×" + qty + " 挂上市集,单价 " + unitPrice + " 园币");
        log.info("[garden] market list family={} item={} qty={} price={} listingId={}",
                familyId, itemCode, qty, unitPrice, listing.getId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("listingId", listing.getId());
        out.put("itemCode", itemCode);
        out.put("qty", qty);
        out.put("unitPrice", unitPrice);
        out.put("expiresInHours", 48);
        out.put("inventoryUsed", inventoryMapper.sumQty(familyId));
        return out;
    }

    // ---------- 列表 ----------

    /** 家庭在售挂单(未过期;mine 标记给挂单人看) */
    public List<Map<String, Object>> listings(Long familyId, String myUid) {
        expireStale(familyId);
        List<GardenListing> rows = listingMapper.selectList(new LambdaQueryWrapper<GardenListing>()
                .eq(GardenListing::getFamilyId, familyId)
                .eq(GardenListing::getStatus, "ACTIVE")
                .gt(GardenListing::getExpiresAt, LocalDateTime.now())
                .orderByAsc(GardenListing::getId));
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (GardenListing r : rows) {
            CropDef crop = content.crop(r.getItemCode());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("listingId", r.getId());
            m.put("itemCode", r.getItemCode());
            m.put("itemName", crop == null ? r.getItemName() : crop.name());
            m.put("qty", r.getQty());
            m.put("unitPrice", r.getUnitPrice());
            m.put("sellerUid", r.getSellerUid());
            m.put("mine", r.getSellerUid().equals(myUid));
            m.put("expiresInHours", java.time.Duration.between(
                    LocalDateTime.now(), r.getExpiresAt()).toHours());
            out.add(m);
        }
        return out;
    }

    // ---------- 购买 ----------

    /** 购买:锁定挂单(条件 UPDATE,非卖家本人)→ 买家园币条件扣减(不足 1008)→ 卖家钱包加 → 货回家庭仓 */
    @Transactional
    public Map<String, Object> buy(Long familyId, GardenMember me, Long listingId, String opIdIn) {
        LocalDateTime now = LocalDateTime.now();
        GardenListing listing = listingMapper.selectById(listingId);
        if (listing == null || !listing.getFamilyId().equals(familyId)) {
            throw new BizException(ResultCode.NOT_FOUND, "没有这个挂单");
        }
        if (listing.getSellerUid().equals(me.getPlayerUid())) {
            throw new BizException(ResultCode.BAD_REQUEST, "这是你自己挂的单,想撤走下架就行");
        }
        int total = listing.getUnitPrice() * listing.getQty();
        if (walletMapper.deduct(familyId, me.getPlayerUid(), total) == 0) {
            GardenWallet w = walletMapper.selectOne(new LambdaQueryWrapper<GardenWallet>()
                    .eq(GardenWallet::getFamilyId, familyId)
                    .eq(GardenWallet::getPlayerUid, me.getPlayerUid()));
            long balance = w == null ? 0 : w.getCoins();
            throw new BizException(ResultCode.INSUFFICIENT_POINTS,
                    "园币不够,还差 " + Math.max(0, total - balance) + " 枚");
        }
        // 锁定挂单(仅 ACTIVE 且未过期且非卖家本人;0 行 = 状态已变)
        if (listingMapper.markSold(listingId, familyId, me.getPlayerUid(), now) == 0) {
            throw new BizException(ResultCode.CONFLICT, "这个挂单刚被别人买走或已下架");
        }
        // 货回家庭仓(家庭内循环:仓内数量不变,币从买家到卖家)
        inventoryMapper.upsertAdd(familyId, listing.getItemCode(), GardenConst.ITEM_TYPE_FRUIT, listing.getQty());
        walletMapper.add(familyId, listing.getSellerUid(), total);
        statMapper.upsertDeltas(familyId, now.toLocalDate(), 0, 0, 0, 0, 0, 0);
        String opId = Ulid.orNext(opIdIn);
        insertLog(familyId, me.getPlayerUid(), opId, "MARKET_BUY", listing.getItemCode(),
                "从市集买下 " + listing.getItemCode() + " ×" + listing.getQty() + ",付给卖家 " + total + " 园币");
        insertLog(familyId, listing.getSellerUid(), Ulid.next(), "MARKET_BUY", listing.getItemCode(),
                "挂单被 " + me.getPlayerUid() + " 买走 " + listing.getQty() + " 个,进账 " + total + " 园币");
        log.info("[garden] market buy family={} listing={} buyer={} seller={} total={}",
                familyId, listingId, me.getPlayerUid(), listing.getSellerUid(), total);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("listingId", listingId);
        out.put("itemCode", listing.getItemCode());
        out.put("qty", listing.getQty());
        out.put("totalPaid", total);
        out.put("sellerUid", listing.getSellerUid());
        GardenWallet meWallet = walletMapper.selectOne(new LambdaQueryWrapper<GardenWallet>()
                .eq(GardenWallet::getFamilyId, familyId)
                .eq(GardenWallet::getPlayerUid, me.getPlayerUid()));
        out.put("walletCoins", meWallet == null ? 0L : meWallet.getCoins());
        out.put("inventoryUsed", inventoryMapper.sumQty(familyId));
        return out;
    }

    // ---------- 系统代售:园币买种子包(§12.2 系统常挂种子清单)----------

    /** 买种子包:seed_price × qty 园币(买家个人钱包),种子入家庭仓;只卖已解锁作物 */
    @Transactional
    public Map<String, Object> shopBuy(Long familyId, GardenMember me, String cropCode, Integer qty,
                                       Integer stage, String opIdIn) {
        if (cropCode == null || cropCode.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "要买哪种种子,得说清楚");
        }
        if (qty == null || qty < 1) {
            throw new BizException(ResultCode.BAD_REQUEST, "要买几包,得说清楚");
        }
        CropDef crop = content.crop(cropCode);
        if (crop == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "没有这种种子");
        }
        if (stage != null && crop.unlockStage() > stage) {
            throw new BizException(ResultCode.BAD_REQUEST, "这种种子还没上架(第 " + crop.unlockStage() + " 阶开放)");
        }
        int total = crop.seedPrice() * qty;
        if (walletMapper.deduct(familyId, me.getPlayerUid(), total) == 0) {
            GardenWallet w = walletMapper.selectOne(new LambdaQueryWrapper<GardenWallet>()
                    .eq(GardenWallet::getFamilyId, familyId)
                    .eq(GardenWallet::getPlayerUid, me.getPlayerUid()));
            long balance = w == null ? 0 : w.getCoins();
            throw new BizException(ResultCode.INSUFFICIENT_POINTS,
                    "园币不够,还差 " + Math.max(0, total - balance) + " 枚");
        }
        inventoryMapper.upsertAdd(familyId, GardenConst.seedItemCode(cropCode),
                GardenConst.ITEM_TYPE_CONSUMABLE, qty);
        statMapper.upsertDeltas(familyId, LocalDateTime.now().toLocalDate(), 0, 0, 0, 0, total, 0);
        String opId = Ulid.orNext(opIdIn);
        insertLog(familyId, me.getPlayerUid(), opId, "SHOP_BUY", cropCode,
                "从系统代售买了 " + crop.name() + " 种子 ×" + qty + ",花了 " + total + " 园币");
        log.info("[garden] shop buy family={} crop={} qty={} total={} opId={}", familyId, cropCode, qty, total, opId);
        GardenWallet meWallet = walletMapper.selectOne(new LambdaQueryWrapper<GardenWallet>()
                .eq(GardenWallet::getFamilyId, familyId)
                .eq(GardenWallet::getPlayerUid, me.getPlayerUid()));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("itemCode", GardenConst.seedItemCode(cropCode));
        out.put("qty", qty);
        out.put("coinsSpent", total);
        out.put("walletCoins", meWallet == null ? 0L : meWallet.getCoins());
        out.put("inventoryUsed", inventoryMapper.sumQty(familyId));
        return out;
    }

    // ---------- 下架 ----------

    /** 卖家下架:货回家庭仓 */
    @Transactional
    public Map<String, Object> cancel(Long familyId, GardenMember me, Long listingId, String opIdIn) {
        GardenListing listing = listingMapper.selectById(listingId);
        if (listing == null || !listing.getFamilyId().equals(familyId)) {
            throw new BizException(ResultCode.NOT_FOUND, "没有这个挂单");
        }
        if (!listing.getSellerUid().equals(me.getPlayerUid())) {
            throw new BizException(ResultCode.FORBIDDEN, "只能下架自己挂的单");
        }
        if (listingMapper.markCancelled(listingId, familyId, me.getPlayerUid()) == 0) {
            throw new BizException(ResultCode.CONFLICT, "这个挂单刚被买走或已下架");
        }
        inventoryMapper.upsertAdd(familyId, listing.getItemCode(), GardenConst.ITEM_TYPE_FRUIT, listing.getQty());
        String opId = Ulid.orNext(opIdIn);
        insertLog(familyId, me.getPlayerUid(), opId, "MARKET_CANCEL", listing.getItemCode(),
                "把 " + listing.getItemCode() + " ×" + listing.getQty() + " 从市集收了回来");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("listingId", listingId);
        out.put("itemCode", listing.getItemCode());
        out.put("qty", listing.getQty());
        out.put("inventoryUsed", inventoryMapper.sumQty(familyId));
        return out;
    }

    // ---------- 过期回仓(读时守卫 + 清理任务)----------

    /** 读时守卫:把已过期的 ACTIVE 单标 EXPIRED 并回仓(逐单条件 UPDATE,防并发) */
    private void expireStale(Long familyId) {
        for (GardenListing r : listingMapper.selectExpired(familyId, LocalDateTime.now())) {
            if (listingMapper.markExpired(r.getId()) > 0) {
                inventoryMapper.upsertAdd(familyId, r.getItemCode(), GardenConst.ITEM_TYPE_FRUIT, r.getQty());
                log.info("[garden] market expired family={} listing={} item={} qty={}",
                        familyId, r.getId(), r.getItemCode(), r.getQty());
            }
        }
    }

    // ---------- 内部 ----------

    /** 参考价上限:单果价 × 错季 0.5(当日判定)× 3(§12.2 家庭保护) */
    private int referencePriceCap(CropDef crop, Long familyId) {
        String season = GardenSeason.seasonOf(java.time.LocalDate.now());
        int unitPrice = GardenGrowth.sellPrice(crop.fruitPrice(),
                GardenSeason.inSeason(crop.seasons(), season));
        return unitPrice * 3;
    }

    private void insertLog(Long familyId, String actorUid, String opId, String action,
                           String cropCode, String detail) {
        GardenLog row = new GardenLog();
        row.setFamilyId(familyId);
        row.setActorUid(actorUid);
        row.setOpId(opId);
        row.setLamport(0L);
        row.setOrigin("IHOMY");
        row.setAction(action);
        row.setCropCode(cropCode);
        row.setDetail(detail);
        logMapper.insert(row);
    }
}
