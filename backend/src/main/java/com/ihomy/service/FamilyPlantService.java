package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.PlantConst;
import com.ihomy.common.ResultCode;
import com.ihomy.common.UserNames;
import com.ihomy.dto.PlantDTO;
import com.ihomy.dto.PlantStateVO;
import com.ihomy.entity.Family;
import com.ihomy.entity.FamilyPlant;
import com.ihomy.entity.FamilyPlantLog;
import com.ihomy.entity.SysUser;
import com.ihomy.entity.SysUserRole;
import com.ihomy.mapper.FamilyMapper;
import com.ihomy.mapper.FamilyPlantLogMapper;
import com.ihomy.mapper.FamilyPlantMapper;
import com.ihomy.mapper.SysUserMapper;
import com.ihomy.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 家庭共养植物业务:全家一棵、实时养成。
 * 成长 = 种植耗时(×营养倍率,雨天自动补水不冻结) + 累计照料加成(浇水/晒太阳/施肥,已按当日天气修正)。
 * 营养随施肥次数实时推导(施肥后 100 随小时衰减至 20);天气影响照料加成与自动补水;
 * 照料/收获给积分、施肥扣积分;每次操作写入成长日志并通知其余成员(家庭内容 + 交互)。
 * 浇水/晒太阳/施肥带家庭级冷却,用条件 UPDATE 原子累加防并发/防刷。
 */
@Service
@RequiredArgsConstructor
public class FamilyPlantService {

    private final FamilyPlantMapper plantMapper;
    private final FamilyPlantLogMapper logMapper;
    private final FamilyMapper familyMapper;
    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final PointsService pointsService;
    private final WeatherService weatherService;
    private final NotificationService notificationService;

    public PlantStateVO state(Long familyId) {
        FamilyPlant p = findByFamily(familyId);
        if (p == null) {
            PlantStateVO empty = new PlantStateVO();
            empty.setPlanted(false);
            empty.setTotalStages(PlantConst.STAGES.length);
            empty.setLogs(List.of());
            return empty;
        }
        return buildState(p, weatherOf(familyId));
    }

    public PlantStateVO plant(Long userId, Long familyId, PlantDTO dto) {
        String species = normalizeSpecies(dto == null ? null : dto.getSpecies());
        if (findByFamily(familyId) != null) {
            throw new BizException(ResultCode.CONFLICT, "家庭已有植物在生长,收获后才能重新种植");
        }
        FamilyPlant p = new FamilyPlant();
        p.setFamilyId(familyId);
        p.setSpecies(species);
        p.setPlantedAt(LocalDateTime.now());
        p.setWaterCount(0);
        p.setSunCount(0);
        p.setFertilizeCount(0);
        p.setCareBoostMinutes(0);
        p.setHarvestCount(0);
        p.setCreatedBy(userId);
        plantMapper.insert(p);
        addLog(familyId, userId, "PLANT", message(dto), "种下了植物");
        notifyFamily(familyId, userId, "在家里种下了一棵植物");
        return state(familyId);
    }

    public PlantStateVO water(Long userId, Long familyId, PlantDTO dto) {
        return care(userId, familyId, true, dto);
    }

    public PlantStateVO sun(Long userId, Long familyId, PlantDTO dto) {
        return care(userId, familyId, false, dto);
    }

