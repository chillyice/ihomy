package com.ihomy.garden.core;

import com.ihomy.garden.GardenConst;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 成长纯函数(玩法稿 §10 的最小闭环子集 + §14.4 节气事件)。
 * 累计成长分钟 = 现实经过分钟(缺水后 ×0.7、枯萎冻结)× 气温胁迫 × 土壤匹配 × 季节倍率 × 备盆修正 + 累计照料加成,
 * 其中「现实经过分钟」按**事件窗分段积分**:每段速率 = 事件 bp × 缺水 bp × 枯萎 bp(详细设计 §2)。
 * 全程整数运算(基点),无浮点、无随机、不取 now()(NFR-01/02)。
 * 兼容口径:单段且事件 bp=100(空窗)时与旧「全程一次乘」逐位一致。
 */
public final class GardenGrowth {

    private GardenGrowth() {
    }

    /** 成长推导结果视图 */
    public record GrowthView(long progressMinutes, int stageIndex, String stageName, int ratioBp,
                             boolean harvestable, boolean thirsty, long minutesToHarvest,
                             long minutesToNextStage, int soilBp, int seasonBp, int preparedBp) {
    }

    /**
     * @param events 节气事件排期(可 null = 无事件,等价空窗);由调用方按世界设置解析,核心不取时钟
     */
    public static GrowthView evaluate(int durationHours, List<String> soilPreference, List<String> soilCompatible,
                                      String hardiness, List<String> seasons, int seasonMultBp, int offSeasonMultBp,
                                      String season, String venueCode, boolean indoor, boolean prepared,
                                      String soilType, LocalDateTime plantedAt, LocalDateTime lastWateredAt,
                                      int careBoostMinutes, LocalDateTime now) {
        return evaluate(durationHours, soilPreference, soilCompatible, hardiness, seasons, seasonMultBp,
                offSeasonMultBp, season, venueCode, indoor, prepared, soilType, plantedAt, lastWateredAt,
                careBoostMinutes, now, null, null);
    }

    /** 带节气事件、无天气事件的旧路径(兼容) */
    public static GrowthView evaluate(int durationHours, List<String> soilPreference, List<String> soilCompatible,
                                      String hardiness, List<String> seasons, int seasonMultBp, int offSeasonMultBp,
                                      String season, String venueCode, boolean indoor, boolean prepared,
                                      String soilType, LocalDateTime plantedAt, LocalDateTime lastWateredAt,
                                      int careBoostMinutes, LocalDateTime now, GardenSolarEvents.Schedule events) {
        return evaluate(durationHours, soilPreference, soilCompatible, hardiness, seasons, seasonMultBp,
                offSeasonMultBp, season, venueCode, indoor, prepared, soilType, plantedAt, lastWateredAt,
                careBoostMinutes, now, events, null);
    }

    /**
     * @param weatherEvent 天气事件(FROST 冻结 / DROUGHT 干旱 ×0.7 / AUTO_WATER 天在浇水;可 null)。
     *                     事件速率只作用于**今日 0 点起**的时段(不追溯历史天气);事件段内替代内部缺水因子,枯萎冻结照旧。
     */
    public static GrowthView evaluate(int durationHours, List<String> soilPreference, List<String> soilCompatible,
                                      String hardiness, List<String> seasons, int seasonMultBp, int offSeasonMultBp,
                                      String season, String venueCode, boolean indoor, boolean prepared,
                                      String soilType, LocalDateTime plantedAt, LocalDateTime lastWateredAt,
                                      int careBoostMinutes, LocalDateTime now, GardenSolarEvents.Schedule events,
                                      GardenWeatherEvents.Event weatherEvent) {
        return evaluate(durationHours, soilPreference, soilCompatible, hardiness, seasons, seasonMultBp,
                offSeasonMultBp, season, venueCode, indoor, prepared, soilType, plantedAt, lastWateredAt,
                careBoostMinutes, now, events, weatherEvent, 100);
    }

