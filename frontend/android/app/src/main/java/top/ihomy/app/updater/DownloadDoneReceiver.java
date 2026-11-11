package top.ihomy.app.updater;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.util.Log;

/**
 * 两条原生回调都落在同一个接收器:
 *
 * <ul>
 *   <li>{@link DownloadManager#ACTION_DOWNLOAD_COMPLETE}:闲时/手动下载的包下完了 —— 设备所有者
 *       直接静默安装,其余人发一条「点此安装」通知(见 {@link ApkInstallHelper});</li>
 *   <li>{@link ApkInstallHelper#ACTION_INSTALL_RESULT}:静默安装会话的结果。成功时系统会替换本应用
 *       并杀掉进程,基本看不到这条;失败(签名/校验/策略拒绝)要留下日志与通知兜底。</li>
 * </ul>
 *
 * <p>清单里 {@code exported="false"}:DOWNLOAD_COMPLETE 由系统按包名定向投递,外部应用发不进来。
 */
public class DownloadDoneReceiver extends BroadcastReceiver {

    private static final String TAG = "IhomyUpdater";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (ApkInstallHelper.ACTION_INSTALL_RESULT.equals(action)) {
            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (status != PackageInstaller.STATUS_SUCCESS) {
                String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                Log.w(TAG, "静默安装失败 status=" + status + " msg=" + message);
                // 退回通知:用户点一下走系统安装器
                ApkDownloads.State st = ApkDownloads.query(context);
                if (!st.file.isEmpty()) ApkInstallHelper.notifyReady(context, st.file);
            }
            return;
        }
        if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) return;
        long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
        if (id <= 0) return;
        final PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                ApkDownloads.State st = ApkDownloads.query(context);
                if (st.id != id || st.file.isEmpty()) return;
                if (!"SUCCESSFUL".equals(st.status)) return;
                if (st.size < ApkDownloads.MIN_BYTES) {
                    Log.w(TAG, "下载完成但包体积异常:" + st.size);
                    ApkDownloads.cleanup(context, "");
                    return;
                }
                ApkInstallHelper.autoInstall(context, st.file);
            } catch (Exception e) {
                Log.w(TAG, "处理下载完成失败", e);
            } finally {
                pending.finish();
            }
        }, "ihomy-apk-install").start();
    }
}
