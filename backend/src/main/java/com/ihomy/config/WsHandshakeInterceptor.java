package com.ihomy.config;

import com.ihomy.security.JwtUtils;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手拦截器:浏览器 WebSocket 握手无法携带 Authorization 头,
 * 故改从 URL 查询参数 ?token= 取 JWT,校验 type=ACCESS(刷新令牌/壁纸令牌不放行)后,
 * 把 userId/familyId/username 写入 session attributes 供 ChatWebSocketHandler 取用。
 * 校验不通过返回 false 直接拒绝握手(不抛异常,避免把失败原因回给客户端)。
 */
@Component
@RequiredArgsConstructor
public class WsHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtils jwtUtils;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String query = request.getURI().getQuery();
        String token = query == null ? null
                : java.util.Arrays.stream(query.split("&"))
                    .filter(p -> p.startsWith("token="))
                    .map(p -> p.substring(6))
                    .findFirst().orElse(null);
        if (!StringUtils.hasText(token)) {
            return false;
        }
        try {
            Claims claims = jwtUtils.parse(token);
            if (!"ACCESS".equals(claims.get("type", String.class))) {
                return false;
            }
            attributes.put("userId", Long.valueOf(claims.getSubject()));
            attributes.put("familyId", claims.get("familyId", Long.class));
            attributes.put("username", claims.get("username", String.class));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}