    /**
     * @param tempStressBp 气温胁迫乘子(玩法稿 §8.1:低温 ×0.6 = 60 / 高温 ×0.7 = 70 / 无胁迫 = 100)。
     *                     日内恒定(日均温当天不变),因此不参与事件窗分段,直接乘在「现实经过分钟」上;
     *                     与天气事件互不替代——室外可以既干旱又高温,两条轴各扣一次(可逆、看得见)。
     */
    public static GrowthView evaluate(int durationHours, List<String> soilPreference, List<String> soilCompatible,
                                      String hardiness, List<String> seasons, int seasonMultBp, int offSeasonMultBp,
                                      String season, String venueCode, boolean indoor, boolean prepared,
                                      String soilType, LocalDateTime plantedAt, LocalDateTime lastWateredAt,
                                      int careBoostMinutes, LocalDateTime now, GardenSolarEvents.Schedule events,
                                      GardenWeatherEvents.Event weatherEvent, int tempStressBp) {
        long durationMin = durationHours * 60L;
        LocalDateTime base = plantedAt == null ? now : plantedAt;
        if (now.isBefore(base)) {
            now = base; // 时钟回拨防御:进度不倒退
        }
        LocalDateTime waterRef = lastWateredAt == null ? base : lastWateredAt;
        if (waterRef.isBefore(base)) {
            waterRef = base;
        }
        // 盛夏缺水阈值减半(向上取整小时);梅雨窗天公浇水
        int thirstyDivisor = events == null ? 1 : Math.max(1, events.thirstyDivisor(now));
        long thirstyHours = (GardenConst.thirstyHours(hardiness) + thirstyDivisor - 1) / thirstyDivisor;
        LocalDateTime thirstyAt = waterRef.plusHours(thirstyHours);
        LocalDateTime wiltAt = waterRef.plusHours(GardenConst.wiltHours(hardiness));
        boolean noWaterNow = events != null && events.noWater(now);
        boolean weatherWaters = weatherEvent != null && weatherEvent.suppressThirsty();
        boolean thirsty = !noWaterNow && !weatherWaters && now.isAfter(thirstyAt);

        // 室外判定:非室内且非大棚(大棚对休眠/寒潮免疫,与 seasonFactor 同口径)
        boolean sheltered = indoor || "GREENHOUSE".equals(venueCode);

        long progressRaw = segmentProgress(base, now, thirstyAt, wiltAt, sheltered, events, weatherEvent);
        long progressRawMin = progressRaw / 100; // 事件/缺水 bp 折回分钟(向下取整)

        int stressBp = tempStressBp <= 0 ? 100 : tempStressBp;
        int soilBp = soilFactor(soilPreference, soilCompatible, soilType);
        int seasonBp = seasonFactor(seasons, seasonMultBp, offSeasonMultBp, season, venueCode, indoor);
        int preparedBp = prepared ? GardenConst.PREPARED_BP : GardenConst.UNPREPARED_BP;

        long grown = progressRawMin;
        grown = grown * stressBp / 100;     // 气温胁迫:日均温越界,室外整体慢下来(§8.1)
        grown = grown * soilBp / 100;
        grown = grown * seasonBp / 100;
        grown = grown * preparedBp / 100;
        long progress = grown + Math.max(0, careBoostMinutes);

        int ratioBp = durationMin <= 0 ? 10000 : (int) Math.min(Integer.MAX_VALUE / 2, progress * 10000 / durationMin);
        int stageIndex = 0;
        for (int i = 0; i < GardenConst.STAGE_THRESHOLDS_BP.length; i++) {
            if (ratioBp >= GardenConst.STAGE_THRESHOLDS_BP[i]) {
                stageIndex = i;
            }
        }
        boolean harvestable = progress >= durationMin;
        long minutesToHarvest = harvestable ? 0
                : forecastMinutes(now, durationMin - progress, sheltered, events, weatherEvent, stressBp);
        long minutesToNextStage = 0;
        if (!harvestable && stageIndex < GardenConst.STAGE_THRESHOLDS_BP.length - 1) {
            int nextBp = GardenConst.STAGE_THRESHOLDS_BP[stageIndex + 1];
            long needed = (nextBp * durationMin + 9999) / 10000;
            minutesToNextStage = Math.max(0, needed - progress);
        }
        return new GrowthView(progress, stageIndex, GardenConst.STAGE_NAMES[stageIndex], ratioBp,
                harvestable, thirsty, minutesToHarvest, minutesToNextStage, soilBp, seasonBp, preparedBp);
    }

