package top.ihomy.app.reminder;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;

import top.ihomy.app.widget.WidgetPrefs;
import top.ihomy.app.widget.WidgetRepository;

/**
 * 提醒兜底自愈:每天在后台对一次账,发现「超过 7 天没校准」且未来 3 天有未完成日程时,
 * 发一条提示让用户打开 App(打开即触发 JS 侧真正的闹钟重排)。
 *
 * <p>本 Worker **不注册任何闹钟**——原生侧保留闹钟会与 JS 对账形成两套来源,必然双响(计划 D3)。
 * 真正的缺口只有「注册窗口 30 天外」与「网页端改过提醒但 App 一直没开」,提示 + 打开即自愈足够。
 */
public class StaleCheckWorker extends Worker {

    private static final String TAG = "IhomyReminder";
    public static final String PERIODIC_WORK = "ihomy-reminder-stale-check";
    /** 超过这个时间没成功对账,就认为本地闹钟可能过期 */
    private static final long STALE_MS = 7L * 24 * 60 * 60 * 1000;
    /** 未来几天内有日程才值得打扰 */
    private static final int LOOKAHEAD_DAYS = 3;

    public StaleCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /** 每日一次、有网才跑;KEEP 策略保证重复调用不会重排 */
    public static void schedulePeriodic(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(StaleCheckWorker.class, 1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        try {
            if (WidgetPrefs.token(ctx).isEmpty()) return Result.success();
            long now = System.currentTimeMillis();
            long lastSync = WidgetPrefs.reminderSyncAt(ctx);
            if (lastSync > 0 && now - lastSync <= STALE_MS) return Result.success();

            String today = LocalDate.now().toString();
            if (today.equals(WidgetPrefs.lastNudgeDate(ctx))) return Result.success();

            List<WidgetRepository.Occurrence> items = WidgetRepository.fetchOccurrences(ctx);
            int count = upcoming(items);
            if (count <= 0) return Result.success();

            ReminderNotifier.notifyStale(ctx, count);
            WidgetPrefs.setLastNudgeDate(ctx, today);
            Log.i(TAG, "stale nudge sent, items=" + count);
            return Result.success();
        } catch (Exception e) {
            // 无令牌/断网/接口失败都不是错误:下次有网再试,保持 App 打开时自愈的口径
            Log.w(TAG, "stale check skipped: " + e.getMessage());
            return Result.success();
        }
    }

    /** 未来 3 天内未完成的日程条数 */
    private static int upcoming(List<WidgetRepository.Occurrence> items) {
        LocalDate today = LocalDate.now();
        LocalDate limit = today.plusDays(LOOKAHEAD_DAYS - 1L);
        int count = 0;
        for (WidgetRepository.Occurrence item : items) {
            if (item == null || item.done || item.date == null || item.date.isEmpty()) continue;
            try {
                LocalDate date = LocalDate.parse(item.date);
                if (!date.isBefore(today) && !date.isAfter(limit)) count++;
            } catch (Exception ignored) {
                // 服务端日期格式异常:跳过这一条,不影响其它日程
            }
        }
        return count;
    }
}
