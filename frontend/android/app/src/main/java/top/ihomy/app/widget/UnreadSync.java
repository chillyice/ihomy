package top.ihomy.app.widget;

import android.content.Context;

import java.time.LocalTime;

/**
 * 后台未读同步的唯一实现:WorkManager 周期任务(约 30 分钟兜底)与前台服务(约 30 秒)共用。
 *
 * <p>只在「计数增长且总数 &gt; 0」时发通知:固定 id 覆盖、内容不变不重发;
 * 23:00–06:00 静默,但静默期仍更新基线——早上不会因为昨晚的增长补响一声。
 * 应用在前台时调用方传 allowNotify=false:用户看得到站内角标,不再叠系统通知。
 */
public final class UnreadSync {

    private UnreadSync() {
    }

    public static void run(Context ctx, boolean allowNotify) throws Exception {
        if (!WidgetPrefs.unreadNotifyEnabled(ctx)) return;
        WidgetRepository.Unread unread = WidgetRepository.fetchUnread(ctx);
        long lastNotice = WidgetPrefs.lastUnreadNotice(ctx);
        long lastChat = WidgetPrefs.lastUnreadChat(ctx);

        if (unread.total() <= 0) {
            UnreadNotifier.cancel(ctx);
        } else if (allowNotify && !quietHours()
                && (unread.notification > lastNotice || unread.chat > lastChat)) {
            UnreadNotifier.notifyUnread(ctx, unread.chat, unread.notification);
        }
        // 前台/静默期也推进基线:下次进入后台不会把「已经看过的旧增长」再提醒一遍
        WidgetPrefs.setLastUnread(ctx, unread.notification, unread.chat);
    }

    /** 与服务端提醒口径一致:23:00–06:00 不打扰 */
    private static boolean quietHours() {
        int hour = LocalTime.now().getHour();
        return hour >= 23 || hour < 6;
    }
}
