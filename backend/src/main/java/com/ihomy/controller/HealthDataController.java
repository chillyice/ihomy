package com.ihomy.controller;

import com.ihomy.annotation.OperationLog;
import com.ihomy.common.BizException;
import com.ihomy.common.HealthIngestJson;
import com.ihomy.common.Ips;
import com.ihomy.common.Result;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.HealthConsentDTO;
import com.ihomy.dto.HealthIngestDTO;
import com.ihomy.dto.HealthTokenDTO;
import com.ihomy.entity.SysHealthToken;
import com.ihomy.security.HealthTokenFilter;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.HealthIngestService;
import com.ihomy.service.HealthPrivacyService;
import com.ihomy.service.HealthQueryService;
import com.ihomy.service.HealthTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 家庭成员健康数据接口:统一上报(令牌或本人登录态)、读侧(概览/睡眠/趋势/摘要)、
 * 令牌管理、敏感指标同意开关、查看审计。
 * 读权限:本人恒可读自己,OWNER 恒可读全家,其他成员需 health:view(在 Service 层判定,
 * 因为同一个端点要支持「本人豁免」,@RequirePermission 表达不了这层豁免)。
 */
@Tag(name = "健康数据")
@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
public class HealthDataController {

    private final HealthIngestService ingestService;
    private final HealthTokenService tokenService;
    private final HealthQueryService queryService;
    private final HealthPrivacyService privacyService;
    private final SecurityHelper securityHelper;
    private final tools.jackson.databind.ObjectMapper objectMapper;

    // ==================== 上报 ====================

    @Operation(summary = "统一上报健康数据(令牌或本人登录态;单次 ≤2000 条)")
    @PostMapping("/ingest")
    public Result<Map<String, Object>> ingest(@RequestBody(required = false) String raw, HttpServletRequest request) {
        LoginUser user = requireLogin();
        // 请求体按原文接收:快捷指令/安卓插件都可能用 text/plain 或干脆不带 Content-Type,
        // 也兼容把整段 JSON 贴成 {"": "<json>"} 的写法
        HealthIngestDTO dto = HealthIngestJson.parse(objectMapper, raw);
        Long tokenId = (Long) request.getAttribute(HealthTokenFilter.TOKEN_ID_ATTR);
        String tokenPrefix = (String) request.getAttribute(HealthTokenFilter.TOKEN_PREFIX_ATTR);
        return Result.success(ingestService.ingest(dto, user.getUserId(), user.getFamilyId(),
                tokenId, tokenPrefix, Ips.realIp(request)));
    }

    @Operation(summary = "上报令牌自检(返回本人、当前家庭与最后上报时间)")
    @GetMapping("/ping")
    public Result<Map<String, Object>> ping(HttpServletRequest request) {
        SysHealthToken token = (SysHealthToken) request.getAttribute(HealthTokenFilter.TOKEN_OBJ_ATTR);
        if (token != null) return Result.success(tokenService.ping(token));
        LoginUser user = requireLogin();
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("userId", user.getUserId());
        data.put("username", user.getUsername());
        data.put("familyId", user.getFamilyId());
        data.put("tokenPrefix", null);
        data.put("lastIngestAt", null);
        return Result.success(data);
    }

    // ==================== 令牌管理 ====================

    @Operation(summary = "生成健康数据上报令牌(明文只返回这一次)")
    @OperationLog(module = "HEALTH", operationType = "CREATE", description = "生成健康数据上报令牌", saveArgs = false)
    @PostMapping("/token")
    public Result<Map<String, Object>> createToken(@RequestBody(required = false) HealthTokenDTO dto) {
        LoginUser user = requireLogin();
        return Result.success(tokenService.create(user.getUserId(), user.getFamilyId(), dto));
    }

    @Operation(summary = "上报令牌列表 + 最近一次上报结果")
    @GetMapping("/token")
    public Result<Map<String, Object>> tokens() {
        LoginUser user = requireLogin();
        return Result.success(tokenService.list(user.getUserId()));
    }

    @Operation(summary = "吊销上报令牌(只能吊销自己的)")
    @OperationLog(module = "HEALTH", operationType = "DELETE", description = "吊销健康数据上报令牌")
    @DeleteMapping("/token/{id}")
    public Result<Void> revokeToken(@PathVariable Long id) {
        LoginUser user = requireLogin();
        tokenService.revoke(user.getUserId(), id);
        return Result.success();
    }

