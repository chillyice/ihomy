package top.ihomy.app.window;

import android.app.Activity;
import android.graphics.Color;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * 窗口底色(插件名 {@code IhomyWindowBars}):把 decorView 背景刷成当前页面的根底色。
 *
 * <p>targetSdk 36 起 Android 强制 edge-to-edge,{@code Window.setStatusBarColor} 已是
 * 不生效的废弃 API,状态栏/手势条露出的底色只能来自窗口背景(decorView)。主题的
 * windowBackground 跟系统深浅色走,而 App 自己的两轴主题常年与系统对不上——华为的
 * 「深色模式」甚至不给应用写 uiMode,主题永远停在亮色白底,白底上叠浅色图标就什么都看不见。
 * 所以由前端把页面根底色刷进来,状态栏底色恒等于页面底色,图标明暗由前端按亮度决定。</p>
 */
@CapacitorPlugin(name = "IhomyWindowBars")
public class WindowBarsPlugin extends Plugin {

    /**
     * 前端最近一次刷进来的页面根底色。系统/Capacitor 会在配置变化、窗口焦点恢复等时机
     * 把窗口底色重刷成主题 windowBackground(华为深色模式不写 uiMode,主题停亮色=白),
     * 这时要用它原样盖回去,否则深色页面顶部会留下一条白。
     */
    private static volatile Integer lastPageColor;

    @PluginMethod
    public void setBackgroundColor(PluginCall call) {
        String color = call.getString("color", "");
        Activity activity = getActivity();
        if (activity == null) {
            call.reject("no_activity");
            return;
        }
        final int argb;
        try {
            argb = Color.parseColor(color);
        } catch (IllegalArgumentException e) {
            call.reject("bad_color");
            return;
        }
        lastPageColor = argb;
        activity.runOnUiThread(() -> activity.getWindow().getDecorView().setBackgroundColor(argb));
        call.resolve();
    }

    /** 用最近一次的页面底色重刷窗口;前端还没刷过则不动。 */
    public static void reapplyLast(Activity activity) {
        Integer color = lastPageColor;
        if (color == null || activity == null) return;
        activity.runOnUiThread(() -> activity.getWindow().getDecorView().setBackgroundColor(color));
    }
}
