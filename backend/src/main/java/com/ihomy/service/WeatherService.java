package com.ihomy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ihomy.common.ThirdPartyHttp;
import com.ihomy.common.WeatherConst;
import com.ihomy.entity.Family;
import com.ihomy.entity.SysUserRole;
import com.ihomy.entity.WeatherCredential;
import com.ihomy.entity.WeatherLog;
import com.ihomy.mapper.FamilyMapper;
import com.ihomy.mapper.SysUserRoleMapper;
import com.ihomy.mapper.WeatherCredentialMapper;
import com.ihomy.mapper.WeatherLogMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 天气服务门面(V9.53 起多天气源):解析生效凭证(status=1 或 yml 兜底)、IP/家庭坐标定位、缓存读写、
 * 运维统计(本地日志聚合),并把取数委托给按 provider 匹配的 WeatherProvider(和风实现见 QWeatherProvider)。
 * 凭证未配置/未适配时返回 null,前端降级为只按时间做光影。
 * Redis 缓存:now 30 分钟 / detail 30 分钟(坐标级,与预警推送共用同一告警缓存)。
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
    private final FamilyMapper familyMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final NotificationService notificationService;
    private final ObjectMapper mapper = new ObjectMapper();

    /** 全部天气源适配器(Spring 收集);按 code 索引后按凭证 provider 分发 */
    private final List<WeatherProvider> providers;
    private final Map<String, WeatherProvider> providerMap = new HashMap<>();

    @PostConstruct
    void initProviders() {
        for (WeatherProvider p : providers) {
            providerMap.put(p.code(), p);
        }
    }

    private static final Duration NOW_TTL = Duration.ofMinutes(30);
    private static final Duration LOCATION_TTL = Duration.ofHours(6);

    /** 取启用凭证:DB 优先(status=1 且凭证字段非空),空则 fallback yml(仅和风)。
     *  和风(QWEATHER)看 private_key,其他天气源看 config_json;ENC(...) 包裹时用 ParameterService 取盐值解密。 */
    private WeatherCredential loadCredential() {
        try {
            WeatherCredential c = credentialMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WeatherCredential>()
                    .eq(WeatherCredential::getStatus, 1).last("LIMIT 1"));
            // 和风看私钥,非和风看 config_json;任一为空则 fallback yml
            if (c != null && usableCredential(c)) {
                if (c.getPrivateKey() != null && !c.getPrivateKey().isBlank()) {
                    c.setPrivateKey(parameterService.decrypt(c.getPrivateKey()));
                }
                if (c.getConfigJson() != null && !c.getConfigJson().isBlank()) {
                    c.setConfigJson(parameterService.decrypt(c.getConfigJson()));
                }
                log.info("[loadCredential] 从DB加载凭证成功, provider={}, keyId={}", c.getProvider(), c.getKeyId());
                return c;
            }
        } catch (Exception e) {
            log.error("[loadCredential] 从DB加载凭证失败,将尝试yml fallback", e);
        }
        // yml fallback(仅和风)
        if (apiHost == null || apiHost.isBlank() || projectId == null || projectId.isBlank()
                || keyId == null || keyId.isBlank() || privateKeyPem == null || privateKeyPem.isBlank()) {
            log.warn("[loadCredential] yml 凭证未配置,天气功能不可用");
            return null;
        }
        WeatherCredential fallback = new WeatherCredential();
        fallback.setProvider(WeatherConst.PROVIDER_QWEATHER);
        fallback.setApiHost(apiHost);
        fallback.setProjectId(projectId);
        fallback.setKeyId(keyId);
        fallback.setPrivateKey(parameterService.decrypt(privateKeyPem));
        log.info("[loadCredential] 从yml加载凭证成功, keyId={}", keyId);
        return fallback;
    }

    /** 凭证是否可用:和风看私钥,其他天气源看 config_json(未适配 provider 也有独立凭证,不能误回落 yml) */
    private boolean usableCredential(WeatherCredential c) {
        if (!WeatherConst.PROVIDER_QWEATHER.equals(c.getProvider())) {
            return c.getConfigJson() != null && !c.getConfigJson().isBlank();
        }
        return c.getPrivateKey() != null && !c.getPrivateKey().isBlank();
    }

    private WeatherProvider providerOf(String code) {
        WeatherProvider p = code == null ? null : providerMap.get(code);
        if (p == null) {
            log.warn("[WeatherService] 未适配的天气源 provider={},返回 null 降级", code);
        }
        return p;
    }

    /** 返回当前天气 {condition, temp, text, city, locationId, ...};凭证未配/未适配/请求失败返回 null */
    public Map<String, Object> getWeather(String clientIp, String[] familyLocation) {
        WeatherCredential cred = loadCredential();
        if (cred == null) return null;
        WeatherProvider p = providerOf(cred.getProvider());
        if (p == null) return null;
        String cacheKey = familyLocation != null ? "ihomy:weather:now:fam:" + familyLocation[0] + ":" + familyLocation[1] : "ihomy:weather:now";
        Map<String, Object> cached = readCache(cacheKey);
        if (cached != null) return cached;

        String coords = resolveLocation(clientIp, cred, familyLocation);
        if (coords == null) return null;

        Map<String, Object> data = p.current(coords, cred);
        if (data == null) return null;
        data.put("city", familyLocation != null && familyLocation.length > 2 && familyLocation[2] != null
                ? familyLocation[2] : resolveCityName(clientIp));
        data.put("locationId", coords);
        writeCache(cacheKey, data, NOW_TTL);
        return data;
    }

    /** 天气详情聚合:now + 7d + 24h + warning + indices + air + minutely */
    public Map<String, Object> getDetail(String clientIp, String[] familyLocation) {
        WeatherCredential cred = loadCredential();
        if (cred == null) return null;
        WeatherProvider p = providerOf(cred.getProvider());
        if (p == null) return null;
        String cacheKey = familyLocation != null ? "ihomy:weather:detail:fam:" + familyLocation[0] + ":" + familyLocation[1] : "ihomy:weather:detail";
        Map<String, Object> cached = readCache(cacheKey);
        if (cached != null) return cached;

        String coords = resolveLocation(clientIp, cred, familyLocation);
        if (coords == null) return null;

        Map<String, Object> data = new HashMap<>();
        data.put("locationId", coords);

        // 当前天气(复用 now 缓存,含全量实况字段 nowFull)
        Map<String, Object> nowData = getWeather(clientIp, familyLocation);
        if (nowData != null) {
            data.put("now", nowData);
            data.put("nowFull", nowData.get("nowFull"));
        }

        Map<String, Object> detail = p.detail(coords, cred);
        if (detail != null) data.putAll(detail);

        writeCache(cacheKey, data, NOW_TTL);
        return data;
    }

    /**
     * 本月配额使用(本地统计:Redis 计数器 / DB fallback)。
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
        result.put("quota", 50000);
        result.put("remaining", Math.max(0, 50000 - used));
        result.put("usagePercent", Math.round(used * 1000.0 / 50000) / 10.0);
        return result;
    }

    /** 控制台 API:财务汇总(委托给对应 provider,和风实现) */
    public Map<String, Object> getFinance() {
        WeatherCredential cred = loadCredential();
        if (cred == null) return null;
        WeatherProvider p = providerOf(cred.getProvider());
        return p == null ? null : p.finance(cred);
    }

    /** 控制台 API:请求量统计(委托给对应 provider,和风实现) */
    public Map<String, Object> getStats() {
        WeatherCredential cred = loadCredential();
        if (cred == null) return null;
        WeatherProvider p = providerOf(cred.getProvider());
        return p == null ? null : p.stats(cred);
    }

    /** v7/v1 并行验证(测试环境 OPS 手动触发,仅和风) */
    public Map<String, Object> compareV7V1() {
        WeatherCredential cred = loadCredential();
        if (cred == null) return Map.of("error", "天气凭证未配置");
        WeatherProvider p = providerOf(cred.getProvider());
        if (p instanceof QWeatherProvider q) return q.compareV7V1(cred);
        return Map.of("error", "当前天气源不支持 v7/v1 对照");
    }

    /**
     * 本地日志聚合:按时间范围返回调用总量+失败量折线图数据,可按 API 类型过滤。
     * 零填充整个时间范围(缺数据的桶补 0)。
     */
    public List<Map<String, Object>> getTimeline(String range, List<String> apiTypes) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime start;
        String fmt;
        String javaPattern;
        java.time.temporal.ChronoUnit unit;
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
        Map<String, Map<String, Object>> byBucket = new HashMap<>();
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

    // ---------- 坐标/城市解析(provider 无关,门面共享) ----------

    /** IP → location 坐标 "lng,lat";家庭偏好位置优先,其次 ip-api.com 定位 */
    private String resolveLocation(String clientIp, WeatherCredential cred, String[] familyLocation) {
        if (familyLocation != null && familyLocation.length >= 2
                && familyLocation[0] != null && familyLocation[1] != null) {
            return familyLocation[1] + "," + familyLocation[0];
        }
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

        try {
            String url = "http://ip-api.com/json/" + clientIp + "?fields=status,lat,lon,city";
            ThirdPartyHttp.Resp r = ThirdPartyHttp.get("ipapi", url, null, 5000);
            JsonNode resp = mapper.readTree(r.body());
            if (resp != null && resp.has("lat") && "success".equals(resp.get("status").asText())) {
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

    // ---------- 预警主动推送(30 分钟一轮,家庭级开关) ----------

    /**
     * 预警主动推送:每 30 分钟扫描所有家庭,新出现的预警发给全部成员站内通知(铃铛)。
     * 家庭级开关存 sys_parameter(weather:alert-push:{familyId}="false" 关闭,缺省=开);
     * 未设置天气地区的家庭用服务器默认位置(济南);预警 ID 用 Redis Set 去重(TTL 3 天)。
     * 告警取数走 provider.alerts(非和风返回空,跳过)。
     */
    @Scheduled(fixedDelay = 1800_000, initialDelay = 120_000)
    public void pushWeatherAlerts() {
        WeatherCredential cred = loadCredential();
        if (cred == null) return;
        WeatherProvider p = providerOf(cred.getProvider());
        if (p == null) return;
        List<Family> families = familyMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Family>()
                        .eq(Family::getDeleted, 0));
        for (Family f : families) {
            try {
                if ("false".equals(parameterService.getString("weather:alert-push:" + f.getId()))) continue;
                String coords = f.getWeatherLat() != null && f.getWeatherLng() != null
                        ? f.getWeatherLng().toPlainString() + "," + f.getWeatherLat().toPlainString()
                        : "117.1201,36.6512";
                List<Map<String, Object>> alerts = p.alerts(coords, cred);
                if (alerts.isEmpty()) continue;
                List<Long> memberIds = sysUserRoleMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUserRole>()
                                        .eq(SysUserRole::getFamilyId, f.getId())
                                        .select(SysUserRole::getUserId))
                        .stream().map(SysUserRole::getUserId).distinct().toList();
                String dedupeKey = "ihomy:weather:alertpush:" + f.getId();
                for (Map<String, Object> a : alerts) {
                    String alertId = String.valueOf(a.get("id"));
                    Long added = redis.opsForSet().add(dedupeKey, alertId);
                    if (added == null || added == 0) continue;
                    redis.expire(dedupeKey, Duration.ofDays(3));
                    String headline = String.valueOf(a.get("title"));
                    if (headline.length() > 120) headline = headline.substring(0, 120) + "…";
                    String content = "【气象预警】" + a.get("typeName") + " " + a.get("level") + "预警:" + headline;
                    for (Long uid : memberIds) {
                        notificationService.create(uid, "system", content, null, "weather_alert", null);
                    }
                    log.info("[pushWeatherAlerts] family={} alert={} 已推送给 {} 名成员", f.getId(), alertId, memberIds.size());
                }
            } catch (Exception e) {
                log.warn("[pushWeatherAlerts] family={} failed: {}", f.getId(), e.getMessage());
            }
        }
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
}
