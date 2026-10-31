package com.ihomy.common;

import java.util.Set;

/**
 * GBA 存档文件名:消毒 + 扩展名白名单。
 * 存档落在游戏目录下的 saves/,文件名来自模拟器(ROM 基名 + .srm),也接受手工放进目录的文件,
 * 因此列表/上传/下载/删除所有入口都要过这里,禁止拿原始文件名直接拼路径。
 */
public final class GameSaveName {

    private GameSaveName() {
    }

    /** 允许的存档扩展名:电池存档(srm/sav)、即时存档(state) */
    private static final Set<String> EXTENSIONS = Set.of("srm", "sav", "state");

    /** 单份存档上限(GBA 电池存档最大 128KB,留足余量) */
    public static final long MAX_BYTES = 4L * 1024 * 1024;

    /** 文件名长度上限 */
    public static final int MAX_LENGTH = 120;

    /**
     * 只取文件名部分并消毒:中文/字母/数字/下划线/点/连字符之外一律替换为下划线,
     * 去掉开头的点(防 .. 逃逸);空名返回 null。
     */
    public static String sanitize(String raw) {
        if (raw == null) return null;
        String base = raw.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).trim();
        base = base.replaceAll("[^\\w.\\-\\u4e00-\\u9fa5]", "_").replaceAll("^\\.+", "");
        if (base.isBlank()) return null;
        return base.length() > MAX_LENGTH ? truncateKeepingExtension(base) : base;
    }

    /** 扩展名白名单判定(大小写不敏感) */
    public static boolean allowed(String name) {
        if (name == null) return false;
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) return false;
        return EXTENSIONS.contains(name.substring(dot + 1).toLowerCase());
    }

    /** 超长时截断主名,保留扩展名 */
    private static String truncateKeepingExtension(String name) {
        int dot = name.lastIndexOf('.');
        String ext = dot > 0 ? name.substring(dot) : "";
        String stem = dot > 0 ? name.substring(0, dot) : name;
        int keep = Math.max(1, MAX_LENGTH - ext.length());
        return stem.substring(0, Math.min(stem.length(), keep)) + ext;
    }
}
