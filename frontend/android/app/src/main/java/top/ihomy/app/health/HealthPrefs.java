package top.ihomy.app.health;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 健康同步本地存储:上传令牌 / 自动同步开关 / 已授权分组 / 上次同步结果。
 * 令牌是 hk_ 长期令牌(只能调 /health/ingest 与 /health/ping),与桌面卡片令牌同样落私有 SharedPreferences;
 * 不存登录态 JWT——前台同步由页面把当前 token 传进来,后台每日任务只认这把长期令牌。
 */
public final class HealthPrefs {

    private static final String FILE = "ihomy_health";

    private static final String KEY_TOKEN = "ingest_token";
    private static final String KEY_AUTO = "auto_sync";
    private static final String KEY_GROUPS = "groups";
    private static final String KEY_LAST_SYNC_AT = "last_sync_at";
    private static final String KEY_LAST_OK = "last_ok";
    private static final String KEY_LAST_MESSAGE = "last_message";

    private HealthPrefs() {
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

    public static boolean autoSync(Context context) {
        return prefs(context).getBoolean(KEY_AUTO, false);
    }

    public static void setAutoSync(Context context, boolean on) {
        prefs(context).edit().putBoolean(KEY_AUTO, on).apply();
    }

    /** 已授权分组(大写,逗号分隔) */
    public static List<String> groups(Context context) {
        String raw = prefs(context).getString(KEY_GROUPS, "");
        List<String> out = new ArrayList<>();
        for (String s : Arrays.asList(raw.split(","))) {
            String v = s.trim().toUpperCase();
            if (!v.isEmpty()) out.add(v);
        }
        return out;
    }

    public static void setGroups(Context context, List<String> groups) {
        prefs(context).edit().putString(KEY_GROUPS, groups == null ? "" : String.join(",", groups)).apply();
    }

    public static long lastSyncAt(Context context) {
        return prefs(context).getLong(KEY_LAST_SYNC_AT, 0L);
    }

    public static boolean lastOk(Context context) {
        return prefs(context).getBoolean(KEY_LAST_OK, false);
    }

    public static String lastMessage(Context context) {
        return prefs(context).getString(KEY_LAST_MESSAGE, "");
    }

    /** 同步成功才推进 lastSyncAt(失败重试仍从上次成功点补) */
    public static void markSync(Context context, boolean ok, String message) {
        SharedPreferences.Editor e = prefs(context).edit()
                .putBoolean(KEY_LAST_OK, ok)
                .putString(KEY_LAST_MESSAGE, message == null ? "" : message);
        if (ok) e.putLong(KEY_LAST_SYNC_AT, System.currentTimeMillis());
        e.apply();
    }
}
