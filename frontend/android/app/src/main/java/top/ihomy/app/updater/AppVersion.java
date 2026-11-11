package top.ihomy.app.updater;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 客户端版本号比较(原生侧唯一实现):与前端 {@code utils/appUpdate.js} 同口径 —— 只认
 * {@code V<主>.<次>},解析不出来一律按「不比」处理(宁漏不误,不提示错误更新)。
 */
public final class AppVersion {

    private static final Pattern VERSION_RE = Pattern.compile("^V(\\d+)\\.(\\d+)$", Pattern.CASE_INSENSITIVE);

    private AppVersion() {
    }

    /** V11.92 → 11092;解析不出来返回 -1 */
    public static int code(String version) {
        Matcher m = VERSION_RE.matcher(version == null ? "" : version.trim());
        if (!m.matches()) return -1;
        try {
            return Integer.parseInt(m.group(1)) * 1000 + Integer.parseInt(m.group(2));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** 比较:{@code a} 低于 {@code b} 返回负数;任一侧非法返回 0(视为相同,不做更新) */
    public static int compare(String a, String b) {
        int left = code(a);
        int right = code(b);
        if (left < 0 || right < 0) return 0;
        return Integer.compare(left, right);
    }
}
