package com.ihomy.common;

import java.util.List;

/**
 * 成就目录(V15.17):成就的唯一来源——编码、分类、图标、判定指标、阈值、成就分全在这里。
 *
 * <p>目录内置不入库:库里只落「已解锁」行(family_achievement),未解锁进度由
 * {@code AchievementService} 每次读取时按指标实时聚合。因此新增/调整成就只改本文件,
 * 不用写迁移、也不会与历史解锁行冲突(解锁行带自己的成就分快照)。</p>
 *
 * <p>指标口径(见 FamilyAchievementMapper.xml 的 loadMetrics):签到用总次数与最长连续;
 * 积分用「累计获得」(change &gt; 0 之和,单调不减),不用余额避免消费后成就倒退;
 * 内容只算已发布且未进回收站的行;游戏来自 family_game_record。</p>
 */
public final class AchievementCatalog {

    private AchievementCatalog() {
    }

    // ---------- 分类(与 sys_dict_item 的 ACHIEVEMENT_CATEGORY 对齐) ----------

    public static final String CATEGORY_CHECKIN = "CHECKIN";
    public static final String CATEGORY_POINTS = "POINTS";
    public static final String CATEGORY_CONTENT = "CONTENT";
    public static final String CATEGORY_GAME = "GAME";
    public static final String CATEGORY_TASK = "TASK";

    // ---------- 游戏记录口径(family_game_record.source / result) ----------

    public static final String SOURCE_SOLO = "SOLO";
    public static final String SOURCE_ROOM = "ROOM";
    public static final String SOURCE_MATCH = "MATCH";

    public static final String RESULT_FINISH = "FINISH";
    public static final String RESULT_WIN = "WIN";
    public static final String RESULT_LOSE = "LOSE";

    // ---------- 指标键(与 mapper 查询列名一一对应) ----------

    public static final String M_CHECKIN_TOTAL = "checkinTotal";
    public static final String M_CHECKIN_MAX_STREAK = "checkinMaxStreak";
    public static final String M_POINTS_EARNED = "pointsEarned";
    public static final String M_BLOG_COUNT = "blogCount";
    public static final String M_DIARY_COUNT = "diaryCount";
    public static final String M_PHOTO_COUNT = "photoCount";
    public static final String M_VIDEO_COUNT = "videoCount";
    public static final String M_BOOK_COUNT = "bookCount";
    public static final String M_WISH_COUNT = "wishCount";
    public static final String M_TASK_DONE_COUNT = "taskDoneCount";
    public static final String M_GAME_TOTAL = "gameTotal";
    public static final String M_GAME_DISTINCT = "gameDistinct";
    public static final String M_GAME_WIN = "gameWin";
    public static final String M_GAME_ROOM_WIN = "gameRoomWin";
    public static final String M_GAME_MAHJONG_WIN = "gameMahjongWin";
    public static final String M_HOMESTEAD_LEVEL_SUM = "homesteadLevelSum";
    public static final String M_HOMESTEAD_MAX_LEVEL = "homesteadMaxLevel";
    public static final String M_HOMESTEAD_ACTION_TOTAL = "homesteadActionTotal";
    public static final String M_HOMESTEAD_SKILL_OPENED = "homesteadSkillOpened";

    /**
     * 单条成就定义。
     *
     * @param code      编码(前端 i18n 键 achievements.catalog.&lt;code&gt;)
     * @param category  分类
     * @param icon      展示图标(emoji,语言无关)
     * @param metric    判定指标键
     * @param threshold 达标阈值(进度 = min(指标值, 阈值))
     * @param score     解锁获得的成就分(只用于等级,不发可消费积分)
     */
    public record Achievement(String code, String category, String icon, String metric,
                              int threshold, int score) {
    }

    /** 等级档位:序号从 1 开始,minScore 为进入该档的累计成就分 */
    public record Level(int level, int minScore) {
    }

