package top.ihomy.app.health;

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

import java.util.concurrent.TimeUnit;

/**
 * 每日健康同步:读 Health Connect 增量 → POST /health/ingest。
 *
 * 只在用户打开「自动同步」且已授权分组时跑;令牌是设置页生成的 hk_ 长期令牌,
 * 与登录态无关(后台没有 WebView,拿不到也不该拿用户 JWT)。
 * Android 15+ 的后台读取还要 Health Connect 的「在后台读取健康数据」授权,
 * 没有该授权时读取会抛错——这里只记一次失败结果,不重试轰炸,前台「立即同步」仍然可用。
 */
public class HealthSyncWorker extends Worker {

    private static final String TAG = "IhomyHealth";
    public static final String PERIODIC_WORK = "ihomy-health-daily-sync";

    public HealthSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /** 每日一次、有网才跑;KEEP 保证重复调用不重排 */
    public static void schedulePeriodic(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(HealthSyncWorker.class, 1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    public static void cancel(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK);
    }

    /** 设置页开关:开=排期 + 立即跑一次,关=撤排期 */
    public static void applyAutoSync(Context context, boolean on) {
        HealthPrefs.setAutoSync(context, on);
        if (on) {
            schedulePeriodic(context);
        } else {
            cancel(context);
        }
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        try {
            if (!HealthPrefs.autoSync(ctx)) return Result.success();
            if (HealthPrefs.token(ctx).isEmpty()) return Result.success();
            String provider = HealthDataPlugin.providerOf(ctx);
            if (provider == null) return Result.success();
            // 增量为主:窗口内多取 2 天,作息跨零点时昨晚的会话不会漏
            HealthSyncer.run(ctx, HealthPrefs.token(ctx), HealthPrefs.groups(ctx), 2, provider);
            return Result.success();
        } catch (Exception e) {
            // 没授权后台读取/网络失败都走这里;不 retry(第二天还有一次),只留痕给设置页
            Log.w(TAG, "daily health sync failed: " + e.getMessage());
            HealthPrefs.markSync(ctx, false, e.getMessage() == null ? "同步失败" : e.getMessage());
            return Result.success();
        }
    }
}
