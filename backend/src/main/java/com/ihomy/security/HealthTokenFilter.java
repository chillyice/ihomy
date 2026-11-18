package com.ihomy.security;

import com.ihomy.common.DictConst;
import com.ihomy.common.Result;
import com.ihomy.common.ResultCode;
import com.ihomy.entity.SysHealthToken;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.SysUserMapper;
import com.ihomy.service.HealthTokenService;
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

import java.io.IOException;
import java.util.List;

/**
 * 健康数据上报令牌过滤器:Authorization: Bearer hk_... → 按 SHA-256 查 sys_health_token,
 * 构造最小权限 principal(只够 POST /health/ingest 与 GET /health/ping,其余一律 403)。
 * 不碰 JWT 轮换、不写 refresh、不触发 OPS 判定——快捷指令拿不到浏览器那套双 token。
 * 家庭按成员实时解析,不信签发时快照(与 JWT 快照的差异在此显式说明)。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HealthTokenFilter extends OncePerRequestFilter {

    /** 请求属性:令牌行 id(上报计数与排障用) */
    public static final String TOKEN_ID_ATTR = "ihomy.healthTokenId";
    /** 请求属性:令牌前缀(日志只记前缀,明文不落日志) */
    public static final String TOKEN_PREFIX_ATTR = "ihomy.healthTokenPrefix";
    /** 请求属性:令牌行(自检接口回显最后上报时间用) */
    public static final String TOKEN_OBJ_ATTR = "ihomy.healthToken";

    private final HealthTokenService tokenService;
    private final SysUserMapper sysUserMapper;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (!StringUtils.hasText(header) || !header.startsWith("Bearer hk_")) {
            chain.doFilter(request, response);
            return;
        }
        String plain = header.substring(7).trim();
        SysHealthToken token = tokenService.findActive(plain);
        if (token == null) {
            // 吊销/伪造:真实 401,交由前端/快捷指令识别
            deny(response, ResultCode.UNAUTHORIZED, HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        if (!allowed(request)) {
            deny(response, ResultCode.FORBIDDEN, HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        SysUser user = sysUserMapper.selectById(token.getUserId());
        if (user == null || DictConst.USER_DISABLED.equals(user.getStatus())) {
            deny(response, ResultCode.UNAUTHORIZED, HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        Long familyId = user.getFamilyId() != null ? user.getFamilyId() : token.getFamilyId();
        LoginUser loginUser = new LoginUser(user.getId(), user.getUsername(), "HEALTH_TOKEN", familyId);
        var auth = new UsernamePasswordAuthenticationToken(
                loginUser, null, List.of(new SimpleGrantedAuthority("ROLE_HEALTH_TOKEN")));
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);
        request.setAttribute("ihomy.userId", user.getId());
        request.setAttribute("ihomy.username", user.getUsername());
        request.setAttribute(TOKEN_ID_ATTR, token.getId());
        request.setAttribute(TOKEN_PREFIX_ATTR, token.getTokenPrefix());
        request.setAttribute(TOKEN_OBJ_ATTR, token);
        try {
            tokenService.touch(token.getId());
        } catch (Exception e) {
            log.debug("更新健康令牌最后使用时间失败 id={}: {}", token.getId(), e.getMessage());
        }
        chain.doFilter(request, response);
    }

    /** 令牌只够两个端点:上报与自检 */
    private boolean allowed(HttpServletRequest request) {
        String path = request.getServletPath();
        String method = request.getMethod();
        return ("POST".equalsIgnoreCase(method) && "/health/ingest".equals(path))
                || ("GET".equalsIgnoreCase(method) && "/health/ping".equals(path));
    }

    private void deny(HttpServletResponse response, ResultCode rc, int status) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(rc)));
    }
}
