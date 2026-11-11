package top.ihomy.app.weather;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import top.ihomy.app.MainActivity;
import top.ihomy.app.R;

/**
 * 每日天气通知:只进通知中心与锁屏,不弹横幅。
 *
 * <p>形态刻意保持「一条干净的系统通知」而不是自定义 RemoteViews 卡片:自定义布局在锁屏折叠态
 * 会被系统裁高,反而看不到关键信息;标准模板用标题(今天什么天、几度)+ 正文一句建议固定占住锁屏,
 * 展开后才给实况/最高最低/空气风力这些细节。大图标按天气分档上色,是这条通知唯一的「设计感」来源。
 */
public final class DailyWeatherNotifier {

    private static final String TAG = "IhomyWeather";
    /** 通道 id 一旦创建参数就被系统冻结,以后想改重要级/提示音只能换新 id */
    public static final String CHANNEL_ID = "daily-weather";
    /** 固定 id:每天一条,后一条覆盖前一条,不堆栈 */
    private static final int NOTIFY_ID = 7202;
    private static final int DEEPLINK_REQUEST = 7203;

    private DailyWeatherNotifier() {
    }

    /** 发一条每日天气;权限关闭或系统拒绝时静默跳过(由调用方决定要不要重试) */
    public static void show(Context context, WeatherSnapshot s, WeatherAdvice.Kind kind) {
        try {
            ensureChannel(context);
            NotificationManagerCompat nm = NotificationManagerCompat.from(context);
            if (!nm.areNotificationsEnabled()) {
                Log.w(TAG, "daily weather skipped: notifications disabled");
                return;
            }
            String advice = adviceText(context, kind, s);
            String city = s.city == null ? "" : s.city.trim();
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setColor(context.getColor(R.color.notify_weather_accent))
                    .setLargeIcon(WeatherIcons.largeIcon(context, s.iconDay))
                    .setContentTitle(titleText(context, s))
                    .setContentText(advice)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(bigText(context, s, advice)))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setCategory(NotificationCompat.CATEGORY_STATUS)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .setWhen(System.currentTimeMillis())
                    .setShowWhen(true)
                    .setContentIntent(deepLink(context));
            // 城市作副标题(系统标题栏右上角),拿不到就不占位
            if (!city.isEmpty()) builder.setSubText(city);
            nm.notify(NOTIFY_ID, builder.build());
        } catch (Exception e) {
            Log.w(TAG, "daily weather notify failed", e);
        }
    }

    /** 关掉开关时撤掉当天残留的一条 */
    public static void cancel(Context context) {
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFY_ID);
        } catch (Exception e) {
            Log.w(TAG, "daily weather cancel failed", e);
        }
    }

    /** 标题 =「今天什么天 · 最低°~最高°」;缺最高最低时退回实况温度,再缺就只写「今日天气」 */
    private static String titleText(Context context, WeatherSnapshot s) {
        String condition = !isBlank(s.dayText) ? s.dayText : s.nowText;
        if (!isBlank(condition) && s.tempMax != null && s.tempMin != null) {
            return context.getString(R.string.notify_weather_title, condition, s.tempMin, s.tempMax);
        }
        if (!isBlank(condition) && s.nowTemp != null) {
            return context.getString(R.string.notify_weather_title_now, condition, s.nowTemp);
        }
        return isBlank(condition) ? context.getString(R.string.notify_weather_title_fallback) : condition;
    }

    /** 正文 = 一句建议;展开后再补实况、最高最低与空气/风力 */
    private static String bigText(Context context, WeatherSnapshot s, String advice) {
        StringBuilder sb = new StringBuilder(advice);
        if (s.nowTemp != null) {
            sb.append('\n').append(s.feelsLike != null
                    ? context.getString(R.string.notify_weather_now, s.nowTemp, s.feelsLike)
                    : context.getString(R.string.notify_weather_now_only, s.nowTemp));
        }
        if (s.tempMax != null && s.tempMin != null) {
            sb.append('\n').append(s.pop != null
                    ? context.getString(R.string.notify_weather_range_pop, s.tempMax, s.tempMin, s.pop)
                    : context.getString(R.string.notify_weather_range, s.tempMax, s.tempMin));
        }
        StringBuilder extra = new StringBuilder();
        if (!isBlank(s.windDir) && !isBlank(s.windScale)) {
            extra.append(context.getString(R.string.notify_weather_wind, s.windDir, s.windScale));
        }
        if (!isBlank(s.aqiCategory)) {
            if (extra.length() > 0) extra.append(" · ");
            extra.append(context.getString(R.string.notify_weather_air, s.aqiCategory));
        }
        if (extra.length() > 0) sb.append('\n').append(extra);
        return sb.toString();
    }

    /** 建议资源:只出一条,文案跟着类型走;预警带上类型与等级 */
    private static String adviceText(Context context, WeatherAdvice.Kind kind, WeatherSnapshot s) {
        switch (kind) {
            case WARNING:
                return isBlank(s.warningLevel)
                        ? context.getString(R.string.notify_weather_advice_warning_plain, s.warningType)
                        : context.getString(R.string.notify_weather_advice_warning, s.warningType, s.warningLevel);
            case THUNDER:
                return context.getString(R.string.notify_weather_advice_thunder);
            case SNOW:
                return context.getString(R.string.notify_weather_advice_snow);
            case RAIN:
                return context.getString(R.string.notify_weather_advice_rain);
            case FOG:
                return context.getString(R.string.notify_weather_advice_fog);
            case AIR_BAD:
                return context.getString(R.string.notify_weather_advice_air);
            case HOT:
                return context.getString(R.string.notify_weather_advice_hot);
            case COLD:
                return context.getString(R.string.notify_weather_advice_cold);
            case BIG_DIFF:
                return context.getString(R.string.notify_weather_advice_big_diff);
            case UV:
                return context.getString(R.string.notify_weather_advice_uv);
            case WINDY:
                return context.getString(R.string.notify_weather_advice_windy);
            case NICE:
            default:
                return context.getString(R.string.notify_weather_advice_nice);
        }
    }

    /**
     * 通道:默认重要级但不给提示音、不振动、不显角标——「只占住通知中心与锁屏」。
     *
     * <p>默认重要级而不是低重要级:低重要级会被系统折叠进「静默通知」分组,反而不直观;
     * 默认重要级 + 无声通道 = 正常位置展示、零打扰,用户想响铃也能在系统设置里给这个通道开声音。
     */
    public static void ensureChannel(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notify_channel_weather),
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(context.getString(R.string.notify_channel_weather_desc));
        // 默认重要级的通道会自带你没设过的系统提示音,必须显式置 null 才是真无声
        channel.setSound(null, null);
        channel.enableVibration(false);
        channel.enableLights(false);
        channel.setShowBadge(false);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(channel);
    }

    /** 深链统一落到 MainActivity,由前端 deeplink.js 解析 ihomy:// 进天气页 */
    private static PendingIntent deepLink(Context context) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("ihomy://weather"));
        intent.setClass(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(
                context, DEEPLINK_REQUEST, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
