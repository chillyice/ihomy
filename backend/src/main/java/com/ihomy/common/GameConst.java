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
            PointsRuleConst.GAME_FREECELL, PointsRuleConst.GAME_TETRIS, PointsRuleConst.GAME_SNAKE);

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

    // ---------- 房间制「一起玩」 ----------

    /** 房间玩法 mode:PK 对抗 / COOP 合作 */
    public static final String ROOM_MODE_PK = "PK";
    public static final String ROOM_MODE_COOP = "COOP";

    /** 房间状态 status */
    public static final String ROOM_WAITING = "WAITING";
    public static final String ROOM_PLAYING = "PLAYING";
    public static final String ROOM_FINISHED = "FINISHED";
    public static final String ROOM_CANCELLED = "CANCELLED";

    /** 结算口径:FIRST_FINISH 先完成者胜;HIGH_SCORE 高分者胜;COOP 合作不计胜负 */
    public static final String ROOM_RESULT_FIRST = "FIRST_FINISH";
    public static final String ROOM_RESULT_SCORE = "HIGH_SCORE";
    public static final String ROOM_RESULT_COOP = "COOP";

    /** 房间人数上限(家庭场景,含房主) */
    public static final int ROOM_MAX_PLAYERS = 8;
    public static final int ROOM_DEFAULT_PLAYERS = 4;

    /** 单个房间的成员/操作数量上限,防止异常客户端刷数据 */
    public static final int ROOM_MEMBER_LIMIT = 8;

    /** 房间游戏能力:允许的玩法 + 结算口径(与前端 utils/gameRoom.js 同口径) */
    public record RoomGame(String code, java.util.Set<String> modes, String result) {
    }

    /**
     * 可开房的 H5 游戏:按玩法性质给模式——
     * 连连看/扫雷/数独天然适合合作,其余单人解谜/实时类按同种子 PK。
     */
    public static final java.util.Map<String, RoomGame> ROOM_GAMES = java.util.Map.of(
            PointsRuleConst.GAME_PETLINK, new RoomGame(PointsRuleConst.GAME_PETLINK,
                    java.util.Set.of(ROOM_MODE_PK, ROOM_MODE_COOP), ROOM_RESULT_FIRST),
            PointsRuleConst.GAME_MINESWEEPER, new RoomGame(PointsRuleConst.GAME_MINESWEEPER,
                    java.util.Set.of(ROOM_MODE_COOP), ROOM_RESULT_COOP),
            PointsRuleConst.GAME_SUDOKU, new RoomGame(PointsRuleConst.GAME_SUDOKU,
                    java.util.Set.of(ROOM_MODE_COOP), ROOM_RESULT_COOP),
            PointsRuleConst.GAME_2048, new RoomGame(PointsRuleConst.GAME_2048,
                    java.util.Set.of(ROOM_MODE_PK), ROOM_RESULT_SCORE),
            PointsRuleConst.GAME_SNAKE, new RoomGame(PointsRuleConst.GAME_SNAKE,
                    java.util.Set.of(ROOM_MODE_PK), ROOM_RESULT_SCORE),
            PointsRuleConst.GAME_TETRIS, new RoomGame(PointsRuleConst.GAME_TETRIS,
                    java.util.Set.of(ROOM_MODE_PK), ROOM_RESULT_SCORE),
            PointsRuleConst.GAME_SOLITAIRE, new RoomGame(PointsRuleConst.GAME_SOLITAIRE,
                    java.util.Set.of(ROOM_MODE_PK), ROOM_RESULT_SCORE),
            PointsRuleConst.GAME_SPIDER, new RoomGame(PointsRuleConst.GAME_SPIDER,
                    java.util.Set.of(ROOM_MODE_PK), ROOM_RESULT_SCORE),
            PointsRuleConst.GAME_FREECELL, new RoomGame(PointsRuleConst.GAME_FREECELL,
                    java.util.Set.of(ROOM_MODE_PK), ROOM_RESULT_SCORE));

    /** 房间游戏名快照(邀请卡/结果卡用) */
    public static final java.util.Map<String, String> ROOM_GAME_NAMES = java.util.Map.ofEntries(
            java.util.Map.entry(PointsRuleConst.GAME_PETLINK, "宠物连连看"),
            java.util.Map.entry(PointsRuleConst.GAME_MINESWEEPER, "扫雷"),
            java.util.Map.entry(PointsRuleConst.GAME_SUDOKU, "数独"),
            java.util.Map.entry(PointsRuleConst.GAME_2048, "2048"),
            java.util.Map.entry(PointsRuleConst.GAME_SNAKE, "贪吃蛇"),
            java.util.Map.entry(PointsRuleConst.GAME_TETRIS, "俄罗斯方块"),
            java.util.Map.entry(PointsRuleConst.GAME_SOLITAIRE, "克朗代克纸牌"),
            java.util.Map.entry(PointsRuleConst.GAME_SPIDER, "蜘蛛纸牌"),
            java.util.Map.entry(PointsRuleConst.GAME_FREECELL, "自由纸牌"));
}
