package top.ihomy.app.reminder;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import top.ihomy.app.MainActivity;
import top.ihomy.app.R;

/**
 * 「日程该校准了」提示:复用 JS 侧已建好的 {@code reminders-v2} 通道与提示音。
 *
 * <p>通道参数创建后被系统冻结,换新 id 会变成静音通道——所以这里只确保通道存在,
 * 绝不新建第二个日程通道(计划 §5.1 / 踩坑速查 §12.3)。
 */
public final class ReminderNotifier {

    private static final String TAG = "IhomyReminder";
    public static final String CHANNEL_ID = "reminders-v2";
    private static final String SOUND_RAW = "ihomy_notify";
    /** 固定 id:多次提示互相覆盖,不堆栈 */
    private static final int NOTIFY_ID = 7200;

    private ReminderNotifier() {
    }

    /** 发一条「最近的日程等你确认」;权限关闭或系统拒绝时静默跳过 */
    public static void notifyStale(Context context, int count) {
        try {
            ensureChannel(context);
            NotificationManagerCompat nm = NotificationManagerCompat.from(context);
            if (!nm.areNotificationsEnabled()) {
                Log.w(TAG, "stale nudge skipped: notifications disabled");
                return;
            }
            String body = context.getString(R.string.notify_stale_body, count);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(context.getString(R.string.notify_stale_title))
                    .setContentText(body)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setAutoCancel(true)
                    .setContentIntent(deepLink(context, "ihomy://plan?tab=today", 7001));
            nm.notify(NOTIFY_ID, builder.build());
        } catch (Exception e) {
            Log.w(TAG, "stale nudge failed", e);
        }
    }

    /** 确保系统里存在带提示音的日程通道(已存在时 no-op,参数不会被改) */
    private static void ensureChannel(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notify_channel_reminders),
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(context.getString(R.string.notify_channel_reminders_desc));
        channel.setSound(soundUri(context), new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build());
        channel.enableVibration(true);
        channel.enableLights(true);
        nm.createNotificationChannel(channel);
    }

    private static Uri soundUri(Context context) {
        return Uri.parse("android.resource://" + context.getPackageName() + "/raw/" + SOUND_RAW);
    }

    /** 深链统一落到 MainActivity,由前端 deeplink.js 解析 ihomy:// */
    static PendingIntent deepLink(Context context, String uri, int requestCode) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        intent.setClass(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(
                context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
