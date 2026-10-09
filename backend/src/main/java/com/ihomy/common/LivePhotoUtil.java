package com.ihomy.common;

import java.util.Locale;

/**
 * 苹果 Live Photo 配对工具:实况照片由静态图(HEIC/JPEG)与同名短片(MOV)成对组成,
 * 上传时按文件名基底配对(苹果导出/隔空投送即此命名,如 IMG_1234.HEIC + IMG_1234.MOV)。
 * ponytail:不解析 EXIF/ContentIdentifier,文件名配对已覆盖苹果导出工作流;需处理改名场景再加元数据解析。
 */
public final class LivePhotoUtil {

    private LivePhotoUtil() {}

    /** 是否实况短片:按 content-type 或扩展名判定 */
    public static boolean isVideo(String filename, String contentType) {
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("video/")) return true;
        String ext = extension(filename);
        return ext.equals("mov") || ext.equals("mp4") || ext.equals("m4v");
    }

    /** 配对键:去目录、去扩展名、转小写(IMG_1234.HEIC 与 IMG_1234.MOV → img_1234) */
    public static String baseKey(String filename) {
        if (filename == null) return "";
        String name = filename.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        return name.toLowerCase(Locale.ROOT);
    }

    private static String extension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }
}
