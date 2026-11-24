package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ihomy.common.AiConst;
import com.ihomy.common.BizException;
import com.ihomy.common.PhotoThumbnail;
import com.ihomy.common.ResultCode;
import com.ihomy.entity.Photo;
import com.ihomy.entity.StorageDevice;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.FamilyMapper;
import com.ihomy.mapper.PhotoMapper;
import com.ihomy.mapper.StorageDeviceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 照片 AI 标题描述:把照片缩到 ≤768px 后以 data URL 交给多模态 LLM,回填 title + description。
 * 家庭总开关存在 sys_family_info.photo_ai_caption_enabled(默认关闭),只控制「上传后自动生成」;
 * 单张/批量手动生成不受开关限制,但只要功能未绑定模型就不能用。
 * 本地 /files/ 照片直接读盘;映射相册的 storage:// 逻辑地址走缩略图服务取图(自带限流与缓存)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoAiService {

    /** 识别用的最长边:480px 缩略图对细节偏小,768px 在 token 与可读性之间取平衡 */
    private static final int MAX_SIDE = 768;
    private static final long MAX_RAW_BYTES = 20L * 1024 * 1024;
    /** base64 后长度上限(约 4.5MB 原图),避免请求体过大被服务商拒 */
    private static final long MAX_B64_CHARS = 6L * 1024 * 1024;
    /** 识别很吃出站带宽与内存,并发上限 2(同缩略图生成口径) */
    private static final Semaphore SLOTS = new Semaphore(2);
    private static final int WAIT_SECONDS = 20;

    private static final String SYSTEM_PROMPT = """
            你是家庭相册的图片说明助手。只输出 JSON,不要解释,不要使用 markdown。""";
    private static final String USER_PROMPT = """
            请观察这张照片,用简体中文写:
            1) title:一个简短标题,不超过 20 个字;
            2) description:一句自然描述,不超过 60 个字,写出画面里最主要的人、物、场景。
            只描述看得见的内容,不要臆造人名、地点或事件;看不清就写得更笼统。
            输出 JSON:{"title":"...","description":"..."}""";

    private final PhotoMapper photoMapper;
    private final FamilyMapper familyMapper;
    private final StorageDeviceMapper storageDeviceMapper;
    private final ThumbnailService thumbnailService;
    private final FamilyAiConfigService familyAiConfigService;
    private final AiService aiService;
    private final AlbumService albumService;

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${file.url-prefix}")
    private String urlPrefix;

    /** 家庭开关 + 功能是否已绑定可用模型(前端据此显示 AI 入口) */
    public Map<String, Object> status(Long familyId) {
        Map<String, Object> m = new LinkedHashMap<>();
        boolean enabled = familyId != null && Boolean.TRUE.equals(familyMapper.photoCaptionEnabled(familyId));
        boolean available = familyId != null && familyAiConfigService.isAvailable(
                familyAiConfigService.resolveForFeature(familyId, AiConst.FEATURE_PHOTO_CAPTION));
        m.put("enabled", enabled);
        m.put("available", available);
        return m;
    }

    /** 整体开关(家长):只影响新上传照片是否自动生成,已有人工内容的照片永不被自动覆盖 */
    public void setEnabled(Long familyId, boolean enabled) {
        if (familyId == null) throw new BizException(ResultCode.FORBIDDEN);
        familyMapper.setPhotoCaptionEnabled(familyId, enabled);
    }

    /** 手动生成单张:覆盖为空,已有标题/描述只作为提示词参考 */
    public Photo caption(Long photoId, SysUser user, boolean isOwner) {
        Photo photo = albumService.requirePhoto(photoId, user, isOwner);
        Caption caption = generate(photo);
        save(photoId, caption);
        return photoMapper.selectById(photoId);
    }

    /** 只生成不落库:编辑弹窗里先给用户看一眼,确认后再保存 */
    public Map<String, Object> preview(Long photoId, SysUser user, boolean isOwner) {
        Photo photo = albumService.requirePhoto(photoId, user, isOwner);
        Caption caption = generate(photo);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", caption.title());
        m.put("description", caption.description());
        return m;
    }

    /** 上传后自动生成:总开关关着、或已有人工写过的内容时跳过 */
    public void autoCaption(Long familyId, Long photoId) {
        if (familyId == null || photoId == null) return;
        if (!Boolean.TRUE.equals(familyMapper.photoCaptionEnabled(familyId))) return;
        Photo photo = photoMapper.selectById(photoId);
        if (photo == null || !familyId.equals(photo.getFamilyId())) return;
        if (notBlank(photo.getTitle()) || notBlank(photo.getDescription())) return;
        save(photoId, generate(photo));
    }

    private void save(Long photoId, Caption caption) {
        LambdaUpdateWrapper<Photo> uw = new LambdaUpdateWrapper<Photo>().eq(Photo::getId, photoId);
        boolean touched = false;
        if (caption.title() != null) {
            uw.set(Photo::getTitle, caption.title());
            touched = true;
        }
        if (caption.description() != null) {
            uw.set(Photo::getDescription, caption.description());
            touched = true;
        }
        if (touched) photoMapper.update(null, uw);
    }

    private Caption generate(Photo photo) {
        Prepared image = imageForAi(photo);
        long base64Len = (long) image.bytes().length * 4 / 3;
        if (base64Len > MAX_B64_CHARS) {
            throw new BizException(ResultCode.BAD_REQUEST, "这张照片太大，暂时无法识别，请压缩后再试");
        }
        String dataUrl = "data:" + image.mime() + ";base64," + Base64.getEncoder().encodeToString(image.bytes());
        boolean acquired = false;
        try {
            acquired = SLOTS.tryAcquire(WAIT_SECONDS, TimeUnit.SECONDS);
            if (!acquired) throw new BizException(ResultCode.BAD_REQUEST, "正在识别的照片较多，请稍后再试");
            JsonNode node = aiService.visionJson(photo.getFamilyId(), AiConst.FEATURE_PHOTO_CAPTION,
                    SYSTEM_PROMPT, userPrompt(photo), dataUrl);
            String title = clip(text(node, "title"), 100);
            String description = clip(text(node, "description"), 255);
            if (title == null && description == null) {
                throw new BizException(ResultCode.INTERNAL_ERROR, "AI 没认出这张照片，请稍后重试或手动填写");
            }
            return new Caption(title, description);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ResultCode.INTERNAL_ERROR, "图片识别被中断，请重试");
        } finally {
            if (acquired) SLOTS.release();
        }
    }

    /** 已有标题/描述作为「补充」参考,让模型保留其中有用的信息 */
    private String userPrompt(Photo photo) {
        StringBuilder sb = new StringBuilder(USER_PROMPT);
        if (notBlank(photo.getTitle()) || notBlank(photo.getDescription())) {
            sb.append("\n已有内容(请在此基础上补充,保留有用信息):");
            if (notBlank(photo.getTitle())) sb.append(" 标题=").append(photo.getTitle().trim());
            if (notBlank(photo.getDescription())) sb.append(" 描述=").append(photo.getDescription().trim());
        }
        return sb.toString();
    }

    /** 取可发给模型的图片字节:本地文件压到 ≤768px;映射相册用缩略图缓存(JPEG/PNG) */
    private Prepared imageForAi(Photo photo) {
        String url = photo.getUrl();
        if (url == null || url.isBlank()) throw new BizException(ResultCode.NOT_FOUND, "照片文件不存在");
        if (url.startsWith("storage://")) return storageImage(url);
        String prefix = urlPrefix == null ? "" : urlPrefix.replaceAll("/+$", "");
        if (!prefix.isEmpty() && url.startsWith(prefix + "/")) return localImage(url, prefix);
        throw new BizException(ResultCode.BAD_REQUEST, "这张照片存放在外部存储，暂时无法识别");
    }

    private Prepared localImage(String url, String prefix) {
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        String rel = url.substring(prefix.length()).replace('\\', '/').replaceFirst("^/+", "");
        Path file = root.resolve(rel).normalize();
        if (!file.startsWith(root)) throw new BizException(ResultCode.BAD_REQUEST);
        try {
            if (!Files.isRegularFile(file)) throw new BizException(ResultCode.NOT_FOUND, "照片文件不存在");
            if (Files.size(file) > MAX_RAW_BYTES) {
                throw new BizException(ResultCode.BAD_REQUEST, "这张照片太大，暂时无法识别，请压缩后再试");
            }
            return prepare(Files.readAllBytes(file), file.getFileName().toString());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("照片读取失败: {}", e.getMessage());
            throw new BizException(ResultCode.INTERNAL_ERROR, "照片读取失败，请稍后重试");
        }
    }

    private Prepared storageImage(String url) {
        try {
            String rest = url.substring("storage://".length());
            int slash = rest.indexOf('/');
            if (slash <= 0) throw new NumberFormatException("bad storage url");
            Long deviceId = Long.valueOf(rest.substring(0, slash));
            String path = rest.substring(slash + 1);
            Long fsId = null;
            int q = path.indexOf("?fsid=");
            if (q >= 0) {
                fsId = Long.valueOf(path.substring(q + 6));
                path = path.substring(0, q);
            }
            StorageDevice device = storageDeviceMapper.selectById(deviceId);
            if (device == null) throw new BizException(ResultCode.NOT_FOUND, "照片所在存储设备已移除");
            byte[] bytes = thumbnailService.thumb(device, path, fsId);
            if (bytes == null || bytes.length == 0) {
                throw new BizException(ResultCode.BAD_REQUEST, "这张照片暂时读不到原图，无法识别");
            }
            return new Prepared(bytes, isPng(bytes) ? "image/png" : "image/jpeg");
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("映射照片读取失败: {}", e.getMessage());
            throw new BizException(ResultCode.INTERNAL_ERROR, "照片读取失败，请稍后重试");
        }
    }

    /** 统一压到 ≤768px JPEG/PNG;ImageIO 解不了的(HEIC 等)给明确提示而不是把原图丢给模型 */
    private Prepared prepare(byte[] raw, String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".heic") || lower.endsWith(".heif")) {
            throw new BizException(ResultCode.BAD_REQUEST, "HEIC 照片暂时无法识别，请先转换为 JPG 再试");
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(raw))) {
            PhotoThumbnail.Encoded encoded = PhotoThumbnail.create(input, MAX_SIDE);
            if (encoded != null) {
                return new Prepared(encoded.bytes(), encoded.png() ? "image/png" : "image/jpeg");
            }
        } catch (Exception e) {
            log.debug("照片缩放失败,按原图发送: {}", e.getMessage());
        }
        String mime = mimeOf(lower);
        if (mime == null) throw new BizException(ResultCode.BAD_REQUEST, "这张照片的格式暂时无法识别，请转换为 JPG 再试");
        return new Prepared(raw, mime);
    }

    private static String mimeOf(String lowerName) {
        if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) return "image/jpeg";
        if (lowerName.endsWith(".png")) return "image/png";
        if (lowerName.endsWith(".webp")) return "image/webp";
        if (lowerName.endsWith(".gif")) return "image/gif";
        if (lowerName.endsWith(".bmp")) return "image/bmp";
        return null;
    }

    private static boolean isPng(byte[] b) {
        return b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node == null ? null : node.path(field);
        if (v == null || v.isMissingNode() || v.isNull()) return null;
        String s = v.asText().trim();
        return s.isEmpty() ? null : s;
    }

    private static String clip(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private record Prepared(byte[] bytes, String mime) {}

    private record Caption(String title, String description) {}
}
