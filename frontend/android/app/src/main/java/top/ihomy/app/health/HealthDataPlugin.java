package top.ihomy.app.health;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.content.Context;
import android.content.Intent;

/**
 * 健康数据桥(Android):Health Connect / HMS Health Kit 授权、读增量、上报本站。
 *
 * 与 iOS 快捷指令共用一条写入口 `POST /health/ingest`;本插件只做「读 + 交」,
 * 指纹/聚合/可见性全在服务端(避免两端算出不同口径)。
 * 数据源自动选择:有 Health Connect(谷歌系)走 HealthConnectReader,
 * 没有的华为/鸿蒙机在 AGC 配好后走 HuaweiHealthReader(华为运动健康),都没有才不可用。
 * 方法:isAvailable / groupStatus / requestPermissions / sync / lastSync / setAutoSync / clearAutoSync。
 */
@CapacitorPlugin(name = "HealthData")
public class HealthDataPlugin extends Plugin {

    private static final int REQ_PERMISSIONS = 4411;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    /** 授权弹窗回来时要 resolve 的那次调用(桥不对外暴露 last plugin call) */
    private PluginCall pendingPermissionCall;

    /** 当前机型用哪条数据链;null = 两条都不可用 */
    static String providerOf(Context ctx) {
        if (HealthConnectReader.isAvailable(ctx)) return HealthConnectReader.PROVIDER;
        if (HuaweiHealthReader.isAvailable(ctx)) return HuaweiHealthReader.PROVIDER;
        return null;
    }

    private static boolean huaweiActive(Context ctx) {
        return HuaweiHealthReader.PROVIDER.equals(providerOf(ctx));
    }

    @PluginMethod
    public void isAvailable(PluginCall call) {
        Context ctx = getContext();
        String provider = providerOf(ctx);
        JSObject ret = new JSObject();
        ret.put("available", provider != null);
        ret.put("provider", provider == null ? "" : provider);
        ret.put("status", huaweiActive(ctx) ? HuaweiHealthReader.sdkStatus(ctx) : HealthConnectReader.sdkStatus(ctx));
        // 华为链自身的状态(NO_HMS/NO_HEALTH_APP/NO_APPID/OK):两条链都不可用时,前端靠它
        // 区分「华为机但 AGC 还没配好」和「这台手机没有健康数据服务」两种情况
        ret.put("huaweiStatus", HuaweiHealthReader.sdkStatus(ctx));
        call.resolve(ret);
    }

    /** 每组授权状态 + 已授权组列表(页面显示「睡眠已连接 / 体征未授权」) */
    @PluginMethod
    public void groupStatus(PluginCall call) {
        List<String> groups = groupsOf(call);
        Context ctx = getContext();
        boolean huawei = huaweiActive(ctx);
        io.execute(() -> {
            try {
                Map<String, Boolean> status = huawei
                        ? HuaweiHealthReader.groupStatusBlocking(ctx, groups)
                        : HealthConnectReader.groupStatusBlocking(ctx, groups);
                List<String> granted = new ArrayList<>();
                JSObject map = new JSObject();
                for (Map.Entry<String, Boolean> e : status.entrySet()) {
                    map.put(e.getKey(), e.getValue());
                    if (e.getValue()) granted.add(e.getKey());
                }
                HealthPrefs.setGroups(ctx, granted);
                JSObject ret = new JSObject();
                ret.put("available", true);
                ret.put("provider", huawei ? HuaweiHealthReader.PROVIDER : HealthConnectReader.PROVIDER);
                ret.put("groups", map);
                ret.put("granted", new JSArray(granted));
                call.resolve(ret);
            } catch (Exception e) {
                call.reject("status_failed", e.getMessage(), e);
            }
        });
    }

    /**
     * 拉起授权:Health Connect 用系统弹窗 Intent;华为走 HMS 扩展授权(HMS 自己管理界面)。
     * 返回结果时按组重新核对(部分授权按未授权算)。
     */
    @PluginMethod
    public void requestPermissions(PluginCall call) {
        Context ctx = getContext();
        if (huaweiActive(ctx)) {
            List<String> groups = groupsOf(call);
            HuaweiHealthReader.requestAuthorization(getActivity(), groups,
                    granted -> {
                        HealthPrefs.setGroups(ctx, granted);
                        JSObject ret = new JSObject();
                        ret.put("granted", new JSArray(granted));
                        List<String> missing = new ArrayList<>(groups);
                        missing.removeAll(granted);
                        ret.put("missing", new JSArray(missing));
                        ret.put("provider", HuaweiHealthReader.PROVIDER);
                        call.resolve(ret);
                        return kotlin.Unit.INSTANCE;
                    },
                    err -> {
                        call.reject("request_failed", err == null ? "授权失败" : err);
                        return kotlin.Unit.INSTANCE;
                    });
            return;
        }
        if (!HealthConnectReader.isAvailable(ctx)) {
            call.reject("unavailable", "本机没有可用的健康数据服务");
            return;
        }
        List<String> groups = groupsOf(call);
        try {
            Intent intent = HealthConnectReader.requestPermissionsIntent(ctx, groups);
            pendingPermissionCall = call;
            startActivityForResult(call, intent, REQ_PERMISSIONS);
        } catch (Exception e) {
            call.reject("request_failed", e.getMessage(), e);
        }
    }

