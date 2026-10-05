package com.ihomy.controller;

import com.ihomy.annotation.RequirePermission;
import com.ihomy.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 独立健康检查端点。
 * - GET /public/health:免登录轻量探活,供部署脚本/外部探针判断「进程已就绪、依赖可用」,
 *   只回整体状态与版本,不暴露组件明细;任一依赖不可用返回 HTTP 503。
 * - GET /ops/health:运维详情(见 {@link OpsController}),含各依赖的探活耗时。
 * 版本号读仓库根 VERSION(与前端 __APP_VERSION__ 同源),读不到回落 unknown。
 */
@Slf4j
@Tag(name = "健康检查")
@RestController
@RequiredArgsConstructor
public class HealthController {

    /** 依赖探活超时(秒):连接池/Redis 客户端默认超时都在几十秒量级,探活必须自带上限 */
    private static final long PROBE_TIMEOUT_SECONDS = 2;

    private final DataSource dataSource;
    private final StringRedisTemplate redis;

    /** 版本号:仓库根 VERSION 文件,进程启动后读一次缓存 */
    private static final String VERSION = readVersion();

    @Operation(summary = "健康检查(免登录;整体 UP 返回 200,DEGRADED 返回 503)")
    @GetMapping("/public/health")
    public ResponseEntity<Result<Map<String, Object>>> health() {
        Map<String, Object> components = probeComponents();
        boolean up = components.values().stream().allMatch(c -> "UP".equals(((Map<?, ?>) c).get("status")));

        Map<String, Object> data = new HashMap<>();
        data.put("status", up ? "UP" : "DEGRADED");
        data.put("version", VERSION);
        data.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(up ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(Result.success(data));
    }

    @Operation(summary = "健康检查详情(运维;含各依赖探活状态与耗时)")
    @RequirePermission("ops:view")
    @GetMapping("/ops/health")
    public Result<Map<String, Object>> detail() {
        Map<String, Object> components = probeComponents();
        Map<String, Object> data = new HashMap<>();
        data.put("status", components.values().stream()
                .allMatch(c -> "UP".equals(((Map<?, ?>) c).get("status"))) ? "UP" : "DEGRADED");
        data.put("version", VERSION);
        data.put("timestamp", Instant.now().toString());
        data.put("components", components);
        return Result.success(data);
    }

    /**
     * 逐依赖探活:返回 name → {status, latencyMs}。
     * 探到异常只记 DEBUG(探活频率可高,ERROR 会淹没日志;真实故障由 /ops/health 与告警发现)。
     */
    public Map<String, Object> probeComponents() {
        Map<String, Object> result = new HashMap<>();
        result.put("db", probe(() -> {
            try (Connection c = dataSource.getConnection()) {
                return c.isValid(2);
            }
        }));
        result.put("redis", probe(() -> "PONG".equalsIgnoreCase(
                redis.execute((RedisCallback<String>) conn -> conn.ping()))));
        return result;
    }

    /** 带超时的单项探活:成功且谓词为真 = UP,否则 DOWN */
    private Map<String, Object> probe(ProbeAction action) {
        long start = System.currentTimeMillis();
        String status;
        try {
            status = CompletableFuture.supplyAsync(() -> {
                try {
                    return action.check() ? "UP" : "DOWN";
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).orTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS).join();
        } catch (Exception e) {
            status = "DOWN";
            log.debug("health probe failed: {}", e.getMessage());
        }
        Map<String, Object> item = new HashMap<>();
        item.put("status", status);
        item.put("latencyMs", System.currentTimeMillis() - start);
        return item;
    }

    @FunctionalInterface
    private interface ProbeAction {
        boolean check() throws Exception;
    }

    private static String readVersion() {
        for (String candidate : new String[]{ "../VERSION", "VERSION" }) {
            try {
                Path p = Path.of(candidate);
                if (Files.isReadable(p)) {
                    String v = Files.readString(p).trim();
                    if (!v.isEmpty()) return v;
                }
            } catch (Exception ignored) {
                // 逐个候选路径尝试,全失败回落 unknown
            }
        }
        return "unknown";
    }
}
