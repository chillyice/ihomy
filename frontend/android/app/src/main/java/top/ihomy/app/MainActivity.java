package top.ihomy.app;

import android.os.Bundle;
import android.webkit.CookieManager;

import com.getcapacitor.BridgeActivity;
import top.ihomy.app.calendar.CalendarSyncPlugin;
import top.ihomy.app.widget.WidgetBridgePlugin;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // 桌面卡片桥:令牌下发 / 登出清理 / 改动后即时刷新(周期任务在插件 load 时注册)。
        // 必须在 super.onCreate 之前注册——桥在 super 里构建,晚了插件就不存在。
        registerPlugin(WidgetBridgePlugin.class);
        // 系统日历导出:写本机专属日历做提醒兜底(设置页手动触发)
        registerPlugin(CalendarSyncPlugin.class);
        super.onCreate(savedInstanceState);
        // 壳内页面 origin 固定为 https://localhost,而接口与文件都在 https://ihomy.top:
        // 家庭私有照片/电子书/视频靠 ihomy_at cookie(后端对壳来源下发 SameSite=None)鉴权,
        // 不显式允许三方 cookie 时这些子资源会一律 403。
        if (bridge != null && bridge.getWebView() != null) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(bridge.getWebView(), true);
        }
    }
}
