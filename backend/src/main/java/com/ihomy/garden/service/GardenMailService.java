package com.ihomy.garden.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.garden.GardenConst;
import com.ihomy.garden.content.CropDef;
import com.ihomy.garden.content.GardenContent;
import com.ihomy.garden.core.GardenMail;
import com.ihomy.garden.core.Ulid;
import com.ihomy.garden.dto.GardenItemVO;
import com.ihomy.garden.dto.MailOpenVO;
import com.ihomy.garden.entity.GardenLog;
import com.ihomy.garden.entity.GardenMember;
import com.ihomy.garden.entity.GardenWallet;
import com.ihomy.garden.entity.GardenWorld;
import com.ihomy.garden.mapper.GardenInventoryMapper;
import com.ihomy.garden.mapper.GardenLogMapper;
import com.ihomy.garden.mapper.GardenStatMapper;
import com.ihomy.garden.mapper.GardenWalletMapper;
import com.ihomy.garden.mapper.GardenWorldMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 每日信封(FR-27 / 玩法稿 §18.7):按世界一天一封、多成员共拆一封;
 * 幂等 = last_mail_date 条件 UPDATE(0 行 = 已拆,不重复发奖);
 * 信的内容与种子由 hash(world_id + 日期 + 季节) 确定性推导,不落库;
 * 可补最多 3 天,更早的是旧便签(无奖励无补偿);首封(世界第一次拆)给初始礼包。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GardenMailService {

    private final GardenWorldService worldService;
    private final GardenQueryService queryService;
    private final GardenWorldMapper worldMapper;
    private final GardenInventoryMapper inventoryMapper;
    private final GardenWalletMapper walletMapper;
    private final GardenStatMapper statMapper;
    private final GardenLogMapper logMapper;
    private final GardenContent content;

    @Transactional
    public MailOpenVO open(Long familyId, Long userId, String opIdIn) {
        GardenWorld world = worldService.ensureWorld(familyId);
        GardenMember me = worldService.requireMember(familyId, userId);
        String opId = normalizeOpId(opIdIn);
        LocalDate today = LocalDate.now();
        LocalDate cycleStart = queryService.cycleStartOf(world);
        List<MailOpenVO.OpenedLetter> todayLetter =
                List.of(toOpened(GardenMail.deriveForDate(world.getWorldId(), today, cycleStart,
                        content.unlockedCrops(), content.mail())));

        // 幂等回执:当天已拆(或并发条件下他人先拆)→ 不重复发奖(FR-27 ②)
        if (world.getLastMailDate() != null && !world.getLastMailDate().isBefore(today)) {
            return receipt(familyId, me.getPlayerUid(), true, todayLetter);
        }
        int claimed = worldMapper.claimMailDay(familyId, today);
        if (claimed == 0) {
            return receipt(familyId, me.getPlayerUid(), true, todayLetter);
        }

        // 待拆批次:从未拆日到今天,可补窗口 ≤3 天;首封(从未拆过)在批次首日给初始礼包
        LocalDate from = world.getLastMailDate() == null
                ? maxOf(today.minusDays(GardenConst.MAIL_BACKFILL_DAYS), cycleStart)
                : maxOf(world.getLastMailDate().plusDays(1), today.minusDays(GardenConst.MAIL_BACKFILL_DAYS));
        boolean firstEver = world.getLastMailDate() == null;

        List<MailOpenVO.OpenedLetter> letters = new ArrayList<>();
        int coinsGrantedTotal = 0;
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            GardenMail.Letter letter = firstEver && d.equals(from)
                    ? GardenMail.deriveFirst(world.getWorldId(), d, content.unlockedCrops(), content.mail())
                    : GardenMail.deriveForDate(world.getWorldId(), d, cycleStart, content.unlockedCrops(), content.mail());
            MailOpenVO.OpenedLetter out = toOpened(letter);
            if (letter.seedCropCode() != null) {
                inventoryMapper.upsertAdd(familyId, GardenConst.seedItemCode(letter.seedCropCode()),
                        GardenConst.ITEM_TYPE_CONSUMABLE, 1);
                CropDef crop = content.crop(letter.seedCropCode());
                GardenItemVO seed = new GardenItemVO();
                seed.setItemCode(GardenConst.seedItemCode(letter.seedCropCode()));
                seed.setCropCode(letter.seedCropCode());
                seed.setItemType(GardenConst.ITEM_TYPE_CONSUMABLE);
                seed.setQty(1);
                out.setSeed(seed);
            }
            if (letter.coins() > 0) {
                walletMapper.add(familyId, me.getPlayerUid(), letter.coins());
                coinsGrantedTotal += letter.coins();
            }
            letters.add(out);
        }

        if (coinsGrantedTotal > 0) {
            statMapper.upsertDeltas(familyId, today, 0, 0, 0, coinsGrantedTotal, 0, 0);
        }
        GardenLog logRow = new GardenLog();
        logRow.setFamilyId(familyId);
        logRow.setActorUid(me.getPlayerUid());
        logRow.setOpId(opId);
        logRow.setLamport(0L);
        logRow.setOrigin("IHOMY");
        logRow.setAction("GIFT");
        logRow.setDetail(firstEver
                ? "拆了第一封信,收下初始礼包(" + letters.size() + " 封)"
                : "拆了桌上的信(" + letters.size() + " 封)");
        logMapper.insert(logRow);
        log.info("[garden] mail opened familyId={} first={} letters={} opId={}",
                familyId, firstEver, letters.size(), opId);

        return receipt(familyId, me.getPlayerUid(), false, letters);
    }

    // ---------- 内部 ----------

    private MailOpenVO receipt(Long familyId, String playerUid, boolean alreadyOpened,
                               List<MailOpenVO.OpenedLetter> letters) {
        MailOpenVO vo = new MailOpenVO();
        vo.setOpened(true);
        vo.setAlreadyOpened(alreadyOpened);
        vo.setLetters(letters);
        GardenWallet wallet = walletMapper.selectOne(new LambdaQueryWrapper<GardenWallet>()
                .eq(GardenWallet::getFamilyId, familyId)
                .eq(GardenWallet::getPlayerUid, playerUid));
        vo.setWalletCoins(wallet == null ? 0L : wallet.getCoins());
        return vo;
    }

    private MailOpenVO.OpenedLetter toOpened(GardenMail.Letter letter) {
        MailOpenVO.OpenedLetter out = new MailOpenVO.OpenedLetter();
        out.setDate(letter.date());
        out.setType(letter.type());
        out.setText(letter.text());
        out.setSignature(letter.signature());
        out.setCoinsGranted(letter.coins());
        return out;
    }

    /** Idempotency-Key 头(ULID);缺省由服务端生成 */
    private String normalizeOpId(String opIdIn) {
        return opIdIn != null && Ulid.isValid(opIdIn) ? opIdIn : Ulid.next();
    }

    private LocalDate maxOf(LocalDate a, LocalDate b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.isAfter(b) ? a : b;
    }
}
