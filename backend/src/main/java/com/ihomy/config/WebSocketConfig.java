package com.ihomy.config;

import com.ihomy.websocket.ChatWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置:注册聊天室端点 /ws/chat,
 * 握手时 JWT 校验(token 参数),按家庭分房间广播。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final WsHandshakeInterceptor wsHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/ws/chat")
                .addInterceptors(wsHandshakeInterceptor)
                // 与 CorsConfig 的 Origin 白名单不同,这里放开任意来源:WS 不带 cookie 凭证
                // (鉴权走 ?token=),没有 CSRF 面;放宽可让 Nginx 前置/局域网直连/App 壳等各来源都能连。
                .setAllowedOriginPatterns("*");
    }
}
