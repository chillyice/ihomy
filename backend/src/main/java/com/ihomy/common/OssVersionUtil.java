package com.ihomy.common;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 开源组件版本工具:纯规则解析比较,不调用 AI/LLM。
 * 支持形如 "1.2.3" / "v1.2.3" / "1.2.3-SNAPSHOT" / "10.9.0+deb" / "2024.10.0" 的版本号,
 * 比较大小并归类 MAJOR/MINOR/PATCH/NONE。
 * 预发布(RC/M/SNAPSHOT/-fix/不同版本轨道的前缀标签)不参与升级判定。
 */
public final class OssVersionUtil {

    private static final Pattern DIGIT = Pattern.compile("\\d+");
    /** 数字段后跟 "-字母":4.2.0-RC1 / 0.14.0-fix.3 / mysql-cluster-26.7.0 都算非稳定版 */
    private static final Pattern DASH_PRERELEASE = Pattern.compile("-[A-Za-z]");
    private static final Pattern SNAPSHOT = Pattern.compile("SNAPSHOT", Pattern.CASE_INSENSITIVE);

    private OssVersionUtil() {
    }

    /** 提取数字段:1.2.3 → [1,2,3];无数字返回空列表 */
    public static List<Integer> parse(String version) {
        List<Integer> seg = new ArrayList<>();
        if (version == null || version.isBlank()) {
            return seg;
        }
        Matcher m = DIGIT.matcher(version);
        while (m.find() && seg.size() < 4) {
            seg.add(Integer.parseInt(m.group()));
        }
        return seg;
    }

    /**
     * 是否预发布/非稳定版:含 SNAPSHOT,或数字段后跟 "-字母"(RC/M/fix 等)。
     * 纯数字段(含 3.5.18.1)与 v12.2 这类前缀标签不是预发布。
     */
    public static boolean isPrerelease(String version) {
        if (version == null || version.isBlank()) {
            return false;
        }
        String v = version.trim();
        return SNAPSHOT.matcher(v).find() || DASH_PRERELEASE.matcher(v).find();
    }

    /** 从版本列表里挑最高稳定版:过滤预发布与不可比较项,空则返回 null */
    public static String pickLatestStable(List<String> versions) {
        if (versions == null || versions.isEmpty()) {
            return null;
        }
        String best = null;
        for (String raw : versions) {
            if (raw == null || raw.isBlank() || isPrerelease(raw) || parse(raw).isEmpty()) {
                continue;
            }
            String v = raw.trim();
            if (best == null) {
                best = v;
                continue;
            }
            Integer cmp = compare(v, best);
            if (cmp != null && cmp > 0) {
                best = v;
            }
        }
        return best;
    }

    /** a > b 返回 1,a < b 返回 -1,相等 0;任一方不可比较返回 null */
    public static Integer compare(String a, String b) {
        List<Integer> sa = parse(a);
        List<Integer> sb = parse(b);
        if (sa.isEmpty() || sb.isEmpty()) {
            return null;
        }
        int n = Math.max(sa.size(), sb.size());
        for (int i = 0; i < n; i++) {
            int va = i < sa.size() ? sa.get(i) : 0;
            int vb = i < sb.size() ? sb.get(i) : 0;
            if (va != vb) {
                return va > vb ? 1 : -1;
            }
        }
        return 0;
    }

    /** 归类更新类型:current → latest;latest 是预发布、不高于 current 或不可比较时返回 NONE */
    public static String updateType(String current, String latest) {
        if (isPrerelease(latest)) {
            return DictConst.OSS_UPDATE_NONE;
        }
        List<Integer> sc = parse(current);
        List<Integer> sl = parse(latest);
        if (sc.isEmpty() || sl.isEmpty()) {
            return DictConst.OSS_UPDATE_NONE;
        }
        Integer cmp = compare(latest, current);
        if (cmp == null || cmp <= 0) {
            return DictConst.OSS_UPDATE_NONE;
        }
        if (sl.get(0) > sc.get(0)) {
            return DictConst.OSS_UPDATE_MAJOR;
        }
        int cMinor = sc.size() > 1 ? sc.get(1) : 0;
        int lMinor = sl.size() > 1 ? sl.get(1) : 0;
        if (lMinor > cMinor) {
            return DictConst.OSS_UPDATE_MINOR;
        }
        return DictConst.OSS_UPDATE_PATCH;
    }
}
