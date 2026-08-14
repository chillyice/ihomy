package com.ihomy.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.Result;
import com.ihomy.common.ResultCode;
import com.ihomy.entity.Family;
import com.ihomy.entity.HomeModule;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.AlbumMapper;
import com.ihomy.mapper.FamilyMapper;
import com.ihomy.mapper.HomeModuleMapper;
import com.ihomy.mapper.PhotoMapper;
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
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 公开访问接口:首页聚合与动态流。
 * 家庭定位优先级 hid(混淆 share_token) > home_id > 当前家庭/默认演示家庭;
 * 非成员仅能读到公开内容,私有家庭一律 NOT_FOUND。
 */
@Tag(name = "公开访问")
@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
public class PublicController {

    private final FamilyMapper familyMapper;
    private final HomeModuleMapper homeModuleMapper;
    private final PhotoMapper photoMapper;
    private final AlbumMapper albumMapper;
    private final ActivityFeedService activityFeedService;
    private final HomeStatsService homeStatsService;
    private final SecurityHelper securityHelper;
    private final MultiFamilyService multiFamilyService;
    private final WeatherService weatherService;
    private final SunService sunService;

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
        return Result.success(sunService.getSunInfo(ip, ld));
    }

    @Operation(summary = "天气(IP 定位,和风天气 API,Key 未配返回 null)")
    @GetMapping("/weather")
    public Result<Map<String, Object>> weather(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
        return Result.success(weatherService.getWeather(ip));
    }

    @Operation(summary = "天气详情聚合(当前+7d预报+24h+预警+指数+空气+分钟降水,Key 未配返回 null)")
    @GetMapping("/weather/detail")
    public Result<Map<String, Object>> weatherDetail(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
        return Result.success(weatherService.getDetail(ip));
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
            data.put("photos", photoMapper.selectLatestByFamily(familyId, 20));
            data.put("stats", homeStatsService.getStats(familyId));
        } else {
            data.put("photos", photoMapper.selectPublicByFamily(familyId, 20));
            data.put("stats", new HashMap<String, Object>());
        }

        return Result.success(data);
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
}