    /**
     * [base, now] 的分段积分(基点分钟):Σ 段分钟 × 事件bp × 缺水bp × 枯萎bp。
     * 边界 = 事件窗边界 ∪ {thirstyAt, wiltAt};段首定事件与缺水状态(段不跨任何边界)。
     */
    private static long segmentProgress(LocalDateTime base, LocalDateTime now,
                                        LocalDateTime thirstyAt, LocalDateTime wiltAt,
                                        boolean sheltered, GardenSolarEvents.Schedule events,
                                        GardenWeatherEvents.Event weatherEvent) {
        List<LocalDateTime> edges = new ArrayList<>();
        edges.add(base);
        if (events != null) {
            LocalDateTime t = base;
            int guard = 0;
            while (t.isBefore(now) && guard++ < 400) {
                t = events.nextBoundary(t);
                if (!t.isBefore(now)) {
                    break;
                }
                edges.add(t);
            }
        }
        if (thirstyAt.isAfter(base) && thirstyAt.isBefore(now)) {
            edges.add(thirstyAt);
        }
        if (wiltAt.isAfter(base) && wiltAt.isBefore(now)) {
            edges.add(wiltAt);
        }
        if (weatherEvent != null && weatherEvent.since().isAfter(base) && weatherEvent.since().isBefore(now)) {
            edges.add(weatherEvent.since()); // 天气事件边界(今日 0 点):事件速率只作用于今日时段
        }
        edges.add(now);
        edges.sort(LocalDateTime::compareTo);

        long sumBpMinutes = 0;
        for (int i = 0; i < edges.size() - 1; i++) {
            LocalDateTime a = edges.get(i);
            LocalDateTime b = edges.get(i + 1);
            if (a.isBefore(base)) {
                a = base;
            }
            if (b.isBefore(a)) {
                continue;
            }
            long minutes = minutesBetween(a, b);
            if (minutes <= 0) {
                continue;
            }
            int rateBp = 100;
            if (events != null) {
                rateBp = events.growthBp(a, sheltered);
            }
            if (!a.isBefore(wiltAt)) {
                rateBp = 0;                       // 枯萎冻结:进度停走(FR-10)
            } else if (weatherEvent != null && !a.isBefore(weatherEvent.since())) {
                // 天气事件段(今日 0 点起):事件速率替代内部缺水因子(干旱就是缺水的天气面,不双罚);枯萎照旧冻结
                rateBp = rateBp * weatherEvent.rateBp() / 100;
            } else if (!a.isBefore(thirstyAt) && (events == null || !events.noWater(a))) {
                rateBp = rateBp * GardenConst.DROUGHT_RATE_BP / 100; // 缺水 ×0.7(梅雨窗雨替人浇,不罚)
            }
            sumBpMinutes += minutes * rateBp;
        }
        return sumBpMinutes;
    }

