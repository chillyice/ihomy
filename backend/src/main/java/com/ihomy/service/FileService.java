package com.ihomy.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 文件存储(本地磁盘,V4.1 分类目录):
 * - 通用上传:音乐(audio/*)进 {uploadDir}/music/,其余进 {uploadDir}/files/{yyyyMM}
 * - 相册图片:进 {uploadDir}/pictures/{相册名}/(相册名为空则平铺)
 * - 视频/海报:进 {uploadDir}/videos/   (上传时影片名不可知,平铺+时间戳前缀)
 * 返回可经 /files/ 访问的 URL。目标目录由 nginx /files/ 托管,开发期经 /api/files/。
 */
@Slf4j
@Service
public class FileService {

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${file.url-prefix}")
    private String urlPrefix;

    /** 通用上传:音乐按内容类型识别进 music/,其余按月分类进 files/ */
    public String upload(byte[] bytes, String originalName, String contentType) {
        if (contentType != null && contentType.startsWith("audio/")) {
            return saveTo(bytes, originalName, "music", null, null);
        }
        String yyyyMM = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        return saveTo(bytes, originalName, "files", yyyyMM, null);
    }

    /** 相册图片上传:按相册名建子目录(相册名为空时平铺) */
    public String upload(byte[] bytes, String originalName, String contentType, Long albumId, String albumName) {
        return saveTo(bytes, originalName, "pictures", albumName, albumId);
    }

    /** 视频/海报上传:统一进 videos/ 平铺 */
    public String uploadVideo(byte[] bytes, String originalName, String contentType) {
        return saveTo(bytes, originalName, "videos", null, null);
    }

    /** 按 URL 删除已上传文件:仅处理本站 URL(外链/空直接忽略),文件不存在容忍,失败仅告警 */
    public void deleteByUrl(String url) {
        if (url == null || !url.startsWith(urlPrefix)) return;
        try {
            Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
            String rel = url.substring(urlPrefix.length()).replace('\\', '/').replaceFirst("^/+", "");
            Path target = root.resolve(rel).normalize();
            if (!target.startsWith(root)) return;   // URL 逃逸防御
            Files.deleteIfExists(target);
            Path parent = target.getParent();
            if (parent != null && !parent.equals(root)) {
                Files.delete(parent);  // 尝试清空父目录(如已空的相册目录),失败忽略
            }
        } catch (IOException e) {
            log.warn("删除文件失败(忽略): {}", url);
        }
    }

    private String saveTo(byte[] bytes, String originalName, String root, String sub, Long albumId) {
        // 扩展名黑名单:拒绝脚本/可执行文件,防存储型 XSS(nginx /files/ 按类型服务)
        if (originalName != null) {
            int dot = originalName.lastIndexOf('.');
            if (dot >= 0) {
                String ext = originalName.substring(dot + 1).toLowerCase();
                if (java.util.Set.of("html", "htm", "svg", "js", "jsp", "php", "asp", "aspx", "exe", "bat", "cmd", "sh", "css").contains(ext)) {
                    throw new com.ihomy.common.BizException(com.ihomy.common.ResultCode.BAD_REQUEST, "不支持的文件类型: " + ext);
                }
            }
        }
        try {
            String base = originalName == null ? "file" : originalName.replaceAll("[^\\w.\\-\\u4e00-\\u9fa5]", "_");
            String fileName = (albumId == null ? "" : albumId + "_") + System.currentTimeMillis() + "_" + base;
            String cleanSub = sub == null || sub.isBlank() ? null
                    : sub.replaceAll("[^\\w.\\-\\u4e00-\\u9fa5]", "_");
            Path dir = cleanSub == null ? Paths.get(uploadDir, root)
                    : Paths.get(uploadDir, root, cleanSub);
            Files.createDirectories(dir);
            Files.write(dir.resolve(fileName), bytes);
            String path = urlPrefix + "/" + root + (cleanSub == null ? "" : "/" + cleanSub) + "/" + fileName;
            log.info("文件已保存: {}", path);
            return path;
        } catch (IOException e) {
            log.error("文件上传失败", e);
            throw new RuntimeException("文件上传失败");
        }
    }
}