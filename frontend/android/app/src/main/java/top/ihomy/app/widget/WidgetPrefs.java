package top.ihomy.app.widget;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 卡片本地存储:令牌 + 各卡片的数据快照 JSON + 更新时间。
 * 数据快照是「上次成功拉取」的结果,断网时卡片显示它而不是空白/报错(计划 §6.5.3)。
 */
public final class WidgetPrefs {

    private static final String FILE = "ihomy_widget";
    public static final String KEY_TOKEN = "widget_token";
    public static final String KEY_CACHE_PREFIX = "cache_";
    public static final String KEY_UPDATED_AT = "updated_at_";

    private WidgetPrefs() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static String token(Context context) {
        return prefs(context).getString(KEY_TOKEN, "");
    }

    public static void setToken(Context context, String token) {
        prefs(context).edit().putString(KEY_TOKEN, token == null ? "" : token).apply();
    }

    public static void clearToken(Context context) {
        prefs(context).edit().remove(KEY_TOKEN).apply();
    }

    public static String cache(Context context, String widgetId) {
        return prefs(context).getString(KEY_CACHE_PREFIX + widgetId, "");
    }

    public static void putCache(Context context, String widgetId, String json) {
        prefs(context).edit()
                .putString(KEY_CACHE_PREFIX + widgetId, json == null ? "" : json)
                .putLong(KEY_UPDATED_AT + widgetId, System.currentTimeMillis())
                .apply();
    }

    public static long updatedAt(Context context, String widgetId) {
        return prefs(context).getLong(KEY_UPDATED_AT + widgetId, 0L);
    }
}
