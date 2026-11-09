package com.ihomy.security;

import com.ihomy.common.DictConst;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.SysUserMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 桌面卡片令牌:type=WIDGET 的签发往返(30 天/角色家庭快照)与过滤器最小权限白名单
 * (仅 GET 的日程/天气/纪念日/公开接口;写接口与 /files 一律 401;禁用账号失效)。
 */
class WidgetTokenTest {

    private static final String SECRET = "ihomy-secret-key-test-0123456789abcdef0123456789abcdef";

    private JwtUtils jwtUtils;
    private SysUserMapper sysUserMapper;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void init() throws Exception {
        jwtUtils = new JwtUtils();
        set("secret", SECRET);
        set("accessExpire", 7200L);
        set("refreshExpire", 604800L);
        set("widgetExpire", 2592000L);
        jwtUtils.init();
        sysUserMapper = mock(SysUserMapper.class);
        filter = new JwtAuthenticationFilter(jwtUtils, sysUserMapper, new AuthCookie(), new ObjectMapper());
    }

    private void set(String name, Object value) throws Exception {
        Field f = JwtUtils.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(jwtUtils, value);
    }

    private SysUser user(String status) {
        SysUser u = new SysUser();
        u.setId(7L);
        u.setUsername("widgeter");
        u.setFamilyId(2L);
        u.setStatus(status);
        return u;
    }

    // ---------------------------------------------------------------- 签发往返

    @Test
    void widgetTokenCarriesRoleFamilyAndThirtyDayExpiry() {
        String token = jwtUtils.generateWidgetToken(7L, "widgeter", "OWNER", 2L);
        Claims c = jwtUtils.parse(token);
        assertThat(c.get("type", String.class)).isEqualTo("WIDGET");
        assertThat(c.getSubject()).isEqualTo("7");
        assertThat(c.get("role", String.class)).isEqualTo("OWNER");
        assertThat(c.get("familyId", Long.class)).isEqualTo(2L);
        long ttl = c.getExpiration().getTime() - System.currentTimeMillis();
        assertThat(ttl).isBetween(29L * 24 * 3600 * 1000, 30L * 24 * 3600 * 1000);
        assertThat(jwtUtils.isValid(token)).isTrue();
    }

    // ---------------------------------------------------------------- 过滤器白名单

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest req = new MockHttpServletRequest(method, path);
        req.setServletPath(path);
        req.setRequestURI("/api" + path);
        return req;
    }

    private MockHttpServletResponse run(MockHttpServletRequest req, String token) throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        req.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(req, res, mock(FilterChain.class));
        return res;
    }

    @Test
    void widgetTokenAllowedOnWhitelistedRead() throws Exception {
        String token = jwtUtils.generateWidgetToken(7L, "widgeter", "OWNER", 2L);
        when(sysUserMapper.selectById(7L)).thenReturn(user(DictConst.USER_ACTIVE));

        run(request("GET", "/schedule/occurrences"), token);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        LoginUser lu = (LoginUser) auth.getPrincipal();
        assertThat(lu.getUserId()).isEqualTo(7L);
        assertThat(lu.getFamilyId()).isEqualTo(2L);
        SecurityContextHolder.clearContext();
    }

    @Test
    void widgetTokenRejectedOnWrite() throws Exception {
        String token = jwtUtils.generateWidgetToken(7L, "widgeter", "OWNER", 2L);
        MockHttpServletResponse res = run(request("POST", "/plan"), token);
        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void widgetTokenRejectedOnFilesAndOffWhitelistPaths() throws Exception {
        String token = jwtUtils.generateWidgetToken(7L, "widgeter", "OWNER", 2L);
        assertThat(run(request("GET", "/files/pictures/a.jpg"), token).getStatus()).isEqualTo(401);
        assertThat(run(request("GET", "/profile"), token).getStatus()).isEqualTo(401);
    }

    @Test
    void widgetTokenFailsForDisabledUser() throws Exception {
        String token = jwtUtils.generateWidgetToken(7L, "widgeter", "OWNER", 2L);
        when(sysUserMapper.selectById(7L)).thenReturn(user(DictConst.USER_DISABLED));

        MockHttpServletRequest req = request("GET", "/schedule/occurrences");
        MockHttpServletResponse res = new MockHttpServletResponse();
        req.addHeader("Authorization", "Bearer " + token);
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(req, res, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(any(), any());
    }
}
