package com.ihomy.service.backup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 备份异步执行器:@Async 必须跨 bean 调用才生效,业务体在 BackupService。
 * 手动/定时入口都通过这里把任务丢到虚拟线程池,HTTP 请求立即返回任务卡片。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BackupJobRunner {

    private final BackupService backupService;

    @Async
    public void run(Long jobId) {
        try {
            backupService.execute(jobId);
        } catch (Throwable t) {
            log.error("backup async task crashed id={}", jobId, t);
        }
    }
}
