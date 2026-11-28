package top.ihomy.app.launcher;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.os.Bundle;

import androidx.core.splashscreen.SplashScreen;

import top.ihomy.app.MainActivity;

/**
 * Keep the WebView task independent of the changeable launcher component.
 * Android may finish a task whose original component is disabled, even with DONT_KILL_APP.
 */
public class LauncherActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen splash = SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        splash.setKeepOnScreenCondition(() -> true);
        Intent open = new Intent(this, MainActivity.class)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            // LocalNotifications can attach its action and notification ID to the launch intent.
            .putExtras(getIntent())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        // 冷启动只该有一次转场:这一跳只是把前台交给主任务,不应再播一次动画。
        // 不带 NEW_TASK 时 MainActivity 会落进另一个任务,华为/鸿蒙对新任务进场播的是
        // 任务切换动画(横向滑入),叠在启动窗口自己的打开动画上,看起来就像「换了个 app」。
        // ActivityOptions 与 overridePendingTransition 两条路都走一遍:部分 ROM 只认其中一条。
        startActivity(open, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle());
        overridePendingTransition(0, 0);
        finish();
    }
}
