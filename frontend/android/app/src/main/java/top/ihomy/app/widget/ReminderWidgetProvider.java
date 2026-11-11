package top.ihomy.app.widget;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import java.time.LocalDate;
import java.util.List;

/**
 * 今日提醒卡(4×2):今天 date 的 REMINDER/TASK 时刻清单,最多 4 条。
 * 行尾圆圈点击即勾选:广播给 WidgetActionReceiver → Worker 勾完整轮重拉,
 * 完成项被服务端 occurrences 过滤后自然消失(不做本地乐观更新,失败保持原样)。
 * 点文字区仍进日程页(WIDGET 令牌只放行勾选这一条写路径,见计划文档 §2-D1)。
 */
public class ReminderWidgetProvider extends BaseWidgetProvider {

    @Override
    protected RemoteViews renderWithData(Context context, List<WidgetRepository.Occurrence> items) {
        RemoteViews v = new RemoteViews(context.getPackageName(), layout());
        LocalDate today = LocalDate.now();
        v.setTextViewText(top.ihomy.app.R.id.w_title, "今日提醒 · " + today.format(MD));

        List<WidgetRepository.Occurrence> todays = new java.util.ArrayList<>();
        for (WidgetRepository.Occurrence o : onDate(items, today)) {
            if ("REMINDER".equals(o.kind) || "TASK".equals(o.kind)) todays.add(o);
        }
        int[] wraps = {top.ihomy.app.R.id.w_wrap1, top.ihomy.app.R.id.w_wrap2,
                top.ihomy.app.R.id.w_wrap3, top.ihomy.app.R.id.w_wrap4};
        int[] checks = {top.ihomy.app.R.id.w_check1, top.ihomy.app.R.id.w_check2,
                top.ihomy.app.R.id.w_check3, top.ihomy.app.R.id.w_check4};
        boolean any = false;
        for (int i = 0; i < wraps.length; i++) {
            if (i < todays.size()) {
                WidgetRepository.Occurrence o = todays.get(i);
                v.setTextViewText(rows()[i], timeText(o) + "  " + o.title);
                v.setViewVisibility(wraps[i], android.view.View.VISIBLE);
                v.setOnClickPendingIntent(checks[i], togglePendingIntent(context, o.key));
                any = true;
            } else {
                v.setViewVisibility(wraps[i], android.view.View.GONE);
            }
        }
        v.setTextViewText(top.ihomy.app.R.id.w_footer, any ? updatedText(context, widgetId())
                : context.getString(top.ihomy.app.R.string.widget_no_reminders));
        v.setOnClickPendingIntent(top.ihomy.app.R.id.w_root, deepLink(context, deepLinkPath()));
        return v;
    }

    private int[] rows() {
        return new int[]{top.ihomy.app.R.id.w_row1, top.ihomy.app.R.id.w_row2,
                top.ihomy.app.R.id.w_row3, top.ihomy.app.R.id.w_row4};
    }

    /** 圆圈的勾选广播:显式指向 WidgetActionReceiver,requestCode 按 key 区分避免互相覆盖 */
    private static PendingIntent togglePendingIntent(Context context, String key) {
        Intent i = new Intent(WidgetActionReceiver.ACTION_TOGGLE);
        i.setClass(context, WidgetActionReceiver.class);
        i.putExtra(WidgetActionReceiver.EXTRA_KEY, key);
        return PendingIntent.getBroadcast(context, key.hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Override
    protected String widgetId() {
        return "reminder";
    }

    @Override
    protected String deepLinkPath() {
        return "plan?date=" + LocalDate.now();
    }

    @Override
    protected int layout() {
        return top.ihomy.app.R.layout.widget_reminder;
    }
}
