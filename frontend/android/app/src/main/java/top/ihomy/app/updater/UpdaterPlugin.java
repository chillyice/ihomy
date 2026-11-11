package top.ihomy.app.updater;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.util.Locale;

import top.ihomy.app.R;

/**
 * 应用内更新(插件名 {@code IhomyUpdater}):下载安装包 + 交给系统安装器,两件事都在原生侧做。
 *
 * <p>下载交给系统 {@link DownloadManager} 而不是 WebView 侧的 HTTP:壳是 WebView,
 * 一锁屏/切后台 JS 就停,文件流的 promise 也跟着断(表现为「锁屏后下载失败」)。
 * DownloadManager 由系统进程托管,锁屏、切后台、壳进程被杀都继续下,完成时下拉栏给通知,
 * 点通知即可直接进安装器;前端只按 {@link #downloadStatus} 轮询进度。
 *
 * <p>落点:应用外部私有目录 {@code Android/data/top.ihomy.app/files/updates/}
 * (卸载随应用清理,无需存储权限);老版本留在缓存目录的包仍能被 {@link #install} 认出。
 *
 * <p>两条前置条件:
 * <ul>
 *   <li>Manifest 声明 {@code REQUEST_INSTALL_PACKAGES}(否则系统直接拒绝安装);</li>
 *   <li>用户在本机「安装未知应用」里给 ihomy 授权(Android 8+ 每个应用独立开关),
 *       未授权时前端引导到 {@code ACTION_MANAGE_UNKNOWN_APP_SOURCES}。</li>
 * </ul>
 */
@CapacitorPlugin(name = "IhomyUpdater")
public class UpdaterPlugin extends Plugin {

    private static final String TAG = "IhomyUpdater";
    /** 安装包目录:外部私有目录下(系统下载器落盘),与前端约定的名字一致 */
    private static final String DIR_NAME = "updates";
    /** 上一次下载的 id / 文件名:进程被杀后前端重启靠它认领「下好了没」 */
    private static final String PREFS = "ihomy_updater";
    private static final String KEY_DOWNLOAD_ID = "download_id";
    private static final String KEY_FILE = "download_file";
    private static final String MIME_APK = "application/vnd.android.package-archive";

    /** 本机能否安装:系统版本够 + 已获「安装未知应用」授权 */
    @PluginMethod
    public void status(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("canInstall", canInstall());
        ret.put("sdk", Build.VERSION.SDK_INT);
        call.resolve(ret);
    }

