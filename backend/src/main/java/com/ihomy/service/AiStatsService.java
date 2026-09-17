package com.ihomy.service;

import com.ihomy.common.ReportAggregateUtil;
import com.ihomy.mapper.AiLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 用量报表(V9.72):按当前家庭聚合 report_ai 调用记录。
 * 与 WeatherService 共用 ReportAggregateUtil 的时间范围解析 + 时间桶零填充,
 * 返回 summary(汇总卡)/timeline(趋势折线)/typeDistribution(功能占比饼图)。
 */
@Service
@RequiredArgsConstructor
public class AiStatsService {

    private final AiLogMapper aiLogMapper;

    /** 汇总卡:总调用/失败/成功/成功率(全量,不限时间) */
    public Map<String, Object> summary(Long familyId) {
        List<Map<String, Object>> rows = aiLogMapper.selectSummary(familyId);
        long total = 0, failed = 0;
        for (Map<String, Object> r : rows) {
            total += ((Number) r.getOrDefault("total", 0)).longValue();
            failed += ((Number) r.getOrDefault("failed", 0)).longValue();
        }
        long success = total - failed;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", total);
        out.put("failed", failed);
        out.put("success", success);
        out.put("successRate", total == 0 ? 0 : Math.round(success * 1000.0 / total) / 10.0);
        return out;
    }

    /** 调用趋势折线:按时间桶聚合 total/failed,零填充覆盖全时间范围;features 为空=全部功能 */
    public List<Map<String, Object>> timeline(Long familyId, String range, List<String> features) {
        ReportAggregateUtil.RangeSpec spec = ReportAggregateUtil.resolveRange(range);
        List<Map<String, Object>> rows = aiLogMapper.selectTimeline(familyId, spec.start(), spec.end(), spec.fmt(), features);
        return ReportAggregateUtil.zeroFillTimeline(spec, rows);
    }

    /** 功能占比饼图:所选时间范围内各功能调用量 */
    public List<Map<String, Object>> typeDistribution(Long familyId, String range) {
        ReportAggregateUtil.RangeSpec spec = ReportAggregateUtil.resolveRange(range);
        return aiLogMapper.selectTypeDistribution(familyId, spec.start(), spec.end());
    }
}
