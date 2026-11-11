package com.ihomy.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import tools.jackson.databind.ObjectMapper;
import com.ihomy.common.Result;
import com.ihomy.common.ResultCode;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * JWT 认证过滤器:从 Authorization: Bearer 头解析访问令牌,
 * 成功后把 LoginUser 注入 SecurityContext,供后续接口取当前用户。
 * 首登强制改密(pwdChange 标记)的令牌只放行改密/登出,其余接口 403。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final com.ihomy.mapper.SysUserMapper sysUserMapper;
    private final AuthCookie authCookie;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtUtils.parse(token);
                if ("WIDGET".equals(claims.get("type", String.class))) {
                    // 桌面卡片令牌:最小权限白名单——仅放行只读日程/天气/纪念日/公开接口,写接口一律 401。
                    // 宁可白名单不要黑名单(§7.2);禁用账号按库校验,签发时的角色/家庭快照随令牌走。
                    if (!widgetAllowed(request)) {
                        denyWidget(response);
                        return;
                    }
                    Long userId = Long.valueOf(claims.getSubject());
                    var u = sysUserMapper.selectById(userId);
                    if (u == null || com.ihomy.common.DictConst.USER_DISABLED.equals(u.getStatus())) {
                        chain.doFilter(request, response);
                        return;
                    }
                    String role = claims.get("role", String.class);
                    Long familyId = claims.get("familyId", Long.class);
                    if (familyId == null) familyId = u.getFamilyId();
                    LoginUser loginUser = new LoginUser(userId, u.getUsername(), role, familyId);
                    var auth = new UsernamePasswordAuthenticationToken(
                            loginUser, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    request.setAttribute("ihomy.userId", userId);
                    request.setAttribute("ihomy.username", u.getUsername());
                    // 控制器按此识别卡片令牌并收紧约束(如勾选只准今天)
                    request.setAttribute(SecurityHelper.WIDGET_TOKEN_ATTR, Boolean.TRUE);
                } else if ("ACCESS".equals(claims.get("type", String.class))) {
                    Long userId = Long.valueOf(claims.getSubject());
                    String username = claims.get("username", String.class);
                    String role = claims.get("role", String.class);
                    if (role == null) {
                        // token 无角色信息,视为无效,交由下游返回 401
                        chain.doFilter(request, response);
                        return;
                    }
                    Long familyId = claims.get("familyId", Long.class);
                    if (familyId == null) {
                        // 旧 token 无家庭信息时回退查库补全
                        var u = sysUserMapper.selectById(userId);
                        familyId = u != null ? u.getFamilyId() : null;
                    }
                    LoginUser loginUser = new LoginUser(userId, username, role, familyId);
                    var auth = new UsernamePasswordAuthenticationToken(
                            loginUser, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    // 操作人放请求属性:AccessLogFilter 在 SecurityContext 清理后仍能记录谁在调用
                    request.setAttribute("ihomy.userId", userId);
                    request.setAttribute("ihomy.username", username);
                    // cookie 缺失/令牌已轮换则补发:图片等静态资源请求带不了 Authorization 头,
                    // 登录态只认这枚 cookie,否则发版后的老会话(登录时还没有该 cookie)图片全 403
                    if (!token.equals(authCookie.read(request))) authCookie.attach(request, response, token);
                    // 首登强制改密:令牌受限,仅放行改密与登出,防止用默认密码拿到的令牌调业务接口
                    if (Boolean.TRUE.equals(claims.get("pwdChange", Boolean.class)) && !passwordChangeAllowed(request)) {
                        denyPasswordChangeRequired(response);
                        return;
                    }
                }
            } catch (Exception e) {
                // 解析失败视为未登录,交由下游拦截器返回 401
                log.debug("JWT 解析失败: {}", e.getMessage());
            }
        }
        chain.doFilter(request, response);
    }

    /** 首登强制改密期间允许的路径:改密本身与登出(不含 context-path) */
    private boolean passwordChangeAllowed(HttpServletRequest request) {
        String path = request.getServletPath();
        return "/profile/password".equals(path) || "/auth/logout".equals(path);
    }

    /** 卡片勾选的唯一写例外:精确到「提醒完成切换」,服务端还强制只准勾今天(ReminderController.toggleDone) */
    private static final Pattern WIDGET_TOGGLE_PATH = Pattern.compile("^/reminder/\\d+/toggle-done$");

    /** 卡片令牌白名单:只读日程/天气/纪念日/提醒列表/公开接口 + 唯一写例外(提醒勾选);不包含 /files(卡片不该拿到文件读权) */
    private boolean widgetAllowed(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getServletPath();
        if ("POST".equalsIgnoreCase(method)) return WIDGET_TOGGLE_PATH.matcher(path).matches();
        if (!"GET".equalsIgnoreCase(method)) return false;
        return path.startsWith("/schedule/") || path.startsWith("/weather/") || path.startsWith("/public/")
                || path.startsWith("/anniversary/") || "/reminder".equals(path) || "/reminder/list".equals(path)
                // 第二批:后台未读轮询。必须精确到路径,不能给 /notification/ 前缀——
                // /notification/list 返回通知正文,卡片令牌只该读计数,不该读内容。
                || "/notification/unread-count".equals(path) || "/chat/unread".equals(path);
    }

    /** 卡片令牌访问白名单外接口:直接 401,不走通用异常链 */
    private void denyWidget(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(ResultCode.UNAUTHORIZED)));
    }

    private void denyPasswordChangeRequired(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(ResultCode.PASSWORD_CHANGE_REQUIRED)));
    }
}
