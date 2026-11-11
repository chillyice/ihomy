package top.ihomy.app.updater;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;

import top.ihomy.app.widget.WidgetPrefs;

/**
 * 应用内更新(插件名 {@code IhomyUpdater}):下载安装包 + 交给系统安装器,两件事都在原生侧做。
 *
 * <p>下载交给系统 {@link android.app.DownloadManager}(见 {@link ApkDownloads}):壳是 WebView,
 * 一锁屏/切后台 JS 就停,文件流的 promise 也跟着断。系统进程托管后锁屏、切后台、壳进程被杀
 * 都继续下,前端只按 {@link #downloadStatus} 轮询进度。
 *
 * <p>另外两条「闲时」能力在这里开合:周期任务 {@link AutoUpdateWorker}(设备空闲 + 非计费网络时
 * 自动下载)与 {@link ApkInstallHelper}(设备所有者静默安装 / 其余人一条通知确认)。
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

    /** 插件随桥加载:顺便确保闲时自动更新在排(幂等;开关关着时内部会撤掉) */
    @Override
    public void load() {
        AutoUpdateWorker.ensureScheduled(getContext());
    }

    /** 本机能否安装:系统版本够 + 已获「安装未知应用」授权 */
    @PluginMethod
    public void status(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("canInstall", ApkInstallHelper.canInstall(getContext()));
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
     * 入队后台下载(用户点「立即更新」)。参数 {@code url}(http/https)+ {@code file}(纯文件名)。
     * 会先取消上一次仍在跑的下载并删掉同名旧文件。
     * 失败码:{@code bad-url} / {@code bad-file} / {@code no-download-manager} / {@code enqueue-failed}。
     */
    @PluginMethod
    public void download(PluginCall call) {
        String url = call.getString("url", "");
        String name = ApkDownloads.safeName(call.getString("file", ""));
        if (!ApkDownloads.isHttpUrl(url)) {
            call.reject("bad-url");
            return;
        }
        if (name == null) {
            call.reject("bad-file");
            return;
        }
        try {
            long id = ApkDownloads.enqueue(getContext(), url, name, false);
            JSObject ret = new JSObject();
            ret.put("id", id);
            ret.put("file", name);
            call.resolve(ret);
        } catch (IllegalStateException e) {
            call.reject("no-download-manager");
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
        ApkDownloads.State st = ApkDownloads.query(getContext());
        JSObject ret = new JSObject();
        ret.put("status", st.status);
        ret.put("file", st.file);
        ret.put("bytes", st.bytes);
        ret.put("total", st.total);
        ret.put("size", st.size);
        if ("FAILED".equals(st.status)) ret.put("reason", st.reason);
        call.resolve(ret);
    }

    /**
     * 调起系统安装器。参数 {@code file}:纯文件名(如 ihomy-V11.84.apk)。
     * 失败码:{@code file-missing} 文件不存在 / {@code install-permission} 未授权 / {@code no-installer} 无安装器。
     */
    @PluginMethod
    public void install(PluginCall call) {
        File apk = ApkDownloads.find(getContext(), call.getString("file", ""));
        if (apk == null || apk.length() <= 0) {
            call.reject("file-missing");
            return;
        }
        if (!ApkInstallHelper.canInstall(getContext())) {
            call.reject("install-permission");
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(ApkInstallHelper.contentUri(getContext(), apk), ApkDownloads.MIME_APK);
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
        JSObject ret = new JSObject();
        ret.put("removed", ApkDownloads.cleanup(getContext(), call.getString("keep", "")));
        call.resolve(ret);
    }

    /** 设置页:闲时自动更新开关 + 本机是否设备所有者(只有它能真静默安装) */
    @PluginMethod
    public void getAutoUpdate(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("enabled", WidgetPrefs.autoUpdateEnabled(getContext()));
        ret.put("deviceOwner", ApkInstallHelper.isDeviceOwner(getContext()));
        call.resolve(ret);
    }

    /** 设置页:开/关闲时自动更新;关掉时同时撤排期,开的时候重排并清掉「已提示过」去重 */
    @PluginMethod
    public void setAutoUpdate(PluginCall call) {
        Boolean enabled = call.getBoolean("enabled", true);
        boolean on = Boolean.TRUE.equals(enabled);
        WidgetPrefs.setAutoUpdateEnabled(getContext(), on);
        if (on) {
            WidgetPrefs.setAutoUpdateNotified(getContext(), "");
            AutoUpdateWorker.ensureScheduled(getContext());
        } else {
            AutoUpdateWorker.cancel(getContext());
        }
        call.resolve();
    }
}
