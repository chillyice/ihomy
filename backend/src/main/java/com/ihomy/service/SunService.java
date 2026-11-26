package com.ihomy.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ihomy.common.SolarUtil;
import com.moodysalem.TimezoneMapper;
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
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 太阳信息服务:地点与时区 → 五分钟太阳轨迹、日月与晨昏时刻。
 * Redis 缓存 6 小时(IP→位置) + 当日缓存(时隙表)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SunService {

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String LOC_PREFIX = "ihomy:sun:loc:";
    private static final Set<String> KNOWN_ZONES = ZoneId.getAvailableZoneIds();
    private static final String SLOTS_PREFIX = "ihomy:sun:slots:v3:";
    /** 每日月相列表天数(天气页「日月与晨昏」卡片的月相条) */
    private static final int MOON_DAYS = 7;

    /** 主入口:位置、日月信息与地点当地日的太阳轨迹。date 为 null 时取当日。 */
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
        data.put("nextMidnight", SolarUtil.solarSlot(lat, lng, today.plusDays(1).atStartOfDay(tz), 0));
        if (familyLocation != null && familyLocation.length > 2 && familyLocation[2] != null) {
            data.put("city", familyLocation[2]);
        }

        // 当前太阳位置与直射点:供前端「晨昏地图」画地球晨昏线(纯天文计算,变化快故不缓存)
        Instant now = Instant.now();
        data.put("generatedAt", now.toString());
        data.put("subsolar", SolarUtil.subsolarPoint(now));
        data.put("current", SolarUtil.sunPosition(lat, lng, now));

        // 日月与晨昏时刻(纯天文计算,不经任何天气 API):日出日落/太阳正午子夜 + 民用航海天文三档晨昏 + 月出月落/月中天月下中天
        data.putAll(SolarUtil.astroTimes(lat, lng, today, tz));

        // 月相:当前时刻的相位/8 档代码/照明比例(含向后兼容的 moonPhase 0~1 数值)
        data.putAll(SolarUtil.moonPhaseInfo(now));

        // 每日月相列表:逐日取当地正午为代表时刻,避免跨午夜跳档
        List<Map<String, Object>> moonDays = new ArrayList<>(MOON_DAYS);
        for (int i = 0; i < MOON_DAYS; i++) {
            LocalDate d = today.plusDays(i);
            Map<String, Object> md = new LinkedHashMap<>(SolarUtil.moonPhaseInfo(d.atTime(12, 0).atZone(tz).toInstant()));
            md.put("fxDate", d.toString());
            moonDays.add(md);
        }
        data.put("moonDays", moonDays);

        // 五分钟时隙以地点当地时间编号。
        String slotsKey = SLOTS_PREFIX + today + ":" + tzId + ":" + Math.round(lat * 100) + ":" + Math.round(lng * 100);
        String cached = redis.opsForValue().get(slotsKey);
        if (cached != null) {
            try {
                data.putAll(mapper.readValue(cached, new tools.jackson.core.type.TypeReference<Map<String, Object>>() {}));
                return data;
            } catch (Exception ignored) {}
        }
        List<Map<String, Object>> slots = SolarUtil.buildSlots(lat, lng, today, tz);
        Map<String, Object> trajectory = new HashMap<>();
        trajectory.put("slots", slots);
        if (slots.size() != 288) {
            trajectory.put("timeline", slots);
            // Existing clients index wall times directly, including clock gaps and repeats.
            List<Map<String, Object>> wallSlots = new ArrayList<>(288);
            for (int i = 0; i < 288; i++) {
                var wallTime = today.atStartOfDay().plusMinutes(i * 5L);
                var sample = new HashMap<>(SolarUtil.solarSlot(lat, lng, wallTime.atZone(tz), i));
                sample.put("time", wallTime.toLocalTime().toString());
                wallSlots.add(sample);
            }
            trajectory.put("slots", wallSlots);
        }
        data.putAll(trajectory);
        try {
            redis.opsForValue().set(slotsKey, mapper.writeValueAsString(trajectory), 12, TimeUnit.HOURS);
        } catch (Exception ignored) {}

        return data;
    }

    /** IP → [lat, lng, timezone](Redis 缓存 6h);familyLocation 非空时优先使用 */
    private String[] resolveLocation(String ip, String[] familyLocation) {
        // 家庭设置的位置偏好优先
        if (familyLocation != null && familyLocation.length >= 2
                && familyLocation[0] != null && familyLocation[1] != null) {
            double lat = Double.parseDouble(familyLocation[0]);
            double lng = Double.parseDouble(familyLocation[1]);
            String zone = TimezoneMapper.tzNameAt(lat, lng);
            // The Chinese city catalogue and its forecasts use civil Beijing time in Xinjiang.
            if ("Asia/Urumqi".equals(zone)) zone = "Asia/Shanghai";
            return new String[]{ familyLocation[0], familyLocation[1], zone != null && KNOWN_ZONES.contains(zone) ? zone : "UTC" };
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
