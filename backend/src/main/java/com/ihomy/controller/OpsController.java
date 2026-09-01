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
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
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
@Slf4j
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

    @Operation(summary = "操作日志检索(分页,支持时间/操作人/模块多选/类型多选/结果多选/关键字)")
    @RequirePermission("ops:view")
    @GetMapping("/logs")
    public Result<IPage<SysOperationLog>> logs(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) List<String> module,
            @RequestParam(required = false) List<String> operationType,
            @RequestParam(required = false) List<String> result,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String keyword) {
        return Result.success(opsService.logs(current, size, operatorId, module, operationType, result, startDate, endDate, keyword));
    }

    @Operation(summary = "操作日志筛选项(distinct 模块/操作类型)")
    @RequirePermission("ops:view")
    @GetMapping("/logs/options")
    public Result<Map<String, List<String>>> logOptions() {
        return Result.success(opsService.logFilterOptions());
    }

    @Operation(summary = "访问量统计(扫描 access 日志文件,按天聚合,上限 14 天)")
    @RequirePermission("ops:view")
    @GetMapping("/traffic/stats")
    public Result<Map<String, Object>> trafficStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(opsService.trafficStats(startDate, endDate));
    }

    @Operation(summary = "详细日志:按 tid 检索 access/server/thirdparty 三类日志文件(缺省查当天,可按来源/级别过滤)")
    @RequirePermission("ops:view")
    @GetMapping("/logs/trace")
    public Result<Map<String, Object>> traceLogs(
            @RequestParam String tid,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) List<String> sources,
            @RequestParam(required = false) List<String> levels) {
        return Result.success(opsService.traceLogs(tid, date, sources, levels));
    }

    @Operation(summary = "和风天气 API 用量统计(控制台 API,凭证未配返回 null)")
    @RequirePermission("ops:view")
    @GetMapping("/weather/quota")
    public Result<Map<String, Object>> weatherQuota() {
        return Result.success(weatherService.getQuota());
    }

    @Operation(summary = "和风天气财务汇总(余额/消费/账单)")
    @RequirePermission("ops:view")
    @GetMapping("/weather/finance")
    public Result<Map<String, Object>> weatherFinance() {
        return Result.success(weatherService.getFinance());
    }

    @Operation(summary = "和风天气请求量统计(24h,按 API 分)")
    @RequirePermission("ops:view")
    @GetMapping("/weather/stats")
    public Result<Map<String, Object>> weatherStats() {
        return Result.success(weatherService.getStats());
    }

    @Operation(summary = "天气API调用折线图(24h/本月/30天/一年,可按 API 类型多选过滤;零填充覆盖全时间范围)")
    @RequirePermission("ops:view")
    @GetMapping("/weather/timeline")
    public Result<List<Map<String, Object>>> weatherTimeline(
            @RequestParam(defaultValue = "24h") String range,
            @RequestParam(required = false) List<String> types) {
        return Result.success(weatherService.getTimeline(range, types));
    }

    @Operation(summary = "天气API类型分布(饼图,与折线图共用时间范围)")
    @RequirePermission("ops:view")
    @GetMapping("/weather/type-distribution")
    public Result<List<Map<String, Object>>> weatherTypeDistribution(
            @RequestParam(defaultValue = "24h") String range) {
        return Result.success(weatherService.getTypeDistribution(range));
    }

    @Operation(summary = "新旧版本并行验证(v7 vs v1,同一位置关键字段对照;每次约 6 次调用,手动触发)")
    @RequirePermission("ops:view")
    @GetMapping("/weather/compare")
    public Result<Map<String, Object>> weatherCompare() {
        return Result.success(weatherService.compareV7V1());
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