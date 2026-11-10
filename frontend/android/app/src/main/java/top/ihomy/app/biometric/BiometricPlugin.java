package top.ihomy.app.biometric;

import android.app.Activity;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.CancellationSignal;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * 系统指纹/人脸校验(仅原生壳):保险箱进页前挡一道。
 *
 * <p>直接用系统框架的 {@link BiometricPrompt}(API 28+),不引第三方库——壳里少一个依赖就少一处
 * 与 Capacitor 大版本绑定的风险。校验通过只说明「是本人拿着这台手机」,
 * 真正的密码明文仍由服务端按登录密码校验,这里只是本地防"别人拿起手机乱翻"。
 */
@CapacitorPlugin(name = "IhomyBiometric")
public class BiometricPlugin extends Plugin {

    private static final int AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG;

    /** 本机不支持(框架没有这个常量,自己留一个不会与系统码冲突的值) */
    private static final int ERROR_UNSUPPORTED = -2;

    private PluginCall pendingCall;
    private CancellationSignal cancellationSignal;

    /** 本机是否可用:hardware / enrolled / unsupported */
    @PluginMethod
    public void available(PluginCall call) {
        JSObject ret = new JSObject();
        int result = canAuthenticate();
        ret.put("available", result == BiometricManager.BIOMETRIC_SUCCESS);
        ret.put("enrolled", result == BiometricManager.BIOMETRIC_SUCCESS);
        ret.put("status", statusOf(result));
        call.resolve(ret);
    }

    /** 弹系统校验框;ok=true 通过,-1 用户取消,其它见 message */
    @PluginMethod
    public void verify(PluginCall call) {
        if (pendingCall != null) {
            call.reject("busy");
            return;
        }
        final Activity activity = getActivity();
        if (activity == null) {
            call.reject("no_activity");
            return;
        }
        int result = canAuthenticate();
        if (result != BiometricManager.BIOMETRIC_SUCCESS) {
            call.reject(statusOf(result));
            return;
        }

        pendingCall = call;
        cancellationSignal = new CancellationSignal();
        String title = call.getString("title", "");
        String subtitle = call.getString("subtitle", "");
        String cancel = call.getString("cancel", "取消");

        BiometricPrompt.Builder builder;
        try {
            builder = new BiometricPrompt.Builder(activity)
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setNegativeButton(cancel, activity.getMainExecutor(),
                            (dialog, which) -> finishError("cancel"));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setAllowedAuthenticators(AUTHENTICATORS);
            }
        } catch (Exception e) {
            pendingCall = null;
            cancellationSignal = null;
            call.reject("unsupported");
            return;
        }

        BiometricPrompt prompt = builder.build();
        prompt.authenticate(cancellationSignal, activity.getMainExecutor(),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) {
                        finishOk();
                    }

                    @Override
                    public void onAuthenticationError(int code, CharSequence errString) {
                        finishError(errorName(code));
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        // 单次识别不匹配:系统框会自己提示重试,不结束本次校验
                    }
                });
    }

    /** 页面离开时主动收起系统框,避免挡住别的页面 */
    @PluginMethod
    public void cancel(PluginCall call) {
        finishError("cancel");
        call.resolve();
    }

    private void finishOk() {
        PluginCall call = pendingCall;
        pendingCall = null;
        cancellationSignal = null;
        if (call == null) return;
        JSObject ret = new JSObject();
        ret.put("ok", true);
        call.resolve(ret);
    }

    private void finishError(String message) {
        PluginCall call = pendingCall;
        pendingCall = null;
        try {
            if (cancellationSignal != null) cancellationSignal.cancel();
        } catch (Exception ignored) {
            // 已经结束的校验再 cancel 会抛,忽略
        }
        cancellationSignal = null;
        if (call == null) return;
        call.reject(message);
    }

    /** 查询本机可用性:API 30+ 按验证器类型查;API 29 只有无参版本 */
    private int canAuthenticate() {
        try {
            BiometricManager bm = getContext().getSystemService(BiometricManager.class);
            if (bm == null) return ERROR_UNSUPPORTED;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return bm.canAuthenticate(AUTHENTICATORS);
            }
            return bm.canAuthenticate();
        } catch (Exception e) {
            return ERROR_UNSUPPORTED;
        }
    }

    private static String statusOf(int result) {
        switch (result) {
            case BiometricManager.BIOMETRIC_SUCCESS:
                return "ok";
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                return "no_enrollment";
            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
                return "no_hardware";
            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                return "hw_unavailable";
            case BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED:
                return "security_update";
            case ERROR_UNSUPPORTED:
                return "unsupported";
            default:
                return "unavailable";
        }
    }

    private static String errorName(int code) {
        switch (code) {
            case BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED:
                return "cancel";
            case BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT:
                return "lockout";
            case BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT_PERMANENT:
                return "lockout_permanent";
            case BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS:
                return "no_enrollment";
            case BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT:
                return "no_hardware";
            case BiometricPrompt.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                return "hw_unavailable";
            default:
                return "failed";
        }
    }
}
