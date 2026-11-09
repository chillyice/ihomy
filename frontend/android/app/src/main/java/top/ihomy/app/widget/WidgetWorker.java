package top.ihomy.app.widget;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

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
            Widgets.updateAll(ctx);
            return Result.success();
        } catch (Exception e) {
            // 无令牌 = 未登录态,卡片显示「打开 App 登录」;网络失败 = 保旧缓存,两者都不是错误
            Log.w(TAG, "widget sync skipped: " + e.getMessage());
            Widgets.updateAll(ctx);
            return Result.success();
        }
    }
}