    /** 打开本应用的「安装未知应用」授权页 */
    @PluginMethod
    public void openInstallSettings(PluginCall call) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            call.reject("not-needed");
            return;
        }
        try {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getContext().getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
            call.resolve();
        } catch (Exception e) {
            Log.w(TAG, "打不开安装授权页", e);
            call.reject("settings-unavailable");
        }
    }

    /**
     * 入队后台下载。参数 {@code url}(http/https)+ {@code file}(纯文件名,如 ihomy-V11.90.apk)。
     * 会先取消上一次仍在跑的下载并删掉同名旧文件,保证同一时刻只有一个 54MB 的包。
     * 失败码:{@code bad-url} / {@code bad-file} / {@code no-download-manager} / {@code enqueue-failed}。
     */
    @PluginMethod
    public void download(PluginCall call) {
        String url = call.getString("url", "");
        String name = apkName(call.getString("file", ""));
        if (!isHttpUrl(url)) {
            call.reject("bad-url");
            return;
        }
        if (name == null) {
            call.reject("bad-file");
            return;
        }
        DownloadManager dm = downloadManager();
        if (dm == null) {
            call.reject("no-download-manager");
            return;
        }
        SharedPreferences prefs = prefs();
        long previous = prefs.getLong(KEY_DOWNLOAD_ID, -1L);
        if (previous > 0) {
            // 换版本或重试:上一次的任务(可能还在跑)直接撤掉,免得两份包抢带宽
            try {
                dm.remove(previous);
            } catch (Exception ignored) {
                // 已被系统清掉,忽略
            }
        }
        File target = apkFile(name);
        if (target != null && target.isFile() && !target.delete()) {
            Log.w(TAG, "删不掉旧安装包:" + target.getName());
        }
        try {
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.setTitle(name);
            req.setDescription(getContext().getString(R.string.app_name));
            req.setMimeType(MIME_APK);
            // 系统托管:锁屏/切后台/进程被杀都不中断;完成时下拉栏留一条通知(点通知也能装)
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalFilesDir(getContext(), null, DIR_NAME + "/" + name);
            req.setAllowedOverMetered(true);
            req.setAllowedOverRoaming(false);
            long id = dm.enqueue(req);
            prefs.edit().putLong(KEY_DOWNLOAD_ID, id).putString(KEY_FILE, name).apply();
            JSObject ret = new JSObject();
            ret.put("id", id);
            ret.put("file", name);
            call.resolve(ret);
        } catch (Exception e) {
            Log.w(TAG, "入队下载失败", e);
            call.reject("enqueue-failed");
        }
    }

    /**
     * 查下载进度。参数可省({@code id}/{@code file} 缺省用上次记录)。
     * 返回 {@code status}(PENDING/RUNNING/PAUSED/SUCCESSFUL/FAILED/UNKNOWN/NONE)、
     * {@code bytes}/{@code total}(进度)、{@code size}(落盘实际字节)、{@code file}。
     * 任务被系统清掉(换机、清数据、系统回收)返回 NONE,前端据此重新入队。
     */
    @PluginMethod
    public void downloadStatus(PluginCall call) {
        SharedPreferences prefs = prefs();
        Long idArg = call.getLong("id");
        long id = idArg != null ? idArg : prefs.getLong(KEY_DOWNLOAD_ID, -1L);
        String name = apkName(call.getString("file", prefs.getString(KEY_FILE, "")));
        JSObject ret = new JSObject();
        ret.put("status", "NONE");
        ret.put("file", name == null ? "" : name);
        ret.put("bytes", 0);
        ret.put("total", 0);
        ret.put("size", diskSize(apkFile(name)));
        if (id <= 0) {
            call.resolve(ret);
            return;
        }
        DownloadManager dm = downloadManager();
        if (dm == null) {
            call.resolve(ret);
            return;
        }
        Cursor cursor = null;
        try {
            cursor = dm.query(new DownloadManager.Query().setFilterById(id));
            if (cursor == null || !cursor.moveToFirst()) {
                // 系统清过任务:文件可能还在(下一行 size 会告诉前端),但没有进度可查
                call.resolve(ret);
                return;
            }
            int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            long bytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            long total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
            ret.put("status", statusName(status));
            ret.put("bytes", Math.max(bytes, 0));
            ret.put("total", Math.max(total, 0));
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                ret.put("size", diskSize(apkFile(name)));
            }
            if (status == DownloadManager.STATUS_FAILED) {
                ret.put("reason", cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)));
            }
            call.resolve(ret);
        } catch (Exception e) {
            Log.w(TAG, "查询下载进度失败", e);
            call.reject("query-failed");
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    /**
     * 调起系统安装器。参数 {@code file}:纯文件名(如 ihomy-V11.84.apk)。
     * 失败码:{@code file-missing} 文件不存在 / {@code install-permission} 未授权 / {@code no-installer} 无安装器。
     */
    @PluginMethod
    public void install(PluginCall call) {
        File apk = apkFile(call.getString("file", ""));
        if (apk == null || !apk.isFile() || apk.length() <= 0) {
            call.reject("file-missing");
            return;
        }
        if (!canInstall()) {
            call.reject("install-permission");
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(
                    getContext(), getContext().getPackageName() + ".fileprovider", apk);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, MIME_APK);
            // 安装器在另一个进程/应用里读这个 content://,必须显式授权
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
            call.resolve();
        } catch (Exception e) {
            Log.w(TAG, "调起安装器失败", e);
            call.reject("no-installer");
        }
    }

    /** 清理安装包(默认只留 {@code keep} 指定的那个,避免缓存越攒越大);两处落点都清 */
    @PluginMethod
    public void cleanup(PluginCall call) {
        String keep = apkName(call.getString("keep", ""));
        int removed = 0;
        for (File dir : new File[] {
                new File(getContext().getExternalFilesDir(null), DIR_NAME),
                new File(getContext().getCacheDir(), DIR_NAME) }) {
            File[] files = dir.listFiles();
            if (files == null) continue;
            for (File f : files) {
                if (f.isFile() && !f.getName().equals(keep) && f.delete()) removed++;
            }
        }
        JSObject ret = new JSObject();
        ret.put("removed", removed);
        call.resolve(ret);
    }

    private DownloadManager downloadManager() {
        return (DownloadManager) getContext().getSystemService(Context.DOWNLOAD_SERVICE);
    }

    private SharedPreferences prefs() {
        return getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private boolean canInstall() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true;
        return getContext().getPackageManager().canRequestPackageInstalls();
    }

    private static boolean isHttpUrl(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://");
    }

    private static long diskSize(File file) {
        return file != null && file.isFile() ? file.length() : 0L;
    }

    private static String statusName(int status) {
        switch (status) {
            case DownloadManager.STATUS_PENDING:
                return "PENDING";
            case DownloadManager.STATUS_RUNNING:
                return "RUNNING";
            case DownloadManager.STATUS_PAUSED:
                return "PAUSED";
            case DownloadManager.STATUS_SUCCESSFUL:
                return "SUCCESSFUL";
            case DownloadManager.STATUS_FAILED:
                return "FAILED";
            default:
                return "UNKNOWN";
        }
    }

    /**
     * 解析成安全文件名:只接受纯 .apk 名字(挡掉 ../ 与子路径),非法返回 null。
     * 兼容前端传「未做校验的原始值」——校验只在这里做一次。
     */
    private static String apkName(String name) {
        if (name == null) return null;
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return null;
        if (trimmed.contains("/") || trimmed.contains("\\") || trimmed.contains("..")) return null;
        if (!trimmed.toLowerCase(Locale.ROOT).endsWith(".apk")) return null;
        return trimmed;
    }

    /** 安装包位置:新落点(外部私有目录)优先,老包可能还在缓存目录;都不存在时返回新落点 */
    private File apkFile(String name) {
        String safe = apkName(name);
        if (safe == null) return null;
        File external = new File(new File(getContext().getExternalFilesDir(null), DIR_NAME), safe);
        if (external.isFile()) return external;
        File cached = new File(new File(getContext().getCacheDir(), DIR_NAME), safe);
        if (cached.isFile()) return cached;
        return external;
    }
}
