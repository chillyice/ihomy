package top.ihomy.app.updater;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import top.ihomy.app.ApiBase;
import top.ihomy.app.net.SimpleHttp;
import top.ihomy.app.widget.WidgetPrefs;

/**
 * 闲时自动更新:设备空闲 + 非计费网络(通常就是夜里连着 Wi-Fi)时,静默把新版本包拉下来。
 *
 * <p>约束:WorkManager 周期任务 + {@code setRequiresDeviceIdle(true)} + {@code UNMETERED} +
 * 电量不低。设备空闲是系统判定的「闲时」(与 Doze 同源),不自己猜屏幕/使用时长。
 *
 * <p>下完之后:设备所有者 → 静默安装;否则发一条不响的「已下载好,点此安装」通知
 * (见 {@link ApkInstallHelper})。下载本身交给系统 DownloadManager,进程被杀也继续。
 *
 * <p>只查公开的 {@code /public/app/latest},不需要令牌——未登录/令牌过期也能自动更新。
 */
public class AutoUpdateWorker extends Worker {

    public static final String PERIODIC_WORK = "ihomy-auto-update";
    private static final String TAG = "IhomyAutoUpdate";
    /** 12 小时一个周期:空闲窗口通常只有夜里几个,频率再高也不会多跑 */
    private static final long PERIOD_HOURS = 12;

    public AutoUpdateWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /** 确保周期任务在排(幂等);关掉开关时撤掉排期 */
    public static void ensureScheduled(Context context) {
        if (!WidgetPrefs.autoUpdateEnabled(context)) {
            cancel(context);
            return;
        }
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(AutoUpdateWorker.class,
                PERIOD_HOURS, TimeUnit.HOURS)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresDeviceIdle(true)
                        .setRequiresBatteryNotLow(true)
                        .build())
                .build();
        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    public static void cancel(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        if (!WidgetPrefs.autoUpdateEnabled(context)) return Result.success();
        String body;
        try {
            body = SimpleHttp.get(ApiBase.URL + "/public/app/latest", "");
        } catch (Exception e) {
            // 网络抖动/服务端重启:下一周期再来(不 retry,避免空闲窗口里反复唤醒)
            Log.i(TAG, "查更新失败,跳过本轮:" + e.getMessage());
            return Result.success();
        }
        try {
            JSONObject data = new JSONObject(body).optJSONObject("data");
            if (data == null) return Result.success();
            String version = data.optString("version", "").trim();
            String path = data.optString("url", "").trim();
            if (version.isEmpty() || path.isEmpty()) return Result.success();
            if (AppVersion.compare(version, currentVersion(context)) <= 0) {
                // 已是最新:顺手清掉历史安装包(常驻一份 14MB 的包没意义)
                ApkDownloads.cleanup(context, "");
                return Result.success();
            }
            String file = "ihomy-" + version + ".apk";
            ApkDownloads.State st = ApkDownloads.query(context);
            boolean ready = file.equals(st.file)
                    && "SUCCESSFUL".equals(st.status)
                    && st.size >= ApkDownloads.MIN_BYTES;
            if (!ready) {
                // 任务被系统清掉(换机/清数据)但包还在的情况
                java.io.File apk = ApkDownloads.find(context, file);
                ready = apk != null && apk.length() >= ApkDownloads.MIN_BYTES;
            }
            if (ready) {
                ApkInstallHelper.autoInstall(context, file);
                return Result.success();
            }
            if (file.equals(st.file) && ApkDownloads.active(st.status)) {
                return Result.success(); // 已经在后台下着,别重复入队
            }
            String url = path.startsWith("http") ? path : ApiBase.ORIGIN + path;
            // 下包本身不挂通知(用户没主动点):设备所有者完成后静默,其余人只留一条「已下载好」提醒
            ApkDownloads.enqueue(context, url, file, true);
            Log.i(TAG, "闲时更新已入队:" + file);
            return Result.success();
        } catch (Exception e) {
            Log.w(TAG, "处理更新信息失败", e);
            return Result.success();
        }
    }

    private static String currentVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionName == null ? "" : info.versionName;
        } catch (Exception e) {
            return "";
        }
    }
}
