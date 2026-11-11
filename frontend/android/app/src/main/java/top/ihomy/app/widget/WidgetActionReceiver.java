package top.ihomy.app.widget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

/**
 * 卡片勾选动作:提醒卡圆圈点击 → 广播 → 入队一次性 Worker(网络与重绘都交给 WidgetWorker)。
 * 广播内不做网络;key 是服务端 occurrences 给的 kind:id:date,最终裁决在后端(家庭隔离 + 只准今天)。
 */
public class WidgetActionReceiver extends BroadcastReceiver {

    public static final String ACTION_TOGGLE = "top.ihomy.app.action.WIDGET_TOGGLE";
    public static final String EXTRA_KEY = "key";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_TOGGLE.equals(intent.getAction())) return;
        String key = intent.getStringExtra(EXTRA_KEY);
        if (key == null || key.isEmpty()) return;
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(WidgetWorker.class)
                .setInputData(new Data.Builder().putString(WidgetWorker.INPUT_TOGGLE_KEY, key).build())
                .build();
        WorkManager.getInstance(context).enqueue(req);
    }
}
