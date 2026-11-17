package com.ihomy.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 在线会话注册表:userId → 会话集合(同人多标签各一条)。
 *
 * <p>从 {@link ChatWebSocketHandler} 抽出来是为了打破依赖环:
 * ChatService → CircleService 已存在,而聊天左列要判成员在线,
 * CircleService 若反向注入 handler(handler → ChatService)会形成循环。
 * 本类不依赖任何业务服务,聊天投递与成员列表/亲友圈共用一个真相。
 *
 * <p>集合允许留空不删键 —— 删键会与 computeIfAbsent 竞争,
 * 可能把并发中的新会话整组孤儿化(空集合内存可忽略),故判在线必须检查 {@code isOpen()}。
 */
@Component
public class PresenceRegistry {

    private final Map<Long, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    public void add(Long userId, WebSocketSession session) {
        userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void remove(Long userId, WebSocketSession session) {
        Set<WebSocketSession> set = userSessions.get(userId);
        if (set != null) {
            set.remove(session);
        }
    }

    /** 定向投递用:返回该用户的会话集合(可能为空集合/空),调用方自行判 isOpen */
    public Set<WebSocketSession> sessionsOf(Long userId) {
        return userSessions.get(userId);
    }

    /** 任一已打开会话即算在线;空集合不删键,不能只判断 containsKey */
    public boolean isOnline(Long userId) {
        if (userId == null) {
            return false;
        }
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return false;
        }
        for (WebSocketSession s : sessions) {
            if (s.isOpen()) {
                return true;
            }
        }
        return false;
    }
}
