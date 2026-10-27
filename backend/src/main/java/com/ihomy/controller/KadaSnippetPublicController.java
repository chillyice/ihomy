package com.ihomy.controller;

import com.ihomy.common.Result;
import com.ihomy.dto.KadaSnippetSubmitRequest;
import com.ihomy.service.KadaSnippetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 咔哒卡带发布(免登录):提交后一律进入待审队列,机器复检 + OPS 人工通过才会上架。
 * 只接收 JSON,不接收文件上传;限流与内容校验在 service。
 */
@Tag(name = "咔哒卡带发布")
@RestController
@RequestMapping("/public/kada/snippets")
@RequiredArgsConstructor
public class KadaSnippetPublicController {

    private final KadaSnippetService service;

    @Operation(summary = "提交卡带(机器复检 + 人工审核;通过前不上架)")
    @PostMapping("/submit")
    public Result<Map<String, Object>> submit(
            @Valid @RequestBody KadaSnippetSubmitRequest req,
            HttpServletRequest request) {
        return Result.success(service.submit(req, clientIp(request)));
    }

    /** 取真实客户端 IP:nginx 反代下优先 X-Real-IP / X-Forwarded-For 第一段。 */
    private String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Real-IP");
        if (ip == null || ip.isBlank()) {
            ip = request.getHeader("X-Forwarded-For");
        }
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.substring(0, ip.indexOf(','));
        }
        return ip == null ? "" : ip.trim();
    }
}