    @Override
    protected void handleOnActivityResult(int requestCode, int resultCode, Intent data) {
        super.handleOnActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PERMISSIONS) return;
        PluginCall call = pendingPermissionCall;
        pendingPermissionCall = null;
        if (call == null) return;
        Context ctx = getContext();
        List<String> groups = groupsOf(call);
        io.execute(() -> {
            try {
                List<String> granted = HealthConnectReader.grantedGroupsBlocking(ctx, groups);
                List<String> missing = new ArrayList<>(groups);
                missing.removeAll(granted);
                HealthPrefs.setGroups(ctx, granted);
                JSObject ret = new JSObject();
                ret.put("granted", new JSArray(granted));
                ret.put("missing", new JSArray(missing));
                call.resolve(ret);
            } catch (Exception e) {
                call.reject("permission_failed", e.getMessage(), e);
            }
        });
    }

    /**
     * 立即同步。token 用页面当前登录态(前台不需要另建令牌);后台每日任务用设置页生成的 hk_ 令牌。
     * days 只在首次(本地没有同步记录)时决定回填天数,上限 90。
     */
    @PluginMethod
    public void sync(PluginCall call) {
        String token = call.getString("token");
        int days = call.getInt("days", 90);
        List<String> groups = groupsOf(call);
        Context ctx = getContext();
        String provider = providerOf(ctx);
        boolean huawei = HuaweiHealthReader.PROVIDER.equals(provider);
        io.execute(() -> {
            try {
                if (provider == null) {
                    call.reject("unavailable", "本机没有可用的健康数据服务");
                    return;
                }
                List<String> granted = huawei
                        ? HuaweiHealthReader.grantedGroupsBlocking(ctx, groups)
                        : HealthConnectReader.grantedGroupsBlocking(ctx, groups);
                if (granted.isEmpty()) {
                    call.reject("no_permission", "还没有授权任何健康数据分组");
                    return;
                }
                HealthPrefs.setGroups(ctx, granted);
                JSONObject result = HealthSyncer.run(ctx, token, granted, days, provider);
                JSObject ret = new JSObject(result.toString());
                ret.put("groups", new JSArray(granted));
                ret.put("provider", provider);
                call.resolve(ret);
            } catch (Exception e) {
                HealthPrefs.markSync(ctx, false, e.getMessage() == null ? "同步失败" : e.getMessage());
                call.reject("sync_failed", e.getMessage(), e);
            }
        });
    }

    @PluginMethod
    public void lastSync(PluginCall call) {
        Context ctx = getContext();
        JSObject ret = new JSObject();
        ret.put("at", HealthPrefs.lastSyncAt(ctx));
        ret.put("ok", HealthPrefs.lastOk(ctx));
        ret.put("message", HealthPrefs.lastMessage(ctx));
        ret.put("groups", new JSArray(HealthPrefs.groups(ctx)));
        ret.put("auto", HealthPrefs.autoSync(ctx));
        ret.put("tokenSet", !HealthPrefs.token(ctx).isEmpty());
        call.resolve(ret);
    }

    /**
     * 自动同步开关:开启时必须带 hk_ 令牌(由页面调 /health/token 生成后传进来),
     * 令牌只用于后台每日任务;关闭即撤排期并清掉令牌,不在设备上留长期凭证。
     */
    @PluginMethod
    public void setAutoSync(PluginCall call) {
        boolean enabled = Boolean.TRUE.equals(call.getBoolean("enabled", false));
        String token = call.getString("token");
        List<String> groups = groupsOf(call);
        Context ctx = getContext();
        if (enabled) {
            if (token == null || token.trim().isEmpty()) {
                call.reject("token_required", "缺少自动同步令牌");
                return;
            }
            HealthPrefs.setToken(ctx, token.trim());
            if (!groups.isEmpty()) HealthPrefs.setGroups(ctx, groups);
        } else {
            HealthPrefs.setToken(ctx, "");
        }
        HealthSyncWorker.applyAutoSync(ctx, enabled);
        JSObject ret = new JSObject();
        ret.put("auto", HealthPrefs.autoSync(ctx));
        call.resolve(ret);
    }

    /** 登出清理:撤排期 + 清令牌,避免换人登录后继续往上一个账号传健康数据 */
    @PluginMethod
    public void clearAutoSync(PluginCall call) {
        Context ctx = getContext();
        HealthPrefs.setToken(ctx, "");
        HealthPrefs.setAutoSync(ctx, false);
        HealthSyncWorker.cancel(ctx);
        call.resolve();
    }

    // ==================== 内部 ====================

    /** groups 缺省 = 四组全要(首次申请),显式传则只申请这些 */
    private List<String> groupsOf(PluginCall call) {
        List<String> out = new ArrayList<>();
        JSArray arr = call.getArray("groups");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                String g = arr.optString(i, "").trim().toUpperCase();
                if (!g.isEmpty() && HealthConnectReader.GROUPS.contains(g)) out.add(g);
            }
        }
        if (out.isEmpty()) out.addAll(HealthConnectReader.GROUPS);
        return out;
    }
}
