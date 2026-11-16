package com.ihomy.service.backup;

import com.ihomy.common.BizException;
import com.ihomy.entity.SysBackupJob;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

/**
 * 定时备份调度器:启动时读取 sys_parameter.backup.policy,按 cron 注册三种任务;
 * 策略保存后调用 reload() 取消旧任务再按新 cron 注册。
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

    private final Map<BackupKind, ScheduledFuture<?>> futures = new EnumMap<>(BackupKind.class);
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
        } catch (Exception e) {
            // 升级后忘了跑 migrations 时表不存在:这时不注册任何备份任务,业务照常启动
            log.error("backup table not ready, scheduled backup disabled until restart", e);
            return;
        }
        reload();
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
        BackupPolicy policy = policyService.load();
        if (!policy.isEnabled()) {
            log.info("backup scheduler disabled by policy");
            return;
        }
        schedule(BackupKind.BASIC, policy.getBasic());
        schedule(BackupKind.CONTENT, policy.getContent());
        schedule(BackupKind.FULL, policy.getFull());
        log.info("backup scheduler reloaded: basic={} content={} full={}",
                policy.getBasic().getCron(), policy.getContent().getCron(), policy.getFull().getCron());
    }

    private void schedule(BackupKind kind, BackupPolicy.Task task) {
        if (task == null || !task.isEnabled()) return;
        try {
            ScheduledFuture<?> future = scheduler.schedule(() -> trigger(kind), new CronTrigger(task.getCron().trim()));
            futures.put(kind, future);
        } catch (Exception e) {
            log.error("register backup cron failed kind={} cron={}", kind, task.getCron(), e);
        }
    }

    private void trigger(BackupKind kind) {
        try {
            SysBackupJob job = backupService.create(kind, BackupScope.INSTANCE, null, null, "SCHEDULED");
            runner.run(job.getId());
        } catch (BizException e) {
            // 已有手动任务在跑/磁盘不足:跳过这一轮,下一轮照常
            log.warn("scheduled backup skipped kind={}: {}", kind, e.getMessage());
        } catch (Exception e) {
            log.error("scheduled backup failed kind={}", kind, e);
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
