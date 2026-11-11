package top.ihomy.app.widget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/**
 * 四个 provider 的统一刷新入口:provider onUpdate 与 Worker 完成后都走这里重绘全部卡片。
 */
public final class Widgets {

    public static final Class<?>[] PROVIDERS = {
            TodayWidgetProvider.class, ReminderWidgetProvider.class, CountdownWidgetProvider.class,
            BookWidgetProvider.class
    };

    private Widgets() {
    }

    public static void updateAll(Context context) {
        for (Class<?> provider : PROVIDERS) {
            // 直接发 UPDATE 广播,让 provider 自己走 onUpdate 渲染(与系统路径一致)
            Intent i = new Intent(context, provider);
            i.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            int[] ids = AppWidgetManager.getInstance(context)
                    .getAppWidgetIds(new ComponentName(context, provider));
            i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
            context.sendBroadcast(i);
        }
    }
}
