package top.ihomy.app.haptics;

import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.HapticFeedbackConstants;
import android.view.View;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * 触觉反馈(插件名 {@code IhomyHaptics}):只把前端的语义动作转成系统触感。
 *
 * <p>语义映射固定五类:tap 轻触、select 选中、success 成功、warning 需注意、error 失败。
 * 系统「触感反馈」关掉或手机处于静音模式时直接返回,不打扰用户;没有马达的设备同样静默。</p>
 */
@CapacitorPlugin(name = "IhomyHaptics")
public class HapticsPlugin extends Plugin {

    @PluginMethod
    public void play(PluginCall call) {
        String kind = call.getString("kind", "tap");
        Context ctx = getContext();
        Vibrator vibrator = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) {
            call.resolve();
            return;
        }
        // 跟随系统触感开关:关掉后不再震(与设置页副标签的承诺一致)
        if (Settings.System.getInt(ctx.getContentResolver(), Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 0) {
            call.resolve();
            return;
        }
        // 静音模式不打扰:会议/夜间场景下用户没有主动要求物理反馈
        AudioManager audio = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        if (audio != null && audio.getRingerMode() == AudioManager.RINGER_MODE_SILENT) {
            call.resolve();
            return;
        }
        int constant = constantFor(kind);
        View decor = getActivity() == null ? null : getActivity().getWindow().getDecorView();
        if (constant != 0 && decor != null) {
            final int feedback = constant;
            getActivity().runOnUiThread(() -> {
                decor.performHapticFeedback(feedback);
                call.resolve();
            });
            return;
        }
        long ms = durationFor(kind);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(ms);
        }
        call.resolve();
    }

    private int constantFor(String kind) {
        switch (kind) {
            case "select":
                return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? HapticFeedbackConstants.CONTEXT_CLICK
                        : HapticFeedbackConstants.CLOCK_TICK;
            case "success":
                return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? HapticFeedbackConstants.CONFIRM : 0;
            case "warning":
                return HapticFeedbackConstants.LONG_PRESS;
            case "error":
                return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? HapticFeedbackConstants.REJECT : 0;
            case "tap":
            default:
                return HapticFeedbackConstants.KEYBOARD_TAP;
        }
    }

    /** 低版本没有对应常量时的兜底时长(毫秒),只做「有回应」,不追求复杂波形 */
    private long durationFor(String kind) {
        switch (kind) {
            case "success":
                return 20;
            case "warning":
                return 30;
            case "error":
                return 40;
            case "select":
                return 12;
            case "tap":
            default:
                return 10;
        }
    }
}
