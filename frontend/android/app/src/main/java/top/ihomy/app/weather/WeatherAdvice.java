package top.ihomy.app.weather;

/**
 * 每日天气的「一句建议」规则:只输出一条,按「最该提醒的一件事」排序,不叠加。
 *
 * <p>排序口径:安全相关(预警/雷/雪/雨/雾)→ 健康相关(空气/高温/低温/紫外线)→ 温差/大风 → 天气不错。
 * 文案在 {@link DailyWeatherNotifier} 里按资源取,这里只给类型,便于纯 JVM 单测锁顺序与阈值。
 */
public final class WeatherAdvice {

    /** 建议类型(一到一条);新增类型必须同步 values / values-en 的文案资源 */
    public enum Kind {
        WARNING, THUNDER, SNOW, RAIN, FOG, AIR_BAD, HOT, COLD, BIG_DIFF, UV, WINDY, NICE
    }

    /** 高温 / 低温 / 温差 / 紫外线 / 大风的提醒阈值 */
    static final int HOT_MAX = 33;
    static final int COLD_MIN = 0;
    static final int BIG_DIFF = 10;
    static final int UV_STRONG = 7;
    static final int WIND_STRONG = 5;
    /** 降水概率达到这个值就按「会下雨」提醒 */
    static final int RAIN_POP = 50;
    /** AQI 达到这个值就按「空气不好」提醒 */
    static final int AIR_BAD_AQI = 150;

    private WeatherAdvice() {
    }

    /** 取唯一一条建议;快照为空时给最保守的「天气不错」 */
    public static Kind of(WeatherSnapshot s) {
        if (s == null) return Kind.NICE;
        if (!isBlank(s.warningType)) return Kind.WARNING;
        WeatherKind kind = WeatherKind.of(s.iconDay);
        if (kind == WeatherKind.THUNDER) return Kind.THUNDER;
        if (kind == WeatherKind.SNOW) return Kind.SNOW;
        if (kind == WeatherKind.RAIN || (s.pop != null && s.pop >= RAIN_POP)) return Kind.RAIN;
        if (kind == WeatherKind.FOG) return Kind.FOG;
        if ((s.aqi != null && s.aqi >= AIR_BAD_AQI) || polluted(s.aqiCategory)) return Kind.AIR_BAD;
        if (s.tempMax != null && s.tempMax >= HOT_MAX) return Kind.HOT;
        if (s.tempMin != null && s.tempMin <= COLD_MIN) return Kind.COLD;
        if (s.tempMax != null && s.tempMin != null && s.tempMax - s.tempMin >= BIG_DIFF) return Kind.BIG_DIFF;
        if (s.uvIndex != null && s.uvIndex >= UV_STRONG) return Kind.UV;
        if (windScale(s.windScale) >= WIND_STRONG) return Kind.WINDY;
        return Kind.NICE;
    }

    /** 空气质量等级文字(服务端按和风中文口径返回)是否已达到「污染」 */
    private static boolean polluted(String category) {
        if (isBlank(category)) return false;
        return category.contains("污染") || category.contains("严重") || category.contains("重度")
                || category.contains("中度");
    }

    /** 风力文字可能带区间(如「3-4」):取最大值判断是否达到大风 */
    private static int windScale(String scale) {
        if (isBlank(scale)) return -1;
        int max = -1;
        for (String part : scale.split("[^0-9]+")) {
            if (part.isEmpty()) continue;
            try {
                max = Math.max(max, Integer.parseInt(part));
            } catch (NumberFormatException ignored) {
                // 非数字段跳过,继续找其它段
            }
        }
        return max;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
