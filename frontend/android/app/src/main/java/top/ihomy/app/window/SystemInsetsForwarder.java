package top.ihomy.app.window;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.webkit.WebView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.getcapacitor.Bridge;
import com.getcapacitor.WebViewListener;

/**
 * 安全区转发器:接管 Capacitor SystemBars 的 insets 处理(capacitor.config.json 已把
 * {@code SystemBars.insetsHandling} 设为 {@code disable})。
 *
 * <p>Capacitor 自带的监听按 WebView 版本分两条路:主流 Google WebView(≥144)走
 * passthrough,内容 edge-to-edge、insets 原样透传、CSS 变量注入真实值;其余(包括
 * 华为自有 WebView,版本号自成序列,major 恒小于 144)走「给 decorView 打
 * systemBars|displayCutout 的 padding」——整块 WebView 被顶下来,横屏顶部就多出
 * 一条系统 inset 高度的空区,而注入给页面的安全区变量还是 0,页面填不了它。</p>
 *
 * <p>这里对所有 WebView 版本统一走 passthrough 行为(与主流设备现状一致):
 * 不打 top/left/right padding,只兜键盘的 bottom(targetSdk 36 起 adjustResize
 * 不再缩窗口);insets 原样透传,WebView 里的 {@code env(safe-area-inset-*)} 与
 * 注入的 {@code --safe-area-inset-*} 都是真实值,页面按现状垫头部/底部。</p>
 *
 * <p>顺带每次 insets 派发都用页面底色重刷一次窗口:{@code SystemBars.setStyle}
 * 的结尾会把 decorView 刷成主题 windowBackground,华为深色模式不给应用写
 * uiMode、主题停在亮色(白),旋转/拉通知栏触发的重刷会在深色页面顶部留下
 * 白条——靠 {@link WindowBarsPlugin#reapplyLast} 盖回页面底色。</p>
 */
public final class SystemInsetsForwarder {

    /** Chromium 修好「键盘弹出时 bottom inset 算错」的 WebView 版本(老版本要绕,bug 457682720) */
    private static final int WEBVIEW_VERSION_WITH_SAFE_AREA_KEYBOARD_FIX = 144;

    private SystemInsetsForwarder() {}

    public static void install(Bridge bridge) {
        if (bridge == null || bridge.getActivity() == null || bridge.getWebView() == null) return;
        Activity activity = bridge.getActivity();

        // Capacitor 的 SystemBars 在插件 load 时装自己的监听;super.onCreate 之后安装,
        // 这里就是 decorView 上唯一的 insets 监听(Keyboard 插件挂在 android.R.id.content,不冲突)
        ViewCompat.setOnApplyWindowInsetsListener(activity.getWindow().getDecorView(), (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            boolean keyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime());

            // 内容全程 edge-to-edge:只兜键盘的 bottom,其余方向不占布局
            v.setPadding(0, 0, 0, keyboardVisible ? ime.bottom : 0);

            WindowInsetsCompat passthrough = new WindowInsetsCompat.Builder(insets)
                    .setInsets(
                            WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout(),
                            Insets.of(bars.left, bars.top, bars.right, getBottomInset(bridge, bars, keyboardVisible)))
                    .build();

            injectSafeAreaCss(bridge, passthrough, keyboardVisible);
            // 系统重派 insets 往往伴随窗口底色被刷回主题白(华为深色模式主题停亮色),盖回页面底色
            WindowBarsPlugin.reapplyLast(activity);
            return passthrough;
        });

        // 页面每次加载后 CSS 变量随文档重建丢失,重触发一次 insets 派发再注入(同 SystemBars 做法)
        bridge.addWebViewListener(new WebViewListener() {
            @Override
            public void onPageCommitVisible(WebView view, String url) {
                super.onPageCommitVisible(view, url);
                WebView web = bridge.getWebView();
                if (web != null) web.requestApplyInsets();
            }
        });
    }

    /** 老 WebView 键盘弹出时 bottom inset 计算有 bug,传 0 绕开(与 SystemBars 同款) */
    private static int getBottomInset(Bridge bridge, Insets bars, boolean keyboardVisible) {
        if (keyboardVisible && webViewMajor(bridge) < WEBVIEW_VERSION_WITH_SAFE_AREA_KEYBOARD_FIX) {
            return 0;
        }
        return bars.bottom;
    }

    private static void injectSafeAreaCss(Bridge bridge, WindowInsetsCompat insets, boolean keyboardVisible) {
        Activity activity = bridge.getActivity();
        WebView web = bridge.getWebView();
        if (activity == null || web == null) return;

        Insets bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
        float density = activity.getResources().getDisplayMetrics().density;
        int top = (int) (bars.top / density);
        int right = (int) (bars.right / density);
        // 键盘弹出时页面不该再垫手势条高度,与 SystemBars 口径一致
        int bottom = keyboardVisible ? 0 : (int) (bars.bottom / density);
        int left = (int) (bars.left / density);

        String script = "try {"
                + "document.documentElement.style.setProperty('--safe-area-inset-top', '" + top + "px');"
                + "document.documentElement.style.setProperty('--safe-area-inset-right', '" + right + "px');"
                + "document.documentElement.style.setProperty('--safe-area-inset-bottom', '" + bottom + "px');"
                + "document.documentElement.style.setProperty('--safe-area-inset-left', '" + left + "px');"
                + "} catch(e) {}";
        web.evaluateJavascript(script, null);
    }

    /** 当前 WebView 包的主版本号;取不到按最老处理(华为自有 WebView 版本号自成序列) */
    private static int webViewMajor(Bridge bridge) {
        Context context = bridge.getContext();
        if (context == null) return 0;
        try {
            PackageInfo info = WebView.getCurrentWebViewPackage();
            if (info != null && info.versionName != null) {
                return Integer.parseInt(info.versionName.split("\\.")[0]);
            }
        } catch (Exception e) {
            // 老系统/取包失败按最老处理
        }
        return 0;
    }
}
