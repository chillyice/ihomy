package com.ihomy.service;

import com.ihomy.mapper.FileAccessMapper;
import com.ihomy.security.AuthCookie;
import com.ihomy.security.JwtUtils;
import com.ihomy.security.LoginUser;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /files/** 读取鉴权(SecurityConfig 的 access 判定入口):
 * <ol>
 *   <li>登录态放行:SecurityContext(Bearer 头)或 ihomy_at cookie(图片请求带不了 Authorization 头);</li>
 *   <li>游客只放行 PUBLIC 内容:按 URL 反查内容表(FileAccessMapper),带 60s 内存缓存。</li>
 * </ol>
 * 查不到/查询失败一律拒绝(fail-closed)。反查结果缓存 ⇒ 内容从非公开改公开后,游客最多 60s 才能看到图。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileAccessService {

    private static final long TTL_MS = 60_000;
    private static final int MAX_ENTRIES = 5000;

    private final FileAccessMapper fileAccessMapper;
    private final JwtUtils jwtUtils;
    private final AuthCookie authCookie;

    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    /** 请求是否可读该文件 */
    public boolean allowed(HttpServletRequest request) {
        if (authenticated(request)) return true;
        String path = pathOf(request);
        return path != null && publicPath(path);
    }

    /** 登录态:SecurityContext(Bearer)或有效 cookie 令牌 */
    private boolean authenticated(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser) return true;

        String token = authCookie.read(request);
        if (token == null) return false;
        try {
            Claims claims = jwtUtils.parse(token);
            String type = claims.get("type", String.class);
            return "ACCESS".equals(type) || "WALLPAPER".equals(type);
        } catch (Exception e) {
            return false; // 过期/篡改 → 游客
        }
    }

    /** 请求路径(去 context-path、URL 解码)→ 与库里存的 /files/... 直接比对 */
    private String pathOf(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) return null;
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx)) uri = uri.substring(ctx.length());
        try {
            return UriUtils.decode(uri, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return uri;
        }
    }

    /** 游客可见:URL 反查内容表(60s 缓存) */
    private boolean publicPath(String path) {
        long now = System.currentTimeMillis();
        Entry hit = cache.get(path);
        if (hit != null && hit.expireAt > now) return hit.allowed;

        boolean allowed;
        try {
            allowed = fileAccessMapper.countPublicHits(path) > 0;
        } catch (Exception e) {
            log.warn("public file lookup failed, path={}", path, e);
            return false;
        }
        // ponytail:容量到顶整表清空,不写逐条淘汰;路径枚举风险靠 60s TTL 收敛
        if (cache.size() >= MAX_ENTRIES) cache.clear();
        cache.put(path, new Entry(now + TTL_MS, allowed));
        return allowed;
    }

    private record Entry(long expireAt, boolean allowed) {
    }
}
