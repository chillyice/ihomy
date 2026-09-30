package com.ihomy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.entity.IotConfig;
import com.ihomy.entity.IotData;
import com.ihomy.mapper.IotConfigMapper;
import com.ihomy.mapper.IotDataMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 智能家居采集调度:
 * 每分钟轮询一次所有已启用家庭的 Home Assistant,拉全量实体状态入库(具体解析见 {@link HomeAssistantService});
 * 每晚清理 90 天前的设备历史(温湿度一类 10 分钟一条,家庭规模数据量小)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IotSyncService {

    private static final int RETENTION_DAYS = 90;

    private final IotConfigMapper configMapper;
    private final IotDataMapper dataMapper;
    private final HomeAssistantService homeAssistantService;

    /** 单个家庭同步失败只记 last_error,不中断其余家庭(fixedDelay 避免上一轮没跑完又叠一轮) */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void syncAll() {
        List<IotConfig> cfgs = configMapper.selectList(new LambdaQueryWrapper<IotConfig>()
                .eq(IotConfig::getEnabled, 1)
                .isNotNull(IotConfig::getBaseUrl));
        for (IotConfig cfg : cfgs) {
            try {
                homeAssistantService.sync(cfg);
            } catch (Exception e) {
                log.warn("iot sync unexpected failure, familyId={}", cfg.getFamilyId(), e);
            }
        }
    }

    @Scheduled(cron = "0 40 4 * * *")
    public void purgeOldSamples() {
        int n = dataMapper.delete(new LambdaQueryWrapper<IotData>()
                .lt(IotData::getCreatedAt, LocalDateTime.now().minusDays(RETENTION_DAYS)));
        if (n > 0) {
            log.info("iot history purged, rows={}, olderThanDays={}", n, RETENTION_DAYS);
        }
    }
}
