package top.ihomy.app.call;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.util.ArrayList;
import java.util.List;

/**
 * 通话权限前置申请(插件名 {@code IhomyCall}):在拉媒体流之前把麦克风/摄像头要到手。
 *
 * <p>WebView 的 onPermissionRequest 只会在 getUserMedia 真的发起时才弹系统框,
 * 页面那时已经进入「正在接通」,用户会以为通话坏了。这里由前端在发起/接听前主动调一次:
 * 先把权限要齐,页面再 getUserMedia 就不会在中途打断。
 *
 * <p>返回 {@code {ok, microphone, camera, blocked}}:blocked=true 表示「拒绝且不再询问」,
 * 系统不会再弹框,只能引导去应用设置里手动打开(openSettings)。
 */
@CapacitorPlugin(
        name = "IhomyCall",
        permissions = {
            @Permission(alias = "microphone", strings = { Manifest.permission.RECORD_AUDIO }),
            @Permission(alias = "camera", strings = { Manifest.permission.CAMERA })
        })
public class CallPermissionPlugin extends Plugin {

    /** 语音只要麦克风;视频再把摄像头补上 */
    @PluginMethod
    public void ensure(PluginCall call) {
        boolean video = !Boolean.FALSE.equals(call.getBoolean("video", Boolean.TRUE));
        List<String> missing = new ArrayList<>();
        if (getPermissionState("microphone") != PermissionState.GRANTED) {
            missing.add("microphone");
        }
        if (video && getPermissionState("camera") != PermissionState.GRANTED) {
            missing.add("camera");
        }
        if (missing.isEmpty()) {
            resolveState(call, video);
            return;
        }
        requestPermissionForAliases(missing.toArray(new String[0]), call, "permissionCallback");
    }

    @PermissionCallback
    private void permissionCallback(PluginCall call) {
        if (call == null) return;
        boolean video = !Boolean.FALSE.equals(call.getBoolean("video", Boolean.TRUE));
        resolveState(call, video);
    }

    /** 去系统里改权限:用户点过「不再询问」后唯一能救回来的路 */
    @PluginMethod
    public void openSettings(PluginCall call) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getContext().getPackageName()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
            call.resolve();
        } catch (Exception e) {
            call.reject("open_failed", e);
        }
    }

    /**
     * 汇总当前授权状态。blocked 只在「拒绝且系统不会再弹框」时为 true:
     * 首次拒绝后 shouldShowRequestPermissionRationale 仍为 true,前端还能再请求一次。
     */
    private void resolveState(PluginCall call, boolean video) {
        boolean micGranted = getPermissionState("microphone") == PermissionState.GRANTED;
        boolean camGranted = getPermissionState("camera") == PermissionState.GRANTED;
        boolean blocked = false;
        if (!micGranted) {
            blocked = !shouldShow(Manifest.permission.RECORD_AUDIO);
        }
        if (video && !camGranted) {
            blocked = blocked || !shouldShow(Manifest.permission.CAMERA);
        }
        JSObject ret = new JSObject();
        ret.put("ok", micGranted && (!video || camGranted));
        ret.put("microphone", micGranted ? "granted" : "denied");
        ret.put("camera", video ? (camGranted ? "granted" : "denied") : "skipped");
        ret.put("blocked", blocked);
        call.resolve(ret);
    }

    private boolean shouldShow(String permission) {
        try {
            return getActivity() != null && getActivity().shouldShowRequestPermissionRationale(permission);
        } catch (Exception e) {
            return true;
        }
    }
}
