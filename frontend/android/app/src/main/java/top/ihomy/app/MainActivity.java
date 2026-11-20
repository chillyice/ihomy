package top.ihomy.app;

import android.content.Intent;
import android.os.Bundle;
import android.webkit.CookieManager;

import com.getcapacitor.BridgeActivity;
import top.ihomy.app.biometric.BiometricPlugin;
import top.ihomy.app.calendar.CalendarSyncPlugin;
import top.ihomy.app.call.CallPermissionPlugin;
import top.ihomy.app.camera.CameraPlugin;
import top.ihomy.app.diagnostics.CrashStore;
import top.ihomy.app.diagnostics.DiagnosticsPlugin;
import top.ihomy.app.haptics.HapticsPlugin;
import top.ihomy.app.health.HealthDataPlugin;
import top.ihomy.app.health.HealthPrefs;
import top.ihomy.app.health.HealthSyncWorker;
import top.ihomy.app.live.AppVisibility;
import top.ihomy.app.photo.PhotoBackupPlugin;
import top.ihomy.app.pip.PipPlugin;
import top.ihomy.app.screen.ScreenPlugin;
import top.ihomy.app.share.ShareInboxPlugin;
import top.ihomy.app.share.ShareIntake;
import top.ihomy.app.speech.SpeechPlugin;
import top.ihomy.app.updater.AutoUpdateWorker;
import top.ihomy.app.updater.UpdaterPlugin;
import top.ihomy.app.widget.WidgetBridgePlugin;
import top.ihomy.app.window.SystemInsetsForwarder;
import top.ihomy.app.window.WindowBarsPlugin;

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
        // 相册自动备份:只读系统相册,把新照片交给前端上传到「手机备份」
        registerPlugin(PhotoBackupPlugin.class);
        // 触觉反馈:勾选/长按/拖拽/保存/校验失败的手感,只在系统允许时震
        registerPlugin(HapticsPlugin.class);
        // 通话权限前置:发起/接听前主动申请麦克风(视频还要摄像头),拒绝后引导去系统设置
        registerPlugin(CallPermissionPlugin.class);
        // 画中画:播放中离开 App 时整页缩进小窗(前端播放态驱动,平时离开就是普通后台)
        registerPlugin(PipPlugin.class);
        // 窗口底色:状态栏/手势条露出的底色跟页面走(targetSdk 36 上 setStatusBarColor 已失效)
        registerPlugin(WindowBarsPlugin.class);
        // 健康数据:Health Connect 授权 + 读睡眠/活动/体征 → 上报本站(设置页里按组授权)
        registerPlugin(HealthDataPlugin.class);
        // 冷启动时先把分享意图接住,JS 起来后再取(getPending 只读,确认落地才清)
        ShareIntake.capture(getIntent());
        super.onCreate(savedInstanceState);
        // 闲时自动更新:周期任务在这里兜一次(插件 load 也会调,幂等);开关关着就撤排期
        AutoUpdateWorker.ensureScheduled(getApplicationContext());
        // 健康数据每日同步:开关打开过才排期(WorkManager KEEP 幂等,重复调用不重排)
        if (HealthPrefs.autoSync(getApplicationContext())) {
            HealthSyncWorker.schedulePeriodic(getApplicationContext());
        }
        // 壳内页面 origin 固定为 https://localhost,而接口与文件都在 https://ihomy.top:
        // 家庭私有照片/电子书/视频靠 ihomy_at cookie(后端对壳来源下发 SameSite=None)鉴权,
        // 不显式允许三方 cookie 时这些子资源会一律 403。
        if (bridge != null && bridge.getWebView() != null) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(bridge.getWebView(), true);
        }
        // 安全区转发:接管 Capacitor SystemBars 的 insets 处理(配置里已 disable),
        // 消掉华为 WebView 上 decorView padding 造成的横屏顶部空区,并注入真实安全区变量
        SystemInsetsForwarder.install(bridge);
    }

    /**
     * 前后台标记:前台服务据此决定「只更新未读基线」还是「真的发系统通知」。
     * 用 onResume/onPause 而不是 onStart/onStop:弹系统权限框时 Activity 仍算前台,
     * 不会把用户正在看的这次操作当成后台事件再提醒一遍。
     */
    @Override
    public void onResume() {
        super.onResume();
        AppVisibility.setForeground(true);
    }

    /**
     * 配置变化(旋转/深浅色/字体缩放)会把 Capacitor SystemBars 的窗口底色重刷成主题
     * windowBackground——华为深色模式不写 uiMode、主题停亮色(白),深色页面顶部会留白条。
     * super 先派发完插件回调,再原样盖回页面底色。
     */
    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        WindowBarsPlugin.reapplyLast(this);
    }

    /** 下拉/收起通知栏等系统交互恢复焦点时,部分 ROM 会重绘窗口底色,同样盖回页面底色 */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            WindowBarsPlugin.reapplyLast(this);
        }
    }

    @Override
    public void onPause() {
        AppVisibility.setForeground(false);
        super.onPause();
    }

    /** 用户按 Home / 切到别的 App:正在播放视频就直接进画中画(没播放则照常进后台) */
    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        PipPlugin.enterIfActive(this);
    }

    /** 应用已在前台/后台时再次收到分享(singleTask):接住并通知 WebView 去取 */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null) setIntent(intent);
        ShareInboxPlugin.captureAndNotify(this, intent);
    }
}
