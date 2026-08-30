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
 * 设备文件短期签名 URL:让 <img>/<video> 标签无需 JWT 即可访问中转端点(签名即凭证,10 分钟有效)。
 * 影子照片在 content_photo.url 存逻辑地址 storage://{deviceId}/{远程路径}?fsid={fsId},
 * 出接口时由 resolve() 动态换成带签名的 /api/storage/file-signed URL。
 */
@Service
public class SignedUrlService {

    private static final String SCHEME = "storage://";
    private static final long TTL_SECONDS = 600;

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
