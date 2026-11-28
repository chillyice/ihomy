package com.ihomy.garden.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.UserNames;
import com.ihomy.entity.SysUser;
import com.ihomy.garden.GardenConst;
import com.ihomy.garden.core.GardenCat;
import com.ihomy.garden.core.GardenGrowth;
import com.ihomy.garden.core.GardenMail;
import com.ihomy.garden.core.GardenSeason;
import com.ihomy.garden.core.GardenSolarEvents;
import com.ihomy.garden.core.GardenWeatherEvents;
import com.ihomy.garden.content.CropDef;
import com.ihomy.garden.content.GardenContent;
import com.ihomy.garden.content.VenueDef;
import com.ihomy.garden.dto.GardenEntryVO;
import com.ihomy.garden.dto.GardenItemVO;
import com.ihomy.garden.dto.GardenMetaVO;
import com.ihomy.garden.dto.GardenPanoramaVO;
import com.ihomy.garden.dto.LetterVO;
import com.ihomy.garden.dto.PlotVO;
import com.ihomy.garden.dto.ShopItemVO;
import com.ihomy.garden.entity.GardenInventory;
import com.ihomy.garden.entity.GardenMember;
import com.ihomy.garden.entity.GardenPlot;
import com.ihomy.garden.entity.GardenWallet;
import com.ihomy.garden.entity.GardenWorld;
import com.ihomy.garden.mapper.GardenInventoryMapper;
import com.ihomy.garden.mapper.GardenPlotMapper;
import com.ihomy.garden.mapper.GardenWalletMapper;
import com.ihomy.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 花园读侧:首页便签状态(entry)、能力协商(meta)、全景(panorama)、窗台影子(silhouette)。
 * 全部纯读推导;别人的个人地块照常下发、只读可见(玩法稿 §3.3);场景数据不含光照参数(FR-33)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GardenQueryService {

    private final GardenWorldService worldService;
    private final GardenPlotMapper plotMapper;
    private final GardenInventoryMapper inventoryMapper;
    private final GardenWalletMapper walletMapper;
    private final GardenContent content;
    private final GardenGrowthService growth;
    private final GardenPlotAssembler assembler;
    private final SysUserMapper sysUserMapper;
    private final ObjectMapper objectMapper;

    /** 首页便签状态(FR-32 ①):MAIL 有信 / CARE 待照料 / CALM 平静 */
    public GardenEntryVO entry(Long familyId, Long userId) {
        GardenWorld world = worldService.ensureWorld(familyId);
        GardenMember me = worldService.requireMember(familyId, userId);
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        String season = GardenSeason.seasonOf(today);
        GardenSolarEvents.Schedule events = growth.scheduleOf(familyId);

        boolean hasUnopenedMail = world.getLastMailDate() == null || world.getLastMailDate().isBefore(today);
        boolean needsCare = false;
        boolean harvestReady = false;
        boolean hasPlanted = false;
        Integer tempMean = growth.tempMeanToday(familyId);
        Map<String, Object> weatherNow = growth.weatherOf(familyId);
        String weatherCondition = weatherNow == null ? null : (String) weatherNow.get("condition");
        for (GardenPlot p : plotsOf(familyId)) {
            if (p.getCropCode() == null || !GardenConst.mayOperate(p.getOwnerUid(), me.getPlayerUid())) {
                continue;
            }
            hasPlanted = true;
            CropDef crop = content.crop(p.getCropCode());
            if (crop == null || p.getPlantedAt() == null) {
                continue;
            }
            VenueDef venue = content.venue(p.getVenue());
            GardenWeatherEvents.Event weatherEvent = growth.weatherEventOf(familyId, p.getVenue(),
                    venue != null && venue.indoor(), p.getLastWateredAt(), p.getPlantedAt(), now);
            GardenGrowth.GrowthView g = GardenGrowth.evaluate(
                    crop.durationHours(), crop.soilPreference(), crop.soilCompatible(), crop.hardiness(),
                    crop.seasons(), crop.seasonMultBp(), crop.offSeasonMultBp(), season, p.getVenue(),
                    venue != null && venue.indoor(),
                    p.getPrepared() != null && p.getPrepared() == 1, p.getSoilType(),
                    p.getPlantedAt(), p.getLastWateredAt(), p.getCareBoostMinutes(), now, events, weatherEvent,
                    growth.tempStressBpOf(familyId, crop.code(),
                            (venue != null && venue.indoor()) || "GREENHOUSE".equals(p.getVenue())));
            if (g.harvestable()) {
                harvestReady = true;
                needsCare = true;
            }
            if (g.thirsty()) {
                needsCare = true;
            }
        }

        GardenEntryVO vo = new GardenEntryVO();
        vo.setEnabled(true);
        vo.setPlayerUid(me.getPlayerUid());
        vo.setHasUnopenedMail(hasUnopenedMail);
        vo.setNeedsCare(needsCare);
        vo.setHarvestReady(harvestReady);
        vo.setHasPlanted(hasPlanted);
        vo.setNote(hasUnopenedMail ? "MAIL" : (needsCare ? "CARE" : "CALM"));
        return vo;
    }

    /** 能力协商(§28.2);匿名只返回平台与能力,world 为 null(§6.3 免登录) */
    public GardenMetaVO meta(Long familyId, Long userId) {
        GardenMetaVO vo = new GardenMetaVO();
        vo.setContentVersion(content.contentVersion());
        Map<String, Object> caps = new LinkedHashMap<>();
        caps.put("points", false);   // 积分闸门 P2(架构分册 §30.4)
        caps.put("fridge", false);   // 入厨 P2
        // 天气:家庭配置了坐标且天气源可用即 true(照料倍率随天气生效,契约 §10)
        caps.put("weather", userId != null && familyId != null
                && growth.weatherOf(familyId) != null);
        caps.put("notify", true);    // 通知通道可用(轻提示,FR-34)
        caps.put("photo", false);
        caps.put("registry", false);
        caps.put("realtime", null);  // WS 推送后续接入;此前客户端轮询
        caps.put("p2pSignal", false);
        vo.setCapabilities(caps);
        if (userId != null && familyId != null) {
            GardenWorld world = worldService.ensureWorld(familyId);
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("worldId", world.getWorldId());
            w.put("kind", world.getKind());
            w.put("origin", world.getOrigin());
            w.put("authority", world.getAuthority());
            w.put("cycleStartAt", cycleStartOf(world));
            vo.setWorld(w);
        }
        return vo;
    }

    /** 花园全景:窗台场景一屏所需的全部数据 */
    public GardenPanoramaVO panorama(Long familyId, Long userId) {
        GardenWorld world = worldService.ensureWorld(familyId);
        GardenMember me = worldService.requireMember(familyId, userId);
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        String season = GardenSeason.seasonOf(today);
        Map<String, String> nicknames = worldService.nicknames(familyId);

        GardenPanoramaVO vo = new GardenPanoramaVO();

        Map<String, Object> w = new LinkedHashMap<>();
        w.put("worldId", world.getWorldId());
        w.put("cycleStartAt", cycleStartOf(world));
        w.put("stage", worldService.stageOf(world));
        // 节气与推进(详细设计 §1/§2):termCount=建号后已交接的节气个数
        int termCount = growth.termCount(world.getSettings());
        var currentTerm = growth.currentTerm(world.getSettings());
        var nextTerm = growth.nextTerm(world.getSettings());
        w.put("termCount", termCount);
        w.put("termCode", currentTerm == null ? null : currentTerm.term().code());
        w.put("termName", currentTerm == null ? null : currentTerm.term().name());
        w.put("nextTermCode", nextTerm.term().code());
        w.put("nextTermName", nextTerm.term().name());
        w.put("nextTermDate", nextTerm.date().toString());
        w.put("season", season);
        w.put("seasonName", content.mail().seasonNames().getOrDefault(season, season));
        vo.setWorld(w);

        SysUser sysUser = me.getSysUserId() == null ? null : sysUserMapper.selectById(me.getSysUserId());
        GardenWallet wallet = walletMapper.selectOne(new LambdaQueryWrapper<GardenWallet>()
                .eq(GardenWallet::getFamilyId, familyId)
                .eq(GardenWallet::getPlayerUid, me.getPlayerUid()));
        Map<String, Object> points = growth.pointsView(familyId);
        Map<String, Object> player = new LinkedHashMap<>();
        player.put("playerUid", me.getPlayerUid());
        player.put("nickname", UserNames.of(sysUser));
        player.put("coins", wallet == null ? 0L : wallet.getCoins());
        // 技能点(§4.2.6:GET /garden 返回可用点数和已投入级数;等级不显示,§4.4)
        player.put("pointsAvailable", ((Number) points.get("pointsAvailable")).intValue());
        player.put("pointsSpent", ((Number) points.get("pointsSpent")).intValue());
        vo.setPlayer(player);

        List<PlotVO> plots = new ArrayList<>();
        GardenSolarEvents.Schedule events = growth.scheduleOf(familyId);
        Integer tempMean = growth.tempMeanToday(familyId);
        Map<String, Object> weatherNow = growth.weatherOf(familyId);
        String weatherCondition = weatherNow == null ? null : (String) weatherNow.get("condition");
        for (GardenPlot p : plotsOf(familyId)) {
            VenueDef vd0 = content.venue(p.getVenue());
            GardenWeatherEvents.Event weatherEvent = growth.weatherEventOf(familyId, p.getVenue(),
                    vd0 != null && vd0.indoor(), p.getLastWateredAt(), p.getPlantedAt(), now);
            plots.add(assembler.toVO(p, now, me.getPlayerUid(), nicknames, season, events, weatherEvent));
        }
        plots.sort(Comparator.comparing((PlotVO pv) -> pv.getVenue() + String.format("%02d", pv.getSlotNo())));
        vo.setPlots(plots);

        // 节气事件(真实今天,不随 day_offset 夹具;空窗 = null,§14.4 空窗是有意的)
        var todayTerm = events.effectOn(today);
        w.put("eventCode", events.termAt(today) == null ? null : events.termAt(today).code());
        w.put("eventName", events.termAt(today) == null ? null : events.termAt(today).name());
        w.put("eventGrowthBp", todayTerm.indoorBp());

        // 今日天气与照料倍率(玩法稿 §8 表;null = 无天气数据,倍率按基线 100)
        Map<String, Object> weather = growth.weatherOf(familyId);
        if (weather != null) {
            String condition = (String) weather.get("condition");
            Map<String, Object> wmap = new LinkedHashMap<>();
            wmap.put("condition", condition);
            wmap.put("text", weather.get("text"));
            wmap.put("tempC", weather.get("temp"));
            int sunBp = GardenConst.WEATHER_SUN_BP.getOrDefault(condition, 100);
            int waterBp = GardenConst.WEATHER_WATER_BP.getOrDefault(condition, 100);
            int seasonBp = GardenConst.SUN_SEASON_BP.getOrDefault(season, 100);
            wmap.put("sunBp", sunBp);
            wmap.put("waterBp", waterBp);
            wmap.put("sunSeasonBp", seasonBp);
            wmap.put("sunTotalBp", sunBp * seasonBp / 100);
            List<String> globalEvents = new ArrayList<>();
            if (tempMean != null && tempMean <= 0) {
                globalEvents.add("FROST");
            }
            if ("rain".equals(condition) || "snow".equals(condition) || "thunder".equals(condition)) {
                globalEvents.add("AUTO_WATER");
            }
            wmap.put("tempMeanC", tempMean);
            wmap.put("events", globalEvents);
            w.put("weather", wmap);
        } else {
            w.put("weather", null);
        }

        // 猫(FR-36 情绪层):只睡空盆,位置 = f(节气, 时段, 已解锁场域),零存储确定性
        List<GardenCat.Candidate> catCandidates = new ArrayList<>();
        List<String> catAreas = new ArrayList<>();
        for (PlotVO pv : plots) {
            if (!catAreas.contains(pv.getVenue())) {
                catAreas.add(pv.getVenue());
            }
            if (pv.getCropCode() == null) {
                VenueDef vd = content.venue(pv.getVenue());
                boolean outdoor = !(vd != null && vd.indoor()) && !"GREENHOUSE".equals(pv.getVenue());
                catCandidates.add(new GardenCat.Candidate(pv.getId(), pv.getVenue(), pv.getSlotNo(), outdoor));
            }
        }
        String catEventCode = (String) w.get("eventCode");
        boolean weatherIndoor = "rain".equals(weatherCondition) || "snow".equals(weatherCondition)
                || "thunder".equals(weatherCondition);
        GardenCat.Position cat = GardenCat.position(world.getWorldId(), catCandidates, catAreas,
                today, now.getHour(), catEventCode, weatherIndoor);
        Map<String, Object> catMap = new LinkedHashMap<>();
        catMap.put("plotId", cat.plotId());
        catMap.put("venue", cat.venue());
        catMap.put("slotNo", cat.slotNo());
        catMap.put("posture", cat.posture());
        vo.setCat(catMap);

        fillInventory(vo, familyId);
        fillShop(vo, season);
        vo.setLetters(deskLetters(world, today));
        return vo;
    }

    /** 窗台影子快照(FR-33):登录者本人那间房;含空盆;不含 cropCode 与光照参数 */
    public Map<String, Object> silhouette(Long familyId, Long userId) {
        GardenWorld world = worldService.ensureWorld(familyId);
        GardenMember me = worldService.requireMember(familyId, userId);
        LocalDateTime now = LocalDateTime.now();
        String season = GardenSeason.seasonOf(now.toLocalDate());
        Map<String, String> nicknames = worldService.nicknames(familyId);
        GardenSolarEvents.Schedule events = growth.scheduleOf(familyId);

        List<Map<String, Object>> plots = new ArrayList<>();
        for (GardenPlot p : plotsOf(familyId)) {
            if (!"WINDOWSILL".equals(p.getVenue()) || !me.getPlayerUid().equals(p.getOwnerUid())) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("slotNo", p.getSlotNo());
            if (p.getCropCode() == null) {
                item.put("form", null);
                item.put("stage", 0);
                item.put("variant", null);
            } else {
                PlotVO pv = assembler.toVO(p, now, me.getPlayerUid(), nicknames, season, events, null);
                item.put("form", pv.getForm());
                item.put("stage", pv.getStage());
                item.put("variant", p.getVariant());
            }
            plots.add(item);
        }
        plots.sort(Comparator.comparingInt(m -> (int) m.get("slotNo")));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("world", world.getWorldId());
        out.put("venue", "WINDOWSILL");
        out.put("updatedAt", System.currentTimeMillis() / 1000);
        out.put("plots", plots);
        return out;
    }

    // ---------- 内部 ----------

    private List<GardenPlot> plotsOf(Long familyId) {
        return plotMapper.selectList(new LambdaQueryWrapper<GardenPlot>()
                .eq(GardenPlot::getFamilyId, familyId)
                .orderByAsc(GardenPlot::getVenue)
                .orderByAsc(GardenPlot::getSlotNo));
    }

    private void fillInventory(GardenPanoramaVO vo, Long familyId) {
        List<GardenInventory> rows = inventoryMapper.selectList(new LambdaQueryWrapper<GardenInventory>()
                .eq(GardenInventory::getFamilyId, familyId));
        List<GardenItemVO> seeds = new ArrayList<>();
        List<GardenItemVO> fruits = new ArrayList<>();
        for (GardenInventory r : rows) {
            GardenItemVO item = new GardenItemVO();
            item.setItemCode(r.getItemCode());
            item.setItemType(r.getItemType());
            item.setQty(r.getQty());
            if (r.getItemCode() != null && r.getItemCode().startsWith(GardenConst.SEED_PREFIX)) {
                item.setCropCode(r.getItemCode().substring(GardenConst.SEED_PREFIX.length()));
                seeds.add(item);
            } else {
                item.setCropCode(r.getItemCode());
                fruits.add(item);
            }
        }
        vo.setSeeds(seeds);
        vo.setFruits(fruits);
        vo.setInventoryUsed(inventoryMapper.sumQty(familyId));
        // 容量 = 90 + 20 × 地块行数(行数即已解锁地块,FR-13 现算不落库)+ 仓廪技能加成
        vo.setInventoryCap(GardenConst.WAREHOUSE_BASE + GardenConst.WAREHOUSE_PER_PLOT * plotMapper.countAll(familyId)
                + growth.warehouseBonus(familyId));
    }

    private void fillShop(GardenPanoramaVO vo, String season) {
        Map<String, Integer> packs = new HashMap<>();
        for (GardenItemVO s : vo.getSeeds()) {
            packs.merge(s.getCropCode(), s.getQty(), Integer::sum);
        }
        List<ShopItemVO> shop = new ArrayList<>();
        for (CropDef c : content.unlockedCrops()) {
            ShopItemVO item = new ShopItemVO();
            item.setCode(c.code());
            item.setName(c.name());
            item.setForm(c.form());
            item.setSeedPrice(c.seedPrice());
            item.setFruitPrice(c.fruitPrice());
            item.setInSeason(GardenSeason.inSeason(c.seasons(), season));
            item.setSeasonName(GardenSeason.isAllSeason(c.seasons()) ? null
                    : content.mail().seasonNames().get(season));
            item.setFirstPot(c.firstPot());
            item.setPackCount(packs.getOrDefault(c.code(), 0));
            shop.add(item);
        }
        // 排序:当季 → 平/逆季;同级按手里的包数倒序,再按 code(FR-28)
        shop.sort(Comparator
                .comparing((ShopItemVO s) -> !s.isInSeason())
                .thenComparing(s -> -s.getPackCount())
                .thenComparing(ShopItemVO::getCode));
        vo.setUnlockedCrops(shop);
    }

    /** 桌上的信:未拆(可拆/可补)+ 旧便签(可读无奖励,text 确定性推导) */
    private List<LetterVO> deskLetters(GardenWorld world, LocalDate today) {
        LocalDate cycleStart = cycleStartOf(world);
        LocalDate earliest = today.minusDays(GardenConst.MAIL_BACKFILL_DAYS);
        if (cycleStart != null && cycleStart.isAfter(earliest)) {
            earliest = cycleStart;
        }
        LocalDate from = world.getLastMailDate() == null ? earliest : world.getLastMailDate().plusDays(1);
        List<LetterVO> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            LetterVO vo = new LetterVO();
            vo.setDate(d.toString());
            vo.setClaimable(!d.isBefore(earliest));
            if (vo.isClaimable()) {
                // 未拆的信不带正文——拆信必须走 POST /garden/mail/open;首封(含初始礼包)在那一步按日期定
                vo.setType("PENDING");
            } else {
                GardenMail.Letter letter = GardenMail.deriveForDate(world.getWorldId(), d, cycleStart,
                        content.unlockedCrops(), content.mail());
                vo.setType(letter.type());
                vo.setText(letter.text());
                vo.setSignature(letter.signature());
            }
            out.add(vo);
        }
        return out;
    }

    /** 从 settings JSON 取 cycle_start_at:MySQL JSON 列会规范化空格,必须走 JSON 解析,不做裸字符串匹配 */
    @SuppressWarnings("unchecked")
    public LocalDate cycleStartOf(GardenWorld world) {
        String settings = world.getSettings();
        if (settings == null || settings.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(settings, Map.class);
            Object v = map.get(GardenConst.SETTINGS_CYCLE_START_KEY);
            return v == null ? null : LocalDate.parse(String.valueOf(v));
        } catch (Exception e) {
            log.warn("[garden] settings 解析失败 familyId={} settings={}", world.getId(), settings);
            return null;
        }
    }
}
