package com.ihomy.common;

import java.util.Set;

/**
 * 植物养殖常量(V9.67→V9.68):品种/阶段/成长时长/照料加成/冷却/营养/天气/积分。
 * 阶段、营养均由「种植耗时 + 照料加成 + 营养/天气修正」实时推导,不落库。
 */
public final class PlantConst {

    private PlantConst() {
    }

    /** 品种 species */
    public static final String SPECIES_SUNFLOWER = "SUNFLOWER";
    public static final String SPECIES_ROSE = "ROSE";
    public static final String SPECIES_SUCCULENT = "SUCCULENT";
    public static final Set<String> SPECIES = Set.of(SPECIES_SUNFLOWER, SPECIES_ROSE, SPECIES_SUCCULENT);

    /** 成长阶段 stage(顺序固定) */
    public static final String STAGE_SEED = "SEED";
    public static final String STAGE_SPROUT = "SPROUT";
    public static final String STAGE_SEEDLING = "SEEDLING";
    public static final String STAGE_BUD = "BUD";
    public static final String STAGE_FLOWER = "FLOWER";
    public static final String STAGE_FRUIT = "FRUIT";
    public static final String[] STAGES = {STAGE_SEED, STAGE_SPROUT, STAGE_SEEDLING, STAGE_BUD, STAGE_FLOWER, STAGE_FRUIT};

    /** 各阶段所需累计成长分钟阈值(与 STAGES 一一对应):0h/6h/24h/48h/72h/96h */
    private static final long[] THRESHOLDS = {0, 360, 1440, 2880, 4320, 5760};

    /** 每次照料(浇水/晒太阳)的基础成长加成(分钟) */
    public static final long CARE_BOOST_MINUTES = 120;

    /** 每次施肥的成长加成(分钟) */
    public static final long FERTILIZE_BOOST_MINUTES = 360;

    /** 浇水/晒太阳的家庭级冷却(分钟) */
    public static final long CARE_COOLDOWN_MINUTES = 360;

    /** 施肥的家庭级冷却(分钟) */
    public static final long FERTILIZE_COOLDOWN_MINUTES = 720;

    /** 缺水/枯萎阈值(分钟):超过未浇水即提示 */
    public static final long THIRSTY_MINUTES = 48L * 60;
    public static final long WILT_MINUTES = 72L * 60;

    /** 营养:施肥后从 100 线性衰减到 20(用时 NUTRIENT_DECAY_HOURS);从未施肥的基线为 50 */
    public static final int NUTRIENT_MAX = 100;
    public static final int NUTRIENT_FLOOR = 20;
    public static final int NUTRIENT_BASELINE = 50;
    public static final long NUTRIENT_DECAY_HOURS = 72;

    /** 积分:施肥消耗 / 照料奖励 / 收获奖励 */
    public static final int FERTILIZE_POINTS_COST = 15;
    public static final int POINTS_WATER = 2;
    public static final int POINTS_SUN = 2;
    public static final int POINTS_HARVEST = 30;

    public static boolean isSpecies(String species) {
        return species != null && SPECIES.contains(species.toUpperCase());
    }

    public static long[] thresholds() {
        return THRESHOLDS.clone();
    }

    public static String stageForGrowthMinutes(long minutes) {
        String stage = STAGE_SEED;
        for (int i = 0; i < THRESHOLDS.length; i++) {
            if (minutes >= THRESHOLDS[i]) {
                stage = STAGES[i];
            }
        }
        return stage;
    }

    public static int stageIndex(String stage) {
        for (int i = 0; i < STAGES.length; i++) {
            if (STAGES[i].equals(stage)) {
                return i;
            }
        }
        return 0;
    }

    /** 到下一阶段还需的成长分钟(已到最终阶段返回 0) */
    public static long minutesToNextStage(long growthMinutes) {
        int idx = stageIndex(stageForGrowthMinutes(growthMinutes));
        if (idx >= THRESHOLDS.length - 1) {
            return 0;
        }
        return Math.max(0, THRESHOLDS[idx + 1] - growthMinutes);
    }

    /**
     * 营养等级(0-100):从未施肥为基线 50;施肥后从 100 随小时线性衰减到 FLOOR。
     */
    public static int nutrientLevel(int fertilizeCount, java.time.LocalDateTime lastFertilizedAt) {
        if (fertilizeCount <= 0 || lastFertilizedAt == null) {
            return NUTRIENT_BASELINE;
        }
        long hours = Math.max(0, java.time.Duration.between(lastFertilizedAt, java.time.LocalDateTime.now()).toMinutes() / 60);
        long decay = NUTRIENT_MAX - hours * (NUTRIENT_MAX - NUTRIENT_FLOOR) / NUTRIENT_DECAY_HOURS;
        return (int) Math.max(NUTRIENT_FLOOR, Math.min(NUTRIENT_MAX, decay));
    }

    /** 营养 → 成长速率倍率(乘到时间累计):0→0.7 / 50→1.0 / 100→1.3 */
    public static double nutrientRate(int nutrient) {
        return 0.7 + nutrient * 0.006;
    }

    /**
     * 天气 → 照料加成倍率(乘到浇水/晒太阳的加成上)。
     * 晴天使劲晒、雨天浇水事半功倍;阴雨/严寒晒太阳效果打折。
     */
    public static double weatherCareFactor(String condition, boolean isWater) {
        if (condition == null) {
            return 1.0;
        }
        return switch (condition) {
            case "clear" -> isWater ? 1.0 : 1.5;
            case "cloud" -> 1.0;
            case "rain" -> isWater ? 1.5 : 0.5;
            case "snow" -> isWater ? 0.8 : 0.3;
            case "fog" -> isWater ? 0.9 : 0.7;
            case "thunder" -> isWater ? 1.3 : 0.4;
            default -> 1.0;
        };
    }

    /** 当前天气是否视为「自动补水」(雨天/雷雨/下雪时植物不会因缺水枯萎) */
    public static boolean isRainWatering(String condition) {
        return "rain".equals(condition) || "thunder".equals(condition) || "snow".equals(condition);
    }
}
