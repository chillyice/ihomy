package com.ihomy.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类:签发/解析 access token(2h)与 refresh token(7d),
 * token 中携带 userId/username/role/familyId,familyId 用于按家庭解析权限。
 */
@Slf4j
@Component
public class JwtUtils {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expire}")
    private long accessExpire;

    @Value("${jwt.refresh-token-expire}")
    private long refreshExpire;

    private SecretKey key;

    /** 由配置的 secret 派生 HMAC 密钥(缺失时启动即失败,密钥只允许来自 external.yml 外挂配置) */
    @PostConstruct
    public void init() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("jwt.secret 未配置:请设环境变量 IHOMY_CONFIG_PATH 指向 external.yml 并配置 jwt.secret(参考 external.yml.template)");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** 签发短期访问令牌(携带角色与当前家庭 ID) */
    public String generateAccessToken(Long userId, String username, String role, Long familyId) {
        return build(userId, username, role, familyId, accessExpire, "ACCESS");
    }

    /** 签发长期刷新令牌(仅含身份,不含角色/家庭) */
    public String generateRefreshToken(Long userId, String username) {
        return build(userId, username, null, null, refreshExpire, "REFRESH");
    }

    /**
     * 签发壁纸介质令牌(type=WALLPAPER):桌面壁纸(WE)无法输入账号密码,用它与普通刷新令牌区分——
     * 普通刷新令牌轮换后即拉黑(单次有效),壁纸令牌不轮换拉黑,允许浏览器会话与多个壁纸实例各持一份、各自滑动续期。
     */
    public String generateWallpaperToken(Long userId, String username) {
        return build(userId, username, null, null, refreshExpire, "WALLPAPER");
    }

    /** 组装 JWT:type 区分 ACCESS/REFRESH,角色与家庭 ID 仅在访问令牌中携带 */
    private String build(Long userId, String username, String role, Long familyId, long expire, String type) {
        Date now = new Date();
        var builder = Jwts.builder()
                .subject(String.valueOf(userId))
                // jti 唯一化:同一秒内同用户重复签发(登录后立即刷新/切换家庭)不会得到字节相同的令牌,
                // 否则轮换拉黑会与被签发的新令牌撞值,把有效会话误判成已拉黑
                .id(java.util.UUID.randomUUID().toString())
                .claim("username", username)
                .claim("type", type);
        if (role != null) {
            builder.claim("role", role);
        }
        if (familyId != null) {
            builder.claim("familyId", familyId);
        }
        return builder.issuedAt(now)
                .expiration(new Date(now.getTime() + expire * 1000))
                .signWith(key)
                .compact();
    }

    /** 解析 token 得到 claims,签名或过期失败会抛异常 */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    /** 校验 token 是否有效(解析不抛异常即为有效) */
    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public long getAccessExpire() {
        return accessExpire;
    }
}
