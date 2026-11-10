package top.ihomy.app.diagnostics;

import android.util.Log;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 诊断桥(插件名 {@code IhomyDiagnostics}):前端错误落盘、崩溃/错误列表、按文件名读取、清空。
 *
 * <p>只服务设置页「诊断信息」块,不做自动上传(计划 §5.3)。
 * {@code read} 只认 {@code diagnostics/} 下的固定文件名,挡掉 {@code ../} 与任意路径。
 */
@CapacitorPlugin(name = "IhomyDiagnostics")
public class DiagnosticsPlugin extends Plugin {

    private static final String TAG = "IhomyDiagnostics";

    /** 前端批量上报(内存环形缓冲 200 条,切后台时 flush) */
    @PluginMethod
    public void report(PluginCall call) {
        JSArray array = call.getArray("lines");
        List<String> lines = new ArrayList<>();
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                Object item = array.opt(i);
                if (item != null) lines.add(String.valueOf(item));
            }
        }
        int stored = CrashStore.appendFrontError(getContext(), lines);
        JSObject ret = new JSObject();
        ret.put("stored", stored);
        call.resolve(ret);
    }

    /** 崩溃份数 + 前端错误条数 */
    @PluginMethod
    public void list(PluginCall call) {
        File[] files = CrashStore.crashFiles(getContext());
        JSArray crashes = new JSArray();
        for (File file : files) {
            JSObject item = new JSObject();
            item.put("name", file.getName());
            item.put("size", file.length());
            item.put("at", file.lastModified());
            crashes.put(item);
        }
        JSObject ret = new JSObject();
        ret.put("crashes", crashes);
        ret.put("errors", CrashStore.frontErrorCount(getContext()));
        call.resolve(ret);
    }

    /** 按文件名读取(只允许 diagnostics/ 下的固定名字) */
    @PluginMethod
    public void read(PluginCall call) {
        String name = call.getString("name", "");
        if (!CrashStore.allowed(name)) {
            call.reject("not-allowed");
            return;
        }
        try {
            JSObject ret = new JSObject();
            ret.put("name", name);
            ret.put("text", CrashStore.read(getContext(), name));
            call.resolve(ret);
        } catch (Exception e) {
            Log.w(TAG, "read failed: " + name, e);
            call.reject("read-failed");
        }
    }

    /** 清空全部诊断文件(崩溃 + 前端错误) */
    @PluginMethod
    public void clear(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("removed", CrashStore.clear(getContext()));
        call.resolve(ret);
    }
}
