package top.ihomy.app.widget;

import android.content.Context;
import android.widget.RemoteViews;

import java.time.LocalDate;
import java.util.List;

/**
 * 今日概览卡(4×2):日期 + 最近 3 条日程。
 * 农历/天气/未读数不做(客户端禁算农历;天气需额外定位参数)——v1 只消费 occurrences(计划 §6.5.2 的精简实现)。
 */
public class TodayWidgetProvider extends BaseWidgetProvider {

    @Override
    protected RemoteViews renderWithData(Context context, List<WidgetRepository.Occurrence> items) {
        RemoteViews v = new RemoteViews(context.getPackageName(), layout());
        LocalDate today = LocalDate.now();
        String weekday = today.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, Locale_CHINA());
        v.setTextViewText(top.ihomy.app.R.id.w_title, "今天 · " + today.format(MD) + " " + weekday);

        List<WidgetRepository.Occurrence> todayItems = onDate(items, today);
        if (todayItems.isEmpty()) {
            // 今天没有就给最近 3 条,别让卡片空白
            todayItems = items.subList(0, Math.min(3, items.size()));
        }
        int[] rows = {top.ihomy.app.R.id.w_row1, top.ihomy.app.R.id.w_row2, top.ihomy.app.R.id.w_row3};
        for (int i = 0; i < rows.length; i++) {
            if (i < todayItems.size()) {
                WidgetRepository.Occurrence o = todayItems.get(i);
                v.setTextViewText(rows[i], timeText(o) + "  " + o.title);
                v.setViewVisibility(rows[i], android.view.View.VISIBLE);
            } else {
                v.setViewVisibility(rows[i], android.view.View.GONE);
            }
        }
        v.setTextViewText(top.ihomy.app.R.id.w_footer, updatedText(context, widgetId()));
        v.setOnClickPendingIntent(top.ihomy.app.R.id.w_root, deepLink(context, deepLinkPath()));
        return v;
    }

    /** 独立取 Locale,避免 import 名与字段混淆 */
    private static java.util.Locale Locale_CHINA() {
        return java.util.Locale.CHINA;
    }

    @Override
    protected String widgetId() {
        return "today";
    }

    @Override
    protected String deepLinkPath() {
        return "plan";
    }

    @Override
    protected int layout() {
        return top.ihomy.app.R.layout.widget_today;
    }
}
