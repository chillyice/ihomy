package com.ihomy.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ihomy.annotation.RequirePermission;
import com.ihomy.common.Result;
import com.ihomy.entity.SysOperationLog;
import com.ihomy.service.OpsService;
import com.ihomy.service.ParameterService;
import com.ihomy.service.WeatherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * 运维接口（V3.8）:系统资源统计/服务器状态/操作日志检索。
 * 仅 OPS 角色可访问（@RequirePermission + OpsAccessFilter 双保险）,
 * 只返回聚合信息,不涉及用户隐私。
 */
@Tag(name = "运维管理")
@RestController
@RequestMapping("/ops")
@RequiredArgsConstructor
public class OpsController {

    private final OpsService opsService;
    private final WeatherService weatherService;
    private final ParameterService parameterService;

    @Operation(summary = "系统资源总数(可按时间/用户/家庭过滤)")
    @RequirePermission("ops:view")
    @GetMapping("/stats")
    public Result<Map<String, Long>> stats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long familyId) {
        return Result.success(opsService.stats(startDate, endDate, userId, familyId));
    }

    @Operation(summary = "服务器/JVM 状态(内存/线程/磁盘)")
    @RequirePermission("ops:view")
    @GetMapping("/server")
    public Result<Map<String, Object>> server() {
        return Result.success(opsService.server());
    }

    @Operation(summary = "操作日志检索(分页,支持时间/操作人/模块/关键字)")
    @RequirePermission("ops:view")
    @GetMapping("/logs")
    public Result<IPage<SysOperationLog>> logs(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String keyword) {
        return Result.success(opsService.logs(current, size, operatorId, module, operationType, startDate, endDate, keyword));
    }

    @Operation(summary = "和风天气 API 用量统计(控制台 API,凭证未配返回 null)")
    @RequirePermission("ops:view")
    @GetMapping("/weather/quota")
    public Result<Map<String, Object>> weatherQuota() {
        return Result.success(weatherService.getQuota());
    }

    @Operation(summary = "加密明文为 ENC(...) 格式(供外挂配置文件使用)")
    @RequirePermission("ops:view")
    @GetMapping("/crypto/encrypt")
    public Result<String> encrypt(@RequestParam String plaintext) {
        return Result.success(parameterService.encrypt(plaintext));
    }

    @Operation(summary = "解密 ENC(...) 密文(验证用)")
    @RequirePermission("ops:view")
    @GetMapping("/crypto/decrypt")
    public Result<String> decrypt(@RequestParam String ciphertext) {
        return Result.success(parameterService.decrypt(ciphertext));
    }
}