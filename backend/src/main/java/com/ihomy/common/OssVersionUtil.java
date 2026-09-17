package com.ihomy.common;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 开源组件版本工具:纯规则解析比较,不调用 AI/LLM。
 * 支持形如 "1.2.3" / "v1.2.3" / "1.2.3-SNAPSHOT" / "10.9.0+deb" / "2024.10.0" 的版本号,
 * 比较大小并归类 MAJOR/MINOR/PATCH/NONE。
 */
public final class OssVersionUtil {

    private static final Pattern DIGIT = Pattern.compile("\\d+");

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

    /** 归类更新类型:current → latest;latest 不高于 current 或不可比较时返回 NONE */
    public static String updateType(String current, String latest) {
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
