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
    /** 今日卡副标题数据(农历/天气,WidgetRepository.TodayInfo):全局单份,按归属日期失效 */
    public static final String KEY_CACHE_TODAY_INFO = "cache_today_info";
    public static final String KEY_UPDATED_AT = "updated_at_";
    /** 前端最近一次成功对账闹钟的时间(ms);0 = 从未上报过 */
    public static final String KEY_REMINDER_SYNC_AT = "reminder_sync_at";
    /** 上次「日程该校准了」提示的日期(yyyy-MM-dd),一天最多一条 */
    public static final String KEY_LAST_NUDGE_DATE = "last_nudge_date";
    /** 后台未读通知:上次看到的通知数/聊天数,用来判断「有增长」 */
    public static final String KEY_LAST_UNREAD_NOTICE = "last_unread_notice";
    public static final String KEY_LAST_UNREAD_CHAT = "last_unread_chat";
    /** 后台未读通知开关(默认开) */
    public static final String KEY_UNREAD_NOTIFY = "unread_notify_enabled";
    /** 每日天气推送开关(默认开)、推送时刻(当天 0 点起的分钟数)、上次发送日期(yyyy-MM-dd) */
    public static final String KEY_DAILY_WEATHER = "daily_weather_enabled";
    public static final String KEY_DAILY_WEATHER_MINUTE = "daily_weather_minute";
    public static final String KEY_LAST_WEATHER_DATE = "last_weather_date";
    /** 默认 07:30(与 DailyWeatherWorker.DEFAULT_MINUTE 同口径) */
    public static final int DEFAULT_DAILY_WEATHER_MINUTE = 7 * 60 + 30;

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

    public static String todayInfo(Context context) {
        return prefs(context).getString(KEY_CACHE_TODAY_INFO, "");
    }

    public static void putTodayInfo(Context context, String json) {
        prefs(context).edit().putString(KEY_CACHE_TODAY_INFO, json == null ? "" : json).apply();
    }

    public static long reminderSyncAt(Context context) {
        return prefs(context).getLong(KEY_REMINDER_SYNC_AT, 0L);
    }

    public static void setReminderSyncAt(Context context, long ts) {
        prefs(context).edit().putLong(KEY_REMINDER_SYNC_AT, ts).apply();
    }

    public static String lastNudgeDate(Context context) {
        return prefs(context).getString(KEY_LAST_NUDGE_DATE, "");
    }

    public static void setLastNudgeDate(Context context, String date) {
        prefs(context).edit().putString(KEY_LAST_NUDGE_DATE, date == null ? "" : date).apply();
    }

    public static long lastUnreadNotice(Context context) {
        return prefs(context).getLong(KEY_LAST_UNREAD_NOTICE, 0L);
    }

    public static long lastUnreadChat(Context context) {
        return prefs(context).getLong(KEY_LAST_UNREAD_CHAT, 0L);
    }

    public static void setLastUnread(Context context, long notice, long chat) {
        prefs(context).edit()
                .putLong(KEY_LAST_UNREAD_NOTICE, notice)
                .putLong(KEY_LAST_UNREAD_CHAT, chat)
                .apply();
    }

    public static boolean unreadNotifyEnabled(Context context) {
        return prefs(context).getBoolean(KEY_UNREAD_NOTIFY, true);
    }

    public static void setUnreadNotifyEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_UNREAD_NOTIFY, enabled).apply();
    }

    public static boolean dailyWeatherEnabled(Context context) {
        return prefs(context).getBoolean(KEY_DAILY_WEATHER, true);
    }

    public static void setDailyWeatherEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_DAILY_WEATHER, enabled).apply();
    }

    /** 推送时刻:当天 0 点起的分钟数(0–1439),越界回落到默认 07:30 */
    public static int dailyWeatherMinute(Context context) {
        int minute = prefs(context).getInt(KEY_DAILY_WEATHER_MINUTE, DEFAULT_DAILY_WEATHER_MINUTE);
        return minute >= 0 && minute < 24 * 60 ? minute : DEFAULT_DAILY_WEATHER_MINUTE;
    }

    public static void setDailyWeatherMinute(Context context, int minute) {
        prefs(context).edit().putInt(KEY_DAILY_WEATHER_MINUTE, minute).apply();
    }

    public static String lastWeatherDate(Context context) {
        return prefs(context).getString(KEY_LAST_WEATHER_DATE, "");
    }

    public static void setLastWeatherDate(Context context, String date) {
        prefs(context).edit().putString(KEY_LAST_WEATHER_DATE, date == null ? "" : date).apply();
    }
}
