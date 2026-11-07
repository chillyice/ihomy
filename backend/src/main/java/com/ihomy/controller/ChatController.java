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
     * 站外链接预览(unfurl)。查询型 RPC 不落库,不加 @OperationLog(限流已防滥用);
     * 抓取失败/内网地址等一律降级返回 preview=null,由前端发纯链接。
     */
    @Operation(summary = "站外链接预览")
    @PostMapping("/unfurl")
    public Result<Map<String, Object>> unfurl(@RequestBody @jakarta.validation.Valid ChatUnfurlDTO dto) {
        LoginUser user = current();
        return Result.success(chatUnfurlService.unfurl(user.getUserId(), dto == null ? null : dto.getUrl()));
    }
}