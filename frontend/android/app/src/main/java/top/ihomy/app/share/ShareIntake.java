package top.ihomy.app.share;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

/**
 * 系统收件箱:把「微信/相册/浏览器/文件管理器 → 分享 → ihomy」的 {@code ACTION_SEND} 意图,
 * 以及「文件管理器/系统相册 → 打开方式 → ihomy」的 {@code ACTION_VIEW} 意图先接住,
 * 再交给 {@link ShareInboxPlugin} 落盘、生成缩略图并推给 WebView。
 *
 * <p>只保存意图里的原始内容(text/subject/uris),**不做耗时 IO**——复制文件与解码缩略图都在
 * 插件的后台线程里做,避免主线程被大图卡住(分享来自相机原图时尤其明显)。
 *
 * <p>{@code ihomy://} 的 VIEW 是卡片/快捷方式深链,由前端 deeplink.js 路由,这里一律不收。
 *
 * <p>进程内静态持有:分享是「用户此刻操作 + 立刻拉起本应用」,不需要跨进程持久化;
 * 但 {@code peek/take} 分离,保证处理失败时内容不丢,用户可以重试一次。
 */
public final class ShareIntake {

    /** 从别的应用分享进来(SEND / SEND_MULTIPLE) */
    public static final String MODE_SHARE = "share";
    /** 用 ihomy 打开(content:// / file://) */
    public static final String MODE_OPEN = "open";

    private static Pending pending;

    private ShareIntake() {}

    /**
     * 是否是可接的收件意图:
     * <ul>
     *   <li>分享(SEND / SEND_MULTIPLE)一律接;</li>
     *   <li>打开(VIEW)只接 content:// 与 file:// 且 MIME 非空的单文件;</li>
     *   <li>{@code ihomy://...} 深链、MAIN 启动意图一律忽略。</li>
     * </ul>
     */
    public static boolean isIncoming(Intent intent) {
        if (intent == null) return false;
        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action)) return true;
        if (!Intent.ACTION_VIEW.equals(action)) return false;
        Uri data = intent.getData();
        if (data == null) return false;
        String scheme = data.getScheme();
        if (!"content".equalsIgnoreCase(scheme) && !"file".equalsIgnoreCase(scheme)) return false;
        String type = intent.getType();
        return type != null && type.contains("/");
    }

    /** 收下意图内容;返回 true 表示确实收到东西(调用方据此通知 WebView) */
    public static boolean capture(Intent intent) {
        if (!isIncoming(intent)) return false;
        boolean open = Intent.ACTION_VIEW.equals(intent.getAction());
        String text = readText(intent);
        String subject = readString(intent, Intent.EXTRA_SUBJECT);
        List<Uri> uris = open ? readViewUri(intent) : readUris(intent);
        if ((text == null || text.trim().isEmpty()) && uris.isEmpty()) return false;
        pending = new Pending(
                open ? MODE_OPEN : MODE_SHARE,
                text,
                subject,
                uris,
                intent.getType(),
                System.currentTimeMillis());
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

    /** 打开方式:数据就是唯一 URI,EXTRA_STREAM 偶尔也带 */
    private static List<Uri> readViewUri(Intent intent) {
        List<Uri> out = new ArrayList<>();
        Uri data = intent.getData();
        if (data != null) out.add(data);
        return out;
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

    /** 一次收件的原始内容 */
    public static final class Pending {
        /** share / open,前端据此换引导语 */
        public final String mode;
        public final String text;
        public final String subject;
        public final List<Uri> uris;
        /** 意图声明的 MIME(VIEW 来源常靠它,单个 URI 也可能为空) */
        public final String mime;
        public final long receivedAt;

        Pending(String mode, String text, String subject, List<Uri> uris, String mime, long receivedAt) {
            this.mode = mode;
            this.text = text;
            this.subject = subject;
            this.uris = uris;
            this.mime = mime;
            this.receivedAt = receivedAt;
        }
    }
}