    // ==================== 隐私 ====================

    @Operation(summary = "读取敏感指标同意开关")
    @GetMapping("/consent")
    public Result<Map<String, Object>> consent() {
        LoginUser user = requireLogin();
        return Result.success(privacyService.consent(user.getUserId()));
    }

    @Operation(summary = "保存敏感指标同意开关(关闭时可一并清除已采数据)")
    @PutMapping("/consent")
    public Result<Map<String, Object>> saveConsent(@RequestBody HealthConsentDTO dto) {
        LoginUser user = requireLogin();
        return Result.success(privacyService.saveConsent(user.getUserId(), dto));
    }

    @Operation(summary = "谁看过我的数据(本人只看自己;家长可看全家)")
    @GetMapping("/view-log")
    public Result<List<Map<String, Object>>> viewLog(@RequestParam(required = false) Integer days) {
        HealthQueryService.Viewer viewer = viewer();
        return Result.success(privacyService.viewLogs(viewer.id(), viewer.familyId(), viewer.owner(),
                days == null ? 30 : days));
    }

    @Operation(summary = "清除自己某来源的全部健康数据(换设备/误配)")
    @OperationLog(module = "HEALTH", operationType = "DELETE", description = "清除健康数据来源")
    @DeleteMapping("/data")
    public Result<Map<String, Object>> clearData(@RequestParam String source) {
        LoginUser user = requireLogin();
        return Result.success(privacyService.clearSource(user.getUserId(), source));
    }

    // ==================== 读侧 ====================

    @Operation(summary = "单日概况(睡眠 + 当日各指标)")
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(queryService.overview(viewer(), userId, date));
    }

    @Operation(summary = "睡眠会话列表(≤62 天)")
    @GetMapping("/sleep")
    public Result<Map<String, Object>> sleep(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.success(queryService.sleep(viewer(), userId, from, to));
    }

    @Operation(summary = "睡眠统计(按天合计或按月平均,≤366 天)")
    @GetMapping("/sleep/stats")
    public Result<Map<String, Object>> sleepStats(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "DAY") String bucket) {
        return Result.success(queryService.sleepStats(viewer(), userId, from, to, bucket));
    }

    @Operation(summary = "某晚睡眠分期区间")
    @GetMapping("/sleep/{id}/stages")
    public Result<Map<String, Object>> stages(@PathVariable Long id) {
        return Result.success(queryService.stages(viewer(), id));
    }

    @Operation(summary = "单指标趋势(≤366 天)")
    @GetMapping("/metric")
    public Result<Map<String, Object>> metric(
            @RequestParam(required = false) Long userId,
            @RequestParam String metric,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.success(queryService.metric(viewer(), userId, metric, from, to));
    }

    @Operation(summary = "首页摘要(只回本人,最近 N 天)")
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary(@RequestParam(required = false) Integer days) {
        LoginUser user = requireLogin();
        return Result.success(queryService.summary(user.getUserId(), days == null ? 7 : days));
    }

    /** 直接回文件流(不走 Result 包装):前端用 blob 存盘,失败时才回 JSON 由前端识别 */
    @Operation(summary = "导出健康数据 CSV(长表:睡眠/分期/样本;默认近 90 天,≤366 天)")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        HealthQueryService.CsvFile file = queryService.exportCsv(viewer(), userId, from, to);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
                .contentLength(file.content().length)
                .body(file.content());
    }

    // ==================== 内部 ====================

    private LoginUser requireLogin() {
        LoginUser user = securityHelper.current();
        if (user == null) throw new BizException(ResultCode.UNAUTHORIZED);
        return user;
    }

    private HealthQueryService.Viewer viewer() {
        LoginUser user = requireLogin();
        return new HealthQueryService.Viewer(user.getUserId(), user.getFamilyId(), user.getRole(),
                securityHelper.hasPermission("health:view"), Ips.realIp(currentRequest()));
    }

    private HttpServletRequest currentRequest() {
        return ((org.springframework.web.context.request.ServletRequestAttributes)
                org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes()).getRequest();
    }
}
