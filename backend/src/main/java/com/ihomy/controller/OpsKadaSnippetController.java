package com.ihomy.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ihomy.annotation.OperationLog;
import com.ihomy.annotation.RequirePermission;
import com.ihomy.common.Result;
import com.ihomy.dto.KadaSnippetReviewRequest;
import com.ihomy.entity.KadaSnippet;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.KadaSnippetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 咔哒卡带人工审核(OPS):列表 / 详情 / 通过 / 驳回(含下架)。
 *
 * <p>机审只给危险等级与法务关键词提示,**是否上架由人决定**;通过后写卡带文件并重建
 * 社区索引,驳回已上架卡带会同时下架。内容字段在审核页只按纯文本展示。
 */
@Tag(name = "咔哒卡带审核")
@RestController
@RequestMapping("/ops/kada/snippets")
@RequiredArgsConstructor
public class OpsKadaSnippetController {

    private final KadaSnippetService service;
    private final SecurityHelper securityHelper;

    @Operation(summary = "卡带待审 / 已审分页(可按状态、危险等级与关键词筛选)")
    @RequirePermission("ops:view")
    @GetMapping
    public Result<IPage<KadaSnippet>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer level,
            @RequestParam(required = false) String q) {
        return Result.success(service.page(current, size, status, level, q));
    }

    @Operation(summary = "审核概览(待审 / 待审中高风险 / 已通过 / 已驳回)")
    @RequirePermission("ops:view")
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        return Result.success(service.summary());
    }

    @Operation(summary = "卡带详情(含规范化后的 JSON,审核页按纯文本展示)")
    @RequirePermission("ops:view")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        KadaSnippet row = service.get(id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("item", row);
        out.put("content", row.getContentJson());
        return Result.success(out);
    }

    @Operation(summary = "通过人工审核并上架(上架前会再跑一次机器审核)")
    @RequirePermission("ops:view")
    @OperationLog(module = "OPS", operationType = "UPDATE", description = "通过咔哒卡带审核")
    @PostMapping("/{id}/approve")
    public Result<KadaSnippet> approve(
            @PathVariable Long id,
            @RequestBody(required = false) KadaSnippetReviewRequest body) {
        String note = body == null ? null : body.getNote();
        return Result.success(service.approve(id, securityHelper.currentUserId(), note));
    }

    @Operation(summary = "驳回卡带;已上架的会同时下架")
    @RequirePermission("ops:view")
    @OperationLog(module = "OPS", operationType = "UPDATE", description = "驳回 / 下架咔哒卡带")
    @PostMapping("/{id}/reject")
    public Result<KadaSnippet> reject(
            @PathVariable Long id,
            @RequestBody(required = false) KadaSnippetReviewRequest body) {
        String note = body == null ? null : body.getNote();
        return Result.success(service.reject(id, securityHelper.currentUserId(), note));
    }
}
