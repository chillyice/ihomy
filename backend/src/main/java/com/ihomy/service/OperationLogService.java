package com.ihomy.service;

import com.ihomy.entity.SysOperationLog;
import com.ihomy.mapper.SysOperationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogService {

    private final SysOperationLogMapper sysOperationLogMapper;

    @Async
    public void save(SysOperationLog logEntry) {
        try {
            sysOperationLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.warn("操作日志保存失败: {}", e.getMessage());
        }
    }
}
