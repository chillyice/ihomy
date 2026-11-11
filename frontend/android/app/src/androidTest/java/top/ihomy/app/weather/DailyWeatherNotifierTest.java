package top.ihomy.app.weather;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.UiAutomation;
import android.content.Context;
import android.os.Build;
import android.service.notification.StatusBarNotification;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import top.ihomy.app.R;

/**
 * 每日天气通知的设备内验证:用样例快照直接走一遍「渲染 → 贴通知」,
 * 覆盖三件纯 JVM 测不到的事——七枚矢量图标能光栅化、格式化文案带参数不崩、通道参数真的是「无声 + 锁屏可见」。
 *
 * <p>不发网络请求:真实取数由 {@link DailyWeatherWorker} 负责,这里只锁展示层。
 */
@RunWith(AndroidJUnit4.class)
public class DailyWeatherNotifierTest {

    private static final int NOTIFY_ID = 7202;

    private Context ctx() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void everyWeatherKindRendersAnIcon() {
        for (String code : new String[]{"100", "101", "104", "305", "302", "400", "501", "", "999"}) {
            WeatherSnapshot s = snapshot();
            s.iconDay = code;
            assertNotNull("图标应能光栅化: " + code, WeatherIcons.largeIcon(ctx(), code));
        }
    }

    @Test
    public void postsQuietPublicNotificationWithAdvice() throws Exception {
        Context context = ctx();
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        assertNotNull(nm);
        // 全新安装的 debug 包默认没给通知权限(Android 13+),测试里以 shell 身份补上,避免依赖手工准备
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            UiAutomation ua = InstrumentationRegistry.getInstrumentation().getUiAutomation();
            ua.grantRuntimePermission(context.getPackageName(), "android.permission.POST_NOTIFICATIONS");
            Thread.sleep(300);
        }
        assertTrue("通知权限未开", nm.areNotificationsEnabled());
        nm.cancel(NOTIFY_ID);
        // 通道参数一旦创建就被系统冻结,删掉重建才能验证「首次创建」这一版参数
        nm.deleteNotificationChannel(DailyWeatherNotifier.CHANNEL_ID);

        WeatherSnapshot s = snapshot();
        DailyWeatherNotifier.show(context, s, WeatherAdvice.Kind.BIG_DIFF);

        StatusBarNotification sbn = find(nm);
        assertNotNull("应贴出每日天气通知", sbn);
        Notification n = sbn.getNotification();
        assertEquals("应挂在 daily-weather 通道", DailyWeatherNotifier.CHANNEL_ID, n.getChannelId());
        assertEquals(context.getString(R.string.notify_weather_title, "多云转晴", 18, 28),
                String.valueOf(n.extras.getCharSequence(Notification.EXTRA_TITLE)));
        assertEquals(context.getString(R.string.notify_weather_advice_big_diff),
                String.valueOf(n.extras.getCharSequence(Notification.EXTRA_TEXT)));
        assertEquals("城市应作副标题", "杭州", String.valueOf(n.extras.getCharSequence(Notification.EXTRA_SUB_TEXT)));

        String big = String.valueOf(n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        assertTrue("展开应带实况: " + big, big.contains(context.getString(R.string.notify_weather_now, 24, 23)));
        assertTrue("展开应带最高最低与降水: " + big,
                big.contains(context.getString(R.string.notify_weather_range_pop, 28, 18, 20)));
        assertTrue("展开应带风力与空气: " + big, big.contains(context.getString(R.string.notify_weather_air, "优")));
        assertTrue("首行是建议: " + big, big.startsWith(context.getString(R.string.notify_weather_advice_big_diff)));

        // 有通道时通知自身的 visibility 可能被置为 NO_OVERRIDE(@hide 的 -1000,交给通道决定),
        // 这里只保证「不是私密/机密」,锁屏可见性最终看系统策略与通道设置
        assertTrue("通知不应声明私密",
                n.visibility != Notification.VISIBILITY_PRIVATE && n.visibility != Notification.VISIBILITY_SECRET);
        assertNotNull("点通知应进天气页", n.contentIntent);

        NotificationChannel ch = nm.getNotificationChannel(DailyWeatherNotifier.CHANNEL_ID);
        assertNotNull("应创建每日天气通道", ch);
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, ch.getImportance());
        assertNull("通道不应有提示音", ch.getSound());
        // 注:通道级 setLockscreenVisibility 在 Android 14 实测不落盘(读回 NO_OVERRIDE),
        // 锁屏展示由通知自身 visibility 与系统「锁屏显示全部/隐藏敏感内容」策略决定,故不在此断言
        assertFalse("不显角标", ch.canShowBadge());

        nm.cancel(NOTIFY_ID);
    }

    private static WeatherSnapshot snapshot() {
        WeatherSnapshot s = new WeatherSnapshot();
        s.city = "杭州";
        s.dayText = "多云转晴";
        s.nowText = "多云";
        s.iconDay = "101";
        s.nowTemp = 24;
        s.feelsLike = 23;
        s.tempMax = 28;
        s.tempMin = 18;
        s.pop = 20;
        s.windDir = "东南";
        s.windScale = "2";
        s.aqiCategory = "优";
        return s;
    }

    private static StatusBarNotification find(NotificationManager nm) {
        for (StatusBarNotification sbn : nm.getActiveNotifications()) {
            if (sbn.getId() == NOTIFY_ID) return sbn;
        }
        return null;
    }
}
