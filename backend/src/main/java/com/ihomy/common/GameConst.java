package com.ihomy.common;

/**
 * 小游戏常量:类型/状态/长度上限。
 * 本次仅支持 SWF 导入;type 字段预留 GBA 等后续扩展。
 */
public final class GameConst {

    private GameConst() {
    }

    /** 游戏类型 type */
    public static final String TYPE_SWF = "SWF";
    public static final String TYPE_GBA = "GBA";

    /** 状态 status */
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";

    /** 长度上限 */
    public static final int NAME_MAX_LENGTH = 100;
    public static final int DESC_MAX_LENGTH = 500;

    /** 支持的扩展名 */
    public static final String SWF_EXTENSION = ".swf";
    public static final String GBA_EXTENSION = ".gba";

    /** H5 小游戏通关奖励每日上限(防刷:宠物连连看等前端上报分数的游戏按天限次) */
    public static final int PETLINK_DAILY_LIMIT = 5;
}
