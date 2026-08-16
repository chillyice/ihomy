package com.ihomy.security;

import com.ihomy.common.Result;
import com.ihomy.common.ResultCode;
import com.ihomy.mapper.SysRoleMapper;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * OPS 角色访问隔离过滤器(V3.8):
 * - 纯 OPS 角色(role=OPS)只放行 /ops/** 与 /auth/**,防止运维账号触达业务接口。
 * - 非 OPS 角色访问 /ops/** 时,检查用户是否有系统级 OPS 角色绑定(family_id=NULL),
 *   有则放行(支持 chillyice 这种同时是家庭 OWNER + 系统级 OPS 的场景),无则 403。
 */
@Component
@RequiredArgsConstructor
public class OpsAccessFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;
    private final SysRoleMapper sysRoleMapper;
    // 缓存:userId → 是否有 OPS 角色(5 分钟内不重复查库)
    private final ConcurrentHashMap<Long, Long> opsCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser) {
            boolean isPureOps = "OPS".equals(loginUser.getRole());
            boolean opsPath = isOpsPath(request);
            boolean hasOpsRole = hasOpsRole(loginUser.getUserId());

            // 纯 OPS 角色访问非运维/认证路径 → 403
            if (isPureOps && !isAllowed(request)) {
                deny(response);
                return;
            }
            // 非 OPS 角色访问运维路径,且没有系统级 OPS 绑定 → 403
            if (!isPureOps && opsPath && !hasOpsRole) {
                deny(response);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /** 检查用户是否有系统级 OPS 角色绑定(family_id=NULL),带 5 分钟缓存 */
    private boolean hasOpsRole(Long userId) {
        if (userId == null) return false;
        long now = System.currentTimeMillis();
        Long cachedAt = opsCache.get(userId);
        if (cachedAt != null && now - cachedAt < CACHE_TTL_MS) {
            return true;
        }
        boolean has = sysRoleMapper.countOpsRole(userId) > 0;
        if (has) {
            opsCache.put(userId, now);
        } else {
            opsCache.remove(userId);
        }
        return has;
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