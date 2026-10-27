package com.ihomy.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 访问统计/访问明细:access 日志扫描的聚合、过滤与有界返回。
 * 只走过文件扫描路径,mapper 不参与,故构造时传 null。
 */
class OpsServiceTest {

    @TempDir
    Path tempDir;

    private OpsService serviceWithLogDir(Path dir) {
        OpsService service = new OpsService(null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
        ReflectionTestUtils.setField(service, "logRoot", dir.toString());
        return service;
    }

    /** 一行 access 日志(格式与 logback-spring.xml/LOG_HEADER 正则一致) */
    private String line(String time, String tid, String msg) {
        return time + " [http-exec-1] [tid:" + tid + "] INFO  com.ihomy.filter.AccessLogFilter - " + msg;
    }

    private static final String T = LocalDate.now() + " ";

    private void writeTodayLog(Path dir, List<String> lines) throws IOException {
        Path access = dir.resolve("access");
        Files.createDirectories(access);
        Files.write(access.resolve("ihomy-access.log"), lines, StandardCharsets.UTF_8);
    }

    private List<String> sampleLines() {
        return List.of(
                // 1) 10 点:两条 blog(其一慢)
                line(T + "10:15:00.123", "tid0000000000aa01", "GET /api/blog user=admin#1 ip=10.0.0.1 status=200 code=0 cost=42ms req=- resp=-"),
                line(T + "10:20:00.123", "tid0000000000aa02", "GET /api/blog user=admin#1 ip=10.0.0.1 status=200 code=0 cost=3500ms req=- resp=-"),
                // 2) 14 点:一条 500 失败 + 一条业务码失败 + 一条正常
                line(T + "14:01:00.123", "tid0000000000aa03", "POST /api/blog user=guest#2 ip=10.0.0.2 status=500 code=- cost=100ms req=- resp=-"),
                line(T + "14:02:00.123", "tid0000000000aa04", "GET /api/photo user=guest#2 ip=10.0.0.2 status=200 code=1 cost=80ms req=- resp=-"),
                line(T + "14:03:00.123", "tid0000000000aa05", "GET /api/photo user=guest#2 ip=10.0.0.2 status=200 code=0 cost=60ms req=- resp=-"),
                // 3) 非 access 行(不匹配)应被跳过
                "2026-10-18 14:04:00.123 [http-exec-1] [tid:ws0000000000000] INFO  com.ihomy.websocket.ChatWebSocketHandler - WS 消息用户=1 房间=1");
    }

    @Test
    void statsAggregatesUsersAndIpsWithFailedCounts() throws IOException {
        writeTodayLog(tempDir, sampleLines());
        Map<String, Object> stats = serviceWithLogDir(tempDir).trafficStats(null, null);

        assertThat(stats.get("total")).isEqualTo(5L);
        assertThat(stats.get("failed")).isEqualTo(2L);
        assertThat(stats.get("slow")).isEqualTo(1L);
        assertThat(stats.get("users")).isEqualTo(2);
        assertThat(stats.get("ips")).isEqualTo(2);

        List<Map<String, Object>> userList = (List<Map<String, Object>>) stats.get("userList");
        assertThat(userList).hasSize(2);
        assertThat(userList.get(0)).containsEntry("key", "guest#2").containsEntry("count", 3L).containsEntry("failed", 2L);
        assertThat(userList.get(1)).containsEntry("key", "admin#1").containsEntry("count", 2L).containsEntry("failed", 0L);

        List<Map<String, Object>> ipList = (List<Map<String, Object>>) stats.get("ipList");
        assertThat(ipList.get(0)).containsEntry("key", "10.0.0.2").containsEntry("count", 3L);
    }

    @Test
    void entriesFilterByHourUserPathAndType() throws IOException {
        writeTodayLog(tempDir, sampleLines());
        OpsService service = serviceWithLogDir(tempDir);

        // 小时过滤 + 最新在前
        Map<String, Object> hour14 = service.trafficEntries(null, null, 14, null, null, null, null, null);
        assertThat(hour14.get("count")).isEqualTo(3L);
        List<Map<String, Object>> entries = (List<Map<String, Object>>) hour14.get("entries");
        assertThat(entries.get(0)).containsEntry("time", T + "14:03:00.123").containsEntry("tid", "tid0000000000aa05");

        // 失败过滤(HTTP>=400 或业务码 != 0)
        assertThat(service.trafficEntries(null, null, null, null, null, null, "failed", null).get("count")).isEqualTo(2L);
        // 慢请求 + 耗时倒序
        List<Map<String, Object>> slow = (List<Map<String, Object>>) service
                .trafficEntries(null, null, null, null, null, null, "slow", "cost").get("entries");
        assertThat(slow).hasSize(1);
        assertThat(slow.get(0)).containsEntry("costMs", 3500L).containsEntry("failed", false);
        // 用户过滤
        assertThat(service.trafficEntries(null, null, null, null, "admin#1", null, null, null).get("count")).isEqualTo(2L);
        // 接口过滤(方法+路径精确匹配,忽略 query)
        assertThat(service.trafficEntries(null, null, null, "GET /api/blog", null, null, null, null).get("count")).isEqualTo(2L);
    }

    @Test
    void entriesKeepsNewest200AndFlagsTruncation() throws IOException {
        List<String> lines = new java.util.ArrayList<>();
        for (int i = 0; i < 250; i++) {
            lines.add(line(T + "10:00:00.123", String.format("tid0000000000%04d", i),
                    "GET /api/blog user=admin#1 ip=10.0.0.1 status=200 code=0 cost=" + i + "ms req=- resp=-"));
        }
        writeTodayLog(tempDir, lines);

        // 时间序:保留最近 200 条(即最后写入的 200 条),仍为倒序
        Map<String, Object> res = serviceWithLogDir(tempDir).trafficEntries(null, null, null, null, null, null, null, null);
        assertThat(res.get("count")).isEqualTo(250L);
        assertThat(res.get("truncated")).isEqualTo(true);
        List<Map<String, Object>> entries = (List<Map<String, Object>>) res.get("entries");
        assertThat(entries).hasSize(200);
        assertThat(entries.get(0)).containsEntry("costMs", 249L);

        // 耗时序:保留最慢 200 条(即 cost 50~249),最慢在前
        List<Map<String, Object>> slowest = (List<Map<String, Object>>) serviceWithLogDir(tempDir)
                .trafficEntries(null, null, null, null, null, null, null, "cost").get("entries");
        assertThat(slowest).hasSize(200);
        assertThat(slowest.get(0)).containsEntry("costMs", 249L);
        assertThat(slowest.get(199)).containsEntry("costMs", 50L);
    }
}
