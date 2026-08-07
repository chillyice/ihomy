package com.ihomy.controller;

import com.ihomy.common.Result;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 站内通知接口:列表/未读数/标记已读。
 */
@Tag(name = "通知")
@RestController
@RequestMapping("/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final SecurityHelper securityHelper;

    @Operation(summary = "通知列表")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        Long uid = securityHelper.currentUserId();
        return Result.success(notificationService.list(uid).stream().map(notificationService::toView).toList());
    }

    @Operation(summary = "未读数量")
    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        return Result.success(notificationService.unreadCount(securityHelper.currentUserId()));
    }

    @Operation(summary = "标记单条已读")
    @PutMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(id, securityHelper.currentUserId());
        return Result.success();
    }

    @Operation(summary = "全部已读")
    @PutMapping("/read-all")
    public Result<Void> markAllRead() {
        notificationService.markAllRead(securityHelper.currentUserId());
        return Result.success();
    }
}
