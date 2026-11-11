package top.ihomy.app.updater;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

import top.ihomy.app.R;
import top.ihomy.app.widget.WidgetPrefs;

/**
 * 安装包落盘之后的两条路:能静默就静默,不能就通知点一下。
 *
 * <p><b>静默安装的前提</b>:Android 不允许普通应用绕过确认框自装 APK。只有
 * <em>设备所有者(device owner)</em> + <em>Android 15 及以上</em>才能用 {@link PackageInstaller} 的
 * {@code USER_ACTION_NOT_REQUIRED} 真静默安装(更老的系统没有公开的免确认接口)。本应用在设置里
 * 声明这个能力,但没有设备所有者时绝不假装成功——退化成一条「已下载好,点此安装」的通知,
 * 点一下进系统安装器(与手动更新确认量一致,不会偷偷装)。
 *
 * <p>成为设备所有者(一次性,需 USB 调试 + 设备上没有登录账号):
 * <pre>adb shell dpm set-device-owner top.ihomy.app/.updater.IhomyDeviceAdminReceiver</pre>
 */
public final class ApkInstallHelper {

    public static final String ACTION_INSTALL_RESULT = "top.ihomy.app.action.INSTALL_RESULT";
    public static final String CHANNEL_ID = "app-update";
    private static final String TAG = "IhomyUpdater";
    /** 固定 id:「已下载好」通知同一时刻只会有一条 */
    private static final int NOTIFY_ID = 7301;

    private ApkInstallHelper() {
    }

    /** 本机能否装(系统版本够 + 已获「安装未知应用」授权) */
    public static boolean canInstall(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true;
        return context.getPackageManager().canRequestPackageInstalls();
    }

    /** 当前包是不是设备所有者(只有它能静默安装) */
    public static boolean isDeviceOwner(Context context) {
        try {
            android.app.admin.DevicePolicyManager dpm =
                    (android.app.admin.DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            return dpm != null && dpm.isDeviceOwnerApp(context.getPackageName());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 后台下载完成后的落点:设备所有者 → 静默安装;否则发一条「点此安装」通知。
     * 关掉了闲时自动更新的直接跳过(用户改主意后不再打扰)。
     */
    public static void autoInstall(Context context, String fileName) {
        if (!WidgetPrefs.autoUpdateEnabled(context)) return;
        File apk = ApkDownloads.find(context, fileName);
        if (apk == null || apk.length() < ApkDownloads.MIN_BYTES) return;
        if (isDeviceOwner(context) && canInstall(context)) {
            try {
                installSilently(context, apk);
                return;
            } catch (Exception e) {
                // 部分机型/策略下会话被拒:退回通知确认,别让用户以为静默装上了
                Log.w(TAG, "静默安装失败,改为通知确认", e);
            }
        }
        notifyReady(context, fileName);
    }

    /**
     * 设备所有者专用:PackageInstaller 全量会话,{@code USER_ACTION_NOT_REQUIRED} 免确认。
     *
     * <p>免确认只有 Android 15+ 暴露了公开接口({@code setRequireUserAction(int)},API 35);
     * 更老的系统上没有任何公开途径绕过确认框,这里直接抛给调用方走通知确认。
     */
    public static void installSilently(Context context, File apk) throws Exception {
        if (Build.VERSION.SDK_INT < 35) {
            throw new UnsupportedOperationException("silent-install-needs-android-15");
        }
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(context.getPackageName());
        params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        int sessionId = installer.createSession(params);
        try (PackageInstaller.Session session = installer.openSession(sessionId)) {
            try (OutputStream out = session.openWrite("ihomy-update.apk", 0, apk.length());
                 InputStream in = new FileInputStream(apk)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                session.fsync(out);
            }
            Intent callback = new Intent(context, DownloadDoneReceiver.class)
                    .setAction(ACTION_INSTALL_RESULT)
                    .putExtra("session", sessionId);
            // 系统会在结果里回填 status/intent,所以必须 MUTABLE
            PendingIntent pending = PendingIntent.getBroadcast(context, sessionId, callback,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            session.commit(pending.getIntentSender());
        }
    }

    /** 一条「新版本已下载好」通知:点一下进系统安装器;没有安装授权时引导去设置 */
    public static void notifyReady(Context context, String fileName) {
        String version = versionOf(fileName);
        if (version.isEmpty() || version.equals(WidgetPrefs.autoUpdateNotified(context))) return;
        WidgetPrefs.setAutoUpdateNotified(context, version);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        ensureChannel(context, nm);
        String text = context.getString(R.string.update_ready_text, version);
        PendingIntent tap = canInstall(context)
                ? installPendingIntent(context, fileName)
                : settingsPendingIntent(context);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_shortcut_schedule)
                .setContentTitle(context.getString(R.string.update_ready_title))
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(tap)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setSound(null)
                .setVibrate(null)
                .setShowWhen(false);
        try {
            nm.notify(NOTIFY_ID, builder.build());
        } catch (Exception e) {
            Log.w(TAG, "更新通知发送失败", e);
        }
    }

    private static PendingIntent installPendingIntent(Context context, String fileName) {
        File apk = ApkDownloads.find(context, fileName);
        Intent intent;
        if (apk == null) {
            intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + context.getPackageName()));
        } else {
            intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(contentUri(context, apk), ApkDownloads.MIME_APK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static PendingIntent settingsPendingIntent(Context context) {
        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + context.getPackageName()))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static void ensureChannel(Context context, NotificationManager nm) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.update_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(context.getString(R.string.update_channel_desc));
        // 更新提示不响不震不显角标,只是等用户点一下
        channel.setShowBadge(false);
        channel.setSound(null, null);
        channel.enableVibration(false);
        nm.createNotificationChannel(channel);
    }

    /** ihomy-V11.92.apk → V11.92(通知文案用) */
    private static String versionOf(String fileName) {
        if (fileName == null || fileName.isEmpty()) return "";
        String name = fileName.startsWith("ihomy-") ? fileName.substring("ihomy-".length()) : fileName;
        return name.endsWith(".apk") ? name.substring(0, name.length() - 4) : name;
    }

    /** 安装包 → 交给安装器的 content://(FileProvider 已声明 updates/ 外部私有目录) */
    public static Uri contentUri(Context context, File apk) {
        return FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", apk);
    }
}