    /** 前瞻「还要长多少分钟」:按未来事件窗逐窗消耗(不预测未来浇水/缺水,与旧口径同简化) */
    private static long forecastMinutes(LocalDateTime now, long remaining, boolean sheltered,
                                        GardenSolarEvents.Schedule events, GardenWeatherEvents.Event weatherEvent,
                                        int tempStressBp) {
        // 气温胁迫日内恒定:先把「还差的进度」折算成需要的现实分钟(向上取整),再走事件窗
        if (tempStressBp > 0 && tempStressBp < 100) {
            remaining = (remaining * 100 + tempStressBp - 1) / tempStressBp;
        }
        long[] minutesLeft = {0};
        // 今日剩余的天气事件段先行(霜冻停走 / 干旱 ×0.7,至明日 0 点),之后回节气速率
        if (weatherEvent != null) {
            LocalDateTime eventEnd = weatherEvent.since().plusDays(1);
            if (now.isBefore(eventEnd)) {
                int solarBp = events == null ? 100 : events.growthBp(now, sheltered);
                int bp = solarBp * weatherEvent.rateBp() / 100;
                long windowMin = minutesBetween(now, eventEnd);
                if (bp <= 0) {
                    minutesLeft[0] += windowMin; // 冻结:时间照走,进度不动
                    now = eventEnd;
                } else if ((long) windowMin * bp / 100 >= remaining) {
                    minutesLeft[0] += (remaining * 100 + bp - 1) / bp;
                    return minutesLeft[0];
                } else {
                    minutesLeft[0] += windowMin;
                    remaining -= (long) windowMin * bp / 100;
                    now = eventEnd;
                }
            }
        }
        if (events == null) {
            return minutesLeft[0] + remaining;
        }
        LocalDateTime t = now;
        int guard = 0;
        while (remaining > 0 && guard++ < 200) {
            LocalDateTime edge = events.nextBoundary(t);
            int bp = events.growthBp(t, sheltered);
            long windowMin = minutesBetween(t, edge);
            if (bp <= 0) {
                minutesLeft[0] += windowMin; // 冻结窗:时间照走,进度不动
                t = edge;
                continue;
            }
            long progressInWindow = windowMin * bp / 100;
            if (progressInWindow >= remaining) {
                minutesLeft[0] += (remaining * 100 + bp - 1) / bp; // 向上取分钟
                remaining = 0;
            } else {
                minutesLeft[0] += windowMin;
                remaining -= progressInWindow;
                t = edge;
            }
        }
        return minutesLeft[0];
    }

    /** 土壤匹配基点(§0.7:偏好 ×1.2 / 兼容 ×1.0 / 错配 ×0.7) */
    public static int soilFactor(List<String> preference, List<String> compatible, String soilType) {
        if (soilType == null) {
            return GardenConst.SOIL_COMPATIBLE_BP;
        }
        if (preference != null && preference.contains(soilType)) {
            return GardenConst.SOIL_PREFERRED_BP;
        }
        if (compatible != null && compatible.contains(soilType)) {
            return GardenConst.SOIL_COMPATIBLE_BP;
        }
        return GardenConst.SOIL_MISMATCH_BP;
    }

    /** 季节倍率基点(§7:本命 1.0~1.3 / 逆季 0.2~0.4;室内软化取下限;大棚免疫) */
    public static int seasonFactor(List<String> seasons, int seasonMultBp, int offSeasonMultBp,
                                   String season, String venueCode, boolean indoor) {
        if ("GREENHOUSE".equals(venueCode)) {
            return GardenConst.GREENHOUSE_SEASON_BP;
        }
        if (GardenSeason.isAllSeason(seasons) || GardenSeason.inSeason(seasons, season)) {
            return seasonMultBp;
        }
        int off = offSeasonMultBp;
        if (indoor) {
            int floor = "TERRARIUM".equals(venueCode)
                    ? GardenConst.TERRARIUM_OFF_SEASON_FLOOR_BP
                    : GardenConst.INDOOR_OFF_SEASON_FLOOR_BP;
            off = Math.max(off, floor);
        }
        return off;
    }

    /** 收获产量(§10:区间中值 × 土壤匹配,整数四舍五入) */
    public static int yieldAmount(int yieldMin, int yieldMax, int soilBp) {
        int sum = Math.max(0, yieldMin) + Math.max(0, yieldMax);
        return (sum * soilBp + 100) / 200;
    }

    /** 参考卖价(§7:错季 ×0.5;收获入账 FR-15 用;节气卖价事件由调用方再乘 priceBp) */
    public static int sellPrice(int fruitPrice, boolean inSeason) {
        if (inSeason) {
            return fruitPrice;
        }
        return (fruitPrice * GardenConst.OFF_SEASON_PRICE_BP + 50) / 100;
    }

    private static long minutesBetween(LocalDateTime a, LocalDateTime b) {
        if (b.isBefore(a)) {
            return 0;
        }
        return Duration.between(a, b).toMinutes();
    }
}
