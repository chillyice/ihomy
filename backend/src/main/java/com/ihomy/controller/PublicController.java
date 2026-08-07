package com.ihomy.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.Result;
import com.ihomy.common.ResultCode;
import com.ihomy.entity.Album;
import com.ihomy.entity.Family;
import com.ihomy.entity.HomeModule;
import com.ihomy.entity.Photo;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.AlbumMapper;
import com.ihomy.mapper.FamilyMapper;
import com.ihomy.mapper.HomeModuleMapper;
import com.ihomy.mapper.PhotoMapper;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.ActivityFeedService;
import com.ihomy.service.HomeStatsService;
import com.ihomy.service.MultiFamilyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @Operation(summary = "首页聚合（支持 ?hid= 混淆ID / ?home_id= 指定家庭）")
    @GetMapping("/home")
    public Result<Map<String, Object>> home(@RequestParam(name = "hid", required = false) String hid,
                                            @RequestParam(name = "home_id", required = false) Long homeId) {
        SysUser user = securityHelper.currentUser();
        Long currentFamily = user != null ? user.getFamilyId() : null;

        Family family;
        if (StringUtils.hasText(hid)) {
            // 混淆 ID 优先:按 share_token 精确查询,防遍历
            family = familyMapper.selectOne(
                    new LambdaQueryWrapper<Family>().eq(Family::getShareToken, hid.trim()));
            if (family == null) {
                throw new BizException(ResultCode.NOT_FOUND);
            }
        } else if (homeId != null) {
            family = familyMapper.selectById(homeId);
            if (family == null) {
                throw new BizException(ResultCode.NOT_FOUND);
            }
        } else {
            family = currentFamily != null ? familyMapper.selectById(currentFamily) : familyMapper.selectDefault();
            if (family == null) {
                throw new BizException(ResultCode.NOT_FOUND);
            }
        }
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

        Family family;
        if (StringUtils.hasText(hid)) {
            family = familyMapper.selectOne(
                    new LambdaQueryWrapper<Family>().eq(Family::getShareToken, hid.trim()));
            if (family == null) {
                throw new BizException(ResultCode.NOT_FOUND);
            }
            if (family.getIsPublic() == null || family.getIsPublic() != 1) {
                throw new BizException(ResultCode.NOT_FOUND);
            }
        } else if (homeId != null) {
            family = familyMapper.selectById(homeId);
            if (family == null) {
                throw new BizException(ResultCode.NOT_FOUND);
            }
            if (family.getIsPublic() == null || family.getIsPublic() != 1) {
                throw new BizException(ResultCode.NOT_FOUND);
            }
        } else {
            family = currentFamily != null ? familyMapper.selectById(currentFamily) : familyMapper.selectDefault();
            if (family == null) {
                throw new BizException(ResultCode.NOT_FOUND);
            }
        }
        return Result.success(activityFeedService.getFeed(family.getId(), limit, true));
    }
}
