package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.annotation.RequirePermission;
import com.ihomy.common.Result;
import com.ihomy.entity.OssComponent;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.OssComponentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 开源组件台账接口:登记 + 版本检测 + 升级提示。
 * 仅 OPS 角色可访问(@RequirePermission("ops:view") + OpsAccessFilter 双保险)。
 */
@Tag(name = "开源组件台账")
@RestController
@RequestMapping("/ops/oss")
@RequiredArgsConstructor
public class OssComponentController {

    private final OssComponentService ossComponentService;
    private final SecurityHelper securityHelper;

    @Operation(summary = "开源组件列表(可升级项排前)")
    @RequirePermission("ops:view")
    @GetMapping("/list")
    public Result<List<OssComponent>> list() {
        return Result.success(ossComponentService.list());
    }

    @Operation(summary = "开源组件汇总(总数/可升级数/最近检测时间)")
    @RequirePermission("ops:view")
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        return Result.success(ossComponentService.summary());
    }

    @Operation(summary = "立即检查更新(并发拉取 npm/maven/GitHub 最新版)")
    @RequirePermission("ops:view")
    @OperationLog(module = "OSS", operationType = "UPDATE", description = "检查开源组件更新")
    @PostMapping("/check")
    public Result<List<OssComponent>> check() {
        return Result.success(ossComponentService.checkNow());
    }

    @Operation(summary = "生成组件升级方案")
    @RequirePermission("ops:view")
    @GetMapping("/{id}/upgrade-plan")
    public Result<Map<String, Object>> upgradePlan(@PathVariable Long id) {
        return Result.success(ossComponentService.buildUpgradePlan(id));
    }

    @Operation(summary = "AI 升级评估(风险分级/迁移点/可行性)")
    @RequirePermission("ops:view")
    @OperationLog(module = "OSS", operationType = "QUERY", description = "AI 评估开源组件升级", saveArgs = false)
    @PostMapping("/{id}/assess")
    public Result<Map<String, Object>> assess(@PathVariable Long id) {
        return Result.success(ossComponentService.assess(id, securityHelper.current().getFamilyId()));
    }

    @Operation(summary = "生成升级 PR(AI 评估可行后触发 Renovate)")
    @RequirePermission("ops:view")
    @OperationLog(module = "OSS", operationType = "CREATE", description = "触发开源组件升级 PR", saveArgs = false)
    @PostMapping("/{id}/upgrade")
    public Result<Map<String, Object>> upgrade(@PathVariable Long id) {
        return Result.success(ossComponentService.requestUpgrade(id));
    }

    @Operation(summary = "编辑组件台账")
    @RequirePermission("ops:view")
    @OperationLog(module = "OSS", operationType = "UPDATE", description = "编辑开源组件台账")
    @PutMapping("/{id}")
    public Result<Void> edit(@PathVariable Long id, @RequestBody OssComponent body) {
        ossComponentService.edit(id, body);
        return Result.success();
    }

    @Operation(summary = "新增组件台账")
    @RequirePermission("ops:view")
    @OperationLog(module = "OSS", operationType = "CREATE", description = "新增开源组件台账")
    @PostMapping
    public Result<Void> add(@RequestBody OssComponent body) {
        ossComponentService.add(body);
        return Result.success();
    }

    @Operation(summary = "确认已升级到最新版")
    @RequirePermission("ops:view")
    @OperationLog(module = "OSS", operationType = "UPDATE", description = "确认开源组件已升级")
    @PutMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id) {
        ossComponentService.confirmUpgraded(id);
        return Result.success();
    }

    @Operation(summary = "忽略/恢复某项更新提示")
    @RequirePermission("ops:view")
    @OperationLog(module = "OSS", operationType = "UPDATE", description = "忽略/恢复开源组件更新提示")
    @PutMapping("/{id}/ignore")
    public Result<Void> ignore(@PathVariable Long id, @RequestParam(defaultValue = "true") boolean ignored) {
        ossComponentService.ignore(id, ignored);
        return Result.success();
    }
}
