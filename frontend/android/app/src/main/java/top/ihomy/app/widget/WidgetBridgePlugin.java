package top.ihomy.app.widget;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import top.ihomy.app.reminder.StaleCheckWorker;

/**
 * 卡片桥:WebView 侧把 WIDGET 令牌交给原生、登出时清掉、改动后即时刷新。
 * 周期任务在此注册一次(幂等);卡片不反向下发任何数据,WebView 是令牌的唯一来源。
 * 提醒对账时间与后台未读开关也落在这里的 WidgetPrefs(不碰插件私有存储)。
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
        StaleCheckWorker.schedulePeriodic(getContext());
        call.resolve();
    }

    /** 前端提醒对账成功后上报时间(ms);Worker 用它判断「多久没校准过」 */
    @PluginMethod
    public void setReminderSyncAt(PluginCall call) {
        Long ts = call.getLong("ts");
        if (ts == null || ts <= 0) {
            call.reject("ts required");
            return;
        }
        WidgetPrefs.setReminderSyncAt(getContext(), ts);
        StaleCheckWorker.schedulePeriodic(getContext());
        call.resolve();
    }

    /** 设置页:后台未读通知开关(默认开) */
    @PluginMethod
    public void setUnreadNotifyEnabled(PluginCall call) {
        Boolean enabled = call.getBoolean("enabled", true);
        WidgetPrefs.setUnreadNotifyEnabled(getContext(), Boolean.TRUE.equals(enabled));
        call.resolve();
    }

    @PluginMethod
    public void getUnreadNotifyEnabled(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("enabled", WidgetPrefs.unreadNotifyEnabled(getContext()));
        call.resolve(ret);
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
        StaleCheckWorker.schedulePeriodic(getContext());
    }
}
