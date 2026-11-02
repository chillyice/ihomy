package com.ihomy.common;

import java.util.List;
import java.util.Locale;

/**
 * 小游戏分类:页面按分类分组与筛选,枚举一律大写英文单词。
 * 与 {@code sys_dict_item.game_category} 及前端 {@code utils/games/gameCategory.js} 保持同一套值与顺序。
 * 未知/空值统一落 OTHER,避免脏值进库。
 */
public final class GameCategory {

    private GameCategory() {
    }

    public static final String ACTION = "ACTION";
    public static final String PUZZLE = "PUZZLE";
    public static final String CASUAL = "CASUAL";
    public static final String SIMULATION = "SIMULATION";
    public static final String ADVENTURE = "ADVENTURE";
    public static final String RPG = "RPG";
    public static final String STRATEGY = "STRATEGY";
    public static final String ARCADE = "ARCADE";
    public static final String OTHER = "OTHER";

    /** 展示顺序(也是前端筛选条顺序) */
    public static final List<String> ALL = List.of(
            ACTION, PUZZLE, CASUAL, SIMULATION, ADVENTURE, RPG, STRATEGY, ARCADE, OTHER);

    /** 与 game_info.category 列宽一致 */
    public static final int MAX_LENGTH = 20;

    /** 归一:去空白转大写,白名单外的值一律落 OTHER */
    public static String normalize(String category) {
        if (category == null || category.isBlank()) {
            return OTHER;
        }
        String upper = category.trim().toUpperCase(Locale.ROOT);
        return ALL.contains(upper) ? upper : OTHER;
    }
}
