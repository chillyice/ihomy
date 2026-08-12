package com.ihomy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 天气服务:和风天气免费 API(IP 定位城市 + 3 天预报)。
 * Key 未配置时 getWeather 返回 null,前端降级为只按时间做光影。
 * Redis 缓存 30 分钟避免频繁请求。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherService {

    @Value("${app.weather-key:}")
    private String weatherKey;

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String CACHE_KEY = "ihomy:weather";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    /** 返回 {condition, temp, text};Key 未配/请求失败返回 null */
    public Map<String, Object> getWeather(String clientIp) {
        if (weatherKey == null || weatherKey.isBlank()) {
            return null;
        }
        String cached = redis.opsForValue().get(CACHE_KEY);
        if (cached != null) {
            try { return mapper.readValue(cached, Map.class); } catch (Exception ignored) {}
        }
        try {
            // 1. IP 定位城市
            String cityUrl = "https://geoapi.qweather.com/v2/city/lookup?location=" + (clientIp == null ? "" : clientIp) + "&key=" + weatherKey;
            JsonNode cityResp = RestClient.create().get().uri(cityUrl).retrieve().body(JsonNode.class);
            if (cityResp == null || !cityResp.has("location") || cityResp.get("location").size() == 0) {
                return null;
            }
            String cityId = cityResp.get("location").get(0).get("id").asText();

            // 2. 当前天气
            String weatherUrl = "https://devapi.qweather.com/v7/weather/now?location=" + cityId + "&key=" + weatherKey;
            JsonNode weatherResp = RestClient.create().get().uri(weatherUrl).retrieve().body(JsonNode.class);
            if (weatherResp == null || !weatherResp.has("now")) {
                return null;
            }
            JsonNode now = weatherResp.get("now");
            String code = now.get("code").asText();
            String text = now.get("text").asText();
            int temp = Integer.parseInt(now.get("temp").asText());

            Map<String, Object> data = new HashMap<>();
            data.put("condition", codeToCondition(code));
            data.put("temp", temp);
            data.put("text", text);
            redis.opsForValue().set(CACHE_KEY, mapper.writeValueAsString(data), CACHE_TTL);
            return data;
        } catch (Exception e) {
            log.warn("weather fetch failed: {}", e.getMessage());
            return null;
        }
    }

    /** 和风天气 code 映射到内部 condition(供前端粒子层用) */
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
