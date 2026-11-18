package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.HealthMetric;
import com.ihomy.common.HealthRetention;
import com.ihomy.entity.FamilyHealthSample;
import com.ihomy.entity.FamilyHealthViewLog;
import com.ihomy.mapper.FamilyHealthSampleMapper;
import com.ihomy.mapper.FamilyHealthViewLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 健康数据保留清理(P3):每天 04:20 删超期的单点样本与查看审计。
 * 只删 period=POINT 的样本——日聚合、睡眠会话与睡眠分期永久保留(年度对比要靠它们);
 * 查看审计只保留 90 天(轻量表,只为纠纷定位)。
 * 与天气日志清理同款:不加分布式锁、不加事务,多实例同时跑也只是一起删同一批行(DELETE 幂等);
 * 这两张表没有 deleted 列,MP 的 delete 就是物理删除。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthRetentionService {

    private final FamilyHealthSampleMapper sampleMapper;
    private final FamilyHealthViewLogMapper viewLogMapper;

    @Value("${app.health.point-retention-days:180}")
    private int pointDays;

    @Value("${app.health.view-log-retention-days:90}")
    private int viewLogDays;

    @Scheduled(cron = "0 20 4 * * *")
    public void purgeExpired() {
        purge(LocalDateTime.now());
    }

    /** 按给定时点执行一次(便于运维手动触发与测试);返回 [样本条数, 审计条数] */
    public int[] purge(LocalDateTime now) {
        return new int[]{purgeSamples(now), purgeViewLogs(now)};
    }

    private int purgeSamples(LocalDateTime now) {
        LocalDateTime cutoff = HealthRetention.cutoff(now, pointDays);
        if (cutoff == null) {
            return 0;
        }
        try {
            int n = sampleMapper.delete(new LambdaQueryWrapper<FamilyHealthSample>()
                    .eq(FamilyHealthSample::getPeriod, HealthMetric.PERIOD_POINT)
                    .lt(FamilyHealthSample::getStartTime, cutoff));
            if (n > 0) {
                log.info("清理超期健康单点样本 {} 条(保留 {} 天)", n, pointDays);
            }
            return n;
        } catch (Exception e) {
            log.warn("清理超期健康单点样本失败: {}", e.getMessage());
            return 0;
        }
    }

    private int purgeViewLogs(LocalDateTime now) {
        LocalDateTime cutoff = HealthRetention.cutoff(now, viewLogDays);
        if (cutoff == null) {
            return 0;
        }
        try {
            int n = viewLogMapper.delete(new LambdaQueryWrapper<FamilyHealthViewLog>()
                    .lt(FamilyHealthViewLog::getCreatedAt, cutoff));
            if (n > 0) {
                log.info("清理超期健康查看审计 {} 条(保留 {} 天)", n, viewLogDays);
            }
            return n;
        } catch (Exception e) {
            log.warn("清理超期健康查看审计失败: {}", e.getMessage());
            return 0;
        }
    }
}
