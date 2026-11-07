package com.ihomy.common;

/**
 * 小游戏常量:类型/状态/长度上限。
 * 文件型游戏分 SWF(Flash 播放器)与模拟器 ROM(机种码见 common/GameSystem),LINK 为外部网页。
 */
public final class GameConst {

    private GameConst() {
    }

    /** 游戏类型 type(SWF/LINK;模拟器机种码见 GameSystem) */
    public static final String TYPE_SWF = "SWF";
    public static final String TYPE_LINK = "LINK";

    /** 状态 status */
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";

    /** 长度上限 */
    public static final int NAME_MAX_LENGTH = 100;
    public static final int DESC_MAX_LENGTH = 500;

    /** H5 小游戏通关奖励每日上限(防刷:宠物连连看等前端上报分数的游戏按天限次) */
    public static final int PETLINK_DAILY_LIMIT = 5;

    /** 数独/2048 等 H5 小游戏的每日奖励次数上限,口径与连连看一致 */
    public static final int H5_GAME_DAILY_LIMIT = 5;
}
