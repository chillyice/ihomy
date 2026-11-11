package top.ihomy.app.live;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import top.ihomy.app.MainActivity;
import top.ihomy.app.R;
import top.ihomy.app.widget.UnreadSync;

/**
 * 后台消息提醒(前台服务):App 在后台/锁屏时仍每 30 秒对一次服务端未读,
 * 有新消息交给 UnreadNotifier 发通知;前台时降到 120 秒且只更新基线(不弹通知)。
 *
 * <p>为什么必须前台服务:WorkManager 最短 15 分钟,满足不了「有人发消息就能收到」;
 * 代价是通知栏一条常驻通知,这是 Android 对前台服务的硬要求,设置页开关关闭即停。
 *
 * <p>只读未读计数,复用 WIDGET 令牌白名单(`/chat/unread`、`/notification/unread-count`);
 * 不建新接口、不拿正文,家庭隔离仍由服务端判定。
 */
public class LiveService extends Service {

    private static final String TAG = "IhomyLive";
    public static final String ACTION_START = "top.ihomy.app.action.LIVE_START";
    public static final String ACTION_STOP = "top.ihomy.app.action.LIVE_STOP";

    private static final String CHANNEL_ID = "background-live";
    private static final int NOTIFY_ID = 7200;
    /** 后台轮询间隔:30 秒够「有消息就能收到」,又不至于太耗电 */
    private static final long BACKGROUND_INTERVAL_MS = 30_000L;
    /** 前台间隔:站内 WS 已实时,这里只是兜底,拉长省电 */
    private static final long FOREGROUND_INTERVAL_MS = 120_000L;

    private ExecutorService executor;
    private volatile boolean running;
    private static volatile boolean started;

    public static boolean isRunning() {
        return started;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ensureChannel();
        executor = Executors.newSingleThreadExecutor();
        running = true;
        executor.submit(this::pollLoop);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        started = true;
        try {
            Notification notification = buildNotification();
            if (Build.VERSION.SDK_INT >= 34) {
                // Android 14+ 必须在 startForeground 时声明类型(manifest 已声明 remoteMessaging)
                ServiceCompat.startForeground(this, NOTIFY_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING);
            } else {
                startForeground(NOTIFY_ID, notification);
            }
        } catch (Exception e) {
            // 系统禁止启动前台服务(极端省电模式)时不崩:服务退化成普通服务,仍会轮询
            Log.w(TAG, "startForeground failed", e);
        }
        return START_STICKY;
    }

    /** 轮询线程:一次同步 + 按当前前后台状态休眠,不固定周期(scheduleAtFixedRate 改不了间隔) */
    private void pollLoop() {
        while (running) {
            try {
                UnreadSync.run(this, !AppVisibility.isForeground());
            } catch (Exception e) {
                // 无令牌/断网/令牌过期都走这里:静默跳过,下一轮自愈
                Log.w(TAG, "unread poll skipped: " + e.getMessage());
            }
            long wait = AppVisibility.isForeground() ? FOREGROUND_INTERVAL_MS : BACKGROUND_INTERVAL_MS;
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        started = false;
        if (executor != null) executor.shutdownNow();
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        } catch (Exception e) {
            Log.w(TAG, "stopForeground failed", e);
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntent.getActivity(this, 7201, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.notify_live_title))
                .setContentText(getString(R.string.notify_live_body))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setContentIntent(content)
                .build();
    }

    /** 常驻通知走独立低优先级通道,显式清音:不设 sound 会退回系统默认提示音 */
    private void ensureChannel() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notify_channel_live),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.notify_channel_live_desc));
        channel.setSound(null, (AudioAttributes) null);
        channel.enableVibration(false);
        channel.setShowBadge(false);
        nm.createNotificationChannel(channel);
    }
}
