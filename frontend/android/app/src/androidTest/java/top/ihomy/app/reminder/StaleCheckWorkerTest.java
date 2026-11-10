package top.ihomy.app.reminder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.service.notification.StatusBarNotification;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import top.ihomy.app.R;
import top.ihomy.app.widget.WidgetPrefs;

/**
 * 提醒兜底自愈(计划 §5.1)的设备内验证:真的跑一次 {@link StaleCheckWorker},
 * 看它是否在「超过 7 天没校准」时发出一条提示、把 {@code last_nudge_date} 落盘,
 * 并且同一天再跑不会重复打扰。
 *
 * <p>用一次性任务触发而不是等系统周期:模拟器把系统时间推过周期边界会同时越过
 * TLS 证书有效期(生产证书 11-11 22:37 到期),网络请求必然失败;Worker 逻辑与
 * 「周期任务确实被系统排了 24 小时一次」分开验证(后者看 dumpsys jobscheduler)。
 */
@RunWith(AndroidJUnit4.class)
public class StaleCheckWorkerTest {

    private static final int NOTIFY_ID = 7200;
    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private Context ctx() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    /** 同步跑一次 Worker,等它结束 */
    private void runWorkerOnce(Context context) throws Exception {
        WorkManager wm = WorkManager.getInstance(context);
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(StaleCheckWorker.class).build();
        wm.enqueue(request);
        UUID id = request.getId();
        WorkInfo info = null;
        long deadline = System.currentTimeMillis() + 120_000L;
        while (System.currentTimeMillis() < deadline) {
            info = wm.getWorkInfoById(id).get(20, TimeUnit.SECONDS);
            if (info != null && info.getState().isFinished()) break;
            Thread.sleep(500L);
        }
        assertNotNull("任务应完成", info);
        assertEquals("任务应成功", WorkInfo.State.SUCCEEDED, info.getState());
    }

    private StatusBarNotification findNudge(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return null;
        for (StatusBarNotification sbn : nm.getActiveNotifications()) {
            if (sbn.getId() == NOTIFY_ID) return sbn;
        }
        return null;
    }

    private void clearNudge(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(NOTIFY_ID);
    }

    @Test
    public void staleSyncPostsNudgeOnceAndStampsToday() throws Exception {
        Context context = ctx();
        assertTrue("需要已登录(卡片令牌为空则不会查日程)", !WidgetPrefs.token(context).isEmpty());
        assertTrue("通知权限未开", context.getSystemService(NotificationManager.class).areNotificationsEnabled());

        // 前置:8 天前对过账 + 今天还没提示过
        WidgetPrefs.setReminderSyncAt(context, System.currentTimeMillis() - 8 * DAY_MS);
        WidgetPrefs.setLastNudgeDate(context, "");
        clearNudge(context);

        try {
            runWorkerOnce(context);

            String today = LocalDate.now().toString();
            assertEquals("提示后应记下当天日期", today, WidgetPrefs.lastNudgeDate(context));

            StatusBarNotification sbn = findNudge(context);
            assertNotNull("应贴出「日程该校准了」提示", sbn);
            Notification n = sbn.getNotification();
            assertEquals(context.getString(R.string.notify_stale_title),
                    String.valueOf(n.extras.getCharSequence(Notification.EXTRA_TITLE)));
            String text = String.valueOf(n.extras.getCharSequence(Notification.EXTRA_TEXT));
            assertTrue("正文应带日程条数: " + text, text.matches(".*\\d+.*"));
            assertEquals("应挂在 reminders-v2 通道", ReminderNotifier.CHANNEL_ID, n.getChannelId());
            assertNotNull("提示必须带深链跳转", n.contentIntent);

            long postedAt = n.when;
            // 同一天再跑:日期守卫拦下,不重复发、不刷新时间戳
            Thread.sleep(300);
            runWorkerOnce(context);
            assertNotNull("提示不应消失", findNudge(context));
            assertEquals("同一天不应重复打扰", postedAt, findNudge(context).getNotification().when);
        } finally {
            WidgetPrefs.setLastNudgeDate(context, "");
        }
    }

    @Test
    public void recentlySyncedDeviceIsNotNudged() throws Exception {
        Context context = ctx();
        assertTrue("需要已登录(卡片令牌为空则不会查日程)", !WidgetPrefs.token(context).isEmpty());

        WidgetPrefs.setReminderSyncAt(context, System.currentTimeMillis() - DAY_MS);
        WidgetPrefs.setLastNudgeDate(context, "");
        clearNudge(context);

        try {
            runWorkerOnce(context);
            assertEquals("7 天内对过账不该打扰", "", WidgetPrefs.lastNudgeDate(context));
            assertNull("不该贴提示", findNudge(context));
        } finally {
            WidgetPrefs.setLastNudgeDate(context, "");
        }
    }
}
