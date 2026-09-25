package com.ihomy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ihomy.common.SolarUtil;
import com.ihomy.common.ThirdPartyHttp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 太阳信息服务:IP 定位(lat/lng/timezone)→ NOAA/Meeus 算法计算 96 时隙太阳位置、日月与晨昏时刻。
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
    /** 每日月相列表天数(天气页「日月与晨昏」卡片的月相条) */
    private static final int MOON_DAYS = 7;

    /** 主入口:返回位置 + 日出日落/三档晨昏/月出月落月相 + 96 时隙表。date 为 null 时取当日。familyLocation 非空时优先使用。 */
    public Map<String, Object> getSunInfo(String ip, LocalDate date, String[] familyLocation) {
        String[] loc = resolveLocation(ip, familyLocation);
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
        if (familyLocation != null && familyLocation.length > 2 && familyLocation[2] != null) {
            data.put("city", familyLocation[2]);
        }

        // 日月与晨昏时刻(纯天文计算,不经任何天气 API):日出日落/太阳正午子夜 + 民用航海天文三档晨昏 + 月出月落/月中天月下中天
        data.putAll(SolarUtil.astroTimes(lat, lng, today, tz));

        // 月相:当前时刻的相位/8 档代码/照明比例(含向后兼容的 moonPhase 0~1 数值)
        data.putAll(SolarUtil.moonPhaseInfo(Instant.now()));

        // 每日月相列表:逐日取当地正午为代表时刻,避免跨午夜跳档
        List<Map<String, Object>> moonDays = new ArrayList<>(MOON_DAYS);
        for (int i = 0; i < MOON_DAYS; i++) {
            LocalDate d = today.plusDays(i);
            Map<String, Object> md = new LinkedHashMap<>(SolarUtil.moonPhaseInfo(d.atTime(12, 0).atZone(tz).toInstant()));
            md.put("fxDate", d.toString());
            moonDays.add(md);
        }
        data.put("moonDays", moonDays);

        // 96 时隙表(按日期+坐标缓存)
        String slotsKey = SLOTS_PREFIX + today + ":" + Math.round(lat * 100) + ":" + Math.round(lng * 100);
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

    /** IP → [lat, lng, timezone](Redis 缓存 6h);familyLocation 非空时优先使用 */
    private String[] resolveLocation(String ip, String[] familyLocation) {
        // 家庭设置的位置偏好优先
        if (familyLocation != null && familyLocation.length >= 2
                && familyLocation[0] != null && familyLocation[1] != null) {
            return new String[]{ familyLocation[0], familyLocation[1], "Asia/Shanghai" };
        }
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
            JsonNode resp = mapper.readTree(ThirdPartyHttp.get("ipapi", url, null, 8000).body());
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
