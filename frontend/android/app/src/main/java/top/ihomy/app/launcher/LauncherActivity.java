package top.ihomy.app.launcher;

import android.app.Activity;
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
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(open);
        finish();
    }
}
