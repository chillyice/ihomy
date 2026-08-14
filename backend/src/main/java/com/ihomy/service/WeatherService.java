package com.ihomy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ihomy.entity.WeatherCredential;
import com.ihomy.entity.WeatherLog;
import com.ihomy.mapper.WeatherCredentialMapper;
import com.ihomy.mapper.WeatherLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

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

    /** 取启用凭证:DB 优先(status=1 且 private_key 非空),空则 fallback yml */
    private WeatherCredential loadCredential() {
        try {
            WeatherCredential c = credentialMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WeatherCredential>()
                    .eq(WeatherCredential::getStatus, 1).last("LIMIT 1"));
            // DB 记录 private_key 非空才用;为空则 fallback yml
            if (c != null && c.getPrivateKey() != null && !c.getPrivateKey().isBlank()) {
                return c;
            }
        } catch (Exception e) {
            log.warn("load weather credential from DB failed: {}", e.getMessage());
        }
        // yml fallback
        if (apiHost == null || apiHost.isBlank() || projectId == null || projectId.isBlank()
                || keyId == null || keyId.isBlank() || privateKeyPem == null || privateKeyPem.isBlank()) {
            return null;
        }
        WeatherCredential fallback = new WeatherCredential();
        fallback.setApiHost(apiHost);
        fallback.setProjectId(projectId);
        fallback.setKeyId(keyId);
        fallback.setPrivateKey(privateKeyPem);
        return fallback;
    }

    /** 凭证是否已配置 */
    public boolean isEnabled() {
        return loadCredential() != null;
    }

    /** 返回当前天气 {condition, temp, text};Key 未配/请求失败返回 null */
    public Map<String, Object> getWeather(String clientIp) {
        WeatherCredential cred = loadCredential();
        if (cred == null) return null;
        String cacheKey = "ihomy:weather:now";
        Map<String, Object> cached = readCache(cacheKey);
        if (cached != null) return cached;

        String locationId = resolveLocation(clientIp, cred);
        if (locationId == null) return null;

        JsonNode now = callApi("/v7/weather/now?location=" + locationId, cred);
        if (now == null || !now.has("now")) return null;
        JsonNode n = now.get("now");
        String code = n.has("icon") ? n.get("icon").asText() : "100";
        Map<String, Object> data = new HashMap<>();
        data.put("condition", codeToCondition(code));
        data.put("temp", Integer.parseInt(n.get("temp").asText()));
        data.put("text", n.get("text").asText());
        data.put("city", resolveCityName(clientIp));
        data.put("locationId", locationId);
        writeCache(cacheKey, data, NOW_TTL);
        return data;
    }

    /** 天气详情聚合:now + 7d + 24h + warning + indices + air + minutely */
    public Map<String, Object> getDetail(String clientIp) {
        WeatherCredential cred = loadCredential();
        if (cred == null) return null;
        String cacheKey = "ihomy:weather:detail";
        Map<String, Object> cached = readCache(cacheKey);
        if (cached != null) return cached;

        String locationId = resolveLocation(clientIp, cred);
        if (locationId == null) return null;

        Map<String, Object> data = new HashMap<>();
        data.put("locationId", locationId);

        // 当前天气(复用 now 缓存)
        Map<String, Object> nowData = getWeather(clientIp);
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

    /** 控制台 API:用量统计(供 OPS 运维页) */
    public Map<String, Object> getQuota() {
        WeatherCredential cred = loadCredential();
        if (cred == null) return null;
        JsonNode resp = callApi("/console/v1/usage", cred);
        if (resp == null) return null;
        Map<String, Object> result = new HashMap<>();
        result.put("raw", resp);
        return result;
    }

    // ---------- 内部:JWT + HTTP ----------

    /** IP → location 坐标(经度,纬度);用 ip-api.com 定位,不依赖 qweather GeoAPI */
    private String resolveLocation(String clientIp, WeatherCredential cred) {
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
            URL url = new URL("http://ip-api.com/json/" + clientIp + "?fields=status,lat,lon,city");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.connect();
            String body = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            conn.disconnect();
            JsonNode resp = mapper.readTree(body);
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
        if (isQuotaExceeded()) {
            log.warn("qweather monthly quota exceeded ({}), skip call [{}]", MONTHLY_QUOTA, pathAndQuery);
            return null;
        }
        String apiType = parseApiType(pathAndQuery);
        String locationId = parseLocationFromQuery(pathAndQuery);
        long t0 = System.currentTimeMillis();
        JsonNode resp = null;
        String errorMsg = null;
        try {
            String jwt = generateJwt(cred);
            String urlStr = "https://" + cred.getApiHost() + pathAndQuery;

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + jwt);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.connect();

            int status = conn.getResponseCode();
            InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
            if (is != null) {
                if ("gzip".equalsIgnoreCase(conn.getContentEncoding())) {
                    is = new GZIPInputStream(is);
                }
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                is.close();
                if (status >= 200 && status < 300) {
                    resp = mapper.readTree(body);
                } else {
                    errorMsg = status + " " + body;
                    log.warn("qweather call failed [{}]: {}", pathAndQuery, errorMsg);
                }
            }
            conn.disconnect();
            return resp;
        } catch (Exception e) {
            errorMsg = e.getMessage();
            log.warn("qweather call failed [{}]: {}", pathAndQuery, errorMsg);
            return null;
        } finally {
            int costMs = (int) (System.currentTimeMillis() - t0);
            logCall(apiType, locationId, resp != null ? "SUCCESS" : "FAIL", costMs, resp, errorMsg, pathAndQuery);
            // 增加月度计数(Redis)
            if (resp != null) {
                String monthKey = "ihomy:weather:quota:" + java.time.YearMonth.now().toString();
                redis.opsForValue().increment(monthKey);
            }
        }
    }

    /** 检查本月 API 调用是否超配额(49999 次/月);Redis 计数器 + DB fallback */
    private boolean isQuotaExceeded() {
        String monthKey = "ihomy:weather:quota:" + java.time.YearMonth.now().toString();
        String cached = redis.opsForValue().get(monthKey);
        if (cached != null) {
            return Long.parseLong(cached) >= MONTHLY_QUOTA;
        }
        // Redis 无计数(DB fallback):查 sys_weather_log 当月记录数
        try {
            java.time.LocalDateTime monthStart = java.time.LocalDate.now().withDayOfMonth(1).atStartOfDay();
            Long count = weatherLogMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WeatherLog>()
                    .ge(WeatherLog::getCreatedAt, monthStart));
            long c = count != null ? count : 0;
            redis.opsForValue().set(monthKey, String.valueOf(c), Duration.ofHours(1));
            return c >= MONTHLY_QUOTA;
        } catch (Exception e) {
            log.warn("quota check failed, allow call: {}", e.getMessage());
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
            if (resp != null && !"quota".equals(apiType)) {
                String json = resp.toString();
                logEntry.setResponse(json.length() > 10000 ? json.substring(0, 10000) : json);
            }
            if (errorMsg != null) {
                logEntry.setErrorMsg(errorMsg.length() > 500 ? errorMsg.substring(0, 500) : errorMsg);
            }
            weatherLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.warn("weather log insert failed: {}", e.getMessage());
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
}

