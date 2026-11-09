package top.ihomy.app.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * 卡片渲染基类:统一「未登录 / 无数据 / 正常」三态与深链 PendingIntent 的拼装。
 * 深链走 ihomy:// 自定义 scheme(Manifest 已注册),进入壳内由 native/deeplink.js 统一路由。
 */
public abstract class BaseWidgetProvider extends AppWidgetProvider {

    protected static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA);

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            manager.updateAppWidget(id, render(context));
        }
    }

    private RemoteViews render(Context context) {
        String token = WidgetPrefs.token(context);
        if (token == null || token.isEmpty()) {
            RemoteViews v = new RemoteViews(context.getPackageName(), emptyLayout());
            v.setTextViewText(android.R.id.text1, context.getString(top.ihomy.app.R.string.widget_sign_in));
            v.setOnClickPendingIntent(android.R.id.text1, deepLink(context, "home"));
            return v;
        }
        List<WidgetRepository.Occurrence> items = WidgetRepository.readCache(context, widgetId());
        if (items.isEmpty()) {
            RemoteViews v = new RemoteViews(context.getPackageName(), emptyLayout());
            v.setTextViewText(android.R.id.text1, context.getString(top.ihomy.app.R.string.widget_loading));
            v.setOnClickPendingIntent(android.R.id.text1, deepLink(context, deepLinkPath()));
            return v;
        }
        return renderWithData(context, items);
    }

    /** 各卡片自己的正常态渲染 */
    protected abstract RemoteViews renderWithData(Context context, List<WidgetRepository.Occurrence> items);

    protected abstract String widgetId();

    protected abstract String deepLinkPath();

    protected abstract int layout();

    protected int emptyLayout() {
        return layout();
    }

    /** 今日(或指定日期)的事项,按时刻升序、无时刻在后 */
    protected static List<WidgetRepository.Occurrence> onDate(List<WidgetRepository.Occurrence> items, LocalDate date) {
        java.util.List<WidgetRepository.Occurrence> out = new java.util.ArrayList<>();
        String d = date.toString();
        for (WidgetRepository.Occurrence o : items) {
            if (d.equals(o.date)) out.add(o);
        }
        out.sort((a, b) -> {
            String ta = a.time == null ? "99:99" : a.time;
            String tb = b.time == null ? "99:99" : b.time;
            int c = ta.compareTo(tb);
            return c != 0 ? c : safeCompare(a.key, b.key);
        });
        return out;
    }

    private static int safeCompare(String a, String b) {
        return a == null ? -1 : b == null ? 1 : a.compareTo(b);
    }

    /** 时刻展示:无时刻给「全天」 */
    protected static String timeText(WidgetRepository.Occurrence o) {
        return o.time == null || o.time.isEmpty() ? "全天" : o.time;
    }

    /** 深链 PendingIntent:显式指向本包,避免外溢 */
    protected static PendingIntent deepLink(Context context, String path) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("ihomy://" + path));
        i.setPackage(context.getPackageName());
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(context,
                (path + "|" + context.getClass().getName()).hashCode(),
                i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** 更新时间小字:HH:mm 更新 */
    protected static String updatedText(Context context, String widgetId) {
        long at = WidgetPrefs.updatedAt(context, widgetId);
        if (at <= 0) return "";
        java.time.LocalTime local = java.time.LocalDateTime
                .ofInstant(java.time.Instant.ofEpochMilli(at), java.time.ZoneId.systemDefault()).toLocalTime();
        return local.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) + " 更新";
    }
}
