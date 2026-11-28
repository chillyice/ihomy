package com.ihomy.garden.service;

import com.ihomy.garden.mapper.GardenLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 日志清理(架构分册 §15.2,与回收站 03:00 清理同批):garden_log 保留 180 天,超期热度行分批物理删。
 * 只清 WATER/SUN/FERTILIZE/GIFT(已全部落 stat 快照);STAGE_ADVANCE / TASK_CLAIM / SKILL_ALLOCATE /
 * SKILL_RESET / PLANT / HARVEST 是标记与记忆行,豁免(详细设计《猫与日志留存》§3.1)。
 * 判定禁读日志的铁律由此成立:XP/阶段/领取/成就不再依赖被清的行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GardenLogCleanup {

    private static final int BATCH = 1000;
    private static final int MAX_ROUNDS = 1000;

    private final GardenLogMapper logMapper;

    @Value("${garden.log-cleanup.enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "0 30 3 * * ?")
    public void daily() {
        if (!enabled) {
            return;
        }
        int removed = run();
        if (removed > 0) {
            log.info("[garden] log cleanup removed {} expired rows", removed);
        }
    }

    /** 清理一次(可复用于验证):返回删除行数 */
    public int run() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(180);
        int total = 0;
        int rounds = 0;
        int deleted;
        do {
            deleted = logMapper.cleanupExpired(cutoff, BATCH);
            total += deleted;
            rounds++;
        } while (deleted >= BATCH && rounds < MAX_ROUNDS);
        return total;
    }
}
