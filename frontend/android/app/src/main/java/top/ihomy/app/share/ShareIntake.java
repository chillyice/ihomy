package top.ihomy.app.share;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

/**
 * 系统分享收件箱:把「微信/相册/浏览器 → 分享 → ihomy」的 {@code ACTION_SEND} 意图先接住,
 * 再交给 {@link ShareInboxPlugin} 落盘、生成缩略图并推给 WebView。
 *
 * <p>只保存意图里的原始内容(text/subject/uris),**不做耗时 IO**——复制文件与解码缩略图都在
 * 插件的后台线程里做,避免主线程被大图卡住(分享来自相机原图时尤其明显)。
 *
 * <p>进程内静态持有:分享是「用户此刻操作 + 立刻拉起本应用」,不需要跨进程持久化;
 * 但 {@code peek/take} 分离,保证处理失败时内容不丢,用户可以重试一次。
 */
public final class ShareIntake {

    private static Pending pending;

    private ShareIntake() {}

    /** 是否是可接的分享意图(其余 VIEW/MAIN 意图一律忽略) */
    public static boolean isShare(Intent intent) {
        if (intent == null) return false;
        String action = intent.getAction();
        return Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action);
    }

    /** 收下意图内容;返回 true 表示确实收到东西(调用方据此通知 WebView) */
    public static boolean capture(Intent intent) {
        if (!isShare(intent)) return false;
        String text = readText(intent);
        String subject = readString(intent, Intent.EXTRA_SUBJECT);
        List<Uri> uris = readUris(intent);
        if ((text == null || text.trim().isEmpty()) && uris.isEmpty()) return false;
        pending = new Pending(text, subject, uris, System.currentTimeMillis());
        return true;
    }

    /** 看一眼但不消费(处理成功后再 {@link #clear()}) */
    public static Pending peek() {
        return pending;
    }

    public static void clear() {
        pending = null;
    }

    private static String readText(Intent intent) {
        CharSequence cs = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if (cs == null) cs = intent.getStringExtra(Intent.EXTRA_TEXT);
        return cs == null ? null : cs.toString();
    }

    private static String readString(Intent intent, String key) {
        try {
            return intent.getStringExtra(key);
        } catch (Exception e) {
            return null;
        }
    }

    private static List<Uri> readUris(Intent intent) {
        List<Uri> out = new ArrayList<>();
        boolean multiple = Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction());
        if (!multiple) {
            try {
                Uri single = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (single != null) out.add(single);
            } catch (Exception ignored) {
                // 个别应用 EXTRA_STREAM 类型不规范,忽略即可
            }
            return out;
        }
        try {
            ArrayList<Uri> many;
            if (Build.VERSION.SDK_INT >= 33) {
                many = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri.class);
            } else {
                many = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            }
            if (many != null) {
                for (Uri u : many) {
                    if (u != null && !out.contains(u)) out.add(u);
                }
            }
        } catch (Exception ignored) {
            // 同上:类型不匹配时保持已取到的部分
        }
        return out;
    }

    /** 一次分享的原始内容 */
    public static final class Pending {
        public final String text;
        public final String subject;
        public final List<Uri> uris;
        public final long receivedAt;

        Pending(String text, String subject, List<Uri> uris, long receivedAt) {
            this.text = text;
            this.subject = subject;
            this.uris = uris;
            this.receivedAt = receivedAt;
        }
    }
}
