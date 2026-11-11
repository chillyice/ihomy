package top.ihomy.app.live;

/**
 * 应用是否在前台(由 MainActivity 的 onResume/onPause 写入)。
 * 前台服务用它决定「只更新未读基线,还是真的发系统通知」:
 * 用户正看着 App 时没必要再叠一条通知,站内角标已经说明了。
 */
public final class AppVisibility {

    private static volatile boolean foreground = false;

    private AppVisibility() {
    }

    public static void setForeground(boolean value) {
        foreground = value;
    }

    public static boolean isForeground() {
        return foreground;
    }
}
