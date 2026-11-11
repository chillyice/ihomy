package top.ihomy.app.widget;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.time.LocalDate;
import java.util.List;

import top.ihomy.app.live.AppVisibility;

/**
 * 卡片数据同步:WorkManager 周期任务(30 分钟)+ App 内改动后的一次性触发。
 * 成功后把快照写缓存并通知三个 provider 重绘;失败时保持旧缓存(断网显示上次数据,计划 §6.5.3)。
 * 带输入 toggle_key 时先勾选再整轮重拉(卡片勾选动作,见 WidgetActionReceiver)。
 */
public class WidgetWorker extends Worker {

    private static final String TAG = "IhomyWidget";
    public static final String PERIODIC_WORK = "ihomy-widget-periodic";
    /** 输入键:待勾选的 occurrences key(kind:id:date),由 WidgetActionReceiver 写入 */
    public static final String INPUT_TOGGLE_KEY = "toggle_key";

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
            toggleIfRequested(ctx);
        } catch (Exception e) {
            // 勾选失败(断网/令牌过期/非今天)行保持原样,下一轮整拉自愈;最终裁决在后端
            Log.w(TAG, "widget toggle skipped: " + e.getMessage());
        }
        try {
            List<WidgetRepository.Occurrence> items = WidgetRepository.fetchOccurrences(ctx);
            WidgetRepository.cacheItems(ctx, "today", items);
            WidgetRepository.cacheItems(ctx, "reminder", items);
            WidgetRepository.cacheItems(ctx, "countdown", items);
        } catch (Exception e) {
            // 无令牌 = 未登录态,卡片显示「打开 App 登录」;网络失败 = 保旧缓存,两者都不是错误
            Log.w(TAG, "widget sync skipped: " + e.getMessage());
        }
        // 今日卡农历/天气:与日程拉取互不拖垮,失败时保留上一份缓存
        try {
            WidgetRepository.cacheTodayInfo(ctx, WidgetRepository.fetchTodayInfo(ctx));
        } catch (Exception e) {
            Log.w(TAG, "today info sync skipped: " + e.getMessage());
        }
        Widgets.updateAll(ctx);
        // 后台未读与卡片同一条 30 分钟周期,不新增任务(计划 §5.2);
        // 前台服务运行时另有 30 秒一次的同步,两边共用 UnreadSync(基线互不冲突)
        try {
            // 前台服务已在跑时,这里只是兜底;用户正看着 App 时不叠系统通知
            UnreadSync.run(ctx, !AppVisibility.isForeground());
        } catch (Exception e) {
            Log.w(TAG, "unread sync skipped: " + e.getMessage());
        }
        return Result.success();
    }

    /**
     * 卡片勾选请求:只处理今天的 REMINDER/TASK(与服务端 occurrences key 同构);
     * 其余情况静默跳过,ReminderController 是最终裁决(家庭隔离 + WIDGET 只准今天)。
     */
    private void toggleIfRequested(Context ctx) throws Exception {
        String key = getInputData().getString(INPUT_TOGGLE_KEY);
        if (key == null || key.isEmpty()) return;
        String[] parts = key.split(":", 3);
        if (parts.length != 3) return;
        if (!"REMINDER".equals(parts[0]) && !"TASK".equals(parts[0])) return;
        if (!LocalDate.now().toString().equals(parts[2])) return;
        WidgetRepository.toggleDone(ctx, Long.parseLong(parts[1]), parts[2]);
    }
}
