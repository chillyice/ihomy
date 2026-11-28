package com.ihomy.garden.core;

import com.ihomy.garden.GardenConst;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 天气事件(玩法稿 §8,详细设计《天气事件与气温双轨》§1/§2):霜冻 / 干旱 / 自动补水。
 * 判定全部读时推导(零存储):霜冻 = 当日日均温 ≤ 0(§8.1 日内恒定);干旱 = 当前晴 + 距上次浇水 > 48h;
 * 自动补水 = 当前雨/雪/雷。仅室外(室内/大棚免疫);不追溯历史——事件速率只作用于**今日 0 点起**的时段。
 * 优先级:FROST > DROUGHT > AUTO_WATER。
 *
 * <p>气温胁迫(§8.1 低温 ×0.6 / 高温 ×0.7)是**另一根轴**:它按作物耐温区间判定、日内恒定、与事件窗无关,
 * 所以不塞进 Event(rateBp),而是以独立的成长乘子交给 GardenGrowth——地块上可以同时「干旱 + 高温」,
 * 显示上各说各的(weatherEvent= DROUGHT,tempStress= HEAT_STRESS),玩家才知道该浇水还是该遮阴。
 */
public final class GardenWeatherEvents {

    /** 气温胁迫码(与 Event.code 同一套展示口径,前端按同一套语气给对策) */
    public static final String COLD_STRESS = "COLD_STRESS";
    public static final String HEAT_STRESS = "HEAT_STRESS";
    public static final String FROST = "FROST";
    public static final String DROUGHT = "DROUGHT";
    public static final String AUTO_WATER = "AUTO_WATER";

    private GardenWeatherEvents() {
    }

    /**
     * 事件视图:rateBp 是今日 0 点起的室外成长速率基点(0 = 冻结);since 恒为今日 0 点;
     * suppressThirsty = 天在浇水(室外 thirsty 恒 false);code ∈ FROST/DROUGHT/AUTO_WATER。
     */
    public record Event(int rateBp, LocalDateTime since, boolean suppressThirsty, String code) {
    }

    /** 干旱判定:当前晴 + 距上次浇水(未浇取播种)> 48h */
    public static Event evaluate(String condition, Integer tempMeanC, boolean outdoor,
                                 LocalDateTime waterRef, LocalDateTime now) {
        if (!outdoor) {
            return null; // 室内/大棚免疫
        }
        LocalDate today = now.toLocalDate();
        LocalDateTime since = today.atStartOfDay();
        // FROST:当日日均温 ≤ 0(冻结优先于一切)
        if (tempMeanC != null && tempMeanC <= 0) {
            return new Event(0, since, false, FROST);
        }
        // DROUGHT:当前晴 + 48h 未浇(浇水即解,读时推导)
        boolean dry48h = waterRef != null && waterRef.plusHours(48).isBefore(now);
        if ("clear".equals(condition) && dry48h) {
            return new Event(GardenConst.DROUGHT_RATE_BP, since, false, DROUGHT);
        }
        // AUTO_WATER:天在浇水(雨/雪/雷),室外不缺水
        if ("rain".equals(condition) || "snow".equals(condition) || "thunder".equals(condition)) {
            return new Event(100, since, true, AUTO_WATER);
        }
        return null;
    }

    /**
     * 气温胁迫乘子(§8.1,整数基点):日均温 < 耐寒下限 → 60(×0.6);日均温 > 耐热上限 → 70(×0.7);否则 100。
     * 仅室外生效(室内/大棚免疫由调用方把 outdoor=false 传进来);无温度数据 → 100(不判、不惩罚缺配置的家庭);
     * 日均温 ≤ 0 一律交 FROST(冻结比 ×0.6 更弱,不必叠加)。
     */
    public static int tempStressBp(Integer tempMeanC, Integer cropTempMinC, Integer cropTempMaxC,
                                   boolean outdoor) {
        if (!outdoor || tempMeanC == null || tempMeanC <= 0) {
            return 100;
        }
        if (cropTempMinC != null && tempMeanC < cropTempMinC) {
            return GardenConst.COLD_STRESS_BP;
        }
        if (cropTempMaxC != null && tempMeanC > cropTempMaxC) {
            return GardenConst.HEAT_STRESS_BP;
        }
        return 100;
    }

    /** 胁迫码(展示用;100 → null = 无胁迫) */
    public static String stressCode(int tempStressBp) {
        if (tempStressBp == GardenConst.COLD_STRESS_BP) {
            return COLD_STRESS;
        }
        if (tempStressBp == GardenConst.HEAT_STRESS_BP) {
            return HEAT_STRESS;
        }
        return null;
    }
}
