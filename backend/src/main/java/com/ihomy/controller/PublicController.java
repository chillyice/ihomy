package com.ihomy.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ihomy.common.BizException;
import com.ihomy.common.Result;
import com.ihomy.common.ResultCode;
import com.ihomy.entity.Family;
import com.ihomy.entity.HomeModule;
import com.ihomy.entity.SysUser;
import com.ihomy.entity.WeatherLocation;
import com.ihomy.mapper.FamilyMapper;
import com.ihomy.mapper.HomeModuleMapper;
import com.ihomy.mapper.PhotoMapper;
import com.ihomy.mapper.WeatherLocationMapper;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.ActivityFeedService;
import com.ihomy.service.HomeStatsService;
import com.ihomy.service.MultiFamilyService;
import com.ihomy.service.SunService;
import com.ihomy.service.WeatherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 公开访问接口:首页聚合与动态流。
 * 家庭定位优先级 hid(混淆 share_token) > home_id > 当前家庭/默认演示家庭;
 * 非成员仅能读到公开内容,私有家庭一律 NOT_FOUND。
 *
 * 性能:/public/home 每访客最多 7 次 DB(family+modules+photos+stats×3+currentUser),
 * 整包按 familyId 缓存到 Redis 5min,模块/相册/家庭设置变更时主动失效。
 * 缓存 key 形式:ihomy:home:pub:{familyId}(只缓存非成员视图,成员视图数据更敏感不缓存)
 */
@Slf4j
@Tag(name = "公开访问")
@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
public class PublicController {

    private static final Duration HOME_TTL = Duration.ofMinutes(5);
    private static final String KEY_HOME_PUBLIC = "ihomy:home:pub:";

