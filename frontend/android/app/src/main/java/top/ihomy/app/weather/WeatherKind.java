package top.ihomy.app.weather;

/**
 * 天气现象分档(由和风图标码归类):推送图标与建议规则共用同一口径,
 * 避免「图标画的是雨、建议却说天气不错」这类不一致。纯逻辑,无 Android 依赖,可单测。
 *
 * <p>和风 v1 图标码按百位分族:1xx 晴/多云/阴,2xx 浮尘扬沙,3xx 雨(302/303 雷阵雨),
 * 4xx 雪,5xx 雾霾。分档只决定「用户看起来是什么天气」,不做气象学细分。
 */
public enum WeatherKind {
    SUN, PARTLY, CLOUD, RAIN, THUNDER, SNOW, FOG;

    /** 图标码 → 分档;空/异常码按多云处理(宁可少说,不猜成晴) */
    public static WeatherKind of(String iconCode) {
        int code = parse(iconCode);
        if (code < 100) return CLOUD;
        if (code < 200) {
            if (code == 100 || code == 150) return SUN;
            if (code <= 103 || (code >= 151 && code <= 153)) return PARTLY;
            return CLOUD;
        }
        if (code < 300) return FOG;
        if (code < 400) {
            if (code == 302 || code == 303) return THUNDER;
            return RAIN;
        }
        if (code < 500) return SNOW;
        if (code < 600) return FOG;
        return CLOUD;
    }

    private static int parse(String iconCode) {
        if (iconCode == null || iconCode.isEmpty()) return -1;
        try {
            return Integer.parseInt(iconCode.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
