package top.ihomy.app.weather;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.TimeUnit;

import top.ihomy.app.widget.WidgetPrefs;

/**
 * 每日天气推送:每天在用户设定的时刻拉一次天气详情,发一条安静的系统通知。
 *
 * <p>调度用 WorkManager 的 24 小时周期任务 + 首次延迟对齐到目标时刻(项目口径:后台能力一律
 * WorkManager,不做保活、不申请精确闹钟)。周期任务可能被系统推迟几分钟到几小时,所以发之前
 * 再校验一次「现在离目标时刻是否在 3 小时内」:手机整夜关机、跨时区后不会在半夜补一条。
 *
 * <p>同一天只发一条(记 last_weather_date);预览(设置页按钮)绕过这个限制,但也不占用当天名额。
 */
public class DailyWeatherWorker extends Worker {

    private static final String TAG = "IhomyWeather";
    public static final String PERIODIC_WORK = "ihomy-daily-weather";
    public static final String PREVIEW_WORK = "ihomy-daily-weather-preview";
    /** 输入键:true = 设置页预览,跳过「今天已发过」与时刻窗口校验 */
    public static final String INPUT_PREVIEW = "preview";
    /** 与目标时刻的允许偏差:超过就不发(宁可今天缺一条,也不半夜响) */
    private static final int WINDOW_MINUTES = 180;

    public DailyWeatherWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /** 确保周期任务在排(幂等):App 启动与令牌下发时调用,已有同任务则不动 */
    public static void ensureScheduled(Context context) {
        if (!WidgetPrefs.dailyWeatherEnabled(context)) {
            cancelSchedule(context);
            return;
        }
        enqueue(context, ExistingPeriodicWorkPolicy.KEEP);
    }

    /** 时间或开关变化后重排:换时刻必须 CANCEL_AND_REENQUEUE,否则旧延迟不会跟着变 */
    public static void reschedule(Context context) {
        if (!WidgetPrefs.dailyWeatherEnabled(context)) {
            cancelSchedule(context);
            return;
        }
        enqueue(context, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE);
    }

    /** 设置页预览:独立的一次性任务,立即跑,不占用当天名额 */
    public static void preview(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(DailyWeatherWorker.class)
                .setInputData(new androidx.work.Data.Builder().putBoolean(INPUT_PREVIEW, true).build())
                .setConstraints(connected())
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(PREVIEW_WORK, ExistingWorkPolicy.REPLACE, request);
    }

    private static void enqueue(Context context, ExistingPeriodicWorkPolicy policy) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(DailyWeatherWorker.class, 24, TimeUnit.HOURS)
                .setInitialDelay(delayMinutes(context), TimeUnit.MINUTES)
                .setConstraints(connected())
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC_WORK, policy, request);
    }

    private static Constraints connected() {
        return new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
    }

    /** 距离下一个目标时刻还有多少分钟(今天已过点就排到明天),至少 1 分钟 */
    static long delayMinutes(Context context) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime target = now.toLocalDate().atStartOfDay()
                .plusMinutes(WidgetPrefs.dailyWeatherMinute(context));
        if (!target.isAfter(now)) target = target.plusDays(1);
        return Math.max(1, Duration.between(now, target).toMinutes());
    }

    /** 当前时刻与目标时刻的分钟差(环形,跨零点取小值) */
    static int minuteDistance(int nowMinute, int targetMinute) {
        int diff = Math.abs(nowMinute - targetMinute);
        return Math.min(diff, 24 * 60 - diff);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        boolean preview = getInputData().getBoolean(INPUT_PREVIEW, false);
        if (!preview && !WidgetPrefs.dailyWeatherEnabled(ctx)) return Result.success();
        // 没有卡片令牌 = 未登录/已登出:天气会退化成服务器 IP 定位,宁可不发
        if (WidgetPrefs.token(ctx).isEmpty()) return Result.success();

        String today = LocalDate.now().toString();
        if (!preview) {
            int nowMinute = LocalTime.now().getHour() * 60 + LocalTime.now().getMinute();
            if (minuteDistance(nowMinute, WidgetPrefs.dailyWeatherMinute(ctx)) > WINDOW_MINUTES) {
                Log.i(TAG, "daily weather skipped: outside the target window");
                return Result.success();
            }
            if (today.equals(WidgetPrefs.lastWeatherDate(ctx))) return Result.success();
        }

        try {
            WeatherSnapshot snapshot = DailyWeatherRepository.fetch(ctx);
            if (snapshot == null) {
                // 天气源未配置:不是故障,等配置好自然会有
                Log.i(TAG, "daily weather skipped: weather not configured");
                return Result.success();
            }
            DailyWeatherNotifier.show(ctx, snapshot, WeatherAdvice.of(snapshot));
            if (!preview) WidgetPrefs.setLastWeatherDate(ctx, today);
            return Result.success();
        } catch (Exception e) {
            Log.w(TAG, "daily weather fetch failed: " + e.getMessage());
            // 令牌过期等未登录态重试也没用,等下个周期;断网/上游超时交给 WorkManager 退避重试
            if (preview || String.valueOf(e.getMessage()).contains("401")) return Result.success();
            return Result.retry();
        }
    }

    private static void cancelSchedule(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK);
        DailyWeatherNotifier.cancel(context);
    }
}
