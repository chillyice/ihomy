package top.ihomy.app.widget;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * 卡片桥:WebView 侧把 WIDGET 令牌交给原生、登出时清掉、改动后即时刷新。
 * 周期任务在此注册一次(幂等);卡片不反向下发任何数据,WebView 是令牌的唯一来源。
 */
@CapacitorPlugin(name = "IhomyWidgets")
public class WidgetBridgePlugin extends Plugin {

    @PluginMethod
    public void setWidgetToken(PluginCall call) {
        String token = call.getString("token");
        if (token == null || token.isEmpty()) {
            call.reject("token required");
            return;
        }
        WidgetPrefs.setToken(getContext(), token);
        WidgetWorker.schedulePeriodic(getContext());
        WidgetWorker.refreshNow(getContext());
        call.resolve();
    }

    @PluginMethod
    public void clearWidgetToken(PluginCall call) {
        WidgetPrefs.clearToken(getContext());
        Widgets.updateAll(getContext());
        call.resolve();
    }

    @PluginMethod
    public void refreshWidgets(PluginCall call) {
        WidgetWorker.refreshNow(getContext());
        call.resolve();
    }

    /** App 冷启动:注册周期任务(KEEP 策略幂等),让卡片在 App 打开期间保持新鲜 */
    @Override
    public void load() {
        WidgetWorker.schedulePeriodic(getContext());
    }
}
