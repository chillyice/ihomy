package com.ihomy.service;

import com.ihomy.mapper.FileAccessMapper;
import com.ihomy.security.AuthCookie;
import com.ihomy.security.JwtUtils;
import com.ihomy.security.LoginUser;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * /files 读取鉴权:登录态(Bearer/context 或 cookie)放行,游客按 PUBLIC 反查(带缓存)。
 */
class FileAccessServiceTest {

    private final FileAccessMapper mapper = mock(FileAccessMapper.class);
    private final JwtUtils jwtUtils = mock(JwtUtils.class);
    private final FileAccessService service = new FileAccessService(mapper, jwtUtils, new AuthCookie());

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest req(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath("/api");
        request.setRequestURI(uri);
        return request;
    }

    @Test
    void guestSeesPublicFile() {
        when(mapper.countPublicHits("/files/pictures/a.jpg")).thenReturn(1);
        assertThat(service.allowed(req("/api/files/pictures/a.jpg"))).isTrue();
    }

    @Test
    void guestDeniedPrivateFileAndResultCached() {
        when(mapper.countPublicHits("/files/pictures/b.jpg")).thenReturn(0);
        assertThat(service.allowed(req("/api/files/pictures/b.jpg"))).isFalse();
        assertThat(service.allowed(req("/api/files/pictures/b.jpg"))).isFalse();
        verify(mapper, times(1)).countPublicHits("/files/pictures/b.jpg");
    }

    @Test
    void lookupFailureFailsClosed() {
        when(mapper.countPublicHits(anyString())).thenThrow(new IllegalStateException("db down"));
        assertThat(service.allowed(req("/api/files/pictures/c.jpg"))).isFalse();
    }

    @Test
    void bearerSessionSkipsLookup() {
        var auth = new UsernamePasswordAuthenticationToken(
                new LoginUser(1L, "admin", "OWNER", 1L), null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
        assertThat(service.allowed(req("/api/files/pictures/d.jpg"))).isTrue();
        verify(mapper, never()).countPublicHits(anyString());
    }

    @Test
    void accessTokenCookieCountsAsSession() {
        MockHttpServletRequest request = req("/api/files/pictures/e.jpg");
        request.setCookies(new Cookie(AuthCookie.NAME, "tok"));
        io.jsonwebtoken.Claims claims = mock(io.jsonwebtoken.Claims.class);
        when(claims.get("type", String.class)).thenReturn("ACCESS");
        when(jwtUtils.parse("tok")).thenReturn(claims);
        assertThat(service.allowed(request)).isTrue();
        verify(mapper, never()).countPublicHits(anyString());
    }

    @Test
    void expiredCookieFallsBackToGuest() {
        MockHttpServletRequest request = req("/api/files/pictures/f.jpg");
        request.setCookies(new Cookie(AuthCookie.NAME, "tok"));
        when(jwtUtils.parse("tok")).thenThrow(new IllegalStateException("expired"));
        when(mapper.countPublicHits("/files/pictures/f.jpg")).thenReturn(0);
        assertThat(service.allowed(request)).isFalse();
    }
}
