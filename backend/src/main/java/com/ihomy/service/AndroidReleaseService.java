package com.ihomy.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Android 客户端更新信息。
 *
 * 口径:**以 uploads/app 里实际存在的安装包为准**,不再依赖外挂配置手工同步版本号。
 * 发新版只要把包按 `ihomy-V<主>.<次>.apk` 传上去就生效(扫目录结果缓存 60s),
 * 不用改 external.yml、不用重启后端——「网页发了、客户端还提示上次版本」这个断层因此不会再出现。
 *
 * external.yml 的 app.android.* 只在目录里扫不到包时兜底:
 * - latest-version 留空 = 尚未发布 Android 包,客户端不提示更新;
 * - latest-notes 只描述它对应的那一版(磁盘版本与配置版本不一致时不显示,免得文案张冠李戴);
 * - min-supported-version 是强制更新下限,仍由配置决定。
 */
@Slf4j
@Service
public class AndroidReleaseService {

    /** 安装包命名(与 scripts/release-app.ps1 一致;文件名就是 /files/app/ 下的下载地址) */
    private static final Pattern APK_NAME = Pattern.compile("^ihomy-V(\\d+)\\.(\\d+)\\.apk$");
    /** 目录扫描缓存:发版后最迟一分钟生效,避免每次 App 启动都列目录 */
    private static final long CACHE_MILLIS = 60_000;

    private final String uploadDir;
    private final String configuredVersion;
    private final String minSupportedVersion;
    private final String configuredNotes;

    private volatile Release cached;
    private volatile long cachedAt;

    public AndroidReleaseService(@Value("${file.upload-dir:}") String uploadDir,
                                 @Value("${app.android.latest-version:}") String configuredVersion,
                                 @Value("${app.android.min-supported-version:}") String minSupportedVersion,
                                 @Value("${app.android.latest-notes:}") String configuredNotes) {
        this.uploadDir = trim(uploadDir);
        this.configuredVersion = trim(configuredVersion);
        this.minSupportedVersion = trim(minSupportedVersion);
        this.configuredNotes = trim(configuredNotes);
    }

    /** /public/app/latest 的响应体:version 留空表示尚未发布,客户端不弹更新 */
    public Map<String, Object> latest() {
        Release release = release();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("version", release.version());
        data.put("url", release.version().isEmpty() ? "" : "/files/app/ihomy-" + release.version() + ".apk");
        data.put("notes", release.notes());
        data.put("minSupported", minSupportedVersion);
        return data;
    }

    private Release release() {
        long now = System.currentTimeMillis();
        Release snapshot = cached;
        if (snapshot != null && now - cachedAt < CACHE_MILLIS) return snapshot;
        synchronized (this) {
            snapshot = cached;
            if (snapshot != null && System.currentTimeMillis() - cachedAt < CACHE_MILLIS) return snapshot;
            cached = scan();
            cachedAt = System.currentTimeMillis();
            return cached;
        }
    }

    /** 目录里版本最高的安装包;扫不到(目录不可读/还没传过包)回落外挂配置 */
    private Release scan() {
        String detected = "";
        try (Stream<Path> files = Files.list(appDir())) {
            detected = pickLatest(files.map(p -> p.getFileName().toString()).toList());
        } catch (Exception e) {
            log.warn("scan android releases failed, dir={}, fallback to config", appDir(), e);
        }
        if (detected.isEmpty()) {
            return new Release(configuredVersion, configuredNotes);
        }
        // 说明文案只跟着它描述的那一版:上传了新包却没同步文案时不显示旧文案
        String notes = detected.equalsIgnoreCase(configuredVersion) ? configuredNotes : "";
        return new Release(detected, notes);
    }

    private Path appDir() {
        return Path.of(uploadDir, "app");
    }

    /** 从文件名列表里挑版本最高的安装包,返回 'V<主>.<次>';没有合法包返回空串 */
    static String pickLatest(List<String> fileNames) {
        int bestMajor = -1;
        int bestMinor = -1;
        for (String name : fileNames) {
            Matcher m = APK_NAME.matcher(name == null ? "" : name);
            if (!m.matches()) continue;
            int major = Integer.parseInt(m.group(1));
            int minor = Integer.parseInt(m.group(2));
            if (major > bestMajor || (major == bestMajor && minor > bestMinor)) {
                bestMajor = major;
                bestMinor = minor;
            }
        }
        return bestMajor < 0 ? "" : "V" + bestMajor + "." + bestMinor;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private record Release(String version, String notes) {
    }
}
