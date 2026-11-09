package top.ihomy.app.widget;

import android.content.Context;
import android.widget.RemoteViews;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 纪念日倒计时卡(2×2):下一个纪念日 + 剩余天数。
 * days 字段由服务端给出(相对服务端今天);本地只在其缺失时兜底现算,不引入第二套日期算法。
 */
public class CountdownWidgetProvider extends BaseWidgetProvider {

    @Override
    protected RemoteViews renderWithData(Context context, List<WidgetRepository.Occurrence> items) {
        RemoteViews v = new RemoteViews(context.getPackageName(), layout());
        WidgetRepository.Occurrence next = null;
        for (WidgetRepository.Occurrence o : items) {
            if (!"ANNIVERSARY".equals(o.kind)) continue;
            if (next == null || o.date.compareTo(next.date) < 0) next = o;
        }
        if (next == null) {
            v.setTextViewText(top.ihomy.app.R.id.w_days, "--");
            v.setTextViewText(top.ihomy.app.R.id.w_title, context.getString(top.ihomy.app.R.string.widget_no_anniversary));
            v.setTextViewText(top.ihomy.app.R.id.w_footer, updatedText(context, widgetId()));
            v.setOnClickPendingIntent(top.ihomy.app.R.id.w_root, deepLink(context, deepLinkPath()));
            return v;
        }
        long days = next.days != Long.MIN_VALUE ? next.days
                : ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(next.date));
        if (days < 0) days = 0;
        v.setTextViewText(top.ihomy.app.R.id.w_days, String.valueOf(days));
        v.setTextViewText(top.ihomy.app.R.id.w_title, next.title);
        v.setTextViewText(top.ihomy.app.R.id.w_footer,
                LocalDate.parse(next.date).format(MD) + " · " + updatedText(context, widgetId()));
        v.setOnClickPendingIntent(top.ihomy.app.R.id.w_root, deepLink(context, deepLinkPath()));
        return v;
    }

    @Override
    protected String widgetId() {
        return "countdown";
    }

    @Override
    protected String deepLinkPath() {
        return "anniversary";
    }

    @Override
    protected int layout() {
        return top.ihomy.app.R.layout.widget_countdown;
    }
}
