package top.ihomy.app;

import android.content.Intent;
import android.os.Bundle;
import android.webkit.CookieManager;

import com.getcapacitor.BridgeActivity;
import top.ihomy.app.calendar.CalendarSyncPlugin;
import top.ihomy.app.share.ShareInboxPlugin;
import top.ihomy.app.share.ShareIntake;
import top.ihomy.app.widget.WidgetBridgePlugin;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // 桌面卡片桥:令牌下发 / 登出清理 / 改动后即时刷新(周期任务在插件 load 时注册)。
        // 必须在 super.onCreate 之前注册——桥在 super 里构建,晚了插件就不存在。
        registerPlugin(WidgetBridgePlugin.class);
        // 系统日历导出:写本机专属日历做提醒兜底(设置页手动触发)
        registerPlugin(CalendarSyncPlugin.class);
        // 系统分享收件:微信/相册/浏览器 → 分享 → ihomy(落点由站内「分享到家庭」页决定)
        registerPlugin(ShareInboxPlugin.class);
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
