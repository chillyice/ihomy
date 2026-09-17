package com.ihomy.common;

import java.util.List;

/**
 * 积分获取规则常量:家庭级「哪些功能能得积分、得多少」的 feature code 清单与默认值。
 * 与积分商城(消费侧)不同,这里只管获取侧;每功能家长可设 enabled 开关 + points 分值。
 * 缺行读取时回退这里的默认值,保证升级后行为不变(默认值与原硬编码常量一致)。
 * 任务(TASK)只受开关控制,分值由任务自身 reward_points 决定(hasPoints=false)。
 */
public final class PointsRuleConst {

    private PointsRuleConst() {
    }

    /** feature code */
    public static final String CHECKIN = "checkin";
    public static final String BLOG = "blog";
    public static final String DIARY = "diary";
    public static final String PHOTO = "photo";
    public static final String VIDEO = "video";
    public static final String TASK = "task";
    public static final String GAME_PETLINK = "game_petlink";
    public static final String PLANT_WATER = "plant_water";
    public static final String PLANT_SUN = "plant_sun";
    public static final String PLANT_HARVEST = "plant_harvest";

    /** 分组(前端分区渲染用) */
    public static final String GROUP_CONTENT = "content";
    public static final String GROUP_TASK = "task";
    public static final String GROUP_GAME = "game";
    public static final String GROUP_GARDEN = "garden";

    /**
     * 功能定义:code / 分组 / 默认分值 / 是否可配分值。
     * hasPoints=false 的功能只受开关控制(如任务,分值由任务自身字段决定)。
     */
    public record Feature(String code, String group, int defaultPoints, boolean hasPoints) {
    }

    /** 功能展示顺序(配置页按此顺序分组渲染) */
    public static final List<Feature> FEATURES = List.of(
            new Feature(CHECKIN, GROUP_CONTENT, 5, true),
            new Feature(BLOG, GROUP_CONTENT, 10, true),
            new Feature(DIARY, GROUP_CONTENT, 8, true),
            new Feature(PHOTO, GROUP_CONTENT, 2, true),
            new Feature(VIDEO, GROUP_CONTENT, 15, true),
            new Feature(TASK, GROUP_TASK, 0, false),
            new Feature(GAME_PETLINK, GROUP_GAME, 10, true),
            new Feature(PLANT_WATER, GROUP_GARDEN, 2, true),
            new Feature(PLANT_SUN, GROUP_GARDEN, 2, true),
            new Feature(PLANT_HARVEST, GROUP_GARDEN, 30, true));

    public static Feature feature(String code) {
        for (Feature f : FEATURES) {
            if (f.code().equals(code)) {
                return f;
            }
        }
        return null;
    }

    public static boolean isValidFeature(String code) {
        return feature(code) != null;
    }
}
