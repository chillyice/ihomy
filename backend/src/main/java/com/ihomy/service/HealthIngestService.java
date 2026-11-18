package com.ihomy.service;

import com.ihomy.common.BizException;
import com.ihomy.common.HealthMetric;
import com.ihomy.common.HealthSleepAggregator;
import com.ihomy.common.HealthTime;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.HealthIngestDTO;
import com.ihomy.entity.FamilyHealthSample;
import com.ihomy.entity.FamilyHealthSleep;
import com.ihomy.entity.FamilyHealthSleepStage;
import com.ihomy.mapper.FamilyHealthSampleMapper;
import com.ihomy.mapper.FamilyHealthSleepMapper;
import com.ihomy.mapper.FamilyHealthSleepStageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 健康数据上报管道(唯一写入口):归一 → 校验 → 样本 upsert / 睡眠整窗重算,单事务。
 * 校验顺序固定(计划 §4.2):白名单 → 敏感同意门槛 → 合理范围 → 未来时间 → 时区折算 → 落库。
 * 部分成功:不合法的条目单独拒收,合法条目照常入库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthIngestService {

    private static final int MAX_ITEMS = 2000;
    private static final int MAX_SPAN_DAYS = 31;
    private static final int MAX_DAILY_ROWS = 50000;
    private static final int MAX_REJECT_DETAIL = 20;
    /** 分期批量插入的分片大小 */
    private static final int STAGE_CHUNK = 500;

    private final FamilyHealthSampleMapper sampleMapper;
    private final FamilyHealthSleepMapper sleepMapper;
    private final FamilyHealthSleepStageMapper stageMapper;
    private final HealthPrivacyService privacyService;
    private final HealthTokenService tokenService;
    private final AuthGuardService authGuardService;
    private final StringRedisTemplate redis;

    @Transactional
    public Map<String, Object> ingest(HealthIngestDTO dto, Long userId, Long familyId,
                                      Long tokenId, String tokenPrefix, String clientIp) {
        long startMs = System.currentTimeMillis();
        if (familyId == null) throw new BizException(ResultCode.BAD_REQUEST, "当前账号还未加入家庭");

        String provider = dto.getProvider() == null ? "" : dto.getProvider().trim().toUpperCase(Locale.ROOT);
        if (!HealthMetric.PROVIDERS.contains(provider)) {
            throw new BizException(ResultCode.BAD_REQUEST, "不支持的上报来源");
        }
        ZoneOffset offset = HealthTime.parseOffset(dto.getTimezone());
        if (offset == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "时区格式不正确,请使用 +08:00 这样的形式");
        }
        List<HealthIngestDTO.Sample> samples = dto.getSamples() == null ? List.of() : dto.getSamples();
        List<HealthIngestDTO.SleepInterval> sleeps = dto.getSleep() == null ? List.of() : dto.getSleep();
        if (samples.size() + sleeps.size() > MAX_ITEMS) {
            throw new BizException(ResultCode.BAD_REQUEST, "单次最多上报 2000 条,请分批发送");
        }
        // 令牌限速:每令牌 60 次/小时(快捷指令可能被误配成每分钟跑)
        if (tokenId != null) {
            authGuardService.checkRate("health-ingest-token", "token-" + tokenId, 60, 3600);
        }
        checkSpan(sleeps, offset);

        LocalDateTime futureLimit = LocalDateTime.now().plusMinutes(10);
        boolean consent = privacyService.reproductiveConsent(userId);

        List<FamilyHealthSample> rows = new ArrayList<>();
        List<Map<String, Object>> rejectedDetail = new ArrayList<>();
        int rejected = 0;
        int skipped = 0;
        String firstReason = null;

        for (int i = 0; i < samples.size(); i++) {
            HealthIngestDTO.Sample s = samples.get(i);
            String reason = null;
            HealthMetric.Def def = HealthMetric.find(s.getMetric());
            if (def == null) {
                reason = "未知指标";
            } else if (def.sensitive() && !consent) {
                // 敏感组默认不采:整条跳过,不落库也不弹错(设置页统计里显示「已跳过 N 条」)
                skipped++;
                continue;
            }
            String period = s.getPeriod() == null ? "" : s.getPeriod().trim().toUpperCase(Locale.ROOT);
            if (reason == null) {
                if (HealthMetric.PERIOD_SESSION.equals(def.period())) {
                    reason = "睡眠请用 sleep 区间上报";
                } else if (!def.period().equals(period)) {
                    reason = "周期与指标不匹配";
                }
            }
            Double value = s.getAvg() != null ? s.getAvg() : s.getValue();
            if (reason == null) {
                if (value == null) reason = "缺少数值";
                else if (value < def.min() || value > def.max()) reason = "数值超出合理范围";
            }
            FamilyHealthSample row = reason == null ? buildSample(s, def, period, value, userId, familyId,
                    provider, offset, futureLimit) : null;
            if (row == null) {
                rejected++;
                if (reason == null) reason = "时间格式不正确";
                if (firstReason == null) firstReason = def == null ? reason : def.code() + ": " + reason;
                addDetail(rejectedDetail, i, s.getMetric(), reason);
                continue;
            }
            rows.add(row);
        }

        // 睡眠:按来源 App 分桶,各自聚合(不同 App 的同晚数据各留一行,展示层取最长一条)
        Map<String, List<HealthSleepAggregator.Interval>> byApp = new LinkedHashMap<>();
        Map<String, String> appSource = new HashMap<>();
        int acceptedIntervals = 0;
        for (int i = 0; i < sleeps.size(); i++) {
            HealthIngestDTO.SleepInterval it = sleeps.get(i);
            HealthTime.Stamp a = HealthTime.parseStamp(it.getStart(), offset);
            HealthTime.Stamp b = HealthTime.parseStamp(it.getEnd(), offset);
            String reason = null;
            if (a == null || b == null) reason = "时间格式不正确";
            else if (!b.wall().isAfter(a.wall())) reason = "结束时间必须晚于开始时间";
            else if (HealthTime.toServerZone(b.wall(), b.offset()).isAfter(futureLimit)) reason = "时间超出允许范围";
            if (reason != null) {
                rejected++;
                if (firstReason == null) firstReason = "睡眠: " + reason;
                addDetail(rejectedDetail, i, "SLEEP", reason);
                continue;
            }
            String stage = it.getStage() == null ? "UNKNOWN" : it.getStage().trim().toUpperCase(Locale.ROOT);
            if (!HealthMetric.SLEEP_STAGES.contains(stage)) stage = "UNKNOWN";
            String app = sourceApp(it.getSourceApp(), provider);
            byApp.computeIfAbsent(app, k -> new ArrayList<>())
                    .add(new HealthSleepAggregator.Interval(a.wall(), b.wall(), stage));
            appSource.putIfAbsent(app, HealthMetric.normalizeSource(it.getSource()));
            acceptedIntervals++;
        }

        int sessionCount = byApp.values().stream()
                .mapToInt(list -> HealthSleepAggregator.aggregate(list).size()).sum();
        checkDailyCap(userId, rows.size() + sessionCount);

        // 同一成员的重算串行化:两路上报同时到达时「整窗删 + 重插」会互相踩(计划 §4.4)
        String lockKey = "ihomy:health:lock:" + userId;
        String lockVal = String.valueOf(System.nanoTime());
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lockKey, lockVal, java.time.Duration.ofSeconds(60)))) {
            throw new BizException(ResultCode.TOO_MANY_REQUESTS, "上一次上报还在处理,请稍后重试");
        }
        int duplicated = 0;
        try {
            if (!rows.isEmpty()) sampleMapper.upsertBatch(rows);

            for (Map.Entry<String, List<HealthSleepAggregator.Interval>> e : byApp.entrySet()) {
                duplicated += rebuildSleep(userId, familyId, provider, offset,
                        e.getKey(), appSource.get(e.getKey()), e.getValue(), futureLimit);
            }
        } finally {
            // 只释放自己拿到的锁,避免把别人的重算解锁
            if (lockVal.equals(redis.opsForValue().get(lockKey))) redis.delete(lockKey);
        }

        if (tokenId != null) tokenService.markIngest(tokenId);

        int accepted = rows.size() + acceptedIntervals;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accepted", accepted);
        result.put("duplicated", duplicated);
        result.put("rejected", rejected);
        result.put("skipped", skipped);
        result.put("rejectedDetail", rejectedDetail);
        result.put("at", LocalDateTime.now());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("at", result.get("at"));
        summary.put("provider", provider);
        summary.put("accepted", accepted);
        summary.put("duplicated", duplicated);
        summary.put("rejected", rejected);
        summary.put("skipped", skipped);
        summary.put("firstReason", firstReason);
        tokenService.saveLastResult(userId, summary);

        log.info("health ingest uid={} provider={} accepted={} duplicated={} rejected={} skipped={} cost={}ms token={}",
                userId, provider, accepted, duplicated, rejected, skipped,
                System.currentTimeMillis() - startMs, tokenPrefix == null ? "-" : tokenPrefix);
        return result;
    }

    /** 构造一条样本行;不合规返回 null(调用方负责补拒收原因) */
    private FamilyHealthSample buildSample(HealthIngestDTO.Sample s, HealthMetric.Def def, String period,
                                            Double value, Long userId, Long familyId, String provider,
                                            ZoneOffset offset, LocalDateTime futureLimit) {
        FamilyHealthSample row = new FamilyHealthSample();
        row.setFamilyId(familyId);
        row.setUserId(userId);
        row.setMetric(def.code());
        row.setPeriod(def.period());
        row.setUnit(def.unit());
        row.setProvider(provider);
        row.setSource(HealthMetric.normalizeSource(s.getSource()));
        row.setSourceApp(sourceApp(s.getSourceApp(), provider));
        row.setValueNum(bd(value));

        if (HealthMetric.PERIOD_DAY.equals(def.period())) {
            LocalDate date = HealthTime.parseDate(s.getDate());
            if (date == null) return null;
            LocalDateTime start = date.atStartOfDay();
            if (start.isAfter(futureLimit)) return null;
            row.setStartTime(start);
            return row;
        }
        // POINT:优先单点 at;否则 start/end 区段(客户端小时聚合)
        String at = s.getAt() != null ? s.getAt() : (s.getStart() != null && s.getEnd() == null ? s.getStart() : null);
        if (at != null) {
            HealthTime.Stamp st = HealthTime.parseStamp(at, offset);
            if (st == null) return null;
            LocalDateTime start = HealthTime.toServerZone(st.wall(), st.offset());
            if (start.isAfter(futureLimit)) return null;
            row.setStartTime(start);
        } else {
            HealthTime.Stamp a = HealthTime.parseStamp(s.getStart(), offset);
            HealthTime.Stamp b = HealthTime.parseStamp(s.getEnd(), offset);
            if (a == null || b == null) return null;
            LocalDateTime start = HealthTime.toServerZone(a.wall(), a.offset());
            LocalDateTime end = HealthTime.toServerZone(b.wall(), b.offset());
            if (!end.isAfter(start)) return null;
            if (end.isAfter(futureLimit)) return null;
            row.setStartTime(start);
            row.setEndTime(end);
            row.setValueMin(s.getMin() == null ? null : bd(s.getMin()));
            row.setValueMax(s.getMax() == null ? null : bd(s.getMax()));
            row.setSampleCount(s.getCount());
        }
        // 单点也允许带 min/max/count(快捷指令小时聚合会同时给值)
        if (row.getValueMin() == null && s.getMin() != null) row.setValueMin(bd(s.getMin()));
        if (row.getValueMax() == null && s.getMax() != null) row.setValueMax(bd(s.getMax()));
        if (row.getSampleCount() == null && s.getCount() != null) row.setSampleCount(s.getCount());
        return row;
    }

    /**
     * 整窗重算睡眠:删掉与本次区间重叠的旧会话(含分期)再插新会话。
     * 厂商修正历史、同一晚重复上报都走这条路,幂等由「窗口替换」保证。
     *
     * @return 被替换掉的旧会话数(响应里的 duplicated,便于排障「覆盖了几条」)
     */
    private int rebuildSleep(Long userId, Long familyId, String provider, ZoneOffset offset,
                             String sourceApp, String source,
                             List<HealthSleepAggregator.Interval> intervals, LocalDateTime futureLimit) {
        List<HealthSleepAggregator.Session> sessions = HealthSleepAggregator.aggregate(intervals);
        if (sessions.isEmpty()) return 0;

        LocalDateTime from = sessions.get(0).start();
        LocalDateTime to = sessions.get(0).end();
        for (HealthSleepAggregator.Session sx : sessions) {
            if (sx.start().isBefore(from)) from = sx.start();
            if (sx.end().isAfter(to)) to = sx.end();
        }
        LocalDateTime serverFrom = HealthTime.toServerZone(from, offset);
        LocalDateTime serverTo = HealthTime.toServerZone(to, offset);

        List<Long> overlapping = sleepMapper.selectOverlappingIds(userId, sourceApp, serverFrom, serverTo);
        int replaced = 0;
        if (!overlapping.isEmpty()) {
            stageMapper.deleteBySleepIds(overlapping);
            replaced = sleepMapper.deleteByIds(overlapping);
        }

        LocalDateTime now = LocalDateTime.now();
        for (HealthSleepAggregator.Session sx : sessions) {
            FamilyHealthSleep row = new FamilyHealthSleep();
            row.setFamilyId(familyId);
            row.setUserId(userId);
            row.setSleepDate(sx.sleepDate());
            row.setKind(sx.kind());
            row.setStartTime(HealthTime.toServerZone(sx.start(), offset));
            row.setEndTime(HealthTime.toServerZone(sx.end(), offset));
            row.setTotalMin(sx.totalMin());
            row.setDeepMin(sx.deepMin());
            row.setLightMin(sx.lightMin());
            row.setRemMin(sx.remMin());
            row.setAwakeMin(sx.awakeMin());
            row.setStageLevel(sx.stageLevel());
            row.setSource(source == null ? "OTHER" : source);
            row.setSourceApp(sourceApp);
            row.setProvider(provider);
            row.setComputedAt(now);
            sleepMapper.insert(row);

            List<FamilyHealthSleepStage> stages = new ArrayList<>();
            for (HealthSleepAggregator.Interval it : sx.stages()) {
                FamilyHealthSleepStage st = new FamilyHealthSleepStage();
                st.setFamilyId(familyId);
                st.setUserId(userId);
                st.setSleepId(row.getId());
                st.setStage(it.stage());
                st.setStartTime(HealthTime.toServerZone(it.start(), offset));
                st.setEndTime(HealthTime.toServerZone(it.end(), offset));
                st.setDurationMin((int) Duration.between(it.start(), it.end()).toMinutes());
                stages.add(st);
            }
            for (int i = 0; i < stages.size(); i += STAGE_CHUNK) {
                stageMapper.insertBatch(stages.subList(i, Math.min(i + STAGE_CHUNK, stages.size())));
            }
        }
        return replaced;
    }

    /** 睡眠区间总跨度 ≤ 31 天(防止一次拉一年历史) */
    private void checkSpan(List<HealthIngestDTO.SleepInterval> sleeps, ZoneOffset offset) {
        LocalDateTime min = null, max = null;
        for (HealthIngestDTO.SleepInterval it : sleeps) {
            HealthTime.Stamp a = HealthTime.parseStamp(it.getStart(), offset);
            HealthTime.Stamp b = HealthTime.parseStamp(it.getEnd(), offset);
            if (a == null || b == null) continue;
            if (min == null || a.wall().isBefore(min)) min = a.wall();
            if (max == null || b.wall().isAfter(max)) max = b.wall();
        }
        if (min != null && max != null && Duration.between(min, max).toDays() > MAX_SPAN_DAYS) {
            throw new BizException(ResultCode.BAD_REQUEST, "单次睡眠数据跨度不能超过 31 天,请分段发送");
        }
    }

    /** 每用户每日入库上限(防死循环自动化把库写爆) */
    private void checkDailyCap(Long userId, int addRows) {
        if (addRows <= 0) return;
        String key = "ihomy:health:rows:" + userId + ":" + LocalDate.now();
        Long n = redis.opsForValue().increment(key, addRows);
        redis.expire(key, Duration.ofDays(2));
        if (n != null && n > MAX_DAILY_ROWS) {
            redis.opsForValue().decrement(key, addRows);
            throw new BizException(ResultCode.TOO_MANY_REQUESTS, "今日健康数据入库量已达上限,请稍后再试");
        }
    }

    private void addDetail(List<Map<String, Object>> detail, int index, String metric, String reason) {
        if (detail.size() >= MAX_REJECT_DETAIL) return;
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("index", index);
        item.put("metric", metric);
        item.put("reason", reason);
        detail.add(item);
    }

    private static String sourceApp(String raw, String provider) {
        String s = raw == null ? "" : raw.trim();
        if (!s.isEmpty()) return s.substring(0, Math.min(s.length(), 120));
        return switch (provider) {
            case "HEALTH_CONNECT" -> "HEALTH_CONNECT";
            case "IOS_NATIVE" -> "APPLE_HEALTH";
            default -> "APPLE_HEALTH";
        };
    }

    private static BigDecimal bd(Double v) {
        return v == null ? null : BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }
}
