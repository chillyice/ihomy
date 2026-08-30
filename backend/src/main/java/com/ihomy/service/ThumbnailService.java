package com.ihomy.service;

import com.ihomy.entity.StorageDevice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.Semaphore;

/**
 * 设备照片缩略图缓存:第一次访问下载原图生成 480px 缩略图落盘,之后直接读缓存。
 * 解决映射相册网格页一次拉几十张原图(百度 dlink 限速/限频)的卡顿。
 * 缓存 key = MD5(deviceId:fsId 或 deviceId:path):百度 fsId 变了(文件被替换)自动重新生成;
 * NAS 无 fsId 用路径(内容原地覆盖不会刷新缓存,家庭照片场景可接受)。
 * HEIC 等 ImageIO 不可读格式返回 null,调用方回退原图流式。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThumbnailService {

    private static final int MAX_WIDTH = 480;
    /** 生成阶段并发上限:原图 byte[] + BufferedImage 全解码很吃堆(-Xmx384m),50 张并发生成会 OOM */
    private static final Semaphore GEN_SLOTS = new Semaphore(2);

    private final StorageService storageService;

    @Value("${file.upload-dir}")
    private String uploadDir;

    /** 取缩略图字节(jpeg);未命中则下载原图生成;不可读格式/失败返回 null(回退原图) */
    public byte[] thumb(StorageDevice device, String path, Long fsId) {
        try {
            Path cache = cachePath(device, path, fsId);
            if (Files.exists(cache)) {
                return Files.readAllBytes(cache);
            }
            GEN_SLOTS.acquire();
            try {
                byte[] src = readSource(device, path, fsId);
                if (src == null || src.length == 0) return null;
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(src));
                if (img == null) return null; // HEIC 等不可读 → 回退原图
                byte[] thumbBytes = scale(img);
                Files.createDirectories(cache.getParent());
                // ponytail: 并发未命中可能重复生成同一张,写文件 REPLACE 幂等,浪费一次下载可接受
                Files.write(cache, thumbBytes);
                return thumbBytes;
            } finally {
                GEN_SLOTS.release();
            }
        } catch (Throwable e) { // 含 OutOfMemoryError:生成失败一律回退原图,不 500
            log.warn("缩略图生成失败(回退原图): device={}, path={} | {}", device.getId(), path, e.toString());
            return null;
        }
    }

    /** 清空设备缩略图缓存(运维/复现用),返回删除的缓存文件数 */
    public int clearCache() {
        Path dir = Paths.get(uploadDir, "device-thumbs");
        if (!Files.exists(dir)) return 0;
        int[] removed = {0};
        try (var files = Files.list(dir)) {
            files.forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                    removed[0]++;
                } catch (Exception ignored) {
                }
            });
        } catch (Exception e) {
            log.warn("清空缩略图缓存失败: {}", e.toString());
        }
        log.info("缩略图缓存已清空,删除 {} 个文件", removed[0]);
        return removed[0];
    }

    /** 下载原图全量字节:BAIDU 走 dlink 中转(并发限流内),本地/挂载读盘 */
    private byte[] readSource(StorageDevice device, String path, Long fsId) throws Exception {
        if ("BAIDU".equals(device.getDeviceType())) {
            StorageService.BaiduFileStream fs = storageService.baiduOpen(device, path, fsId);
            try (InputStream in = fs.in()) {
                return in.readAllBytes();
            }
        }
        return storageService.readFileBytes(device, path);
    }

    private byte[] scale(BufferedImage src) throws Exception {
        int w = src.getWidth(), h = src.getHeight();
        if (w <= MAX_WIDTH) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(src, "jpg", bos);
            return bos.toByteArray();
        }
        int newH = Math.round(h * ((float) MAX_WIDTH / w));
        BufferedImage dst = new BufferedImage(MAX_WIDTH, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        g.drawImage(src, 0, 0, MAX_WIDTH, newH, null);
        g.dispose();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(dst, "jpg", bos);
        return bos.toByteArray();
    }

    /** 缓存文件:uploadDir/device-thumbs/{md5}.jpg */
    private Path cachePath(StorageDevice device, String path, Long fsId) throws Exception {
        String key = device.getId() + ":" + (fsId != null && fsId > 0 ? "fs" + fsId : path);
        byte[] digest = MessageDigest.getInstance("MD5")
                .digest(key.getBytes(StandardCharsets.UTF_8));
        String name = HexFormat.of().formatHex(digest) + ".jpg";
        return Paths.get(uploadDir, "device-thumbs").resolve(name);
    }
}
