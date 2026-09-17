package com.ihomy.common;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 报表聚合公共工具(V9.72):AI/天气用量报表共用的时间范围解析 + 时间桶零填充。
 * 消除 WeatherService 与 AiStatsService 里重复的 range switch 与零填充逻辑。
 */
public final class ReportAggregateUtil {

    private ReportAggregateUtil() {
    }

    /** 时间范围规格:start(含)、end(不含)、DB DATE_FORMAT 格式、Java 时间格式、步进单位 */
    public record RangeSpec(LocalDateTime start, LocalDateTime end, String fmt, String javaPattern, ChronoUnit unit) {
    }

    /** 解析时间范围:24h(默认)/month/30d/year */
    public static RangeSpec resolveRange(String range) {
        LocalDateTime now = LocalDateTime.now();
        return switch (range == null || range.isBlank() ? "24h" : range) {
            case "month" -> new RangeSpec(now.toLocalDate().withDayOfMonth(1).atStartOfDay(), now,
                    "%Y-%m-%d", "yyyy-MM-dd", ChronoUnit.DAYS);
            case "30d" -> new RangeSpec(now.toLocalDate().minusDays(29).atStartOfDay(), now,
                    "%Y-%m-%d", "yyyy-MM-dd", ChronoUnit.DAYS);
            case "year" -> new RangeSpec(now.toLocalDate().withDayOfYear(1).atStartOfDay(), now,
                    "%Y-%m", "yyyy-MM", ChronoUnit.MONTHS);
            default -> new RangeSpec(now.minusHours(23).truncatedTo(ChronoUnit.HOURS), now,
                    "%m-%d %H:00", "MM-dd HH:00", ChronoUnit.HOURS);
        };
    }

    /**
     * 时间桶零填充:把 SQL 聚合出的有数据桶(键 time_bucket)铺到整个时间范围,缺数据的桶补 0。
     * rows 元素须含 time_bucket/total/failed 三个键(与 mapper XML 约定一致)。
     */
    public static List<Map<String, Object>> zeroFillTimeline(RangeSpec spec, List<Map<String, Object>> rows) {
        Map<String, Map<String, Object>> byBucket = new HashMap<>();
        for (Map<String, Object> r : rows) {
            byBucket.put(String.valueOf(r.get("time_bucket")), r);
        }
        DateTimeFormatter jf = DateTimeFormatter.ofPattern(spec.javaPattern());
        List<Map<String, Object>> result = new ArrayList<>();
        for (LocalDateTime t = spec.start(); !t.isAfter(spec.end()); t = t.plus(1, spec.unit())) {
            String bucket = t.format(jf);
            Map<String, Object> r = byBucket.get(bucket);
            Map<String, Object> point = new HashMap<>();
            point.put("time_bucket", bucket);
            point.put("total", r == null ? 0L : ((Number) r.getOrDefault("total", 0)).longValue());
            point.put("failed", r == null ? 0L : ((Number) r.getOrDefault("failed", 0)).longValue());
            result.add(point);
        }
        return result;
    }
}
