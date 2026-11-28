package com.ihomy.garden.core;

import java.time.LocalDate;

/**
 * 季节纯函数(FR-20):3–5 春 / 6–8 夏 / 9–11 秋 / 12–2 冬,由月份直接决定,不存储。
 */
public final class GardenSeason {

    public static final String SPRING = "SPRING";
    public static final String SUMMER = "SUMMER";
    public static final String AUTUMN = "AUTUMN";
    public static final String WINTER = "WINTER";

    private GardenSeason() {
    }

    public static String seasonOf(LocalDate date) {
        int m = date.getMonthValue();
        if (m >= 3 && m <= 5) {
            return SPRING;
        }
        if (m >= 6 && m <= 8) {
            return SUMMER;
        }
        if (m >= 9 && m <= 11) {
            return AUTUMN;
        }
        return WINTER;
    }

    /** 是否全年作物(四季皆本命) */
    public static boolean isAllSeason(java.util.List<String> seasons) {
        return seasons != null && seasons.size() >= 4;
    }

    /** 作物在该季是否本命(全年作物恒真) */
    public static boolean inSeason(java.util.List<String> seasons, String season) {
        return isAllSeason(seasons) || (seasons != null && seasons.contains(season));
    }
}
