package top.ihomy.app.widget;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.media.AudioAttributes;
import android.net.Uri;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import top.ihomy.app.MainActivity;
import top.ihomy.app.R;

/**
 * 后台未读通知(计划 §5.2):固定 id 覆盖不堆栈,内容不变不重发由 Worker 比对上次计数保证。
 *
 * <p>文案如实写「约每 30 分钟」的口径放在设置页说明里;这条通知只表示「有人找你」,
 * 不承诺秒级,也不代表实时 IM。
 */
public final class UnreadNotifier {

    private static final String TAG = "IhomyWidget";
    private static final String CHANNEL_ID = "family-updates";
    private static final String SOUND_RAW = "ihomy_notify";
    /** 固定 id:后一条覆盖前一条,不堆栈 */
    private static final int NOTIFY_ID = 7201;

    private UnreadNotifier() {
    }

    public static void notifyUnread(Context context, long chat, long notice) {
        try {
            ensureChannel(context);
            NotificationManagerCompat nm = NotificationManagerCompat.from(context);
            if (!nm.areNotificationsEnabled()) return;
            String body = context.getString(R.string.notify_unread_body, chat, notice);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(context.getString(R.string.notify_unread_title))
                    .setContentText(body)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setNumber((int) (chat + notice))
                    .setOnlyAlertOnce(false)
                    .setAutoCancel(true)
                    .setContentIntent(deepLink(context, "ihomy://chat", 7101))
                    .addAction(R.drawable.ic_notification, context.getString(R.string.notify_action_chat),
                            deepLink(context, "ihomy://chat", 7102))
                    .addAction(R.drawable.ic_notification, context.getString(R.string.notify_action_notice),
                            deepLink(context, "ihomy://notification", 7103));
            nm.notify(NOTIFY_ID, builder.build());
        } catch (Exception e) {
            Log.w(TAG, "unread notify failed", e);
        }
    }

    /** 未读清零后撤掉旧提示 */
    public static void cancel(Context context) {
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFY_ID);
        } catch (Exception e) {
            Log.w(TAG, "unread cancel failed", e);
        }
    }

    private static void ensureChannel(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notify_channel_updates),
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(context.getString(R.string.notify_channel_updates_desc));
        channel.setSound(Uri.parse("android.resource://" + context.getPackageName() + "/raw/" + SOUND_RAW),
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
        channel.enableVibration(true);
        channel.enableLights(true);
        nm.createNotificationChannel(channel);
    }

    private static PendingIntent deepLink(Context context, String uri, int requestCode) {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(uri));
        intent.setClass(context, MainActivity.class);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(
                context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
