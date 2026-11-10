package top.ihomy.app.tile;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import top.ihomy.app.MainActivity;

/**
 * 快速设置磁贴的公共实现:点一下直接从下拉栏进入站内某个页面。
 *
 * <p>磁贴没有「开/关」语义(不做状态),统一用非激活态 + 图标 + 短标签;
 * 点击走 ihomy:// 深链,与桌面卡片/快捷方式共用一套落点(deeplink.js 统一路由)。
 */
abstract class DeeplinkTileService extends TileService {

    private static final String TAG = "IhomyTile";

    /** 磁贴短标签(下拉栏里显示的 3–4 个字) */
    protected abstract String tileLabel();

    /** 磁贴图标(单色 vector) */
    protected abstract int tileIcon();

    /** 站内深链,例如 ihomy://book */
    protected abstract String deeplink();

    @Override
    public void onStartListening() {
        super.onStartListening();
        Tile tile = getQsTile();
        if (tile == null) return;
        tile.setLabel(tileLabel());
        tile.setIcon(Icon.createWithResource(this, tileIcon()));
        tile.setState(Tile.STATE_INACTIVE);
        tile.updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        // 锁屏下拉栏点击:系统要求先解锁再执行
        if (isLocked()) {
            unlockAndRun(this::launch);
        } else {
            launch();
        }
    }

    /** API 34 以下只有 Intent 版本(已废弃但必须用),34+ 必须用 PendingIntent 版本 */
    @SuppressWarnings("deprecation")
    private void launch() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(deeplink()));
        intent.setClass(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14 起必须用 PendingIntent 版本,旧签名已废弃
                PendingIntent pending = PendingIntent.getActivity(
                        this, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                startActivityAndCollapse(pending);
            } else {
                startActivityAndCollapse(intent);
            }
        } catch (Exception e) {
            // 拉起失败不能抛给系统面板(否则弹「设置已停止运行」),记日志即可
            Log.w(TAG, "磁贴拉起失败: " + deeplink(), e);
        }
    }
}
