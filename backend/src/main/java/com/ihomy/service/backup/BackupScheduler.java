package com.ihomy.service.backup;

import com.ihomy.common.BizException;
import com.ihomy.entity.SysBackupJob;
import com.ihomy.entity.SysBackupPolicy;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

/**
 * 定时备份调度器:启动时读取 sys_backup_policy 中启用的策略,每条策略按自己的 cron 注册任务;
 * 策略增删改后调用 reload() 取消旧任务再按新配置注册。
 *
 * <p>独立单线程调度器(线程名 ihomy-backup),避免与业务 @Scheduled 抢同一个默认调度池。
 * 任务体只负责 create + 异步执行,真正的转储在工作线程里跑。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BackupScheduler {

    private final BackupPolicyService policyService;
    private final BackupService backupService;
    private final BackupJobRunner runner;

    private final Map<Long, ScheduledFuture<?>> futures = new HashMap<>();
    private ThreadPoolTaskScheduler scheduler;
    private ScheduledFuture<?> purgeFuture;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ihomy-backup-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.initialize();
        try {
            backupService.markInterrupted();
            reload();
        } catch (Exception e) {
            // 升级后忘了跑 migrations 时策略表不存在:这时不注册任何备份任务,业务照常启动
            log.error("backup table not ready, scheduled backup disabled until restart", e);
            return;
        }
        // 每天 05:00 独立清理一次:即使某天没跑备份,过期文件也能按时释放
        purgeFuture = scheduler.schedule(() -> {
            try {
                backupService.purgeByRetention();
            } catch (Exception e) {
                log.error("daily backup retention failed", e);
            }
        }, new CronTrigger("0 0 5 * * *"));
        log.info("backup scheduler started");
    }

    /** 策略变更后重建 cron 任务 */
    public synchronized void reload() {
        if (scheduler == null) return;
        futures.values().forEach(f -> f.cancel(false));
        futures.clear();
        var policies = policyService.listEnabled();
        for (SysBackupPolicy policy : policies) {
            schedule(policy);
        }
        log.info("backup scheduler reloaded: {} enabled policies", policies.size());
    }

    private void schedule(SysBackupPolicy policy) {
        String cron = policyService.cron(policy);
        if (cron == null) {
            log.warn("skip backup policy id={} name={}: invalid schedule", policy.getId(), policy.getName());
            return;
        }
        try {
            ScheduledFuture<?> future = scheduler.schedule(() -> trigger(policy.getId()), new CronTrigger(cron));
            futures.put(policy.getId(), future);
        } catch (Exception e) {
            log.error("register backup policy failed id={} name={} cron={}",
                    policy.getId(), policy.getName(), cron, e);
        }
    }

    private void trigger(Long policyId) {
        try {
            SysBackupPolicy policy = policyService.mustGet(policyId);
            if (Boolean.FALSE.equals(policy.getEnabled())) return;
            SysBackupJob job = backupService.createPolicy(policy, null, "SCHEDULED");
            runner.run(job.getId());
        } catch (BizException e) {
            // 已有手动任务在跑/磁盘不足:跳过这一轮,下一轮照常
            log.warn("scheduled backup skipped policy={}: {}", policyId, e.getMessage());
        } catch (Exception e) {
            log.error("scheduled backup failed policy={}", policyId, e);
        }
    }

    @PreDestroy
    public void stop() {
        if (purgeFuture != null) purgeFuture.cancel(false);
        futures.values().forEach(f -> f.cancel(false));
        futures.clear();
        if (scheduler != null) scheduler.shutdown();
    }
}