    public PlantStateVO fertilize(Long userId, Long familyId, PlantDTO dto) {
        require(familyId);
        if (pointsService.balance(userId) < PlantConst.FERTILIZE_POINTS_COST) {
            throw new BizException(ResultCode.INSUFFICIENT_POINTS, "积分不足,施肥需要 " + PlantConst.FERTILIZE_POINTS_COST + " 积分");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime boundary = now.minusMinutes(PlantConst.FERTILIZE_COOLDOWN_MINUTES);
        LambdaUpdateWrapper<FamilyPlant> uw = new LambdaUpdateWrapper<FamilyPlant>()
                .eq(FamilyPlant::getFamilyId, familyId)
                .and(w -> w.isNull(FamilyPlant::getLastFertilizedAt).or().lt(FamilyPlant::getLastFertilizedAt, boundary))
                .set(FamilyPlant::getLastFertilizedAt, now)
                .setSql("fertilize_count = fertilize_count + 1")
                .setSql("care_boost_minutes = care_boost_minutes + " + PlantConst.FERTILIZE_BOOST_MINUTES);
        int rows = plantMapper.update(null, uw);
        if (rows == 0) {
            FamilyPlant fresh = require(familyId);
            long remain = remainingCooldown(fresh.getLastFertilizedAt(), PlantConst.FERTILIZE_COOLDOWN_MINUTES);
            throw new BizException(ResultCode.CONFLICT, "施肥冷却中" + (remain > 0 ? ",还需 " + remain + " 分钟" : ""));
        }
        pointsService.addRecord(userId, familyId, "PLANT_FERTILIZE", -PlantConst.FERTILIZE_POINTS_COST, "给植物施肥");
        addLog(familyId, userId, "FERTILIZE", message(dto), "施了肥,营养拉满");
        notifyFamily(familyId, userId, "给家里的植物施了肥");
        return state(familyId);
    }

    public PlantStateVO harvest(Long userId, Long familyId, PlantDTO dto) {
        FamilyPlant p = require(familyId);
        String condition = conditionOf(familyId);
        if (!PlantConst.STAGE_FRUIT.equals(currentStage(p, condition))) {
            throw new BizException(ResultCode.CONFLICT, "植物尚未成熟,还不能收获");
        }
        String species = p.getSpecies();
        if (dto != null && dto.getSpecies() != null && !dto.getSpecies().isBlank()) {
            species = normalizeSpecies(dto.getSpecies());
        }
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<FamilyPlant> uw = new LambdaUpdateWrapper<FamilyPlant>()
                .eq(FamilyPlant::getFamilyId, familyId)
                .set(FamilyPlant::getSpecies, species)
                .set(FamilyPlant::getPlantedAt, now)
                .set(FamilyPlant::getLastWateredAt, null)
                .set(FamilyPlant::getLastSunAt, null)
                .set(FamilyPlant::getLastFertilizedAt, null)
                .set(FamilyPlant::getWaterCount, 0)
                .set(FamilyPlant::getSunCount, 0)
                .set(FamilyPlant::getFertilizeCount, 0)
                .set(FamilyPlant::getCareBoostMinutes, 0)
                .setSql("harvest_count = harvest_count + 1");
        plantMapper.update(null, uw);
        pointsService.addRecord(userId, familyId, "PLANT_HARVEST", PlantConst.POINTS_HARVEST, "收获植物");
        addLog(familyId, userId, "HARVEST", message(dto), "收获了成熟的植物,开启新的一轮");
        notifyFamily(familyId, userId, "收获了家里的植物");
        return state(familyId);
    }

    // ---------- 照料(浇水/晒太阳) ----------

    /** 浇水/晒太阳:家庭级冷却,条件 UPDATE 原子累加;加成按当日天气修正;奖励积分并通知成员 */
    private PlantStateVO care(Long userId, Long familyId, boolean isWater, PlantDTO dto) {
        require(familyId);
        String condition = conditionOf(familyId);
        long boost = Math.round(PlantConst.CARE_BOOST_MINUTES * PlantConst.weatherCareFactor(condition, isWater));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime boundary = now.minusMinutes(PlantConst.CARE_COOLDOWN_MINUTES);
        LambdaUpdateWrapper<FamilyPlant> uw = new LambdaUpdateWrapper<FamilyPlant>()
                .eq(FamilyPlant::getFamilyId, familyId);
        if (isWater) {
            uw.and(w -> w.isNull(FamilyPlant::getLastWateredAt).or().lt(FamilyPlant::getLastWateredAt, boundary))
                    .set(FamilyPlant::getLastWateredAt, now)
                    .setSql("water_count = water_count + 1");
        } else {
            uw.and(w -> w.isNull(FamilyPlant::getLastSunAt).or().lt(FamilyPlant::getLastSunAt, boundary))
                    .set(FamilyPlant::getLastSunAt, now)
                    .setSql("sun_count = sun_count + 1");
        }
        uw.setSql("care_boost_minutes = care_boost_minutes + " + boost);
        int rows = plantMapper.update(null, uw);
        if (rows == 0) {
            FamilyPlant fresh = require(familyId);
            LocalDateTime last = isWater ? fresh.getLastWateredAt() : fresh.getLastSunAt();
            long remain = remainingCooldown(last);
            throw new BizException(ResultCode.CONFLICT,
                    (isWater ? "浇水冷却中" : "晒太阳冷却中") + (remain > 0 ? ",还需 " + remain + " 分钟" : ""));
        }
        int pts = isWater ? PlantConst.POINTS_WATER : PlantConst.POINTS_SUN;
        pointsService.addRecord(userId, familyId, isWater ? "PLANT_WATER" : "PLANT_SUN", pts,
                isWater ? "给植物浇水" : "给植物晒太阳");
        addLog(familyId, userId, isWater ? "WATER" : "SUN", message(dto), isWater ? "浇了水" : "晒了太阳");
        notifyFamily(familyId, userId, isWater ? "给家里的植物浇了水" : "带家里的植物晒了太阳");
        return state(familyId);
    }

    // ---------- 成长/营养/天气推导 ----------

    private FamilyPlant findByFamily(Long familyId) {
        return plantMapper.selectOne(new LambdaQueryWrapper<FamilyPlant>()
                .eq(FamilyPlant::getFamilyId, familyId));
    }

    private FamilyPlant require(Long familyId) {
        FamilyPlant p = findByFamily(familyId);
        if (p == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return p;
    }

    private String normalizeSpecies(String species) {
        return PlantConst.isSpecies(species) ? species.toUpperCase() : PlantConst.SPECIES_SUNFLOWER;
    }

    /** 家庭当前天气 condition(clear/cloud/rain/snow/fog/thunder);无凭证/未配置返回 null */
    private String conditionOf(Long familyId) {
        Map<String, Object> weather = weatherOf(familyId);
        return weather == null ? null : (String) weather.get("condition");
    }

    private Map<String, Object> weatherOf(Long familyId) {
        try {
            Family f = familyMapper.selectById(familyId);
            String[] loc = null;
            if (f != null && f.getWeatherLat() != null && f.getWeatherLng() != null) {
                loc = new String[]{f.getWeatherLat().toPlainString(), f.getWeatherLng().toPlainString(), f.getWeatherCity()};
            }
            return weatherService.getWeather(null, loc);
        } catch (Exception e) {
            return null;
        }
    }

    private long growthMinutes(FamilyPlant p, String condition) {
        int nutrient = PlantConst.nutrientLevel(nz(p.getFertilizeCount()), p.getLastFertilizedAt());
        long elapsed = effectiveElapsedMinutes(p, condition);
        long rateApplied = Math.round(elapsed * PlantConst.nutrientRate(nutrient));
        return rateApplied + nz(p.getCareBoostMinutes());
    }

    /** 累计成长的「时间」部分:枯萎(距上次浇水超 WILT 分钟)后不再累计时间;雨天/雷雨/下雪自动补水不冻结 */
    private long effectiveElapsedMinutes(FamilyPlant p, String condition) {
        if (p.getPlantedAt() == null) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        if (PlantConst.isRainWatering(condition)) {
            return Math.max(0, Duration.between(p.getPlantedAt(), now).toMinutes());
        }
        LocalDateTime waterRef = p.getLastWateredAt() != null ? p.getLastWateredAt() : p.getPlantedAt();
        LocalDateTime wiltAt = waterRef.plusMinutes(PlantConst.WILT_MINUTES);
        LocalDateTime end = wiltAt.isBefore(now) ? wiltAt : now;
        return Math.max(0, Duration.between(p.getPlantedAt(), end).toMinutes());
    }

    private String currentStage(FamilyPlant p, String condition) {
        return PlantConst.stageForGrowthMinutes(growthMinutes(p, condition));
    }

    private PlantStateVO buildState(FamilyPlant p, Map<String, Object> weather) {
        String cond = weather == null ? null : (String) weather.get("condition");
        LocalDateTime now = LocalDateTime.now();
        int nutrient = PlantConst.nutrientLevel(nz(p.getFertilizeCount()), p.getLastFertilizedAt());
        long gm = growthMinutes(p, cond);
        String stage = PlantConst.stageForGrowthMinutes(gm);
        int idx = PlantConst.stageIndex(stage);

        PlantStateVO vo = new PlantStateVO();
        vo.setPlanted(true);
        vo.setSpecies(p.getSpecies());
        vo.setStage(stage);
        vo.setStageIndex(idx);
        vo.setTotalStages(PlantConst.STAGES.length);
        vo.setProgressPct(progressPct(gm, idx));
        vo.setGrowthMinutes(gm);
        vo.setMinutesToNextStage(PlantConst.minutesToNextStage(gm));
        vo.setDaysGrown(p.getPlantedAt() == null ? 0 : Math.max(0, Duration.between(p.getPlantedAt(), now).toDays()));
        vo.setWaterCount(nz(p.getWaterCount()));
        vo.setSunCount(nz(p.getSunCount()));
        vo.setFertilizeCount(nz(p.getFertilizeCount()));
        vo.setHarvestCount(nz(p.getHarvestCount()));
        vo.setPlantedAt(p.getPlantedAt());
        vo.setLastWateredAt(p.getLastWateredAt());
        vo.setLastSunAt(p.getLastSunAt());
        vo.setLastFertilizedAt(p.getLastFertilizedAt());

        long wc = remainingCooldown(p.getLastWateredAt());
        long sc = remainingCooldown(p.getLastSunAt());
        long fc = remainingCooldown(p.getLastFertilizedAt(), PlantConst.FERTILIZE_COOLDOWN_MINUTES);
        vo.setWaterReady(wc == 0);
        vo.setSunReady(sc == 0);
        vo.setFertilizeReady(fc == 0);
        vo.setWaterCooldownMin(wc);
        vo.setSunCooldownMin(sc);
        vo.setFertilizeCooldownMin(fc);

        boolean raining = PlantConst.isRainWatering(cond);
        LocalDateTime waterRef = p.getLastWateredAt() != null ? p.getLastWateredAt() : p.getPlantedAt();
        long sinceWater = waterRef == null ? 0 : Math.max(0, Duration.between(waterRef, now).toMinutes());
        vo.setThirsty(!raining && sinceWater > PlantConst.THIRSTY_MINUTES);
        vo.setWilted(!raining && sinceWater > PlantConst.WILT_MINUTES);
        vo.setHarvestable(PlantConst.STAGE_FRUIT.equals(stage));

        vo.setNutrient(nutrient);
        vo.setNutrientLevel(nutrientLevel(nutrient));
        vo.setWeather(weather);
        vo.setRainWatering(raining);
        vo.setWeatherWaterFactor(PlantConst.weatherCareFactor(cond, true));
        vo.setWeatherSunFactor(PlantConst.weatherCareFactor(cond, false));
        vo.setLogs(recentLogs(p.getFamilyId()));
        return vo;
    }

    private int progressPct(long gm, int idx) {
        long[] t = PlantConst.thresholds();
        if (idx >= t.length - 1) {
            return 100;
        }
        long span = t[idx + 1] - t[idx];
        if (span <= 0) {
            return 100;
        }
        long pct = (gm - t[idx]) * 100 / span;
        return (int) Math.max(0, Math.min(100, pct));
    }

    /** 剩余冷却分钟(无记录或已就绪返回 0) */
    private long remainingCooldown(LocalDateTime last) {
        return remainingCooldown(last, PlantConst.CARE_COOLDOWN_MINUTES);
    }

    private long remainingCooldown(LocalDateTime last, long cooldownMin) {
        if (last == null) {
            return 0;
        }
        LocalDateTime readyAt = last.plusMinutes(cooldownMin);
        LocalDateTime now = LocalDateTime.now();
        if (!readyAt.isAfter(now)) {
            return 0;
        }
        long secs = Duration.between(now, readyAt).getSeconds();
        return (secs + 59) / 60; // 向上取整,避免倒计时出现 0 却仍不可用
    }

    private String nutrientLevel(int nutrient) {
        if (nutrient >= 75) {
            return "HIGH";
        }
        if (nutrient >= 40) {
            return "MEDIUM";
        }
        return "LOW";
    }

    // ---------- 家庭内容 / 交互 ----------

    private void addLog(Long familyId, Long userId, String action, String message, String detail) {
        FamilyPlantLog log = new FamilyPlantLog();
        log.setFamilyId(familyId);
        log.setUserId(userId);
        log.setAction(action);
        log.setMessage(message);
        log.setDetail(detail);
        logMapper.insert(log);
    }

    /** 最近成长日志(倒序 20 条,批量回填成员昵称/头像) */
    private List<Map<String, Object>> recentLogs(Long familyId) {
        List<FamilyPlantLog> logs = logMapper.selectList(new LambdaQueryWrapper<FamilyPlantLog>()
                .eq(FamilyPlantLog::getFamilyId, familyId)
                .orderByDesc(FamilyPlantLog::getCreatedAt)
                .last("LIMIT 20"));
        if (logs.isEmpty()) {
            return List.of();
        }
        List<Long> ids = logs.stream().map(FamilyPlantLog::getUserId).distinct().collect(Collectors.toList());
        Map<Long, SysUser> userMap = ids.isEmpty() ? Map.of()
                : sysUserMapper.selectBatchIds(ids).stream()
                        .collect(Collectors.toMap(SysUser::getId, u -> u));
        List<Map<String, Object>> out = new ArrayList<>(logs.size());
        for (FamilyPlantLog l : logs) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", l.getId());
            m.put("action", l.getAction());
            m.put("message", l.getMessage());
            m.put("detail", l.getDetail());
            m.put("createdAt", l.getCreatedAt());
            SysUser u = userMap.get(l.getUserId());
            m.put("nickname", UserNames.of(u));
            m.put("avatar", u != null ? u.getAvatar() : null);
            out.add(m);
        }
        return out;
    }

    /** 通知家庭成员(排除操作者):站内通知「XX 给家里的植物做了某事」 */
    private void notifyFamily(Long familyId, Long actorId, String actionText) {
        try {
            SysUser actor = sysUserMapper.selectById(actorId);
            String actorName = UserNames.of(actor);
            List<Long> memberIds = sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                            .eq(SysUserRole::getFamilyId, familyId)
                            .select(SysUserRole::getUserId))
                    .stream().map(SysUserRole::getUserId).distinct().toList();
            for (Long uid : memberIds) {
                if (uid.equals(actorId)) {
                    continue;
                }
                notificationService.create(uid, "plant", (actorName != null ? actorName : "家人") + " " + actionText,
                        familyId, "plant", familyId);
            }
        } catch (Exception ignored) {
            // 通知失败不影响主流程
        }
    }

    private String message(PlantDTO dto) {
        if (dto == null || dto.getMessage() == null || dto.getMessage().isBlank()) {
            return null;
        }
        String m = dto.getMessage().trim();
        return m.length() > 200 ? m.substring(0, 200) : m;
    }

    private int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
