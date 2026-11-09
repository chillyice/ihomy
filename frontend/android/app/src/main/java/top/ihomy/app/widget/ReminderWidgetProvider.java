package top.ihomy.app.widget;

import android.content.Context;
import android.widget.RemoteViews;

import java.time.LocalDate;
import java.util.List;

/**
 * 今日提醒卡(4×2):今天 date 的 REMINDER/TASK 时刻清单,最多 4 条。
 * 点击卡片进日程页(完成在 App 内做——WIDGET 令牌只读,卡片不直接勾选,见实施差异)。
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
        int[] rows = {top.ihomy.app.R.id.w_row1, top.ihomy.app.R.id.w_row2,
                top.ihomy.app.R.id.w_row3, top.ihomy.app.R.id.w_row4};
        boolean any = false;
        for (int i = 0; i < rows.length; i++) {
            if (i < todays.size()) {
                WidgetRepository.Occurrence o = todays.get(i);
                v.setTextViewText(rows[i], timeText(o) + "  " + o.title);
                v.setViewVisibility(rows[i], android.view.View.VISIBLE);
                any = true;
            } else {
                v.setViewVisibility(rows[i], android.view.View.GONE);
            }
        }
        v.setTextViewText(top.ihomy.app.R.id.w_footer, any ? updatedText(context, widgetId())
                : context.getString(top.ihomy.app.R.string.widget_no_reminders));
        v.setOnClickPendingIntent(top.ihomy.app.R.id.w_root, deepLink(context, deepLinkPath()));
        return v;
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
