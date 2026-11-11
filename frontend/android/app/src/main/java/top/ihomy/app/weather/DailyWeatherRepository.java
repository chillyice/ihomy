package top.ihomy.app.weather;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import top.ihomy.app.net.SimpleHttp;
import top.ihomy.app.widget.WidgetPrefs;
import top.ihomy.app.widget.WidgetRepository;

/**
 * 每日天气取数:凭卡片令牌 GET /public/weather/detail,把聚合详情解析成 {@link WeatherSnapshot}。
 *
 * <p>为什么复用卡片令牌:服务端按「家庭天气偏好位置」解析坐标,未登录会退化成服务器 IP 定位,
 * 拿到的是机房所在城市——所以没有令牌就不发这条推送。天气源未配置时服务端 data=null,这里返回 null。
 */
public final class DailyWeatherRepository {

    private static final String TAG = "IhomyWeather";

    private DailyWeatherRepository() {
    }

    /** 拉取并解析;返回 null = 天气源未配置(不是错误,静默跳过) */
    public static WeatherSnapshot fetch(Context context) throws Exception {
        String token = WidgetPrefs.token(context);
        if (token == null || token.isEmpty()) throw new IllegalStateException("no widget token");
        JSONObject root = new JSONObject(SimpleHttp.get(WidgetRepository.apiBase() + "/public/weather/detail", token));
        if (root.optInt("code", -1) != 0) throw new IllegalStateException("bad code " + root.optInt("code"));
        if (root.isNull("data")) return null;
        JSONObject data = root.optJSONObject("data");
        if (data == null) return null;
        try {
            return parse(data);
        } catch (Exception e) {
            Log.w(TAG, "weather detail parse skipped", e);
            return null;
        }
    }

    /** 解析聚合详情(data 内层);包内可见便于设备内用例用真实响应形状做回归 */
    static WeatherSnapshot parse(JSONObject data) {
        WeatherSnapshot s = new WeatherSnapshot();
        JSONObject now = data.optJSONObject("now");
        JSONObject nowFull = data.optJSONObject("nowFull");
        if (nowFull == null && now != null) nowFull = now.optJSONObject("nowFull");

        if (now != null) {
            s.city = now.optString("city", "");
            s.nowText = now.optString("text", "");
            s.iconDay = now.optString("iconCode", "");
            s.nowTemp = intOrNull(now, "temp");
        }
        if (nowFull != null) {
            s.feelsLike = intOrNull(nowFull, "feelsLike");
            if (s.nowText.isEmpty()) s.nowText = nowFull.optString("text", "");
            if (s.iconDay.isEmpty()) s.iconDay = nowFull.optString("icon", "");
            s.windDir = nowFull.optString("windDir", "");
            s.windScale = nowFull.optString("windScale", "");
        }

        JSONArray days = data.optJSONArray("daily");
        JSONObject day = days != null && days.length() > 0 ? days.optJSONObject(0) : null;
        if (day != null) {
            s.dayText = day.optString("textDay", "");
            String iconDay = day.optString("iconDay", "");
            if (!iconDay.isEmpty()) s.iconDay = iconDay;
            s.tempMax = intOrNull(day, "tempMax");
            s.tempMin = intOrNull(day, "tempMin");
            s.pop = intOrNull(day, "pop");
            if (!day.optString("windDirDay", "").isEmpty()) s.windDir = day.optString("windDirDay", "");
            if (!day.optString("windScaleDay", "").isEmpty()) s.windScale = day.optString("windScaleDay", "");
            s.uvIndex = looseInt(day.optString("uvIndex", ""));
        }

        JSONObject air = data.optJSONObject("air");
        if (air != null) {
            s.aqiCategory = air.optString("category", "");
            s.aqi = looseInt(air.optString("aqi", ""));
        }

        JSONArray warnings = data.optJSONArray("warning");
        if (warnings != null && warnings.length() > 0) {
            JSONObject w = warnings.optJSONObject(0);
            if (w != null) {
                s.warningType = w.optString("typeName", "");
                s.warningLevel = w.optString("level", "");
            }
        }
        return s;
    }

    /** 数值字段可能缺席或被上游给成空串:只在真的是数字时取值,别把缺数据写成 0 */
    private static Integer intOrNull(JSONObject o, String key) {
        if (!o.has(key) || o.isNull(key)) return null;
        try {
            return (int) Math.round(o.optDouble(key));
        } catch (Exception e) {
            return null;
        }
    }

    /** 上游有的数值字段是字符串(如 uvIndex="5" / aqi="42") */
    private static Integer looseInt(String raw) {
        if (raw == null) return null;
        String v = raw.trim();
        if (v.isEmpty()) return null;
        try {
            return (int) Math.round(Double.parseDouble(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
