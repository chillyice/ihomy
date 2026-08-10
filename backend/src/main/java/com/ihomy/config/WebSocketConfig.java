package com.ihomy.config;

import com.ihomy.websocket.ChatWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 閰嶇疆:娉ㄥ唽鑱婂ぉ瀹ょ鐐?/ws/chat,
 * 鎻℃墜璧?JWT 鏍￠獙(token 鍙傛暟),鎸夊搴垎鎴块棿銆? */
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
                .setAllowedOriginPatterns("*");
    }
}