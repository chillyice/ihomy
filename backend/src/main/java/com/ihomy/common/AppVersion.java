package com.ihomy.common;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 应用版本号:读仓库根 VERSION(与前端 __APP_VERSION__、APK 的 versionName 同源),读不到回落 unknown。
 * 进程启动后读一次即缓存;页脚、运维页、/public/version、/public/health 共用这一份,不发散。
 */
public final class AppVersion {

    public static final String VERSION = read();

    private AppVersion() {
    }

    private static String read() {
        for (String candidate : new String[]{ "../VERSION", "VERSION" }) {
            try {
                Path p = Path.of(candidate);
                if (Files.isReadable(p)) {
                    String v = Files.readString(p).trim();
                    if (!v.isEmpty()) return v;
                }
            } catch (Exception ignored) {
                // 逐个候选路径尝试,全失败回落 unknown
            }
        }
        return "unknown";
    }
}
