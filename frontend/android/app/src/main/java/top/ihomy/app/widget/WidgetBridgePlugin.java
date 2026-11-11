package top.ihomy.app.widget;

import android.content.Intent;
import android.provider.Settings;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import top.ihomy.app.live.LiveService;
import top.ihomy.app.reminder.StaleCheckWorker;
import top.ihomy.app.weather.DailyWeatherNotifier;
import top.ihomy.app.weather.DailyWeatherWorker;

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
        // 每日天气:登录后才有令牌,顺便确保推送周期任务在排(开关关闭时内部会跳过)
        DailyWeatherWorker.ensureScheduled(getContext());
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

    /** 启动后台消息提醒(前台服务)。必须由前台 UI 调用:Android 12+ 禁止后台启动前台服务 */
    @PluginMethod
    public void startLive(PluginCall call) {
        try {
            Intent intent = new Intent(getContext(), LiveService.class).setAction(LiveService.ACTION_START);
            ContextCompat.startForegroundService(getContext(), intent);
            call.resolve();
        } catch (Exception e) {
            call.reject("start_failed", e);
        }
    }

    /** 停止后台消息提醒(登出/关开关/系统不允许时) */
    @PluginMethod
    public void stopLive(PluginCall call) {
        try {
            getContext().stopService(new Intent(getContext(), LiveService.class));
            call.resolve();
        } catch (Exception e) {
            call.reject("stop_failed", e);
        }
    }

    @PluginMethod
    public void isLiveRunning(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("running", LiveService.isRunning());
        call.resolve(ret);
    }

    @PluginMethod
    public void clearWidgetToken(PluginCall call) {
        WidgetPrefs.clearToken(getContext());
        Widgets.updateAll(getContext());
        call.resolve();
    }

    /** 设置页回填:每日天气开关与时刻(时刻按「当天 0 点起的分钟数」回传,前端换算 HH:mm) */
    @PluginMethod
    public void getDailyWeather(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("enabled", WidgetPrefs.dailyWeatherEnabled(getContext()));
        ret.put("minute", WidgetPrefs.dailyWeatherMinute(getContext()));
        call.resolve(ret);
    }

    /** 设置页开关:关掉时同时撤掉当天残留的通知 */
    @PluginMethod
    public void setDailyWeatherEnabled(PluginCall call) {
        Boolean enabled = call.getBoolean("enabled", true);
        WidgetPrefs.setDailyWeatherEnabled(getContext(), Boolean.TRUE.equals(enabled));
        DailyWeatherWorker.reschedule(getContext());
        call.resolve();
    }

    /** 设置页改时刻:重排周期任务,下一跳按新时刻对齐 */
    @PluginMethod
    public void setDailyWeatherMinute(PluginCall call) {
        Integer minute = call.getInt("minute");
        if (minute == null || minute < 0 || minute >= 24 * 60) {
            call.reject("minute out of range");
            return;
        }
        WidgetPrefs.setDailyWeatherMinute(getContext(), minute);
        DailyWeatherWorker.reschedule(getContext());
        call.resolve();
    }

    /** 设置页「发一条预览」:立刻跑一次 Worker;权限/网络不满足就直接告诉前端,别让用户白等 */
    @PluginMethod
    public void previewDailyWeather(PluginCall call) {
        if (!NotificationManagerCompat.from(getContext()).areNotificationsEnabled()) {
            call.reject("permission");
            return;
        }
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                getContext().getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
        if (cm == null || cm.getActiveNetwork() == null) {
            call.reject("network");
            return;
        }
        DailyWeatherNotifier.ensureChannel(getContext());
        DailyWeatherWorker.preview(getContext());
        call.resolve();
    }

    @PluginMethod
    public void refreshWidgets(PluginCall call) {
        WidgetWorker.refreshNow(getContext());
        call.resolve();
    }

    /** 设置页「通知设置」:直达本 App 的系统通知页,4 条通道由系统列出(不重建通道) */
    @PluginMethod
    public void openNotificationSettings(PluginCall call) {
        try {
            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getContext().getPackageName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
            call.resolve();
        } catch (Exception e) {
            call.reject("open_failed", e);
        }
    }

    /** App 冷启动:注册周期任务(KEEP 策略幂等),让卡片在 App 打开期间保持新鲜 */
    @Override
    public void load() {
        WidgetWorker.schedulePeriodic(getContext());
        StaleCheckWorker.schedulePeriodic(getContext());
        DailyWeatherWorker.ensureScheduled(getContext());
    }
}
