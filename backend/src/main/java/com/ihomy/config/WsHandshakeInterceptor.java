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
 * WebSocket 鎻℃墜鎷︽埅鍣?浠??token= 瑙ｆ瀽 JWT,
 * 鏍￠獙閫氳繃鍚庢妸 userId/familyId/鏄电О鏀惧叆 session attributes(渚?Handler 鍙栫敤)銆? */
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