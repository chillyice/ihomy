package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.annotation.RequirePermission;
import com.ihomy.common.Result;
import com.ihomy.dto.PointsRuleDTO;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.PointsRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 积分获取规则接口(家长):哪些功能能得积分、得多少。仅 points:manage(OWNER)。
 */
@Tag(name = "积分规则")
@RestController
@RequestMapping("/points/rules")
@RequiredArgsConstructor
public class PointsRuleController {

    private final PointsRuleService pointsRuleService;
    private final SecurityHelper securityHelper;

    @Operation(summary = "积分获取规则列表(家长)")
    @RequirePermission("points:manage")
    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        return Result.success(pointsRuleService.list(securityHelper.current().getFamilyId()));
    }

    @Operation(summary = "保存积分获取规则(家长,批量)")
    @RequirePermission("points:manage")
    @OperationLog(module = "POINTS", operationType = "UPDATE", description = "配置积分获取规则")
    @PutMapping
    public Result<Void> save(@RequestBody List<PointsRuleDTO> body) {
        pointsRuleService.save(securityHelper.current().getFamilyId(), body);
        return Result.success();
    }
}
