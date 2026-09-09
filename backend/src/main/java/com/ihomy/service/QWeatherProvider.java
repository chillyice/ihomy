package com.ihomy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ihomy.common.ThirdPartyHttp;
import com.ihomy.common.WeatherConst;
import com.ihomy.entity.WeatherCredential;
import com.ihomy.entity.WeatherLog;
import com.ihomy.mapper.WeatherLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 和风天气适配器(V9.53 起从 WeatherService 抽出):JWT(Ed25519)身份认证 + v1/v7 混合接口,
 * 把 v1 响应归一化为旧 v7 字段形状(前端零改动)。含取数/映射/配额/调用日志/告警缓存。
 * 凭证由 WeatherService.loadCredential 解析(私钥已解密)后传入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QWeatherProvider implements WeatherProvider {

    private final StringRedisTemplate redis;
    private final WeatherLogMapper weatherLogMapper;
    private final ObjectMapper mapper = new ObjectMapper();

    /** 月度 API 调用配额(超限后本月停止调用,返回 null 前端降级) */
    private static final int MONTHLY_QUOTA = 50000;

    @Override
    public String code() {
        return WeatherConst.PROVIDER_QWEATHER;
    }

    /** 当前天气:归一化 {condition, precipLevel, iconCode, temp, text, nowFull};失败返回 null */
    @Override
    public Map<String, Object> current(String coords, WeatherCredential cred) {
        String[] ll = toLatLon(coords);
        JsonNode now = callApi("/weather/v1/current/" + ll[0] + "/" + ll[1] + "?localTime=true", cred);
        if (now == null || !now.has("condition")) return null;
        String code = now.path("condition").path("code").asText("100");
        Map<String, Object> data = new HashMap<>();
        data.put("condition", codeToCondition(code));
        data.put("precipLevel", codeToPrecipLevel(code));
        data.put("iconCode", code);
        data.put("temp", (int) Math.round(now.path("temperature").path("value").asDouble()));
        data.put("text", now.path("condition").path("text").asText());
        data.put("nowFull", buildNowFull(now));
        return data;
    }

    /** 天气详情:归一化 {daily, hourly, warning, air, indices, minutely}(字段可缺);now 由门面组装 */
    @Override
    public Map<String, Object> detail(String coords, WeatherCredential cred) {
        Map<String, Object> data = new HashMap<>();
        String[] ll = toLatLon(coords);
        JsonNode dailyResp = callApi("/weather/v1/daily/" + ll[0] + "/" + ll[1] + "?days=10&localTime=true", cred);
        if (dailyResp != null && dailyResp.has("days")) data.put("daily", mapDailyV1(dailyResp.get("days")));
        JsonNode hourlyResp = callApi("/weather/v1/hourly/" + ll[0] + "/" + ll[1] + "?localTime=true", cred);
        if (hourlyResp != null && hourlyResp.has("hours")) data.put("hourly", mapHourlyV1(hourlyResp.get("hours")));
        List<Map<String, Object>> warnings = fetchAlertsCached(coords, cred);
        if (!warnings.isEmpty()) data.put("warning", warnings);
        Map<String, Object> air = fetchAir(coords, cred);
        if (air != null) data.put("air", air);
        JsonNode indices = callApi("/v7/indices/1d?location=" + coords + "&type=0", cred);
        if (indices != null && indices.has("daily")) data.put("indices", indices.get("daily"));
        JsonNode minutely = callApi("/v7/minutely/5m?location=" + coords, cred);
        if (minutely != null && minutely.has("minutely")) data.put("minutely", minutely);
        return data;
    }

    /** 当前生效预警(坐标级共享缓存,供详情与主动推送共用) */
    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> alerts(String coords, WeatherCredential cred) {
        return fetchAlertsCached(coords, cred);
    }

    /** 财务汇总(控制台 /finance/v1/summary) */
    @Override
    public Map<String, Object> finance(WeatherCredential cred) {
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

    /** 请求量统计(控制台 /metrics/v1/stats) */
    @Override
    public Map<String, Object> stats(WeatherCredential cred) {
        JsonNode resp = callApi("/metrics/v1/stats", cred);
        if (resp == null) return null;
        Map<String, Object> result = new HashMap<>();
        result.put("raw", resp);
        result.put("asOf", resp.path("asOf").asText());
        result.put("success", resp.path("success"));
        result.put("errors", resp.path("errors"));
        return result;
    }

    /** v7/v1 新旧版本并行验证(测试环境 OPS 手动触发);每轮约 6 次调用,不缓存 */
    public Map<String, Object> compareV7V1(WeatherCredential cred) {
        String coords = "117.1201,36.6512"; // 测试环境默认济南
        String[] ll = toLatLon(coords);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("location", coords);

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

    // ---------- v1 → v7 字段形状适配 ----------

    private static final Map<String, String> COMPASS_CN = Map.ofEntries(
            Map.entry("n", "北风"), Map.entry("nne", "北东北风"), Map.entry("ne", "东北风"), Map.entry("ene", "东东北风"),
            Map.entry("e", "东风"), Map.entry("ese", "东东南风"), Map.entry("se", "东南风"), Map.entry("sse", "南东南风"),
            Map.entry("s", "南风"), Map.entry("ssw", "南西南风"), Map.entry("sw", "西南风"), Map.entry("wsw", "西西南风"),
            Map.entry("w", "西风"), Map.entry("wnw", "西西北风"), Map.entry("nw", "西北风"), Map.entry("nnw", "北西北风"));

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

    private List<Map<String, Object>> mapWarningV1(JsonNode alerts) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (JsonNode a : alerts) {
            if ("cancel".equals(a.path("messageType").path("code").asText())) {
                continue;
            }
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("id", a.path("id").asText());
            w.put("status", "active");
            w.put("code", a.path("eventType").path("code").asText());
            w.put("title", a.path("headline").asText());
            w.put("senderName", a.path("senderName").asText());
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

    private Map<String, Object> fetchAir(String coords, WeatherCredential cred) {
        try {
            String[] ll = toLatLon(coords);
            JsonNode resp = callApi("/airquality/v1/current/" + ll[0] + "/" + ll[1], cred);
            if (resp == null) return null;
            JsonNode picked = null;
            for (JsonNode idx : resp.path("indexes")) {
                String code = idx.path("code").asText();
                if ("cn".equals(code) || "aqi-cn".equals(code)) { picked = idx; break; }
                if (picked == null) picked = idx;
            }
            if (picked == null) return null;
            Map<String, Object> m = new HashMap<>();
            m.put("aqi", picked.path("aqiDisplay").asText(picked.path("aqi").asText()));
            m.put("category", picked.path("category").asText());
            m.put("level", picked.path("level").asText());
            String pp = picked.path("primaryPollutant").path("name").asText("");
            if (!pp.isBlank()) m.put("primary", pp);
            JsonNode advice = picked.path("health").path("advice");
            if (!advice.isMissingNode()) {
                m.put("adviceGeneral", advice.path("generalPopulation").asText(""));
                m.put("adviceSensitive", advice.path("sensitivePopulation").asText(""));
            }
            for (JsonNode p : resp.path("pollutants")) {
                m.put(p.path("code").asText(), p.path("concentration").path("value").asText());
            }
            return m;
        } catch (Exception e) {
            log.warn("[fetchAir] airquality lookup failed: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchAlertsCached(String coords, WeatherCredential cred) {
        String key = "ihomy:weather:alerts:" + coords;
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) return (List<Map<String, Object>>) mapper.readValue(cached, List.class);
        } catch (Exception e) {
            log.warn("[fetchAlertsCached] read cache failed: {}", e.getMessage());
        }
        String[] ll = toLatLon(coords);
        JsonNode resp = callApi("/weatheralert/v1/current/" + ll[0] + "/" + ll[1] + "?localTime=true", cred);
        List<Map<String, Object>> list = resp != null && resp.has("alerts") ? mapWarningV1(resp.get("alerts")) : List.of();
        try {
            redis.opsForValue().set(key, mapper.writeValueAsString(list), Duration.ofMinutes(25));
        } catch (Exception e) {
            log.warn("[fetchAlertsCached] write cache failed: {}", e.getMessage());
        }
        return list;
    }

    // ---------- JWT + HTTP 传输 ----------

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
            if (resp != null && !"quota".equals(apiType) && !"finance".equals(apiType) && !"metrics".equals(apiType)) {
                String monthKey = "ihomy:weather:quota:" + java.time.YearMonth.now().toString();
                redis.opsForValue().increment(monthKey);
                redis.expire(monthKey, Duration.ofDays(62));
            }
        }
    }

    private boolean isQuotaExceeded() {
        String monthKey = "ihomy:weather:quota:" + java.time.YearMonth.now().toString();
        String cached = redis.opsForValue().get(monthKey);
        if (cached != null) {
            boolean exceeded = Long.parseLong(cached) >= MONTHLY_QUOTA;
            if (exceeded) log.warn("[isQuotaExceeded] Redis 计数已达配额, count={}, quota={}", cached, MONTHLY_QUOTA);
            return exceeded;
        }
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

    private String parseLocationFromQuery(String pathAndQuery) {
        int idx = pathAndQuery.indexOf("location=");
        if (idx < 0) return null;
        String sub = pathAndQuery.substring(idx + 9);
        int amp = sub.indexOf('&');
        return amp > 0 ? sub.substring(0, amp) : sub;
    }

    private void logCall(String apiType, String locationId, String status, int costMs,
                         JsonNode resp, String errorMsg, String pathAndQuery) {
        try {
            WeatherLog logEntry = new WeatherLog();
            logEntry.setApiType(apiType);
            logEntry.setLocationId(locationId);
            logEntry.setStatus(status);
            logEntry.setCostMs(costMs);
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

    private int codeToPrecipLevel(String code) {
        if (code == null) return 0;
        int c = Integer.parseInt(code);
        if (c == 305 || c == 309 || c == 314 || c == 300 || c == 399) return 1;
        if (c == 306 || c == 315) return 2;
        if (c == 301 || c == 307 || c == 316) return 3;
        if (c == 310 || c == 317) return 4;
        if (c == 311 || c == 318) return 5;
        if (c == 312) return 6;
        if (c >= 300 && c <= 399) return 1;
        if (c == 400 || c == 408 || c == 407 || c == 404 || c == 405 || c == 406 || c == 499) return 1;
        if (c == 401 || c == 409) return 2;
        if (c == 402 || c == 410) return 3;
        if (c == 403) return 4;
        if (c >= 400 && c <= 499) return 1;
        return 0;
    }
}
