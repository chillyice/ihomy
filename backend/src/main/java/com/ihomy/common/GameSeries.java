package com.ihomy.common;

import java.util.regex.Pattern;

/**
 * 游戏系列:自由文本(如「宝可梦」「Mario」),一个游戏最多属于一个系列。
 * 自动归类只在创建/批量归类时使用——按名称取第一个分隔段,并去掉括号标注与结尾版本号;
 * 名称里没有可切分的部分就返回 null(不猜),避免把每个游戏都变成独立系列。
 * 手动填写的系列永远优先,自动归类不会覆盖已有值。
 */
public final class GameSeries {

    private GameSeries() {
    }

    /** 与 game_info.series 列宽一致 */
    public static final int MAX_LENGTH = 50;

    /** 括号标注(通常是版本/语言/平台说明),归类前整段去掉 */
    private static final Pattern BRACKETED = Pattern.compile("[（(\\[【].*?[）)\\]】]");
    /** 系列名与副标题之间的分隔符 */
    private static final Pattern SEPARATOR = Pattern.compile("[\\s\\-_/\\\\:：·—|]+");
    /** 名称结尾的版本号(如「最终幻想7」) */
    private static final Pattern TRAILING_VERSION = Pattern.compile("\\s*\\d+$");

    /** 手动值归一:去空白、截断;空串返回 null(=不归系列) */
    public static String normalize(String series) {
        if (series == null || series.isBlank()) {
            return null;
        }
        String s = series.trim();
        return s.length() > MAX_LENGTH ? s.substring(0, MAX_LENGTH) : s;
    }

    /**
     * 按名称猜系列:去掉括号标注 → 取第一个分隔段;没有分隔符时去掉结尾数字。
     * 没有切掉任何内容(如「超级马里奥世界」)或只剩单字时返回 null,交给手动归类。
     */
    public static String guess(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String original = name.trim();
        String cleaned = BRACKETED.matcher(original).replaceAll(" ").trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        String[] parts = SEPARATOR.split(cleaned, 2);
        String candidate = (parts.length > 1 ? parts[0] : TRAILING_VERSION.matcher(cleaned).replaceFirst("")).trim();
        if (candidate.equals(original) || candidate.length() < 2) {
            return null;
        }
        return normalize(candidate);
    }
}
