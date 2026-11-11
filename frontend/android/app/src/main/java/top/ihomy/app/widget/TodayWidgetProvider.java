package top.ihomy.app.widget;

import android.content.Context;
import android.widget.RemoteViews;

import java.time.LocalDate;
import java.util.List;

/**
 * 今日概览卡(4×2):公历日期 + 副标题(农历/节气/天气,全部由服务端算) + 最近 3 条日程。
 * 农历来自 /public/calendar、天气来自 /public/weather(WidgetWorker 随卡片同步缓存),
 * 数据非今天(过期/缺失)时副标题隐藏;未读数仍不做。
 */
public class TodayWidgetProvider extends BaseWidgetProvider {

    @Override
    protected RemoteViews renderWithData(Context context, List<WidgetRepository.Occurrence> items) {
        RemoteViews v = new RemoteViews(context.getPackageName(), layout());
        LocalDate today = LocalDate.now();
        String weekday = today.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, Locale_CHINA());
        v.setTextViewText(top.ihomy.app.R.id.w_title, "今天 · " + today.format(MD) + " " + weekday);
        v.setTextViewText(top.ihomy.app.R.id.w_subtitle, subtitle(context, today));

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

    /** 副标题:「农历十月廿二 · 立冬 · 12° 晴」;缓存非今天(跨天未同步)返回空串隐藏 */
    private static String subtitle(Context context, LocalDate today) {
        WidgetRepository.TodayInfo info = WidgetRepository.readTodayInfo(context);
        if (info.date == null || !today.toString().equals(info.date)) return "";
        StringBuilder sb = new StringBuilder();
        if (notBlank(info.lunar)) sb.append("农历").append(info.lunar);
        if (notBlank(info.mark)) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(info.mark);
        }
        if (notBlank(info.weather)) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(info.weather);
        }
        return sb.toString();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isEmpty();
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
