package com.ihomy.service;

import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import javax.imageio.ImageIO;

/**
 * 文件存储(本地磁盘,V4.1 分类目录):
 * - 通用上传:音乐(audio/*)进 {uploadDir}/music/,其余进 {uploadDir}/files/{yyyyMM}
 * - 相册图片:进 {uploadDir}/pictures/{相册名}/(相册名为空则平铺)
 * - 视频/海报:进 {uploadDir}/videos/   (上传时影片名不可知,平铺+时间戳前缀)
 * 返回可经 /files/ 访问的 URL。目标目录由 nginx /files/ 托管,开发期经 /api/files/。
 *
 * 性能:大文件(视频/海报)用流式 transferTo,不再 getBytes() 全量入堆,
 * 生产 -Xmx384m 上传 200MB 视频不再 OOM。小文件仍走 byte[] 重载(简单)。
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
        String url = saveTo(bytes, originalName, "pictures", albumName, albumId);
        generateThumbIfImage(url, contentType);
        return url;
    }

    /** 视频/海报上传:统一进 videos/ 平铺 */
    public String uploadVideo(byte[] bytes, String originalName, String contentType) {
        return saveTo(bytes, originalName, "videos", null, null);
    }

    /** 通用上传(流式):大文件优先用这个,避免 getBytes() 全量入堆 */
    public String upload(MultipartFile file, String originalName, String contentType) {
        if (contentType != null && contentType.startsWith("audio/")) {
            return saveTo(file, originalName, "music", null, null);
        }
        String yyyyMM = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String url = saveTo(file, originalName, "files", yyyyMM, null);
        generateThumbIfImage(url, contentType);
        return url;
    }

    /** 通用上传(本地文件源,流式 Files.copy):MultipartFile 已被 transferTo 消费后用这个 */
    public String upload(Path source, String originalName, String contentType) {
        if (contentType != null && contentType.startsWith("audio/")) {
            return saveTo(source, originalName, "music", null, null);
        }
        String yyyyMM = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String url = saveTo(source, originalName, "files", yyyyMM, null);
        generateThumbIfImage(url, contentType);
        return url;
    }

    /** 相册图片上传(流式) */
    public String upload(MultipartFile file, String originalName, String contentType, Long albumId, String albumName) {
        String url = saveTo(file, originalName, "pictures", albumName, albumId);
        generateThumbIfImage(url, contentType);
        return url;
    }

    /** 视频/海报上传(流式) */
    public String uploadVideo(MultipartFile file, String originalName, String contentType) {
        return saveTo(file, originalName, "videos", null, null);
    }

    /** 电子书上传(流式):存 books/{yyyyMM}/ */
    public String uploadBook(MultipartFile file, String originalName, String contentType) {
        String yyyyMM = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        return saveTo(file, originalName, "books", yyyyMM, null);
    }

    /** 按 URL 删除已上传文件:仅处理本站 URL(外链/空直接忽略),文件不存在容忍,失败仅告警 */
    public void deleteByUrl(String url) {
        String prefix = urlPrefix.replaceAll("/+$", "");
        if (url == null || !url.startsWith(prefix + "/")) return;
        try {
            Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
            String rel = url.substring(prefix.length()).replace('\\', '/').replaceFirst("^/+", "");
            Path target = root.resolve(rel).normalize();
            if (!target.startsWith(root)) return;   // URL 逃逸防御
            Files.deleteIfExists(target);
            // 顺带删除缩略图(约定:原图 xxx.jpg → xxx_thumb.jpg)
            Path thumb = thumbPath(target);
            if (thumb != null) Files.deleteIfExists(thumb);
            Path parent = target.getParent();
            if (parent != null && !parent.equals(root)) {
                Files.delete(parent);  // 尝试清空父目录(如已空的相册目录),失败忽略
            }
        } catch (IOException e) {
            log.warn("删除文件失败(忽略): {}", url);
        }
    }

    /** 扩展名黑名单 + 路径组装,byte[] 写入版本(小文件) */
    private String saveTo(byte[] bytes, String originalName, String root, String sub, Long albumId) {
        validateName(originalName);
        try {
            String[] parts = buildPath(originalName, root, sub, albumId);
            Path dir = Paths.get(parts[0]);
            Files.createDirectories(dir);
            Files.write(dir.resolve(parts[1]), bytes);
            return parts[2];
        } catch (IOException e) {
            log.error("文件上传失败", e);
            throw new RuntimeException("文件上传失败");
        }
    }

    /** 流式版本(大文件优先):直接 transferTo 不入堆 */
    private String saveTo(MultipartFile file, String originalName, String root, String sub, Long albumId) {
        validateName(originalName);
        try {
            String[] parts = buildPath(originalName, root, sub, albumId);
            Path dir = Paths.get(parts[0]);
            Files.createDirectories(dir);
            Path target = dir.resolve(parts[1]);
            // 优先用 MultipartFile.transferTo(底层可能零拷贝),失败回退 InputStream + Files.copy
            try {
                file.transferTo(target.toFile());
            } catch (Exception fallback) {
                try (InputStream in = file.getInputStream()) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            log.info("文件已保存: {}", parts[2]);
            return parts[2];
        } catch (IOException e) {
            log.error("文件上传失败", e);
            throw new RuntimeException("文件上传失败");
        }
    }

    /** 本地文件源版本:Files.copy 不入堆 */
    private String saveTo(Path source, String originalName, String root, String sub, Long albumId) {
        validateName(originalName);
        try {
            String[] parts = buildPath(originalName, root, sub, albumId);
            Path dir = Paths.get(parts[0]);
            Files.createDirectories(dir);
            Files.copy(source, dir.resolve(parts[1]), StandardCopyOption.REPLACE_EXISTING);
            log.info("文件已保存: {}", parts[2]);
            return parts[2];
        } catch (IOException e) {
            log.error("文件上传失败", e);
            throw new RuntimeException("文件上传失败");
        }
    }

    /** 校验文件名扩展名黑名单(防存储型 XSS) */
    private void validateName(String originalName) {
        if (originalName != null) {
            int dot = originalName.lastIndexOf('.');
            if (dot >= 0) {
                String ext = originalName.substring(dot + 1).toLowerCase();
                if (java.util.Set.of("html", "htm", "svg", "js", "jsp", "php", "asp", "aspx", "exe", "bat", "cmd", "sh", "css").contains(ext)) {
                    throw new BizException(ResultCode.BAD_REQUEST, "不支持的文件类型: " + ext);
                }
            }
        }
    }

    /**
     * 组装保存路径 + URL,返回 [dirAbsolute, fileName, urlPath]
     * dirAbsolute: 磁盘绝对路径(用于 Files.write)
     * fileName:    最终文件名(albumId_时间戳_原名)
     * urlPath:     可访问 URL(/files/pictures/相册名/xxx.jpg)
     */
    private String[] buildPath(String originalName, String root, String sub, Long albumId) {
        String base = originalName == null ? "file" : originalName.replaceAll("[^\\w.\\-\\u4e00-\\u9fa5]", "_");
        String fileName = (albumId == null ? "" : albumId + "_") + System.currentTimeMillis() + "_" + base;
        String cleanSub = sub == null || sub.isBlank() ? null
                : sub.replaceAll("[^\\w.\\-\\u4e00-\\u9fa5]", "_");
        String dirStr = cleanSub == null ? Paths.get(uploadDir, root).toString()
                : Paths.get(uploadDir, root, cleanSub).toString();
        String prefix = urlPrefix.replaceAll("/+$", "");
        String urlPath = prefix + "/" + root + (cleanSub == null ? "" : "/" + cleanSub) + "/" + fileName;
        return new String[]{ dirStr, fileName, urlPath };
    }

    /**
     * 图片上传后生成缩略图(约定:原图 xxx.jpg → xxx_thumb.jpg,maxWidth 480px)。
     * 列表/瀑布流用缩略图 URL,大图 viewer 用原图,省手机原图流量(单张可 5-10MB)。
     * 失败仅告警(不影响上传主流程)。
     */
    private void generateThumbIfImage(String url, String contentType) {
        if (contentType == null || !contentType.startsWith("image/")) return;
        try {
            Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
            String prefix = urlPrefix.replaceAll("/+$", "");
            if (!url.startsWith(prefix + "/")) return;
            String rel = url.substring(prefix.length()).replace('\\', '/').replaceFirst("^/+", "");
            Path source = root.resolve(rel).normalize();
            if (!source.startsWith(root) || !Files.exists(source)) return;
            Path thumb = thumbPath(source);
            if (thumb == null || Files.exists(thumb)) return;
            BufferedImage src = ImageIO.read(source.toFile());
            if (src == null) return;  // 非 ImageIO 可读图片(如 HEIC),跳过
            int w = src.getWidth(), h = src.getHeight();
            int maxW = 480;
            if (w <= maxW) {
                // 小图直接复制,不缩放
                Files.copy(source, thumb, StandardCopyOption.REPLACE_EXISTING);
                return;
            }
            int newH = Math.round(h * ((float) maxW / w));
            BufferedImage dst = new BufferedImage(maxW, newH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = dst.createGraphics();
            g.drawImage(src, 0, 0, maxW, newH, null);
            g.dispose();
            ImageIO.write(dst, "jpg", thumb.toFile());
        } catch (Exception e) {
            log.warn("缩略图生成失败(忽略): {} | {}", url, e.toString());
        }
    }

    /** 原图路径 → 缩略图路径(xxx.jpg → xxx_thumb.jpg);非图片返回 null */
    private Path thumbPath(Path source) {
        String name = source.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0) return null;
        String thumbName = name.substring(0, dot) + "_thumb.jpg";
        return source.resolveSibling(thumbName);
    }
}
