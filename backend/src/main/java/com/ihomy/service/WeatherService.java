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
    private static final int MONTHLY_QUOTA = 49999;

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

        JsonNode now = callApi("/v7/weather/now?location=" + locationId, cred);
        if (now == null || !now.has("now")) return null;
        JsonNode n = now.get("now");
        String code = n.has("icon") ? n.get("icon").asText() : "100";
        Map<String, Object> data = new HashMap<>();
        data.put("condition", codeToCondition(code));
        data.put("precipLevel", codeToPrecipLevel(code));
        data.put("iconCode", code);
        data.put("temp", Integer.parseInt(n.get("temp").asText()));
        data.put("text", n.get("text").asText());
        data.put("city", familyLocation != null && familyLocation.length > 2 && familyLocation[2] != null ? familyLocation[2] : resolveCityName(clientIp));
        data.put("locationId", locationId);
        writeCache(cacheKey, data, NOW_TTL);
        return data;
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

        // 当前天气(复用 now 缓存)
        Map<String, Object> nowData = getWeather(clientIp, familyLocation);
        if (nowData != null) data.put("now", nowData);

        // 7 天预报 + 24 小时预报
        JsonNode forecast = callApi("/v7/weather/7d?location=" + locationId, cred);
        if (forecast != null && forecast.has("daily")) data.put("daily", forecast.get("daily"));
        JsonNode hourly = callApi("/v7/weather/24h?location=" + locationId, cred);
        if (hourly != null && hourly.has("hourly")) data.put("hourly", hourly.get("hourly"));

        // 灾害预警
        JsonNode warning = callApi("/v7/warning/now?location=" + locationId, cred);
        if (warning != null && warning.has("warning")) data.put("warning", warning.get("warning"));

        // 天气指数(全部类型 type=0)
        JsonNode indices = callApi("/v7/indices/1d?location=" + locationId + "&type=0", cred);
        if (indices != null && indices.has("daily")) data.put("indices", indices.get("daily"));

        // 空气质量(5 天)
        JsonNode air = callApi("/v7/air/now?location=" + locationId, cred);
        if (air != null && air.has("now")) data.put("air", air.get("now"));

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

    /** 从路径解析接口类型(用于日志分类) */
    private String parseApiType(String pathAndQuery) {
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