    /** 目录顺序即前端展示顺序(同分类内) */
    public static final List<Achievement> ACHIEVEMENTS = List.of(
            // 签到
            new Achievement("checkin_first", CATEGORY_CHECKIN, "🌱", M_CHECKIN_TOTAL, 1, 10),
            new Achievement("checkin_week", CATEGORY_CHECKIN, "📅", M_CHECKIN_MAX_STREAK, 7, 20),
            new Achievement("checkin_month", CATEGORY_CHECKIN, "🔥", M_CHECKIN_MAX_STREAK, 30, 50),
            new Achievement("checkin_100", CATEGORY_CHECKIN, "💯", M_CHECKIN_TOTAL, 100, 100),
            // 积分
            new Achievement("points_100", CATEGORY_POINTS, "🪙", M_POINTS_EARNED, 100, 10),
            new Achievement("points_1000", CATEGORY_POINTS, "💰", M_POINTS_EARNED, 1000, 40),
            new Achievement("points_5000", CATEGORY_POINTS, "🏦", M_POINTS_EARNED, 5000, 100),
            // 内容
            new Achievement("blog_first", CATEGORY_CONTENT, "✍️", M_BLOG_COUNT, 1, 10),
            new Achievement("blog_10", CATEGORY_CONTENT, "📚", M_BLOG_COUNT, 10, 40),
            new Achievement("diary_first", CATEGORY_CONTENT, "📔", M_DIARY_COUNT, 1, 10),
            new Achievement("diary_50", CATEGORY_CONTENT, "📖", M_DIARY_COUNT, 50, 40),
            new Achievement("photo_first", CATEGORY_CONTENT, "📷", M_PHOTO_COUNT, 1, 10),
            new Achievement("photo_100", CATEGORY_CONTENT, "🖼️", M_PHOTO_COUNT, 100, 50),
            new Achievement("video_first", CATEGORY_CONTENT, "🎬", M_VIDEO_COUNT, 1, 10),
            new Achievement("video_20", CATEGORY_CONTENT, "🎥", M_VIDEO_COUNT, 20, 40),
            new Achievement("book_first", CATEGORY_CONTENT, "📕", M_BOOK_COUNT, 1, 10),
            new Achievement("wish_first", CATEGORY_CONTENT, "🌠", M_WISH_COUNT, 1, 10),
            // 任务
            new Achievement("task_first", CATEGORY_TASK, "✅", M_TASK_DONE_COUNT, 1, 10),
            new Achievement("task_10", CATEGORY_TASK, "🧹", M_TASK_DONE_COUNT, 10, 30),
            // 游戏
            new Achievement("game_first", CATEGORY_GAME, "🎮", M_GAME_TOTAL, 1, 10),
            new Achievement("game_5", CATEGORY_GAME, "🎲", M_GAME_DISTINCT, 5, 30),
            new Achievement("game_10", CATEGORY_GAME, "🕹️", M_GAME_DISTINCT, 10, 60),
            new Achievement("game_50", CATEGORY_GAME, "🏅", M_GAME_TOTAL, 50, 50),
            new Achievement("game_room_win", CATEGORY_GAME, "🤝", M_GAME_ROOM_WIN, 10, 40),
            new Achievement("mahjong_master", CATEGORY_GAME, "🀄", M_GAME_MAHJONG_WIN, 10, 50),
            // 小院慢生活(放置):指标直接聚合游戏自己的表,不挂 family_game_record
            new Achievement("homestead_first", CATEGORY_GAME, "🏡", M_HOMESTEAD_ACTION_TOTAL, 1, 10),
            new Achievement("homestead_skills", CATEGORY_GAME, "🧺", M_HOMESTEAD_SKILL_OPENED, 11, 40),
            new Achievement("homestead_level", CATEGORY_GAME, "🌾", M_HOMESTEAD_LEVEL_SUM, 400, 50),
            new Achievement("homestead_expert", CATEGORY_GAME, "🪵", M_HOMESTEAD_MAX_LEVEL, 50, 60),
            new Achievement("homestead_veteran", CATEGORY_GAME, "⏳", M_HOMESTEAD_ACTION_TOTAL, 5000, 50));

    /** 等级(成就分越高档位越高;目录扩充后满分高于最高档,满档后仍可继续积累) */
    public static final List<Level> LEVELS = List.of(
            new Level(1, 0),
            new Level(2, 100),
            new Level(3, 250),
            new Level(4, 450),
            new Level(5, 650),
            new Level(6, 800));

    /** 目录满分(全部解锁时的成就分) */
    public static int totalScore() {
        int sum = 0;
        for (Achievement a : ACHIEVEMENTS) {
            sum += a.score();
        }
        return sum;
    }

    public static Achievement find(String code) {
        for (Achievement a : ACHIEVEMENTS) {
            if (a.code().equals(code)) {
                return a;
            }
        }
        return null;
    }

    /** 按累计成就分取等级序号(低于第一档也返回 1) */
    public static int levelOf(int score) {
        int level = 1;
        for (Level l : LEVELS) {
            if (score >= l.minScore()) {
                level = l.level();
            }
        }
        return level;
    }

    /** 下一档起始分;已是最高档返回 -1 */
    public static int nextLevelScore(int score) {
        for (Level l : LEVELS) {
            if (score < l.minScore()) {
                return l.minScore();
            }
        }
        return -1;
    }
}
