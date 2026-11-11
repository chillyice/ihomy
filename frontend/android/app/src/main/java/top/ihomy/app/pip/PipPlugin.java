package top.ihomy.app.pip;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Rational;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * 画中画(插件名 {@code IhomyPip}):只负责「播放中离开 App 时把整页缩进小窗」。
 *
 * <p>前端在视频真正播放时 {@code setActive(true)},暂停/关闭时关掉。离开 App 时
 * {@code MainActivity.onUserLeaveHint} 调 {@link #enterIfActive} 进入画中画;
 * Android 12+ 另设 {@code setAutoEnterEnabled},由系统在合适的时机直接接管。
 *
 * <p>呈现的是整个 WebView(即播放页),不是裸视频画面——WebView 里无法把 {@code <video>}
 * 单独交给系统 PiP;这一点在文档里写明,不假装是原生播放器。
 */
@CapacitorPlugin(name = "IhomyPip")
public class PipPlugin extends Plugin {

    /** 是否有视频正在播放(静态:MainActivity 离开时也要读) */
    private static volatile boolean active = false;

    private static boolean supported(Activity activity) {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false;
        return activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE);
    }

    @PluginMethod
    public void isSupported(PluginCall call) {
        Activity activity = getActivity();
        boolean ok = supported(activity);
        call.resolve(new com.getcapacitor.JSObject().put("supported", ok));
    }

    @PluginMethod
    public void setActive(PluginCall call) {
        Boolean on = call.getBoolean("active", false);
        active = Boolean.TRUE.equals(on);
        Activity activity = getActivity();
        if (activity != null) activity.runOnUiThread(() -> applyAutoEnter(activity));
        call.resolve();
    }

    @PluginMethod
    public void enter(PluginCall call) {
        Activity activity = getActivity();
        if (!supported(activity) || !enterPip(activity)) {
            call.reject("pip_unavailable");
            return;
        }
        call.resolve();
    }

    /** MainActivity.onUserLeaveHint 调用:只在播放中才缩小窗,平时离开就是普通后台 */
    public static void enterIfActive(Activity activity) {
        if (!active) return;
        enterPip(activity);
    }

    private static boolean enterPip(Activity activity) {
        if (!supported(activity)) return false;
        try {
            PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder()
                    .setAspectRatio(new Rational(16, 9));
            activity.enterPictureInPictureMode(builder.build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Android 12+:打开「播放中按 Home 自动进小窗」,免掉用户手动返回的操作 */
    private static void applyAutoEnter(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        try {
            PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder()
                    .setAspectRatio(new Rational(16, 9))
                    .setAutoEnterEnabled(active);
            activity.setPictureInPictureParams(builder.build());
        } catch (Exception e) {
            // 个别 ROM 不支持自动进入,保留 onUserLeaveHint 兜底
        }
    }
}
