package com.ihomy.security;

import com.ihomy.common.Result;
import com.ihomy.common.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * OPS 角色访问隔离过滤器（V3.8）:
 * - 角色为 OPS 的请求只放行 /ops/**（运维页）与 /auth/**（登录/改密/登出）,
 *   防止运维账号凭 JWT 触达业务接口（现有 Controller 多只校验"已登录"）。
 * - 非 OPS 角色一律拒绝 /ops/**（@RequirePermission 对 OWNER 恒真,必须在此硬拦）,
 *   保证运维数据只对运维角色可见。
 */
@Component
@RequiredArgsConstructor
public class OpsAccessFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser) {
            boolean isOps = "OPS".equals(loginUser.getRole());
            boolean opsPath = isOpsPath(request);
            // OPS 访问非运维/认证路径,或非 OPS 访问运维路径:一律 403
            if ((isOps && !isAllowed(request)) || (!isOps && opsPath)) {
                deny(response);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /** 是否为运维接口路径(含 context-path=/api 前缀) */
    private boolean isOpsPath(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/ops");
    }

    /** 放行路径:运维接口、认证接口、预检请求 */
    private boolean isAllowed(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        // context-path=/api,URI 形如 /api/ops/xxx
        return path.startsWith("/api/ops")
                || path.startsWith("/api/auth");
    }

    private void deny(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(ResultCode.FORBIDDEN)));
    }
}