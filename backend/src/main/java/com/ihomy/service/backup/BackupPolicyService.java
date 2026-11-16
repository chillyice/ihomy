package com.ihomy.service.backup;

import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.service.ParameterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 备份策略读写:sys_parameter.name = backup.policy 存 JSON。
 * 策略是实例级配置,只有 OPS 能改;改完由 BackupScheduler.reload() 重新注册 cron。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupPolicyService {

    public static final String KEY = "backup.policy";
    private static final DateTimeFormatter HUMAN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ParameterService parameterService;
    private final ObjectMapper mapper;

    public BackupPolicy load() {
        try {
            String json = parameterService.getString(KEY);
            if (json != null && !json.isBlank()) {
                return mapper.readValue(json, BackupPolicy.class).normalized();
            }
        } catch (Exception e) {
            log.warn("read backup policy failed, fallback to defaults", e);
        }
        return new BackupPolicy().normalized();
    }

    public BackupPolicy save(BackupPolicy policy) {
        BackupPolicy normalized = policy == null ? new BackupPolicy() : policy;
        normalized.normalized();
        List<String> errors = normalized.validate();
        if (!errors.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, String.join(";", errors));
        }
        try {
            parameterService.put(KEY, mapper.writeValueAsString(normalized), "定时备份策略(运维页维护)");
        } catch (Exception e) {
            log.error("save backup policy failed", e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "备份策略保存失败");
        }
        return normalized;
    }

    /** 三种定时任务的下次执行时间(总开关关闭时返回空) */
    public Map<String, String> nextRuns(BackupPolicy policy) {
        Map<String, String> out = new LinkedHashMap<>();
        if (policy == null || !policy.isEnabled()) return out;
        LocalDateTime now = LocalDateTime.now();
        putNext(out, "BASIC", policy.getBasic(), now);
        putNext(out, "CONTENT", policy.getContent(), now);
        putNext(out, "FULL", policy.getFull(), now);
        return out;
    }

    private void putNext(Map<String, String> out, String key, BackupPolicy.Task task, LocalDateTime now) {
        if (task == null || !task.isEnabled() || task.getCron() == null || task.getCron().isBlank()) return;
        try {
            LocalDateTime next = CronExpression.parse(task.getCron().trim()).next(now);
            if (next != null) out.put(key, next.format(HUMAN));
        } catch (Exception e) {
            log.warn("invalid backup cron for {}: {}", key, task.getCron());
        }
    }
}
