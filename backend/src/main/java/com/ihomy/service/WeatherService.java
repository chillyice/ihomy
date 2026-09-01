package com.ihomy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ihomy.common.ThirdPartyHttp;
import com.ihomy.entity.WeatherCredential;
import com.ihomy.entity.WeatherLog;
import com.ihomy.mapper.WeatherCredentialMapper;
import com.ihomy.mapper.WeatherLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 和风天气服务(V5.6):JWT(Ed25519)身份认证 + API Host。
 * 凭证优先从 sys_weather_credential 表读 status=1 的记录(多环境账本),yml 作 fallback。
 * 支持当前天气、预报、预警、天气指数、空气质量、分钟降水六类查询。
 * 凭证未配置时所有方法返回 null,前端降级为只按时间做光影。
 * Redis 缓存:now 30 分钟 / forecast 30 分钟 / warning 5 分钟 / indices 30 分钟 / air 30 分钟 / minutely 10 分钟。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherService {

    @Value("${app.weather-api-host:}")
    private String apiHost;

    @Value("${app.weather-project-id:}")
    private String projectId;

    @Value("${app.weather-key-id:}")
    private String keyId;

    @Value("${app.weather-private-key:}")
    private String privateKeyPem;

    private final StringRedisTemplate redis;
    private final WeatherCredentialMapper credentialMapper;
    private final WeatherLogMapper weatherLogMapper;
    private final ParameterService parameterService;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final Duration NOW_TTL = Duration.ofMinutes(30);
    private static final Duration FORECAST_TTL = Duration.ofMinutes(30);
    private static final Duration WARNING_TTL = Duration.ofMinutes(5);
    private static final Duration INDICES_TTL = Duration.ofMinutes(30);
    private static final Duration AIR_TTL = Duration.ofMinutes(30);
    private static final Duration MINUTELY_TTL = Duration.ofMinutes(10);
    private static final Duration LOCATION_TTL = Duration.ofHours(6);

    /** 月度 API 调用配额(超限后本月停止调用,返回 null 前端降级) */
    private static final int MONTHLY_QUOTA = 50000;

    /** 取启用凭证:DB 优先(status=1 且 private_key 非空),空则 fallback yml。
     *  private_key 若为 ENC(...) 包裹格式,用 ParameterService 取盐值解密。 */
    private WeatherCredential loadCredential() {
        try {
            WeatherCredential c = credentialMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WeatherCredential>()
                    .eq(WeatherCredential::getStatus, 1).last("LIMIT 1"));
            // DB 记录 private_key 非空才用;为空则 fallback yml
            if (c != null && c.getPrivateKey() != null && !c.getPrivateKey().isBlank()) {
                // 私钥可能 ENC(...) 加密,解密后使用
                c.setPrivateKey(parameterService.decrypt(c.getPrivateKey()));
                log.info("[loadCredential] 从DB加载凭证成功, keyId={}", c.getKeyId());
                return c;
            }
        } catch (Exception e) {
            log.error("[loadCredential] 从DB加载凭证失败,将尝试yml fallback", e);
        }
        // yml fallback
        if (apiHost == null || apiHost.isBlank() || projectId == null || projectId.isBlank()
                || keyId == null || keyId.isBlank() || privateKeyPem == null || privateKeyPem.isBlank()) {
            log.warn("[loadCredential] yml 凭证未配置,天气功能不可用");
            return null;
        }
        WeatherCredential fallback = new WeatherCredential();
        fallback.setApiHost(apiHost);
        fallback.setProjectId(projectId);
        fallback.setKeyId(keyId);
        // yml 的私钥也可能 ENC(...) 加密
        fallback.setPrivateKey(parameterService.decrypt(privateKeyPem));
        log.info("[loadCredential] 从yml加载凭证成功, keyId={}", keyId);
        return fallback;
    }

    /** 返回当前天气 {condition, temp, text};Key 未配/请求失败返回 null */
    public Map<String, Object> getWeather(String clientIp, String[] familyLocation) {
        WeatherCredential cred = loadCredential();
        if (cred == null) return null;
        String cacheKey = familyLocation != null ? "ihomy:weather:now:fam:" + familyLocation[0] + ":" + familyLocation[1] : "ihomy:weather:now";
        Map<String, Object> cached = readCache(cacheKey);
        if (cached != null) return cached;

        String locationId = resolveLocation(clientIp, cred, familyLocation);
        if (locationId == null) return null;

        // v1 实时天气(坐标路径,最多两位小数;localTime=true 返回当地时间)
        String[] ll = toLatLon(locationId);
        JsonNode now = callApi("/weather/v1/current/" + ll[0] + "/" + ll[1] + "?localTime=true", cred);
        if (now == null || !now.has("condition")) return null;
        String code = now.path("condition").path("code").asText("100");
        Map<String, Object> data = new HashMap<>();
        data.put("condition", codeToCondition(code));
        data.put("precipLevel", codeToPrecipLevel(code));
        data.put("iconCode", code);
        data.put("temp", (int) Math.round(now.path("temperature").path("value").asDouble()));
        data.put("text", now.path("condition").path("text").asText());
        data.put("city", familyLocation != null && familyLocation.length > 2 && familyLocation[2] != null ? familyLocation[2] : resolveCityName(clientIp));
        data.put("locationId", locationId);
        data.put("nowFull", buildNowFull(now)); // v1 实况映射为旧 v7 字段形状(前端零改动)
        writeCache(cacheKey, data, NOW_TTL);
        return data;
    }

    /** v1 实时天气 → 旧 v7 now 字段形状(单位换算:湿度/云量 0-1→%、能见度 m→km、风速 m/s→km/h) */
    private Map<String, Object> buildNowFull(JsonNode c) {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("temp", round1(c.path("temperature").path("value").asDouble()));
        n.put("feelsLike", round1(c.path("feelsLike").path("value").asDouble()));
        n.put("icon", c.path("condition").path("code").asText());
        n.put("text", c.path("condition").path("text").asText());
        n.put("wind360", c.path("wind").path("direction").path("degree").asText());
        n.put("windDir", compassCn(c.path("wind").path("direction").path("compass").asText()));
        n.put("windScale", c.path("wind").path("scale").asText());
        n.put("windSpeed", round1(c.path("wind").path("speed").path("value").asDouble() * 3.6));
        n.put("humidity", (int) Math.round(c.path("humidity").asDouble() * 100));
        n.put("precip", round2(c.path("precipitation").path("amount").path("value").asDouble()));
        n.put("pressure", round1(c.path("pressure").path("value").asDouble()));
        n.put("vis", round1(c.path("visibility").path("value").asDouble() / 1000));
        n.put("dew", round1(c.path("dewPoint").path("value").asDouble()));
        n.put("cloud", (int) Math.round(c.path("cloudCover").asDouble() * 100));
        n.put("uvIndex", c.path("uvIndex").asText());
        return n;
    }

    /** 天气详情聚合:now + 7d + 24h + warning + indices + air + minutely */
    public Map<String, Object> getDetail(String clientIp, String[] familyLocation) {
        WeatherCredential cred = loadCredential();
        if (cred == null) return null;
        String cacheKey = familyLocation != null ? "ihomy:weather:detail:fam:" + familyLocation[0] + ":" + familyLocation[1] : "ihomy:weather:detail";
        Map<String, Object> cached = readCache(cacheKey);
        if (cached != null) return cached;

        String locationId = resolveLocation(clientIp, cred, familyLocation);
        if (locationId == null) return null;

        Map<String, Object> data = new HashMap<>();
        data.put("locationId", locationId);

        // 当前天气(复用 now 缓存,含全量实况字段 nowFull)
        Map<String, Object> nowData = getWeather(clientIp, familyLocation);
        if (nowData != null) {
            data.put("now", nowData);
            data.put("nowFull", nowData.get("nowFull"));
        }

        // 7 天 + 24 小时预报(v1 坐标路径,映射为旧 v7 字段形状)
        String[] ll = toLatLon(locationId);
        JsonNode dailyResp = callApi("/weather/v1/daily/" + ll[0] + "/" + ll[1] + "?localTime=true", cred);
        if (dailyResp != null && dailyResp.has("days")) data.put("daily", mapDailyV1(dailyResp.get("days")));
        JsonNode hourlyResp = callApi("/weather/v1/hourly/" + ll[0] + "/" + ll[1] + "?localTime=true", cred);
        if (hourlyResp != null && hourlyResp.has("hours")) data.put("hourly", mapHourlyV1(hourlyResp.get("hours")));

        // 灾害预警(v1 /weatheralert/v1/current 坐标路径;v7 已弃用 403)
        JsonNode warning = callApi("/weatheralert/v1/current/" + ll[0] + "/" + ll[1] + "?localTime=true", cred);
        if (warning != null && warning.has("alerts")) data.put("warning", mapWarningV1(warning.get("alerts")));

        // 空气质量(新版 /airquality/v1,坐标路径参数;映射为旧字段形状供前端)
        Map<String, Object> air = fetchAir(locationId, cred);
        if (air != null) data.put("air", air);

        // 天气指数(全部类型 type=0)
        JsonNode indices = callApi("/v7/indices/1d?location=" + locationId + "&type=0", cred);
        if (indices != null && indices.has("daily")) data.put("indices", indices.get("daily"));

        // 分钟降水
        JsonNode minutely = callApi("/v7/minutely/5m?location=" + locationId, cred);
        if (minutely != null && minutely.has("minutely")) data.put("minutely", minutely.get("minutely"));

        writeCache(cacheKey, data, NOW_TTL);
        return data;
    }

    /**
     * 本月配额使用(本地统计:Redis 计数器 / DB fallback)。
     * 原实现调 /console/v1/usage 实测恒 404(死接口,从未成功过),改为本地口径:
     * 只统计计费的数据 API(now/forecast/air 等),控制台类(quota/finance/metrics)不计费不计数。
     */
    public Map<String, Object> getQuota() {
        String monthKey = "ihomy:weather:quota:" + java.time.YearMonth.now();
        String cached = redis.opsForValue().get(monthKey);
        long used;
        if (cached != null) {
            used = Long.parseLong(cached);
        } else {
            java.time.LocalDateTime monthStart = java.time.LocalDate.now().withDayOfMonth(1).atStartOfDay();
            Long count = weatherLogMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WeatherLog>()
                            .ge(WeatherLog::getCreatedAt, monthStart)
                            .eq(WeatherLog::getStatus, "SUCCESS")
                            .notIn(WeatherLog::getApiType, List.of("quota", "finance", "metrics")));
            used = count != null ? count : 0;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("month", java.time.YearMonth.now().toString());
        result.put("used", used);
        result.put("quota", MONTHLY_QUOTA);
        result.put("remaining", Math.max(0, MONTHLY_QUOTA - used));
        result.put("usagePercent", Math.round(used * 1000.0 / MONTHLY_QUOTA) / 10.0);
        return result;
    }

    /** 控制台 API:财务汇总(余额/本月消费/待付账单) */
    public Map<String, Object> getFinance() {
        WeatherCredential cred = loadCredential();
        if (cred == null) return null;
        JsonNode resp = callApi("/finance/v1/summary", cred);
        if (resp == null) return null;
        Map<String, Object> result = new HashMap<>();
        result.put("raw", resp);
        result.put("balance", resp.path("balance").asText());
        result.put("currency", resp.path("currency").asText());
        result.put("thisMonth", resp.path("accruedCharges").path("thisMonth").asText());
        result.put("previousDay", resp.path("accruedCharges").path("previousDay").asText());
        return result;
    }

    /** 控制台 API:请求量统计(24h,按 API 名分,成功/失败) */
    public Map<String, Object> getStats() {
        WeatherCredential cred = loadCredential();
        if (cred == null) return null;
        JsonNode resp = callApi("/metrics/v1/stats", cred);
        if (resp == null) return null;
        Map<String, Object> result = new HashMap<>();
        result.put("raw", resp);
        result.put("asOf", resp.path("asOf").asText());
        result.put("success", resp.path("success"));
        result.put("errors", resp.path("errors"));
        return result;
    }

    /**
     * 本地日志聚合:按时间范围返回调用总量+失败量折线图数据,可按 API 类型过滤。
     * 零填充整个时间范围(缺数据的桶补 0)——修 x 轴覆盖不全 + 24h 跨零点同小时合并问题
     * (24h 桶格式带日期 "%m-%d %H:00",昨天 23 点与今天 23 点不再合并、词法排序即时间序)。
     */
    public List<Map<String, Object>> getTimeline(String range, List<String> apiTypes) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime start;
        String fmt;                 // MySQL DATE_FORMAT
        String javaPattern;         // 与 fmt 对齐的 Java 模式
        java.time.temporal.ChronoUnit unit; // 桶粒度
        switch (range) {
            case "month" -> {
                start = now.toLocalDate().withDayOfMonth(1).atStartOfDay();
                fmt = "%Y-%m-%d"; javaPattern = "yyyy-MM-dd"; unit = java.time.temporal.ChronoUnit.DAYS;
            }
            case "30d" -> {
                start = now.toLocalDate().minusDays(29).atStartOfDay();
                fmt = "%Y-%m-%d"; javaPattern = "yyyy-MM-dd"; unit = java.time.temporal.ChronoUnit.DAYS;
            }
            case "year" -> {
                start = now.toLocalDate().withDayOfYear(1).atStartOfDay();
                fmt = "%Y-%m"; javaPattern = "yyyy-MM"; unit = java.time.temporal.ChronoUnit.MONTHS;
            }
            default -> {
                start = now.minusHours(23).truncatedTo(java.time.temporal.ChronoUnit.HOURS);
                fmt = "%m-%d %H:00"; javaPattern = "MM-dd HH:00"; unit = java.time.temporal.ChronoUnit.HOURS;
            }
        }
        List<Map<String, Object>> rows = weatherLogMapper.selectTimeline(start, now, fmt, apiTypes);
        java.util.Map<String, Map<String, Object>> byBucket = new java.util.HashMap<>();
        for (Map<String, Object> r : rows) {
            byBucket.put(String.valueOf(r.get("time_bucket")), r);
        }
        DateTimeFormatter jf = DateTimeFormatter.ofPattern(javaPattern);
        List<Map<String, Object>> result = new ArrayList<>();
        for (java.time.LocalDateTime t = start; !t.isAfter(now); t = t.plus(1, unit)) {
            String bucket = t.format(jf);
            Map<String, Object> r = byBucket.get(bucket);
            Map<String, Object> point = new HashMap<>();
            point.put("time_bucket", bucket);
            point.put("total", r == null ? 0L : ((Number) r.getOrDefault("total", 0)).longValue());
            point.put("failed", r == null ? 0L : ((Number) r.getOrDefault("failed", 0)).longValue());
            result.add(point);
        }
        log.debug("[getTimeline] range={}, types={}, 数据点={}", range, apiTypes, result.size());
        return result;
    }

    /** API 类型分布(饼图):所选时间范围内各类型调用量占比 */
    public List<Map<String, Object>> getTypeDistribution(String range) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime start = switch (range) {
            case "month" -> now.toLocalDate().withDayOfMonth(1).atStartOfDay();
            case "30d" -> now.toLocalDate().minusDays(29).atStartOfDay();
            case "year" -> now.toLocalDate().withDayOfYear(1).atStartOfDay();
            default -> now.minusHours(23).truncatedTo(java.time.temporal.ChronoUnit.HOURS);
        };
        return weatherLogMapper.selectTypeDistribution(start, now);
    }

    /**
     * 新旧版本并行验证(测试环境,OPS 手动触发):同一位置分别调 v7 与 v1,返回关键字段对照。
     * v7 air/warning 已弃用(403),仅对比 now/7d/24h 三组;每次约 6 次调用,不缓存。
     */
    public Map<String, Object> compareV7V1() {
        WeatherCredential cred = loadCredential();
        if (cred == null) return Map.of("error", "天气凭证未配置");
        String coords = "117.1201,36.6512"; // 测试环境默认济南
        String[] ll = toLatLon(coords);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("location", coords);

        // 实况:v7 now vs v1 current
        JsonNode now7 = callApi("/v7/weather/now?location=" + coords, cred);
        JsonNode n7 = now7 == null ? null : now7.path("now");
        JsonNode now1 = callApi("/weather/v1/current/" + ll[0] + "/" + ll[1] + "?localTime=true", cred);
        Map<String, Object> nowCmp = new LinkedHashMap<>();
        nowCmp.put("v7Status", n7 != null && n7.has("temp") ? "ok" : "fail");
        nowCmp.put("v1Status", now1 != null && now1.has("condition") ? "ok" : "fail");
        if (n7 != null && n7.has("temp")) {
            nowCmp.put("v7", Map.of("temp", n7.path("temp").asText(), "feelsLike", n7.path("feelsLike").asText(),
                    "humidity", n7.path("humidity").asText(), "windDir", n7.path("windDir").asText(),
                    "windScale", n7.path("windScale").asText(), "vis", n7.path("vis").asText(), "text", n7.path("text").asText()));
        }
        if (now1 != null && now1.has("condition")) {
            nowCmp.put("v1", Map.of("temp", round1(now1.path("temperature").path("value").asDouble()),
                    "feelsLike", round1(now1.path("feelsLike").path("value").asDouble()),
                    "humidity", (int) Math.round(now1.path("humidity").asDouble() * 100),
                    "windDir", compassCn(now1.path("wind").path("direction").path("compass").asText()) + " " + now1.path("wind").path("scale").asText() + "级",
                    "windScale", now1.path("wind").path("scale").asText(),
                    "vis", round1(now1.path("visibility").path("value").asDouble() / 1000),
                    "text", now1.path("condition").path("text").asText()));
        }
        result.put("now", nowCmp);

        // 每日预报首日
        JsonNode d7resp = callApi("/v7/weather/7d?location=" + coords, cred);
        JsonNode d7 = d7resp == null ? null : d7resp.path("daily").path(0);
        JsonNode d1resp = callApi("/weather/v1/daily/" + ll[0] + "/" + ll[1] + "?localTime=true", cred);
        JsonNode d1 = d1resp == null ? null : d1resp.path("days").path(0);
        Map<String, Object> dailyCmp = new LinkedHashMap<>();
        dailyCmp.put("v7Status", d7 != null && d7.has("tempMax") ? "ok" : "fail");
        dailyCmp.put("v1Status", d1 != null && d1.has("temperatureMax") ? "ok" : "fail");
        if (d7 != null && d7.has("tempMax")) {
            dailyCmp.put("v7", Map.of("fxDate", d7.path("fxDate").asText(), "tempMax", d7.path("tempMax").asText(),
                    "tempMin", d7.path("tempMin").asText(), "textDay", d7.path("textDay").asText(),
                    "sunrise", d7.path("sunrise").asText()));
        }
        if (d1 != null && d1.has("temperatureMax")) {
            dailyCmp.put("v1", Map.of("fxDate", d1.path("forecastStartTime").asText().substring(0, 10),
                    "tempMax", (int) Math.round(d1.path("temperatureMax").path("value").asDouble()),
                    "tempMin", (int) Math.round(d1.path("temperatureMin").path("value").asDouble()),
                    "textDay", d1.path("daytime").path("condition").path("text").asText(),
                    "sunrise", d1.path("astro").path("sunrise").asText()));
        }
        result.put("daily0", dailyCmp);

        // 小时预报首条
        JsonNode h7resp = callApi("/v7/weather/24h?location=" + coords, cred);
        JsonNode h7 = h7resp == null ? null : h7resp.path("hourly").path(0);
        JsonNode h1resp = callApi("/weather/v1/hourly/" + ll[0] + "/" + ll[1] + "?localTime=true", cred);
        JsonNode h1 = h1resp == null ? null : h1resp.path("hours").path(0);
        Map<String, Object> hourlyCmp = new LinkedHashMap<>();
        hourlyCmp.put("v7Status", h7 != null && h7.has("temp") ? "ok" : "fail");
        hourlyCmp.put("v1Status", h1 != null && h1.has("temperature") ? "ok" : "fail");
        if (h7 != null && h7.has("temp")) {
            hourlyCmp.put("v7", Map.of("fxTime", h7.path("fxTime").asText(), "temp", h7.path("temp").asText(),
                    "text", h7.path("text").asText(), "pop", h7.path("pop").asText()));
        }
        if (h1 != null && h1.has("temperature")) {
            hourlyCmp.put("v1", Map.of("fxTime", h1.path("forecastTime").asText(),
                    "temp", (int) Math.round(h1.path("temperature").path("value").asDouble()),
                    "text", h1.path("condition").path("text").asText(),
                    "pop", (int) Math.round(h1.path("precipitation").path("probability").asDouble() * 100)));
        }
        result.put("hourly0", hourlyCmp);
        return result;
    }

    // ---------- v1 → v7 字段形状适配(前端零改动) ----------

    /** 风向方位代码 → 中文(v1 compass 16 方位) */
    private static final Map<String, String> COMPASS_CN = Map.ofEntries(
            Map.entry("n", "北风"), Map.entry("nne", "北东北风"), Map.entry("ne", "东北风"), Map.entry("ene", "东东北风"),
            Map.entry("e", "东风"), Map.entry("ese", "东东南风"), Map.entry("se", "东南风"), Map.entry("sse", "南东南风"),
            Map.entry("s", "南风"), Map.entry("ssw", "南西南风"), Map.entry("sw", "西南风"), Map.entry("wsw", "西西南风"),
            Map.entry("w", "西风"), Map.entry("wnw", "西西北风"), Map.entry("nw", "西北风"), Map.entry("nnw", "北西北风"));

    /** v1 预警颜色代码 → 中文级别(前端 warnLevelColor 识别白/蓝/黄/橙/红) */
    private static final Map<String, String> ALERT_COLOR_CN = Map.of(
            "white", "白色", "gray", "灰色", "green", "绿色", "blue", "蓝色", "yellow", "黄色",
            "amber", "橙色", "orange", "橙色", "red", "红色", "purple", "紫色", "black", "黑色");

    private String compassCn(String compass) {
        return COMPASS_CN.getOrDefault(compass == null ? "" : compass, "");
    }

    private double round1(double v) { return Math.round(v * 10) / 10.0; }
    private double round2(double v) { return Math.round(v * 100) / 100.0; }

    /** 坐标 "lng,lat" → v1 路径参数 [lat, lon](十进制最多两位,官方要求) */
    private String[] toLatLon(String coords) {
        String[] ll = coords.split(",");
        return new String[]{
                String.format("%.2f", Double.parseDouble(ll[1])),
                String.format("%.2f", Double.parseDouble(ll[0]))};
    }

    /** v1 每日预报 days[] → 旧 v7 daily[] 字段形状(湿度 0-1→%、温度取整) */
    private List<Map<String, Object>> mapDailyV1(JsonNode days) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (JsonNode day : days) {
            Map<String, Object> d = new LinkedHashMap<>();
            String start = day.path("forecastStartTime").asText();
            d.put("fxDate", start.length() >= 10 ? start.substring(0, 10) : start);
            d.put("sunrise", day.path("astro").path("sunrise").asText());
            d.put("sunset", day.path("astro").path("sunset").asText());
            d.put("moonrise", day.path("astro").path("moonrise").asText());
            d.put("moonset", day.path("astro").path("moonset").asText());
            d.put("moonPhase", day.path("astro").path("moonPhase").asText());
            d.put("tempMax", (int) Math.round(day.path("temperatureMax").path("value").asDouble()));
            d.put("tempMin", (int) Math.round(day.path("temperatureMin").path("value").asDouble()));
            JsonNode dt = day.path("daytime");
            JsonNode nt = day.path("nighttime");
            d.put("iconDay", dt.path("condition").path("code").asText());
            d.put("textDay", dt.path("condition").path("text").asText());
            d.put("iconNight", nt.path("condition").path("code").asText());
            d.put("textNight", nt.path("condition").path("text").asText());
            d.put("wind360Day", dt.path("wind").path("direction").path("degree").asText());
            d.put("windDirDay", compassCn(dt.path("wind").path("direction").path("compass").asText()));
            d.put("windScaleDay", dt.path("wind").path("scale").asText());
            d.put("windSpeedDay", round1(dt.path("wind").path("speed").path("value").asDouble() * 3.6));
            d.put("humidity", (int) Math.round(dt.path("humidity").asDouble() * 100));
            d.put("precip", round2(dt.path("precipitation").path("amount").path("value").asDouble()));
            d.put("pop", (int) Math.round(dt.path("precipitation").path("probability").asDouble() * 100));
            d.put("uvIndex", day.path("uvIndexMax").asText());
            list.add(d);
        }
        return list;
    }

    /** v1 小时预报 hours[] → 旧 v7 hourly[] 字段形状(降水概率 0-1→%) */
    private List<Map<String, Object>> mapHourlyV1(JsonNode hours) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (JsonNode h : hours) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("fxTime", h.path("forecastTime").asText());
            m.put("temp", (int) Math.round(h.path("temperature").path("value").asDouble()));
            m.put("icon", h.path("condition").path("code").asText());
            m.put("text", h.path("condition").path("text").asText());
            m.put("pop", (int) Math.round(h.path("precipitation").path("probability").asDouble() * 100));
            m.put("windDir", compassCn(h.path("wind").path("direction").path("compass").asText()));
            m.put("windScale", h.path("wind").path("scale").asText());
            m.put("precip", round2(h.path("precipitation").path("amount").path("value").asDouble()));
            m.put("humidity", (int) Math.round(h.path("humidity").asDouble() * 100));
            list.add(m);
        }
        return list;
    }

    /** v1 预警 alerts[] → 旧 v7 warning[] 字段形状(过滤 cancel 性质;颜色代码→中文级别) */
    private List<Map<String, Object>> mapWarningV1(JsonNode alerts) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (JsonNode a : alerts) {
            if ("cancel".equals(a.path("messageType").path("code").asText())) {
                continue; // 取消性质的预警有效期 1 小时,不展示
            }
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("id", a.path("id").asText());
            w.put("status", "active");
            w.put("code", a.path("eventType").path("code").asText());
            w.put("title", a.path("headline").asText());
            w.put("typeName", a.path("eventType").path("name").asText());
            w.put("level", ALERT_COLOR_CN.getOrDefault(a.path("color").path("code").asText(), "橙色"));
            w.put("startTime", a.path("effectiveTime").asText());
            w.put("endTime", a.path("expireTime").asText());
            w.put("text", a.path("description").asText(a.path("headline").asText()));
            w.put("severity", a.path("severity").asText());
            w.put("instruction", a.path("instruction").asText());
            list.add(w);
        }
        return list;
    }

    // ---------- 内部:JWT + HTTP ----------

    /** IP → location 坐标(经度,纬度);家庭偏好位置优先,其次 ip-api.com 定位 */
    private String resolveLocation(String clientIp, WeatherCredential cred, String[] familyLocation) {
        // 家庭设置的位置偏好优先
        if (familyLocation != null && familyLocation.length >= 2
                && familyLocation[0] != null && familyLocation[1] != null) {
            return familyLocation[1] + "," + familyLocation[0]; // 和风天气 location=lng,lat
        }
        // 本地/内网 IP 用济南默认坐标
        String lookupIp = clientIp;
        if (clientIp == null || clientIp.isBlank()
                || clientIp.equals("0:0:0:0:0:0:0:1") || clientIp.equals("127.0.0.1")
                || clientIp.startsWith("192.168.") || clientIp.startsWith("10.")) {
            String cacheKey = "ihomy:weather:loc:default";
            String cached = redis.opsForValue().get(cacheKey);
            if (cached != null) return cached;
            redis.opsForValue().set(cacheKey, "117.1201,36.6512", LOCATION_TTL);
            redis.opsForValue().set("ihomy:weather:city:default", "济南", LOCATION_TTL);
            return "117.1201,36.6512";
        }

        String cacheKey = "ihomy:weather:loc:" + clientIp;
        String cached = redis.opsForValue().get(cacheKey);
        if (cached != null) return cached;

        // 用 ip-api.com 定位(与 SunService 同源),同时取城市名
        try {
            String url = "http://ip-api.com/json/" + clientIp + "?fields=status,lat,lon,city";
            ThirdPartyHttp.Resp r = ThirdPartyHttp.get("ipapi", url, null, 5000);
            JsonNode resp = mapper.readTree(r.body());
            if (resp != null && resp.has("lat") && resp.get("status").asText().equals("success")) {
                double lng = resp.get("lon").asDouble();
                double lat = resp.get("lat").asDouble();
                String coords = String.format("%.4f,%.4f", lng, lat);
                redis.opsForValue().set(cacheKey, coords, LOCATION_TTL);
                String city = resp.has("city") ? resp.get("city").asText() : "未知";
                redis.opsForValue().set("ihomy:weather:city:" + clientIp, city, LOCATION_TTL);
                return coords;
            }
        } catch (Exception e) {
            log.warn("ip-api lookup failed: {}", e.getMessage());
        }
        // fallback 济南
        redis.opsForValue().set("ihomy:weather:city:" + (clientIp == null ? "default" : clientIp), "济南", LOCATION_TTL);
        return "117.1201,36.6512";
    }

    /** 取城市名(与 resolveLocation 同步缓存) */
    private String resolveCityName(String clientIp) {
        String key = "ihomy:weather:city:" + (clientIp == null || clientIp.isBlank()
                || clientIp.equals("0:0:0:0:0:0:0:1") || clientIp.equals("127.0.0.1")
                || clientIp.startsWith("192.168.") || clientIp.startsWith("10.") ? "default" : clientIp);
        String city = redis.opsForValue().get(key);
        return city != null ? city : "济南";
    }

    /** 新版空气质量 /airquality/v1/current/{lat}/{lon} → 旧字段形状(aqi/category/level/primary/污染物浓度) */
    private Map<String, Object> fetchAir(String coords, WeatherCredential cred) {
        try {
            String[] ll = toLatLon(coords);
            JsonNode resp = callApi("/airquality/v1/current/" + ll[0] + "/" + ll[1], cred);
            if (resp == null) return null;
            Map<String, Object> m = new HashMap<>();
            for (JsonNode idx : resp.path("indexes")) {
                // 优先国标 AQI(cn / aqi-cn),否则取第一个
                String code = idx.path("code").asText();
                if ("cn".equals(code) || "aqi-cn".equals(code) || !m.containsKey("aqi")) {
                    m.put("aqi", idx.path("aqiDisplay").asText(idx.path("aqi").asText()));
                    m.put("category", idx.path("category").asText());
                    m.put("level", idx.path("level").asText());
                    String pp = idx.path("primaryPollutant").path("name").asText("");
                    if (!pp.isBlank()) m.put("primary", pp);
                }
            }
            for (JsonNode p : resp.path("pollutants")) {
                m.put(p.path("code").asText(), p.path("concentration").path("value").asText());
            }
            return m.containsKey("aqi") ? m : null;
        } catch (Exception e) {
            log.warn("[fetchAir] airquality lookup failed: {}", e.getMessage());
            return null;
        }
    }

    /** 调和风 API(自动加 JWT 头,每次调用记录日志);超月度配额返回 null */
    private JsonNode callApi(String pathAndQuery, WeatherCredential cred) {
        String apiType = parseApiType(pathAndQuery);
        String locationId = parseLocationFromQuery(pathAndQuery);
        if (isQuotaExceeded()) {
            log.warn("[callApi] 月度配额已耗尽({}),跳过调用, apiType={}, path={}", MONTHLY_QUOTA, apiType, pathAndQuery);
            return null;
        }
        long t0 = System.currentTimeMillis();
        JsonNode resp = null;
        String errorMsg = null;
        try {
            String jwt = generateJwt(cred);
            String urlStr = "https://" + cred.getApiHost() + pathAndQuery;
            // 出站走 ThirdPartyHttp:thirdparty 日志自动记请求/响应/耗时/堆栈
            ThirdPartyHttp.Resp r = ThirdPartyHttp.get("weather", urlStr,
                    Map.of("Authorization", "Bearer " + jwt), 10000);
            if (r.ok()) {
                resp = mapper.readTree(r.body());
            } else {
                errorMsg = r.status() + " " + r.body();
            }
            return resp;
        } catch (Exception e) {
            errorMsg = e.getMessage();
            return null;
        } finally {
            int costMs = (int) (System.currentTimeMillis() - t0);
            logCall(apiType, locationId, resp != null ? "SUCCESS" : "FAIL", costMs, resp, errorMsg, pathAndQuery);
            // 增加月度计数(Redis):只计计费的数据 API,控制台类不计费;key 两个月未活动自动过期
            if (resp != null && !"quota".equals(apiType) && !"finance".equals(apiType) && !"metrics".equals(apiType)) {
                String monthKey = "ihomy:weather:quota:" + java.time.YearMonth.now().toString();
                redis.opsForValue().increment(monthKey);
                redis.expire(monthKey, java.time.Duration.ofDays(62));
            }
        }
    }

    /** 检查本月 API 调用是否超配额(49999 次/月);Redis 计数器 + DB fallback */
    private boolean isQuotaExceeded() {
        String monthKey = "ihomy:weather:quota:" + java.time.YearMonth.now().toString();
        String cached = redis.opsForValue().get(monthKey);
        if (cached != null) {
            boolean exceeded = Long.parseLong(cached) >= MONTHLY_QUOTA;
            if (exceeded) log.warn("[isQuotaExceeded] Redis 计数已达配额, count={}, quota={}", cached, MONTHLY_QUOTA);
            return exceeded;
        }
        // Redis 无计数(DB fallback):查 sys_weather_log 当月数据 API 成功数(口径与计数器一致)
        try {
            java.time.LocalDateTime monthStart = java.time.LocalDate.now().withDayOfMonth(1).atStartOfDay();
            Long count = weatherLogMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WeatherLog>()
                    .ge(WeatherLog::getCreatedAt, monthStart)
                    .eq(WeatherLog::getStatus, "SUCCESS")
                    .notIn(WeatherLog::getApiType, List.of("quota", "finance", "metrics")));
            long c = count != null ? count : 0;
            log.info("[isQuotaExceeded] Redis 未命中,DB fallback 当月计数={}, quota={}", c, MONTHLY_QUOTA);
            redis.opsForValue().set(monthKey, String.valueOf(c), Duration.ofHours(1));
            return c >= MONTHLY_QUOTA;
        } catch (Exception e) {
            log.error("[isQuotaExceeded] DB fallback 查询失败,放行调用", e);
            return false;
        }
    }

    /** 从路径解析接口类型(用于日志分类;v1 与 v7 映射到同一类型码,运维统计口径连续) */
    private String parseApiType(String pathAndQuery) {
        if (pathAndQuery.startsWith("/weather/v1/current")) return "now";
        if (pathAndQuery.startsWith("/weather/v1/daily")) return "forecast";
        if (pathAndQuery.startsWith("/weather/v1/hourly")) return "hourly";
        if (pathAndQuery.startsWith("/weatheralert/v1/")) return "warning";
        if (pathAndQuery.startsWith("/airquality/v1/")) return "air";
        if (pathAndQuery.startsWith("/v7/weather/now")) return "now";
        if (pathAndQuery.startsWith("/v7/weather/7d")) return "forecast";
        if (pathAndQuery.startsWith("/v7/weather/24h")) return "hourly";
        if (pathAndQuery.startsWith("/v7/warning")) return "warning";
        if (pathAndQuery.startsWith("/v7/indices")) return "indices";
        if (pathAndQuery.startsWith("/v7/air")) return "air";
        if (pathAndQuery.startsWith("/v7/minutely")) return "minutely";
        if (pathAndQuery.startsWith("/geo/")) return "location";
        if (pathAndQuery.startsWith("/console/")) return "quota";
        if (pathAndQuery.startsWith("/finance/")) return "finance";
        if (pathAndQuery.startsWith("/metrics/")) return "metrics";
        return "other";
    }

    /** 从 query 解析 location 参数 */
    private String parseLocationFromQuery(String pathAndQuery) {
        int idx = pathAndQuery.indexOf("location=");
        if (idx < 0) return null;
        String sub = pathAndQuery.substring(idx + 9);
        int amp = sub.indexOf('&');
        return amp > 0 ? sub.substring(0, amp) : sub;
    }

    /** 记录调用日志(天气数据公开可存;quota 响应可能含账号信息不存) */
    private void logCall(String apiType, String locationId, String status, int costMs,
                         JsonNode resp, String errorMsg, String pathAndQuery) {
        try {
            WeatherLog logEntry = new WeatherLog();
            logEntry.setApiType(apiType);
            logEntry.setLocationId(locationId);
            logEntry.setStatus(status);
            logEntry.setCostMs(costMs);
            // quota 接口响应可能含账号信息,不存 response;其余天气数据公开可存
            if (resp != null && !"quota".equals(apiType) && !"finance".equals(apiType) && !"metrics".equals(apiType)) {
                String json = resp.toString();
                logEntry.setResponse(json.length() > 10000 ? json.substring(0, 10000) : json);
            }
            if (errorMsg != null) {
                logEntry.setErrorMsg(errorMsg.length() > 500 ? errorMsg.substring(0, 500) : errorMsg);
            }
            weatherLogMapper.insert(logEntry);
            log.debug("[logCall] 日志落库成功, apiType={}, locationId={}, status={}, costMs={}", apiType, locationId, status, costMs);
        } catch (Exception e) {
            log.error("[logCall] 日志落库失败, apiType={}, locationId={}, status={}, path={}", apiType, locationId, status, pathAndQuery, e);
        }
    }

    /** 生成 Ed25519 签名的 JWT */
    private String generateJwt(WeatherCredential cred) throws Exception {
        String pemBody = cred.getPrivateKey()
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] keyBytes = Base64.getDecoder().decode(pemBody);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("EdDSA");
        PrivateKey privateKey = kf.generatePrivate(keySpec);

        long iat = ZonedDateTime.now(ZoneOffset.UTC).toEpochSecond() - 30;
        long exp = iat + 900;
        String headerJson = "{\"alg\":\"EdDSA\",\"kid\":\"" + cred.getKeyId() + "\"}";
        String payloadJson = "{\"sub\":\"" + cred.getProjectId() + "\",\"iat\":" + iat + ",\"exp\":" + exp + "}";

        Base64.Encoder b64u = Base64.getUrlEncoder().withoutPadding();
        String headerEncoded = b64u.encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
        String payloadEncoded = b64u.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        String data = headerEncoded + "." + payloadEncoded;

        Signature signer = Signature.getInstance("EdDSA");
        signer.initSign(privateKey);
        signer.update(data.getBytes(StandardCharsets.UTF_8));
        byte[] signature = signer.sign();
        String signatureEncoded = b64u.encodeToString(signature);

        return data + "." + signatureEncoded;
    }

    // ---------- 缓存读写 ----------

    @SuppressWarnings("unchecked")
    private Map<String, Object> readCache(String key) {
        String cached = redis.opsForValue().get(key);
        if (cached == null) return null;
        try { return mapper.readValue(cached, Map.class); } catch (Exception ignored) { return null; }
    }

    private void writeCache(String key, Map<String, Object> data, Duration ttl) {
        try {
            redis.opsForValue().set(key, mapper.writeValueAsString(data), ttl);
        } catch (Exception ignored) {}
    }

    /** 和风天气 code → 内部 condition */
    private String codeToCondition(String code) {
        if (code == null) return "clear";
        int c = Integer.parseInt(code);
        if (c == 100) return "clear";
        if (c >= 101 && c <= 104) return "cloud";
        if (c >= 150 && c <= 154) return "cloud";
        if (c >= 300 && c <= 399) return "rain";
        if (c >= 400 && c <= 499) return "snow";
        if (c >= 500 && c <= 599) return "fog";
        if (c >= 200 && c <= 299) return "thunder";
        return "cloud";
    }

    /** 和风天气 code → 降水等级(1~6);非降水返回 0 */
    private int codeToPrecipLevel(String code) {
        if (code == null) return 0;
        int c = Integer.parseInt(code);
        // 雨 300-399
        if (c == 305 || c == 309 || c == 314 || c == 300 || c == 399) return 1;  // 小雨/阵雨/毛毛雨
        if (c == 306 || c == 315) return 2;  // 中雨
        if (c == 301 || c == 307 || c == 316) return 3;  // 强阵雨/大雨
        if (c == 310 || c == 317) return 4;  // 暴雨
        if (c == 311 || c == 318) return 5;  // 大暴雨
        if (c == 312) return 6;  // 特大暴雨
        if (c >= 300 && c <= 399) return 1;  // 其他雨默认1级
        // 雪 400-499
        if (c == 400 || c == 408 || c == 407 || c == 404 || c == 405 || c == 406 || c == 499) return 1;  // 小雪/阵雪/雨夹雪
        if (c == 401 || c == 409) return 2;  // 中雪
        if (c == 402 || c == 410) return 3;  // 大雪
        if (c == 403) return 4;  // 暴雪
        if (c >= 400 && c <= 499) return 1;  // 其他雪默认1级
        return 0;
    }
}

