package top.ihomy.app.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.widget.RemoteViews;

import java.util.Locale;

/**
 * 记账本月卡(4×2):本月支出 / 收入 / 结余 + 笔数 + 更新时间,点击进站内记账本。
 * 数据来自 GET /book/summary(只读摘要,WIDGET 令牌白名单精确放行),由 WidgetWorker 随卡片同步缓存。
 *
 * <p>三态与另三张卡一致:未登录 → 「打开 ihomy 登录后查看」;无缓存 → 「正在加载…」;有缓存 → 正常。
 * 不继承 {@link BaseWidgetProvider},因为这里是摘要而不是 occurrences 列表,但复用它拼深链/更新时间。
 */
public class BookWidgetProvider extends AppWidgetProvider {

    private static final String WIDGET_ID = "book";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            manager.updateAppWidget(id, render(context));
        }
    }

    private RemoteViews render(Context context) {
        RemoteViews v = new RemoteViews(context.getPackageName(), top.ihomy.app.R.layout.widget_book);
        String token = WidgetPrefs.token(context);
        if (token == null || token.isEmpty()) {
            v.setTextViewText(top.ihomy.app.R.id.w_title, context.getString(top.ihomy.app.R.string.widget_book_title));
            v.setTextViewText(top.ihomy.app.R.id.w_subtitle, context.getString(top.ihomy.app.R.string.widget_sign_in));
            clearAmounts(v);
            v.setTextViewText(top.ihomy.app.R.id.w_footer, "");
            bindClick(context, v);
            return v;
        }
        String raw = WidgetPrefs.cache(context, WIDGET_ID);
        if (raw == null || raw.isEmpty()) {
            v.setTextViewText(top.ihomy.app.R.id.w_title, context.getString(top.ihomy.app.R.string.widget_book_title));
            v.setTextViewText(top.ihomy.app.R.id.w_subtitle, context.getString(top.ihomy.app.R.string.widget_loading));
            clearAmounts(v);
            v.setTextViewText(top.ihomy.app.R.id.w_footer, "");
            bindClick(context, v);
            return v;
        }
        WidgetRepository.BookSummary s = WidgetRepository.readBookSummary(context);
        v.setTextViewText(top.ihomy.app.R.id.w_title, context.getString(top.ihomy.app.R.string.widget_book_title));
        v.setTextViewText(
                top.ihomy.app.R.id.w_subtitle,
                s.count == 0
                        ? context.getString(top.ihomy.app.R.string.widget_book_empty)
                        : context.getString(top.ihomy.app.R.string.widget_book_count, s.count));
        // 笔数为 0 时三个数字照样显示 0.00,避免卡片忽大忽小
        v.setTextViewText(top.ihomy.app.R.id.w_expense,
                context.getString(top.ihomy.app.R.string.widget_book_expense) + " " + money(s.expense));
        v.setTextViewText(top.ihomy.app.R.id.w_income,
                context.getString(top.ihomy.app.R.string.widget_book_income) + " " + money(s.income));
        v.setTextViewText(top.ihomy.app.R.id.w_balance,
                context.getString(top.ihomy.app.R.string.widget_book_balance) + " " + money(s.balance));
        v.setTextViewText(top.ihomy.app.R.id.w_footer, footer(context, s.month));
        bindClick(context, v);
        return v;
    }

    private static void clearAmounts(RemoteViews v) {
        v.setTextViewText(top.ihomy.app.R.id.w_expense, "");
        v.setTextViewText(top.ihomy.app.R.id.w_income, "");
        v.setTextViewText(top.ihomy.app.R.id.w_balance, "");
    }

    /** 三个态都能点进 /book */
    private static void bindClick(Context context, RemoteViews v) {
        v.setOnClickPendingIntent(top.ihomy.app.R.id.w_root, BaseWidgetProvider.deepLink(context, "book"));
    }

    /** 千分位 + 两位小数;负结余直接带 - 号 */
    private static String money(double value) {
        return String.format(Locale.CHINA, "%,.2f", value);
    }

    /** 「11月 · 21:30 更新」;月份取不到就只显示更新时间 */
    private static String footer(Context context, String month) {
        String updated = BaseWidgetProvider.updatedText(context, WIDGET_ID);
        int m = monthOf(month);
        if (m <= 0) return updated;
        String head = context.getString(top.ihomy.app.R.string.widget_book_month, m);
        return updated.isEmpty() ? head : head + " · " + updated;
    }

    /** yyyy-MM → 月;格式不对返回 0 */
    private static int monthOf(String month) {
        if (month == null || month.length() < 7) return 0;
        try {
            return Integer.parseInt(month.substring(5, 7));
        } catch (Exception e) {
            return 0;
        }
    }
}
