package com.ihomy.homestead.content;

/**
 * 加成维度的统一枚举值(内容 JSON 与引擎共用)。
 * 全部用大写英文常量,不出现数字编号。
 */
public final class EffectKind {

    private EffectKind() {
    }

    public static final String SPEED = "SPEED";
    public static final String XP = "XP";
    public static final String YIELD = "YIELD";
    public static final String RARE = "RARE";
    public static final String EFFICIENCY = "EFFICIENCY";
    public static final String OFFLINE_CAP = "OFFLINE_CAP";
    public static final String SELL_BONUS = "SELL_BONUS";

    public static final String TARGET_GATHER = "GATHER";
    public static final String TARGET_PROCESS = "PROCESS";
    public static final String TARGET_ALL = "ALL";
}
