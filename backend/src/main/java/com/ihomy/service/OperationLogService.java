package com.ihomy.service;

import com.ihomy.entity.SysOperationLog;
import com.ihomy.mapper.SysOperationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 操作日志落库服务:异步保存,失败仅告警不影响主业务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogService {

    private final SysOperationLogMapper sysOperationLogMapper;

    /** 异步插入日志,失败降级为告警日志 */
    @Async
    public void save(SysOperationLog logEntry) {
        try {
            sysOperationLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.warn("操作日志保存失败: {}", e.getMessage());
        }
    }
}
