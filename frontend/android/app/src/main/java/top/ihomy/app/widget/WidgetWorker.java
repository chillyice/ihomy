package top.ihomy.app.widget;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.time.LocalTime;
import java.util.List;

/**
 * 卡片数据同步:WorkManager 周期任务(30 分钟)+ App 内改动后的一次性触发。
 * 成功后把快照写缓存并通知三个 provider 重绘;失败时保持旧缓存(断网显示上次数据,计划 §6.5.3)。
 */
public class WidgetWorker extends Worker {

    private static final String TAG = "IhomyWidget";
    public static final String PERIODIC_WORK = "ihomy-widget-periodic";

    public WidgetWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /** 周期任务注册:App 启动时由 WidgetBridgePlugin 调一次(幂等,已有同命任务不重排) */
    public static void schedulePeriodic(Context context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP,
                new androidx.work.PeriodicWorkRequest.Builder(WidgetWorker.class, 30,
                        java.util.concurrent.TimeUnit.MINUTES).build());
    }

    /** 一次性刷新:App 内日程改动后由 WidgetBridgePlugin 触发 */
    public static void refreshNow(Context context) {
        WorkManager.getInstance(context).enqueue(new OneTimeWorkRequest.Builder(WidgetWorker.class).build());
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        try {
            List<WidgetRepository.Occurrence> items = WidgetRepository.fetchOccurrences(ctx);
            WidgetRepository.cacheItems(ctx, "today", items);
            WidgetRepository.cacheItems(ctx, "reminder", items);
            WidgetRepository.cacheItems(ctx, "countdown", items);
        } catch (Exception e) {
            // 无令牌 = 未登录态,卡片显示「打开 App 登录」;网络失败 = 保旧缓存,两者都不是错误
            Log.w(TAG, "widget sync skipped: " + e.getMessage());
        }
        Widgets.updateAll(ctx);
        // 后台未读与卡片同一条 30 分钟周期,不新增任务(计划 §5.2)
        try {
            syncUnread(ctx);
        } catch (Exception e) {
            Log.w(TAG, "unread sync skipped: " + e.getMessage());
        }
        return Result.success();
    }

    /**
     * 只在「计数增长且总数 >0」时发通知:固定 id 覆盖、内容不变不重发。
     * 23:00–06:00 静默,但静默期仍更新基线——早上不会因为昨晚的增长补响一声。
     */
    private static void syncUnread(Context ctx) throws Exception {
        if (!WidgetPrefs.unreadNotifyEnabled(ctx)) return;
        WidgetRepository.Unread unread = WidgetRepository.fetchUnread(ctx);
        long lastNotice = WidgetPrefs.lastUnreadNotice(ctx);
        long lastChat = WidgetPrefs.lastUnreadChat(ctx);

        if (quietHours()) {
            if (unread.total() <= 0) UnreadNotifier.cancel(ctx);
            WidgetPrefs.setLastUnread(ctx, unread.notification, unread.chat);
            return;
        }
        if (unread.total() <= 0) {
            UnreadNotifier.cancel(ctx);
        } else if (unread.notification > lastNotice || unread.chat > lastChat) {
            UnreadNotifier.notifyUnread(ctx, unread.chat, unread.notification);
        }
        WidgetPrefs.setLastUnread(ctx, unread.notification, unread.chat);
    }

    /** 与服务端提醒口径一致:23:00–06:00 不打扰 */
    private static boolean quietHours() {
        int hour = LocalTime.now().getHour();
        return hour >= 23 || hour < 6;
    }
}