    private final FamilyMapper familyMapper;
    private final HomeModuleMapper homeModuleMapper;
    private final PhotoMapper photoMapper;
    private final WeatherLocationMapper weatherLocationMapper;
    private final ActivityFeedService activityFeedService;
    private final HomeStatsService homeStatsService;
    private final com.ihomy.service.SignedUrlService signedUrlService;
    private final SecurityHelper securityHelper;
    private final MultiFamilyService multiFamilyService;
    private final WeatherService weatherService;
    private final SunService sunService;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    @Operation(summary = "太阳信息(IP 定位日出日落 + 96 时隙太阳位置,可选 date=YYYY-MM-DD 模拟任意日期)")
    @GetMapping("/sun-info")
    public Result<Map<String, Object>> sunInfo(HttpServletRequest request,
            @RequestParam(value = "date", required = false) String date) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
        LocalDate ld = null;
        if (date != null && !date.isBlank()) {
            try { ld = LocalDate.parse(date); } catch (Exception ignored) {}
        }
        return Result.success(sunService.getSunInfo(ip, ld, resolveFamilyLocation()));
    }

    @Operation(summary = "天气(IP 定位或家庭偏好位置,和风天气 API,Key 未配返回 null)")
    @GetMapping("/weather")
    public Result<Map<String, Object>> weather(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
        return Result.success(weatherService.getWeather(ip, resolveFamilyLocation()));
    }

    @Operation(summary = "天气详情聚合(当前+7d预报+24h+预警+指数+空气+分钟降水,Key 未配返回 null)")
    @GetMapping("/weather/detail")
    public Result<Map<String, Object>> weatherDetail(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
        return Result.success(weatherService.getDetail(ip, resolveFamilyLocation()));
    }

    /** 解析当前家庭的天气位置偏好(未登录或未设置返回 null,走 IP 定位) */
    private String[] resolveFamilyLocation() {
        try {
            SysUser user = securityHelper.currentUser();
            if (user == null || user.getFamilyId() == null) return null;
            Family f = familyMapper.selectById(user.getFamilyId());
            if (f != null && f.getWeatherLat() != null && f.getWeatherLng() != null) {
                return new String[]{ f.getWeatherLat().toPlainString(), f.getWeatherLng().toPlainString(), f.getWeatherCity() };
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Operation(summary = "首页聚合（支持 ?hid= 混淆ID / ?home_id= 指定家庭）")
    @GetMapping("/home")
    public Result<Map<String, Object>> home(@RequestParam(name = "hid", required = false) String hid,
                                            @RequestParam(name = "home_id", required = false) Long homeId) {
        SysUser user = securityHelper.currentUser();
        Long currentFamily = user != null ? user.getFamilyId() : null;

        Family family = resolveFamily(hid, homeId, currentFamily);
        Long familyId = family.getId();

        boolean member = user != null && (currentFamily != null && currentFamily.equals(familyId)
                || multiFamilyService.isMember(user.getId(), familyId));
        if (!member) {
            if (family.getIsPublic() == null || family.getIsPublic() != 1) {
                throw new BizException(ResultCode.NOT_FOUND);
            }
        }

        // 非成员视图(访客):走整包 Redis 缓存,5min TTL,模块/相册/家庭设置变更时主动失效
        if (!member) {
            String key = KEY_HOME_PUBLIC + familyId;
            try {
                String cached = redis.opsForValue().get(key);
                if (cached != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = mapper.readValue(cached, Map.class);
                    return Result.success(data);
                }
            } catch (Exception e) {
                log.warn("read home cache failed fid={}, fallback to DB", familyId, e);
            }
            Map<String, Object> data = buildHomeData(family, member, familyId);
            try {
                redis.opsForValue().set(key, mapper.writeValueAsString(data), HOME_TTL);
            } catch (Exception e) {
                log.warn("write home cache failed fid={}", familyId, e);
            }
            return Result.success(data);
        }

        // 成员视图:不缓存(数据敏感 + 频繁更新),直接组装
        return Result.success(buildHomeData(family, member, familyId));
    }

    /** 组装首页聚合数据:family + isMember + modules + photos + stats */
    private Map<String, Object> buildHomeData(Family family, boolean member, Long familyId) {
        Map<String, Object> data = new HashMap<>();
        data.put("family", family);
        data.put("isMember", member);
        data.put("isDemo", family.getIsDemo() != null && family.getIsDemo() == 1);

        LambdaQueryWrapper<HomeModule> qw = new LambdaQueryWrapper<>();
        qw.and(w -> w.isNull(HomeModule::getFamilyId)
                    .or().eq(HomeModule::getFamilyId, familyId))
           .eq(HomeModule::getEnabled, 1)
           .orderByAsc(HomeModule::getPosition)
           .orderByAsc(HomeModule::getSortOrder);
        data.put("modules", homeModuleMapper.selectList(qw));

        if (member) {
            data.put("photos", resolvePhotoUrls(photoMapper.selectLatestByFamily(familyId, 20)));
            data.put("stats", homeStatsService.getStats(familyId));
        } else {
            data.put("photos", resolvePhotoUrls(photoMapper.selectLatestPublicByFamily(familyId, 20)));
            data.put("stats", new HashMap<String, Object>());
        }
        return data;
    }

    /** 影子照片的 storage:// 逻辑地址 → 签名中转 URL */
    private List<com.ihomy.entity.Photo> resolvePhotoUrls(List<com.ihomy.entity.Photo> photos) {
        for (com.ihomy.entity.Photo p : photos) {
            p.setUrl(signedUrlService.resolve(p.getUrl()));
        }
        return photos;
    }

    /** 失效某家庭的公开首页缓存(供模块/相册/家庭设置变更时调用) */
    public void invalidateHomeCache(Long familyId) {
        if (familyId != null) {
            redis.delete(KEY_HOME_PUBLIC + familyId);
        }
    }

    @Operation(summary = "公开动态流（支持 ?hid= 混淆ID / ?home_id= 指定家庭）")
    @GetMapping("/feed")
    public Result<List<Map<String, Object>>> feed(@RequestParam(defaultValue = "10") int limit,
                                                  @RequestParam(name = "hid", required = false) String hid,
                                                  @RequestParam(name = "home_id", required = false) Long homeId) {
        SysUser user = securityHelper.currentUser();
        Long currentFamily = user != null ? user.getFamilyId() : null;

        Family family = resolveFamily(hid, homeId, currentFamily);
        // 显式指定家庭时仅公开家庭可读,不指定则跟随当前家庭/默认演示家庭
        if (StringUtils.hasText(hid) || homeId != null) {
            if (family.getIsPublic() == null || family.getIsPublic() != 1) {
                throw new BizException(ResultCode.NOT_FOUND);
            }
        }
        return Result.success(activityFeedService.getFeed(family.getId(), limit, true, null, false));
    }

    /** 家庭定位:hid(混淆 share_token) > home_id > 当前家庭/默认家庭;定位不到 404 */
    private Family resolveFamily(String hid, Long homeId, Long currentFamily) {
        if (StringUtils.hasText(hid)) {
            // 混淆 ID 优先:按 share_token 精确查询,防遍历
            Family f = familyMapper.selectOne(
                    new LambdaQueryWrapper<Family>().eq(Family::getShareToken, hid.trim()));
            if (f == null) {
                throw new BizException(ResultCode.NOT_FOUND);
            }
            return f;
        }
        if (homeId != null) {
            Family f = familyMapper.selectById(homeId);
            if (f == null) {
                throw new BizException(ResultCode.NOT_FOUND);
            }
            return f;
        }
        Family f = currentFamily != null ? familyMapper.selectById(currentFamily) : familyMapper.selectDefault();
        if (f == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return f;
    }

    @Operation(summary = "搜索和风天气地区(城市名模糊搜索,返回 id/名称/省份/经纬度)")
    @GetMapping("/weather/locations")
    public Result<List<WeatherLocation>> weatherLocations(@RequestParam String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Result.success(List.of());
        }
        List<WeatherLocation> list = weatherLocationMapper.selectList(
            new LambdaQueryWrapper<WeatherLocation>()
                .like(WeatherLocation::getName, keyword.trim())
                .last("LIMIT 20"));
        return Result.success(list);
    }
}
