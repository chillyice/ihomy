package top.ihomy.app.screen;

import android.app.Activity;
import android.view.WindowManager;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * 屏幕保护(插件名 {@code IhomyScreen}):只做两件极小的原生事。
 *
 * <ul>
 *   <li>{@code setSecure}:保险箱页打开时加 {@code FLAG_SECURE},截图/录屏/最近任务缩略图全黑;
 *       离开页面必须立刻清除——全局开会把正常截图也一起封死。</li>
 *   <li>{@code setKeepOn}:看板/相框页保持常亮(不需要权限),离开即清。</li>
 * </ul>
 */
@CapacitorPlugin(name = "IhomyScreen")
public class ScreenPlugin extends Plugin {

    @PluginMethod
    public void setSecure(PluginCall call) {
        Boolean secure = call.getBoolean("secure", false);
        Activity activity = getActivity();
        if (activity == null) {
            call.reject("no_activity");
            return;
        }
        final boolean on = Boolean.TRUE.equals(secure);
        activity.runOnUiThread(() -> {
            if (on) {
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            } else {
                activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            }
        });
        call.resolve();
    }

    @PluginMethod
    public void setKeepOn(PluginCall call) {
        Boolean keepOn = call.getBoolean("keepOn", false);
        Activity activity = getActivity();
        if (activity == null) {
            call.reject("no_activity");
            return;
        }
        final boolean on = Boolean.TRUE.equals(keepOn);
        activity.runOnUiThread(() -> {
            if (on) {
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        });
        call.resolve();
    }
}
