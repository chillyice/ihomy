package top.ihomy.app.health;

import android.app.Activity;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import top.ihomy.app.R;

/**
 * Health Connect 的「数据用途说明」页。
 * 系统授权弹窗里的「隐私政策/为什么需要」会打开这个 Activity(androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE),
 * 文案只说用户能做什么,不写表名/接口/权限码。
 */
public class HealthPermissionRationaleActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24,
                getResources().getDisplayMetrics());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText(getString(R.string.health_rationale_title));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        root.addView(title);

        TextView body = new TextView(this);
        body.setText(getString(R.string.health_rationale_body));
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        body.setPadding(0, pad / 2, 0, pad / 2);
        root.addView(body);

        Button back = new Button(this);
        back.setText(getString(R.string.health_rationale_ok));
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        root.addView(back);

        setContentView(root);
    }
}
