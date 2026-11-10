package top.ihomy.app.updater;

import android.content.Intent;
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

/**
 * 应用内更新(插件名 {@code IhomyUpdater}):把前端下载好的安装包交给系统安装器。
 *
 * <p>分工:下载交给 {@code @capacitor/filesystem} 的 downloadFile(带进度事件)写到
 * {@code cache/updates/},这里只负责「打开安装器」这一件原生才能做的事——
 * 缓存文件必须经 FileProvider 换成 content:// 才允许交给安装器(Android 7+ 禁止 file:// 外发)。
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
    /** 与前端约定的下载目录(应用缓存下),文件名只允许纯文件名,防目录穿越 */
    private static final String DIR_NAME = "updates";

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
     * 调起系统安装器。参数 {@code file}:下载目录下的纯文件名(如 ihomy-V11.84.apk)。
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
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            // 安装器在另一个进程/应用里读这个 content://,必须显式授权
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
            call.resolve();
        } catch (Exception e) {
            Log.w(TAG, "调起安装器失败", e);
            call.reject("no-installer");
        }
    }

    /** 清理下载目录(默认只留 {@code keep} 指定的那个旧包,避免缓存越攒越大) */
    @PluginMethod
    public void cleanup(PluginCall call) {
        String keep = call.getString("keep", "");
        int removed = 0;
        File dir = new File(getContext().getCacheDir(), DIR_NAME);
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && !f.getName().equals(keep) && f.delete()) removed++;
            }
        }
        JSObject ret = new JSObject();
        ret.put("removed", removed);
        call.resolve(ret);
    }

    private boolean canInstall() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true;
        return getContext().getPackageManager().canRequestPackageInstalls();
    }

    /** 只接受下载目录下的 .apk 纯文件名,挡掉 ../ 与子路径 */
    private File apkFile(String name) {
        if (name == null || name.isEmpty()) return null;
        if (name.contains("/") || name.contains("\\") || name.contains("..")) return null;
        if (!name.toLowerCase(Locale.ROOT).endsWith(".apk")) return null;
        return new File(new File(getContext().getCacheDir(), DIR_NAME), name);
    }
}
