package com.ihomy.controller;

import com.ihomy.common.Result;
import com.ihomy.dto.ChatReadDTO;
import com.ihomy.dto.ChatUnfurlDTO;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.ChatService;
import com.ihomy.service.ChatUnfurlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 聊天室接口:历史消息(分页)、未读数、标记已读。
 * 实时收发走 WebSocket(/ws/chat?token=),这里提供 REST 兜底与初始数据。
 */
@Tag(name = "聊天室")
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ChatUnfurlService chatUnfurlService;
    private final SecurityHelper securityHelper;

    /** ICE 服务器配置(app.webrtc.ice-servers,生产经 external.yml 覆盖加 TURN) */
    @org.springframework.beans.factory.annotation.Value("${app.webrtc.ice-servers:stun:stun.l.google.com:19302}")
    private String iceServersConfig;

    private LoginUser current() {
        return securityHelper.current();
    }

    @Operation(summary = "历史消息(beforeId 翻页,缺省最新一批)")
    @GetMapping("/history")
    public Result<List<Map<String, Object>>> history(@RequestParam(required = false) Long beforeId,
                                                     @RequestParam(defaultValue = "50") int limit) {
        return Result.success(chatService.history(current().getFamilyId(), beforeId, limit));
    }

    @Operation(summary = "未读数与最新消息")
    @GetMapping("/unread")
    public Result<Map<String, Object>> unread() {
        LoginUser user = current();
        return Result.success(chatService.unread(user.getUserId(), user.getFamilyId()));
    }

    @Operation(summary = "标记已读(记录用户已读游标)")
    @PostMapping("/read")
    public Result<Void> read(@RequestBody(required = false) ChatReadDTO dto) {
        LoginUser user = current();
        chatService.markRead(user.getUserId(), user.getFamilyId(), dto == null ? null : dto.getMsgId());
        return Result.success();
    }

    /**
     * WebRTC ICE 服务器配置(1:1 通话用):app.webrtc.ice-servers 解析为标准 iceServers 结构。
     * 配置形如 "stun:…;turn:…|user|pass",凭证只在后端,前端拿结构化结果。
     */
    @Operation(summary = "通话 ICE 服务器配置")
    @GetMapping("/ice-servers")
    public Result<Map<String, Object>> iceServers() {
        List<Map<String, Object>> servers = new ArrayList<>();
        for (String raw : iceServersConfig.split(";")) {
            if (raw.isBlank()) {
                continue;
            }
            String[] parts = raw.trim().split("\\|");
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("urls", parts[0]);
            if (parts.length > 1 && !parts[1].isBlank()) {
                s.put("username", parts[1]);
            }
            if (parts.length > 2 && !parts[2].isBlank()) {
                s.put("credential", parts[2]);
            }
            servers.add(s);
        }
        return Result.success(Map.of("iceServers", servers));
    }
}