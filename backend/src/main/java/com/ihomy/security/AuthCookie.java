package com.ihomy.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 访问令牌 cookie(ihomy_at):
 * <img>/<video> 等静态资源请求带不了 Authorization 头,登录态只能靠 cookie 传达,
 * 否则登录用户打开自己的私有图片也会被 /files 读取鉴权挡成 403。
 * 只给 /files 读取判定认这枚 cookie(见 FileAccessService),业务接口仍只认 Bearer 头 → 无 CSRF 面。
 * HttpOnly(前端 JS 不可读)+ SameSite=Lax + Secure(仅 https 请求)+ Path=/,寿命 = 访问令牌 TTL。
 */
@Component
public class AuthCookie {

    public static final String NAME = "ihomy_at";

    /** Capacitor 客户端壳的固定页面 origin(WebView androidScheme=https + hostname=localhost) */
    private static final String APP_ORIGIN = "https://localhost";

    @Value("${jwt.access-token-expire}")
    private long accessExpire;

    /** 下发/刷新(登录、刷新、以及 Bearer 请求发现 cookie 缺失或过期时补发) */
    public void attach(HttpServletRequest request, HttpServletResponse response, String token) {
        response.addHeader("Set-Cookie", build(token, request));
    }

    /** 同上,但 cookie 已存在则不动(壁纸令牌是长寿命独立令牌,别覆盖正常会话) */
    public void attachIfAbsent(HttpServletRequest request, HttpServletResponse response, String token) {
        if (read(request) != null) return;
        response.addHeader("Set-Cookie", build(token, request));
    }

    /** 登出清除 */
    public void clear(HttpServletResponse response) {
        response.addHeader("Set-Cookie", NAME + "=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0");
    }

    /** 读 cookie 原值(是否有效由调用方解析判定) */
    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (NAME.equals(c.getName()) && StringUtils.hasText(c.getValue())) return c.getValue();
        }
        return null;
    }

    private String build(String token, HttpServletRequest request) {
        boolean secure = request.isSecure();
        // Android 客户端壳内页面 origin 固定为 https://localhost(Capacitor androidScheme=https + hostname=localhost),
        // 相对 ihomy.top 属跨站:默认 Lax 不会随 <img>/<video> 子资源发出,家庭私有照片/电子书会被判未登录。
        // 仅对壳来源降为 SameSite=None(None 必须配 Secure);Web 端仍是 Lax,CSRF 面不变。
        boolean appOrigin = isAppOrigin(request);
        String sameSite = appOrigin ? "None" : "Lax";
        StringBuilder sb = new StringBuilder(NAME).append('=').append(token)
                .append("; Path=/; HttpOnly; SameSite=").append(sameSite)
                .append("; Max-Age=").append(Math.max(accessExpire / 1000, 60));
        // 开发环境 http 下不加 Secure,否则浏览器直接丢弃 cookie、图片全 403
        // (SameSite=None 例外:规范要求必须带 Secure,壳只连 https 生产环境)
        if (secure || appOrigin) sb.append("; Secure");
        return sb.toString();
    }

    /** 请求是否来自 Capacitor 客户端壳(跨站固定 origin) */
    private boolean isAppOrigin(HttpServletRequest request) {
        return APP_ORIGIN.equalsIgnoreCase(request.getHeader("Origin"));
    }
}
