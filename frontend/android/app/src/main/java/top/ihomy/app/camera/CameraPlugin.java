package top.ihomy.app.camera;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;

import androidx.activity.result.ActivityResult;
import androidx.core.content.FileProvider;

import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 相机直传(插件名 {@code IhomyCamera}):拍照后把图交回前端,走现有相册/记账上传接口。
 *
 * <p>用系统相机 {@code ACTION_IMAGE_CAPTURE} + {@code EXTRA_OUTPUT} 写进应用缓存目录。
 * manifest 里因为通话的 getUserMedia 必须声明 CAMERA,系统相机代拍就变成「应用已声明但未授权会抛
 * SecurityException」,所以调起系统相机前先补一次运行时授权;用户拒绝就按 denied 回前端。
 *
 * <p>回传用 base64:WebView 侧读 file:// 受同源策略限制,而我们又不希望为此开放
 * Capacitor 的本地文件映射口子。单张限 12MB(与后端图片上限 10MB 留出余量)。
 */
@CapacitorPlugin(
        name = "IhomyCamera",
        permissions = { @Permission(alias = "camera", strings = { Manifest.permission.CAMERA }) })
public class CameraPlugin extends Plugin {

    private static final String TAG = "IhomyCamera";
    private static final long MAX_FILE_BYTES = 12L * 1024L * 1024L;
    private static final String DIR_NAME = "camera";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private File pendingFile;

    /** 是否有可用相机(设备/系统相机应用) */
    @PluginMethod
    public void available(PluginCall call) {
        JSObject ret = new JSObject();
        boolean has = false;
        try {
            has = new Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    .resolveActivity(getContext().getPackageManager()) != null;
        } catch (Exception ignored) {
            // 取不到就当没有
        }
        ret.put("available", has);
        call.resolve(ret);
    }

    /** 打开系统相机;拍好返回 {name,size},取消返回 reject("cancel") */
    @PluginMethod
    public void capture(PluginCall call) {
        // 应用已声明 CAMERA:未授权时 ACTION_IMAGE_CAPTURE 会直接抛 SecurityException,
        // 所以先补运行时授权,拿到再进相机
        if (getPermissionState("camera") != PermissionState.GRANTED) {
            requestPermissionForAlias("camera", call, "cameraPermissionCallback");
            return;
        }
        openCamera(call);
    }

    /** 相机授权回调:允许才继续进系统相机,拒绝按 denied 回前端(前端目前统一按失败提示) */
    @PermissionCallback
    private void cameraPermissionCallback(PluginCall call) {
        if (getPermissionState("camera") == PermissionState.GRANTED) {
            openCamera(call);
        } else {
            call.reject("denied");
        }
    }

    private void openCamera(PluginCall call) {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getContext().getPackageManager()) == null) {
            call.reject("no_camera");
            return;
        }
        try {
            File dir = new File(getContext().getCacheDir(), DIR_NAME);
            if (!dir.exists() && !dir.mkdirs()) {
                call.reject("no_space");
                return;
            }
            clearDir(dir);
            String name = "IMG_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".jpg";
            File out = new File(dir, name);
            Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", out);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            pendingFile = out;
            startActivityForResult(call, intent, "captureResult");
        } catch (ActivityNotFoundException e) {
            call.reject("no_camera");
        } catch (Exception e) {
            Log.w(TAG, "打开相机失败", e);
            call.reject("failed");
        }
    }

    /** 系统相机回写完成后回调(签名必须是 PluginCall + ActivityResult) */
    @ActivityCallback
    private void captureResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        File out = pendingFile;
        pendingFile = null;
        if (result == null || result.getResultCode() != Activity.RESULT_OK) {
            deleteQuietly(out);
            call.reject("cancel");
            return;
        }
        if (out == null || !out.isFile() || out.length() <= 0) {
            deleteQuietly(out);
            call.reject("failed");
            return;
        }
        JSObject ret = new JSObject();
        ret.put("name", out.getName());
        ret.put("size", out.length());
        ret.put("path", out.getAbsolutePath());
        call.resolve(ret);
    }

    /**
     * 读回刚拍的照片(base64)。只允许读 {@code <cache>/camera/} 内的文件,
     * 不让这个接口变成任意文件读取口子。
     */
    @PluginMethod
    public void readFile(PluginCall call) {
        String path = call.getString("path");
        if (path == null) {
            call.reject("path required");
            return;
        }
        io.execute(() -> {
            try {
                File dir = new File(getContext().getCacheDir(), DIR_NAME);
                File file = new File(path);
                if (!file.getCanonicalPath().startsWith(dir.getCanonicalPath() + File.separator) || !file.isFile()) {
                    call.reject("文件已失效,请重新拍照");
                    return;
                }
                if (file.length() > MAX_FILE_BYTES) {
                    call.reject("照片太大,请重拍");
                    return;
                }
                byte[] bytes = new byte[(int) file.length()];
                try (InputStream in = new FileInputStream(file)) {
                    int read = 0;
                    while (read < bytes.length) {
                        int n = in.read(bytes, read, bytes.length - read);
                        if (n < 0) break;
                        read += n;
                    }
                }
                JSObject ret = new JSObject();
                ret.put("data", Base64.encodeToString(bytes, Base64.NO_WRAP));
                ret.put("name", file.getName());
                ret.put("size", bytes.length);
                call.resolve(ret);
            } catch (Exception e) {
                call.reject("读取照片失败:" + e.getMessage(), e);
            }
        });
    }

    private static void clearDir(File dir) {
        File[] old = dir.listFiles();
        if (old == null) return;
        for (File f : old) deleteQuietly(f);
    }

    private static void deleteQuietly(File f) {
        if (f == null) return;
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
