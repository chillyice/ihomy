package com.ihomy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ihomy.common.SolarUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 太阳信息服务:IP 定位(lat/lng/timezone)→ NOAA 算法计算 96 时隙太阳位置。
 * Redis 缓存 6 小时(IP→位置) + 当日缓存(时隙表)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SunService {

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String LOC_PREFIX = "ihomy:sun:loc:";
    private static final String SLOTS_PREFIX = "ihomy:sun:slots:";

    /** 主入口:返回位置 + 日出日落月相 + 96 时隙表。date 为 null 时取当日。 */
    public Map<String, Object> getSunInfo(String ip, LocalDate date) {
        String[] loc = resolveLocation(ip);
        double lat = Double.parseDouble(loc[0]);
        double lng = Double.parseDouble(loc[1]);
        String tzId = loc[2];
        ZoneId tz = ZoneId.of(tzId);
        LocalDate today = date != null ? date : LocalDate.now(tz);

        Map<String, Object> data = new HashMap<>();
        data.put("lat", Math.round(lat * 100) / 100.0);
        data.put("lng", Math.round(lng * 100) / 100.0);
        data.put("timezone", tz.getId());
        data.put("date", today.toString());

        // 日出日落
        Map<String, String> sun = SolarUtil.sunTimes(lat, lng, today, tz);
        data.putAll(sun);

        // 月相 + 月出月落
        double phase = SolarUtil.moonPhase(today);
        data.put("moonPhase", phase);
        Map<String, String> moon = SolarUtil.moonTimes(lat, lng, today, tz);
        data.putAll(moon);

        // 96 时隙表(按日期缓存)
        String slotsKey = SLOTS_PREFIX + today;
        String cached = redis.opsForValue().get(slotsKey);
        if (cached != null) {
            try {
                data.put("slots", mapper.readValue(cached, List.class));
                return data;
            } catch (Exception ignored) {}
        }
        List<Map<String, Object>> slots = SolarUtil.buildSlots(lat, lng, today, tz);
        data.put("slots", slots);
        try {
            redis.opsForValue().set(slotsKey, mapper.writeValueAsString(slots), 12, TimeUnit.HOURS);
        } catch (Exception ignored) {}

        return data;
    }

    /** 兼容旧调用 */
    public Map<String, Object> getSunInfo(String ip) {
        return getSunInfo(ip, null);
    }

    /** IP → [lat, lng, timezone](Redis 缓存 6h) */
    private String[] resolveLocation(String ip) {
        if (ip != null && (ip.equals("127.0.0.1") || ip.equals("0:0:0:0:0:0:0:1") || ip.startsWith("192.168."))) {
            ip = "";
        }
        String cacheKey = LOC_PREFIX + (ip != null ? ip : "local");
        String cached = redis.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                JsonNode node = mapper.readTree(cached);
                return new String[]{ String.valueOf(node.get("lat").asDouble()), String.valueOf(node.get("lng").asDouble()), node.get("tz").asText() };
            } catch (Exception ignored) {}
        }
        try {
            String url = "http://ip-api.com/json/" + ip + "?fields=status,lat,lon,timezone";
            JsonNode resp = RestClient.create().get().uri(url).retrieve().body(JsonNode.class);
            if (resp != null && resp.has("lat")) {
                String lat = String.valueOf(resp.get("lat").asDouble());
                String lng = String.valueOf(resp.get("lon").asDouble());
                String tz = resp.has("timezone") ? resp.get("timezone").asText() : "Asia/Shanghai";
                Map<String, Object> loc = Map.of("lat", Double.parseDouble(lat), "lng", Double.parseDouble(lng), "tz", tz);
                redis.opsForValue().set(cacheKey, mapper.writeValueAsString(loc), 6, TimeUnit.HOURS);
                return new String[]{ lat, lng, tz };
            }
        } catch (Exception e) {
            log.warn("IP 定位失败,使用默认济南: {}", e.getMessage());
        }
        return new String[]{ "36.6512", "117.1201", "Asia/Shanghai" };
    }
}
