package com.ihomy.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 短期签名 URL:让 <img>/<video> 标签无需 JWT 即可访问中转端点(签名即凭证)。
 * 影子照片在 content_photo.url 存逻辑地址 storage://{deviceId}/{远程路径}?fsid={fsId},
 * 出接口时由 resolve() 动态换成带签名的 /api/storage/file-signed URL;
 * 放映厅海报走同一套签名(/api/media/image-signed),令牌只留在后端。
 */
@Service
public class SignedUrlService {

    private static final String SCHEME = "storage://";
    private static final long TTL_SECONDS = 600;
    /** 海报是静态资源,签名有效期给长一些(横幅在页面上可能停留很久) */
    private static final long IMAGE_TTL_SECONDS = 24 * 3600;
    /** 字幕轨:<track> 在播放器打开时取一次,给 12 小时够覆盖一次长时间观影 */
    private static final long SUBTITLE_TTL_SECONDS = 12 * 3600;

    @Value("${jwt.secret}")
    private String secret;

    /** 生成带过期时间与 HMAC 签名的中转 URL */
    public String sign(Long deviceId, String path, Long fsId) {
        long exp = System.currentTimeMillis() / 1000 + TTL_SECONDS;
        StringBuilder sb = new StringBuilder("/api/storage/file-signed?deviceId=").append(deviceId)
                .append("&path=").append(URLEncoder.encode(path, StandardCharsets.UTF_8));
        if (fsId != null && fsId > 0) sb.append("&fsId=").append(fsId);
        return sb.append("&exp=").append(exp).append("&sig=").append(hmac(payload(deviceId, path, fsId, exp)))
                .toString();
    }

    /** 校验签名与有效期(常量时间比较) */
    public boolean verify(Long deviceId, String path, Long fsId, long exp, String sig) {
        if (sig == null || exp < System.currentTimeMillis() / 1000) return false;
        return MessageDigest.isEqual(
                hmac(payload(deviceId, path, fsId, exp)).getBytes(StandardCharsets.UTF_8),
                sig.getBytes(StandardCharsets.UTF_8));
    }

    /** storage:// 逻辑地址 → 签名 URL;普通 /files/ URL 原样返回 */
    public String resolve(String url) {
        if (url == null || !url.startsWith(SCHEME)) return url;
        try {
            String rest = url.substring(SCHEME.length());
            Long fsId = null;
            int q = rest.indexOf('?');
            if (q >= 0) {
                String qs = rest.substring(q + 1);
                if (qs.startsWith("fsid=")) fsId = Long.valueOf(qs.substring(5));
                rest = rest.substring(0, q);
            }
            int slash = rest.indexOf('/');
            if (slash <= 0) return url;
            Long deviceId = Long.valueOf(rest.substring(0, slash));
            String path = rest.substring(slash + 1);
            if (path.isEmpty()) return url;
            return sign(deviceId, path, fsId);
        } catch (Exception e) {
            return url;
        }
    }

    private String payload(Long deviceId, String path, Long fsId, long exp) {
        return deviceId + "|" + path + "|" + (fsId == null ? "" : fsId) + "|" + exp;
    }

    /** 放映厅海报的签名中转 URL(与设备文件共用密钥,签名带用途前缀防跨端点重放) */
    public String signMediaImage(Long familyId, String itemId, String type, Integer maxWidth) {
        long exp = System.currentTimeMillis() / 1000 + IMAGE_TTL_SECONDS;
        int width = maxWidth == null ? 0 : maxWidth;
        String imageType = type == null ? "Primary" : type;
        // familyId 也进签名:端点免登录,靠它定位家庭配置,被篡改即签名不通过
        return "/api/media/image-signed?familyId=" + familyId
                + "&itemId=" + URLEncoder.encode(itemId, StandardCharsets.UTF_8)
                + "&type=" + URLEncoder.encode(imageType, StandardCharsets.UTF_8)
                + "&maxWidth=" + width
                + "&exp=" + exp
                + "&sig=" + hmac(mediaPayload(familyId, itemId, imageType, width, exp));
    }

    /** 校验海报签名与有效期(常量时间比较);maxWidth 未传按 0 参与校验,与签发口径一致 */
    public boolean verifyMediaImage(Long familyId, String itemId, String type, Integer maxWidth, long exp, String sig) {
        if (sig == null || exp < System.currentTimeMillis() / 1000) return false;
        String imageType = type == null ? "Primary" : type;
        int width = maxWidth == null ? 0 : maxWidth;
        return MessageDigest.isEqual(
                hmac(mediaPayload(familyId, itemId, imageType, width, exp)).getBytes(StandardCharsets.UTF_8),
                sig.getBytes(StandardCharsets.UTF_8));
    }

    private String mediaPayload(Long familyId, String itemId, String type, int maxWidth, long exp) {
        return "media|" + familyId + "|" + itemId + "|" + type + "|" + maxWidth + "|" + exp;
    }

    /**
     * 放映厅字幕轨的签名中转 URL(轨道列表随播放地址一起下发,<track> 同样带不了 JWT)。
     * userId 与 sourceId 一并进签名:前者让后端按同一成员账号取字幕(与播放同口径),后者是媒体源 id ——
     * 多版本条目里字幕挂在自己的媒体源下,拿条目 id 顶替会取不到。
     */
    public String signMediaSubtitle(Long familyId, Long userId, String itemId, String sourceId, int index) {
        long exp = System.currentTimeMillis() / 1000 + SUBTITLE_TTL_SECONDS;
        long uid = userId == null ? 0L : userId;
        return "/api/media/subtitle-signed?familyId=" + familyId
                + "&userId=" + uid
                + "&itemId=" + URLEncoder.encode(itemId, StandardCharsets.UTF_8)
                + "&sourceId=" + URLEncoder.encode(sourceId, StandardCharsets.UTF_8)
                + "&index=" + index
                + "&exp=" + exp
                + "&sig=" + hmac(subtitlePayload(familyId, uid, itemId, sourceId, index, exp));
    }

    /** 校验字幕签名与有效期(常量时间比较);userId 未登录按 0 参与校验,与签发口径一致 */
    public boolean verifyMediaSubtitle(Long familyId, long userId, String itemId, String sourceId,
                                       int index, long exp, String sig) {
        if (sig == null || exp < System.currentTimeMillis() / 1000) return false;
        return MessageDigest.isEqual(
                hmac(subtitlePayload(familyId, userId, itemId, sourceId, index, exp)).getBytes(StandardCharsets.UTF_8),
                sig.getBytes(StandardCharsets.UTF_8));
    }

    /** 用途前缀与海报区分开:两端点免登录,签名不可跨端点重放 */
    private String subtitlePayload(Long familyId, long userId, String itemId, String sourceId, int index, long exp) {
        return "media-sub|" + familyId + "|" + userId + "|" + itemId + "|" + sourceId + "|" + index + "|" + exp;
    }

    private String hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 签名失败", e);
        }
    }
}
