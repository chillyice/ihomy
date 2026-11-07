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

    /** H5 小游戏 code 全集(与 PointsRuleConst 的 game_* 一致):聊天游戏邀请卡校验用 */
    public static final java.util.Set<String> H5_GAME_CODES = java.util.Set.of(
            PointsRuleConst.GAME_PETLINK, PointsRuleConst.GAME_SUDOKU, PointsRuleConst.GAME_2048,
            PointsRuleConst.GAME_MINESWEEPER, PointsRuleConst.GAME_SOLITAIRE, PointsRuleConst.GAME_SPIDER,
            PointsRuleConst.GAME_TETRIS, PointsRuleConst.GAME_SNAKE);

    /** 对战赛制 mode:同种子赛(各自打完上报)/ 实时对战(WS 输入级同步) */
    public static final String MATCH_MODE_ASYNC = "ASYNC_SEED";
    public static final String MATCH_MODE_REALTIME = "REALTIME";

    /** 对战状态 status */
    public static final String MATCH_WAITING = "WAITING";
    public static final String MATCH_PLAYING = "PLAYING";
    public static final String MATCH_FINISHED = "FINISHED";
    public static final String MATCH_CANCELLED = "CANCELLED";

    /** 支持对战的 H5 游戏全集(P4:同种子赛 2048 + 实时对战贪吃蛇) */
    public static final java.util.Set<String> MATCH_GAME_CODES = java.util.Set.of(PointsRuleConst.GAME_2048, PointsRuleConst.GAME_SNAKE);

    /** 同一对手同游戏每日计分上限(防互刷:超限比赛照打,积分不再发) */
    public static final int MATCH_DAILY_SCORE_LIMIT = 5;

    /** 对战卡/结果卡的游戏名快照(服务端发卡用;前端展示另有 i18n) */
    public static final java.util.Map<String, String> MATCH_GAME_NAMES =
            java.util.Map.of(PointsRuleConst.GAME_2048, "2048", PointsRuleConst.GAME_SNAKE, "贪吃蛇");
}
