package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.HealthMetric;
import com.ihomy.common.ResultCode;
import com.ihomy.entity.FamilyHealthSample;
import com.ihomy.entity.FamilyHealthSleep;
import com.ihomy.entity.FamilyHealthSleepStage;
import com.ihomy.entity.SysUser;
import com.ihomy.mapper.FamilyHealthSampleMapper;
import com.ihomy.mapper.FamilyHealthSleepMapper;
import com.ihomy.mapper.FamilyHealthSleepStageMapper;
import com.ihomy.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 健康数据读侧:概览 / 睡眠 / 分段 / 单指标趋势 / 首页摘要。
 * 可见性梯度:本人恒可读自己;OWNER 恒可读全家;其他成员需 health:view。
 * 敏感组(月经流量)一律仅本人可见——OWNER 也不返回他人的敏感指标行。
 * 跨人读取写查看审计(轻量表),本人读自己不记。不缓存(敏感数据)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthQueryService {

    private static final int MAX_SLEEP_RANGE_DAYS = 62;
    private static final int MAX_METRIC_RANGE_DAYS = 366;

    private final SysUserMapper userMapper;
    private final FamilyHealthSampleMapper sampleMapper;
    private final FamilyHealthSleepMapper sleepMapper;
    private final FamilyHealthSleepStageMapper stageMapper;
    private final HealthPrivacyService privacyService;

    /** 查看者上下文:由控制器从登录态/权限缓存拼出 */
    public record Viewer(Long id, Long familyId, String role, boolean canView, String ip) {
        public boolean owner() {
            return "OWNER".equals(role);
        }
    }

    // ==================== 单日概况 ====================

    public Map<String, Object> overview(Viewer viewer, Long targetUserId, LocalDate date) {
        SysUser target = requireTarget(viewer, targetUserId);
        LocalDate d = date == null ? LocalDate.now() : date;
        boolean self = Objects.equals(viewer.id(), target.getId());

        List<FamilyHealthSample> rows = sampleMapper.selectList(new LambdaQueryWrapper<FamilyHealthSample>()
                .eq(FamilyHealthSample::getUserId, target.getId())
                .ge(FamilyHealthSample::getStartTime, d.atStartOfDay())
                .lt(FamilyHealthSample::getStartTime, d.plusDays(1).atStartOfDay())
                .orderByDesc(FamilyHealthSample::getStartTime));
        // 一天内同指标取最新一条(DAY 行 start_time 固定 00:00,POINT 取最近)
        Map<String, FamilyHealthSample> picked = new LinkedHashMap<>();
        for (FamilyHealthSample r : rows) {
            if (HealthMetric.isSensitive(r.getMetric()) && !self) continue;
            picked.putIfAbsent(r.getMetric(), r);
        }
        List<Map<String, Object>> metrics = new ArrayList<>();
        picked.values().forEach(r -> metrics.add(sampleMap(r)));

        privacyService.logView(viewer.id(), viewer.ip(), target.getId(), viewer.familyId(),
                "OVERVIEW", null, d, d);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", target.getId());
        data.put("date", d);
        data.put("sleep", latestNightSleep(target.getId(), d));
        data.put("metrics", metrics);
        return data;
    }

    /** 昨夜睡眠:入睡日在前一天或当天、醒来最晚的一条(跨零点归前一天) */
    private Map<String, Object> latestNightSleep(Long userId, LocalDate date) {
        FamilyHealthSleep row = sleepMapper.selectOne(new LambdaQueryWrapper<FamilyHealthSleep>()
                .eq(FamilyHealthSleep::getUserId, userId)
                .between(FamilyHealthSleep::getSleepDate, date.minusDays(1), date)
                .orderByDesc(FamilyHealthSleep::getEndTime)
                .last("LIMIT 1"));
        return row == null ? null : sleepMap(row);
    }

    // ==================== 睡眠列表 ====================

    public Map<String, Object> sleep(Viewer viewer, Long targetUserId, LocalDate from, LocalDate to) {
        SysUser target = requireTarget(viewer, targetUserId);
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate begin = from == null ? end.minusDays(29) : from;
        if (begin.isAfter(end)) {
            LocalDate t = begin;
            begin = end;
            end = t;
        }
        if (ChronoUnit.DAYS.between(begin, end) > MAX_SLEEP_RANGE_DAYS) {
            throw new BizException(ResultCode.BAD_REQUEST, "单次最多查看 62 天睡眠数据");
        }
        List<FamilyHealthSleep> rows = sleepMapper.selectList(new LambdaQueryWrapper<FamilyHealthSleep>()
                .eq(FamilyHealthSleep::getUserId, target.getId())
                .between(FamilyHealthSleep::getSleepDate, begin, end)
                .orderByDesc(FamilyHealthSleep::getStartTime)
                .last("LIMIT 200"));
        List<Map<String, Object>> list = new ArrayList<>();
        rows.forEach(r -> list.add(sleepMap(r)));

        privacyService.logView(viewer.id(), viewer.ip(), target.getId(), viewer.familyId(),
                "SLEEP", null, begin, end);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", target.getId());
        data.put("from", begin);
        data.put("to", end);
        data.put("items", list);
        return data;
    }

    // ==================== 睡眠分段 ====================

    public Map<String, Object> stages(Viewer viewer, Long sleepId) {
        FamilyHealthSleep row = sleepMapper.selectById(sleepId);
        if (row == null || !Objects.equals(row.getFamilyId(), viewer.familyId())) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        requireTarget(viewer, row.getUserId());
        List<FamilyHealthSleepStage> stages = stageMapper.selectList(
                new LambdaQueryWrapper<FamilyHealthSleepStage>()
                        .eq(FamilyHealthSleepStage::getSleepId, sleepId)
                        .orderByAsc(FamilyHealthSleepStage::getStartTime));
        List<Map<String, Object>> list = new ArrayList<>();
        for (FamilyHealthSleepStage s : stages) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("stage", s.getStage());
            item.put("startTime", s.getStartTime());
            item.put("endTime", s.getEndTime());
            item.put("durationMin", s.getDurationMin());
            list.add(item);
        }

        privacyService.logView(viewer.id(), viewer.ip(), row.getUserId(), viewer.familyId(),
                "SLEEP", null, row.getSleepDate(), row.getSleepDate());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sleep", sleepMap(row));
        data.put("stages", list);
        return data;
    }

    // ==================== 单指标趋势 ====================

    public Map<String, Object> metric(Viewer viewer, Long targetUserId, String metric,
                                      LocalDate from, LocalDate to) {
        HealthMetric.Def def = HealthMetric.find(metric);
        if (def == null || HealthMetric.PERIOD_SESSION.equals(def.period())) {
            throw new BizException(ResultCode.NOT_FOUND, "指标不存在");
        }
        SysUser target = requireTarget(viewer, targetUserId);
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate begin = from == null ? end.minusDays(29) : from;
        if (begin.isAfter(end)) {
            LocalDate t = begin;
            begin = end;
            end = t;
        }
        if (ChronoUnit.DAYS.between(begin, end) > MAX_METRIC_RANGE_DAYS) {
            throw new BizException(ResultCode.BAD_REQUEST, "单次最多查看 366 天数据");
        }
        boolean self = Objects.equals(viewer.id(), target.getId());
        // 敏感组仅本人可见:对他人返回空列表(不报 403,避免探测「某人是否开了这个开关」)
        List<FamilyHealthSample> rows = (def.sensitive() && !self) ? List.of()
                : sampleMapper.selectList(new LambdaQueryWrapper<FamilyHealthSample>()
                .eq(FamilyHealthSample::getUserId, target.getId())
                .eq(FamilyHealthSample::getMetric, def.code())
                .ge(FamilyHealthSample::getStartTime, begin.atStartOfDay())
                .lt(FamilyHealthSample::getStartTime, end.plusDays(1).atStartOfDay())
                .orderByAsc(FamilyHealthSample::getStartTime)
                .last("LIMIT 2000"));
        List<Map<String, Object>> points = new ArrayList<>();
        rows.forEach(r -> points.add(sampleMap(r)));

        privacyService.logView(viewer.id(), viewer.ip(), target.getId(), viewer.familyId(),
                "METRIC", def.code(), begin, end);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", target.getId());
        data.put("metric", def.code());
        data.put("unit", def.unit());
        data.put("period", def.period());
        data.put("from", begin);
        data.put("to", end);
        data.put("points", points);
        return data;
    }

    // ==================== 首页摘要(只回本人) ====================

    public Map<String, Object> summary(Long userId, int days) {
        int n = Math.max(1, Math.min(days, 90));
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(n - 1L);

        List<FamilyHealthSleep> sleeps = sleepMapper.selectList(new LambdaQueryWrapper<FamilyHealthSleep>()
                .eq(FamilyHealthSleep::getUserId, userId)
                .between(FamilyHealthSleep::getSleepDate, from, to)
                .orderByAsc(FamilyHealthSleep::getSleepDate));
        Map<LocalDate, FamilyHealthSleep> sleepByDate = new HashMap<>();
        for (FamilyHealthSleep s : sleeps) {
            // 同一天多来源时取时长最长的(展示层标注来源)
            FamilyHealthSleep old = sleepByDate.get(s.getSleepDate());
            if (old == null || s.getTotalMin() > old.getTotalMin()) sleepByDate.put(s.getSleepDate(), s);
        }

        List<FamilyHealthSample> dayRows = sampleMapper.selectList(new LambdaQueryWrapper<FamilyHealthSample>()
                .eq(FamilyHealthSample::getUserId, userId)
                .eq(FamilyHealthSample::getPeriod, HealthMetric.PERIOD_DAY)
                .ge(FamilyHealthSample::getStartTime, from.atStartOfDay())
                .lt(FamilyHealthSample::getStartTime, to.plusDays(1).atStartOfDay()));
        Map<LocalDate, Map<String, FamilyHealthSample>> sampleByDate = new HashMap<>();
        for (FamilyHealthSample r : dayRows) {
            sampleByDate.computeIfAbsent(r.getStartTime().toLocalDate(), k -> new HashMap<>())
                    .put(r.getMetric(), r);
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", d);
            FamilyHealthSleep s = sleepByDate.get(d);
            item.put("sleepMin", s == null ? null : s.getTotalMin());
            item.put("deepMin", s == null ? null : s.getDeepMin());
            item.put("stageLevel", s == null ? null : s.getStageLevel());
            item.put("sleepSource", s == null ? null : s.getSource());
            Map<String, FamilyHealthSample> day = sampleByDate.getOrDefault(d, Map.of());
            item.put("steps", valueOf(day, "STEPS"));
            item.put("distance", valueOf(day, "DISTANCE"));
            item.put("activeEnergy", valueOf(day, "ACTIVE_ENERGY"));
            item.put("exerciseMin", valueOf(day, "EXERCISE_MIN"));
            item.put("restingHr", valueOf(day, "RESTING_HR"));
            list.add(item);
        }

        Map<String, Object> latest = new LinkedHashMap<>();
        latest.put("HEART_RATE", latestPoint(userId, "HEART_RATE"));
        latest.put("SPO2", latestPoint(userId, "SPO2"));
        latest.put("WEIGHT", latestPoint(userId, "WEIGHT"));

        boolean hasData = !sleeps.isEmpty() || !dayRows.isEmpty();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("hasData", hasData);
        data.put("days", n);
        data.put("items", list);
        data.put("latest", latest);
        return data;
    }

    private Object valueOf(Map<String, FamilyHealthSample> day, String metric) {
        FamilyHealthSample r = day.get(metric);
        return r == null ? null : r.getValueNum();
    }

    private Map<String, Object> latestPoint(Long userId, String metric) {
        FamilyHealthSample r = sampleMapper.selectOne(new LambdaQueryWrapper<FamilyHealthSample>()
                .eq(FamilyHealthSample::getUserId, userId)
                .eq(FamilyHealthSample::getMetric, metric)
                .orderByDesc(FamilyHealthSample::getStartTime)
                .last("LIMIT 1"));
        if (r == null) return null;
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("value", r.getValueNum());
        item.put("unit", r.getUnit());
        item.put("startTime", r.getStartTime());
        item.put("source", r.getSource());
        return item;
    }

    // ==================== 可见性 ====================

    /**
     * 目标成员校验:必须是本家庭成员(跨家庭一律 NOT_FOUND,避免探测别人家庭);
     * 看别人还需要 health:view(OWNER 恒真)。本人读自己不需要权限。
     */
    private SysUser requireTarget(Viewer viewer, Long targetUserId) {
        Long id = targetUserId == null ? viewer.id() : targetUserId;
        SysUser target = userMapper.selectById(id);
        if (target == null || Integer.valueOf(1).equals(target.getDeleted())
                || !Objects.equals(target.getFamilyId(), viewer.familyId())) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        if (!Objects.equals(id, viewer.id()) && !viewer.owner() && !viewer.canView()) {
            throw new BizException(ResultCode.FORBIDDEN, "没有查看家人健康数据的权限");
        }
        return target;
    }

    private Map<String, Object> sleepMap(FamilyHealthSleep r) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", r.getId());
        item.put("sleepDate", r.getSleepDate());
        item.put("kind", r.getKind());
        item.put("startTime", r.getStartTime());
        item.put("endTime", r.getEndTime());
        item.put("totalMin", r.getTotalMin());
        item.put("deepMin", r.getDeepMin());
        item.put("lightMin", r.getLightMin());
        item.put("remMin", r.getRemMin());
        item.put("awakeMin", r.getAwakeMin());
        item.put("stageLevel", r.getStageLevel());
        item.put("source", r.getSource());
        item.put("provider", r.getProvider());
        return item;
    }

    private Map<String, Object> sampleMap(FamilyHealthSample r) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("metric", r.getMetric());
        item.put("period", r.getPeriod());
        item.put("value", r.getValueNum());
        item.put("min", r.getValueMin());
        item.put("max", r.getValueMax());
        item.put("count", r.getSampleCount());
        item.put("unit", r.getUnit());
        item.put("startTime", r.getStartTime());
        item.put("endTime", r.getEndTime());
        item.put("source", r.getSource());
        item.put("provider", r.getProvider());
        return item;
    }
}
