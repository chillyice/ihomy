package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.PointsRuleConst;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.PointsRuleDTO;
import com.ihomy.entity.PointsRule;
import com.ihomy.mapper.PointsRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 家庭级积分获取规则:family_points_rule 每家庭每功能一行(enabled 开关 + points 分值)。
 * 读高频写极低频,走内存缓存(ConcurrentHashMap 按 familyId 懒加载,写后 evict);
 * 未配置的功能读取时回退 PointsRuleConst 默认值(enabled=true + 常量默认分值),不落库。
 */
@Service
@RequiredArgsConstructor
public class PointsRuleService {

    private final PointsRuleMapper ruleMapper;

    /** 家庭级规则内存缓存(写后 remove;懒加载,不预热) */
    private final ConcurrentHashMap<Long, Map<String, PointsRule>> familyCache = new ConcurrentHashMap<>();

    public record Rule(boolean enabled, int points) {
    }

    /** 某功能生效规则:未配置回退默认(enabled=true + 常量默认分值);未知 code 视为关闭 */
    public Rule effective(Long familyId, String code) {
        PointsRuleConst.Feature f = PointsRuleConst.feature(code);
        if (f == null) {
            return new Rule(false, 0);
        }
        PointsRule r = configured(familyId).get(code);
        if (r == null) {
            return new Rule(true, f.defaultPoints());
        }
        boolean enabled = r.getEnabled() == null || r.getEnabled() == 1;
        int points = r.getPoints() == null ? f.defaultPoints() : r.getPoints();
        return new Rule(enabled, points);
    }

    /** 配置页列表:按常量顺序返回,附分组/默认值/是否可配分值 */
    public List<Map<String, Object>> list(Long familyId) {
        Map<String, PointsRule> cfg = configured(familyId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (PointsRuleConst.Feature f : PointsRuleConst.FEATURES) {
            Rule eff = effective(familyId, f.code());
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("featureCode", f.code());
            o.put("group", f.group());
            o.put("hasPoints", f.hasPoints());
            o.put("enabled", eff.enabled());
            o.put("points", eff.points());
            o.put("defaultPoints", f.defaultPoints());
            out.add(o);
        }
        return out;
    }

    /** 批量保存(只 upsert 请求中出现的项,其余保持不变);写后失效缓存 */
    public void save(Long familyId, List<PointsRuleDTO> body) {
        if (body == null) {
            return;
        }
        for (PointsRuleDTO dto : body) {
            if (dto == null || dto.getFeatureCode() == null || !PointsRuleConst.isValidFeature(dto.getFeatureCode())) {
                throw new BizException(ResultCode.BAD_REQUEST, "未知的积分功能");
            }
            upsert(familyId, dto.getFeatureCode(), dto.getEnabled(), dto.getPoints());
        }
        familyCache.remove(familyId);
    }

    private void upsert(Long familyId, String code, Boolean enabled, Integer points) {
        int en = enabled == null || enabled ? 1 : 0;
        int pts = points == null ? PointsRuleConst.feature(code).defaultPoints() : points;
        PointsRule r = ruleMapper.selectOne(new LambdaQueryWrapper<PointsRule>()
                .eq(PointsRule::getFamilyId, familyId)
                .eq(PointsRule::getFeatureCode, code));
        if (r == null) {
            r = new PointsRule();
            r.setFamilyId(familyId);
            r.setFeatureCode(code);
            r.setEnabled(en);
            r.setPoints(pts);
            ruleMapper.insert(r);
        } else {
            ruleMapper.update(null, new LambdaUpdateWrapper<PointsRule>()
                    .eq(PointsRule::getId, r.getId())
                    .set(PointsRule::getEnabled, en)
                    .set(PointsRule::getPoints, pts));
        }
    }

    private Map<String, PointsRule> configured(Long familyId) {
        if (familyId == null) {
            return Map.of();
        }
        return familyCache.computeIfAbsent(familyId, this::load);
    }

    private Map<String, PointsRule> load(Long familyId) {
        return ruleMapper.selectList(new LambdaQueryWrapper<PointsRule>()
                        .eq(PointsRule::getFamilyId, familyId)).stream()
                .collect(Collectors.toMap(PointsRule::getFeatureCode, r -> r, (a, b) -> a));
    }
}
