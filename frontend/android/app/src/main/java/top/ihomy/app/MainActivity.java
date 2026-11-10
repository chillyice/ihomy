package top.ihomy.app;

import android.content.Intent;
import android.os.Bundle;
import android.webkit.CookieManager;

import com.getcapacitor.BridgeActivity;
import top.ihomy.app.biometric.BiometricPlugin;
import top.ihomy.app.calendar.CalendarSyncPlugin;
import top.ihomy.app.camera.CameraPlugin;
import top.ihomy.app.diagnostics.CrashStore;
import top.ihomy.app.diagnostics.DiagnosticsPlugin;
import top.ihomy.app.screen.ScreenPlugin;
import top.ihomy.app.share.ShareInboxPlugin;
import top.ihomy.app.share.ShareIntake;
import top.ihomy.app.speech.SpeechPlugin;
import top.ihomy.app.updater.UpdaterPlugin;
import top.ihomy.app.widget.WidgetBridgePlugin;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // 崩溃落盘必须是越早越好:先于 super.onCreate 装好,桥构建期的崩溃也能留下证据
        CrashStore.install(getApplicationContext());
        // 桌面卡片桥:令牌下发 / 登出清理 / 改动后即时刷新(周期任务在插件 load 时注册)。
        // 必须在 super.onCreate 之前注册——桥在 super 里构建,晚了插件就不存在。
        registerPlugin(WidgetBridgePlugin.class);
        // 系统日历导出:写本机专属日历做提醒兜底(设置页手动触发)
        registerPlugin(CalendarSyncPlugin.class);
        // 系统分享收件:微信/相册/浏览器 → 分享 → ihomy(落点由站内「分享到家庭」页决定)
        registerPlugin(ShareInboxPlugin.class);
        // 保险箱进页前的本机指纹/人脸校验
        registerPlugin(BiometricPlugin.class);
        // 相机直传:拍照 → 站内相册/记账(用系统相机,不申请 CAMERA 权限)
        registerPlugin(CameraPlugin.class);
        // 语音记账:说一句话 → 识别文本 → 前端解析成金额与备注
        registerPlugin(SpeechPlugin.class);
        // 应用内更新:前端下好安装包后,由原生把它交给系统安装器
        registerPlugin(UpdaterPlugin.class);
        // 诊断:设置页查看/导出时读原生落盘的崩溃与前端错误
        registerPlugin(DiagnosticsPlugin.class);
        // 保险箱防截屏 / 看板常亮:按页面进出开关窗口 Flag,离开即清
        registerPlugin(ScreenPlugin.class);
        // 冷启动时先把分享意图接住,JS 起来后再取(getPending 只读,确认落地才清)
        ShareIntake.capture(getIntent());
        super.onCreate(savedInstanceState);
        // 壳内页面 origin 固定为 https://localhost,而接口与文件都在 https://ihomy.top:
        // 家庭私有照片/电子书/视频靠 ihomy_at cookie(后端对壳来源下发 SameSite=None)鉴权,
        // 不显式允许三方 cookie 时这些子资源会一律 403。
        if (bridge != null && bridge.getWebView() != null) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(bridge.getWebView(), true);
        }
    }

    /** 应用已在前台/后台时再次收到分享(singleTask):接住并通知 WebView 去取 */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null) setIntent(intent);
        ShareInboxPlugin.captureAndNotify(this, intent);
    }
}
