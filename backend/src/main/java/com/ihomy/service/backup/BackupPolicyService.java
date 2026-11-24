package com.ihomy.service.backup;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.dto.BackupPolicyDTO;
import com.ihomy.entity.SysBackupJob;
import com.ihomy.entity.SysBackupPolicy;
import com.ihomy.mapper.SysBackupJobMapper;
import com.ihomy.mapper.SysBackupPolicyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 备份策略读写(sys_backup_policy):列表、新建、编辑、删除、模块目录与执行情况。
 *
 * <p>策略是实例级配置,只有 OPS 能改;改完由 {@link BackupScheduler#reload()} 重新注册 cron。
 * 执行记录仍落 sys_backup_job,策略列表的「最近执行」由 {@code selectLatestJobs} 一次取回。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupPolicyService {

    private final SysBackupPolicyMapper policyMapper;
    private final SysBackupJobMapper jobMapper;

    public List<SysBackupPolicy> list() {
        return policyMapper.selectList(new LambdaQueryWrapper<SysBackupPolicy>()
                .orderByAsc(SysBackupPolicy::getId));
    }

    public List<SysBackupPolicy> listEnabled() {
        return policyMapper.selectList(new LambdaQueryWrapper<SysBackupPolicy>()
                .eq(SysBackupPolicy::getEnabled, true)
                .orderByAsc(SysBackupPolicy::getId));
    }

    public SysBackupPolicy mustGet(Long id) {
        SysBackupPolicy policy = id == null ? null : policyMapper.selectById(id);
        if (policy == null) throw new BizException(ResultCode.NOT_FOUND, "备份策略不存在");
        return policy;
    }

    public SysBackupPolicy create(BackupPolicyDTO dto, Long operatorId) {
        SysBackupPolicy policy = new SysBackupPolicy();
        policy.setCreatedBy(operatorId);
        policy.setCreatedAt(LocalDateTime.now());
        apply(policy, dto);
        policyMapper.insert(policy);
        log.info("backup policy created id={} name={} modules={}", policy.getId(), policy.getName(), policy.getModules());
        return policy;
    }

    public SysBackupPolicy update(Long id, BackupPolicyDTO dto) {
        SysBackupPolicy policy = mustGet(id);
        apply(policy, dto);
        policy.setUpdatedAt(LocalDateTime.now());
        policyMapper.updateById(policy);
        log.info("backup policy updated id={} name={} modules={}", id, policy.getName(), policy.getModules());
        return policy;
    }

    public void delete(Long id) {
        SysBackupPolicy policy = mustGet(id);
        Long running = jobMapper.selectCount(new LambdaQueryWrapper<SysBackupJob>()
                .eq(SysBackupJob::getPolicyId, id)
                .eq(SysBackupJob::getStatus, BackupStatus.RUNNING.name()));
        if (running != null && running > 0) {
            throw new BizException(ResultCode.CONFLICT, "该策略正在执行备份,请等任务结束后再删除");
        }
        policyMapper.deleteById(id);
        log.info("backup policy deleted id={} name={}", id, policy.getName());
    }

    /** 策略的 cron;配置不完整时返回 null(不注册定时任务) */
    public String cron(SysBackupPolicy policy) {
        if (policy == null) return null;
        try {
            return BackupSchedule.cron(policy.getScheduleType(), policy.getScheduleTime(), policy.getScheduleDay());
        } catch (IllegalArgumentException e) {
            log.warn("invalid backup policy schedule id={} type={} time={} day={}",
                    policy.getId(), policy.getScheduleType(), policy.getScheduleTime(), policy.getScheduleDay());
            return null;
        }
    }

    public LocalDateTime nextRun(SysBackupPolicy policy, LocalDateTime now) {
        if (policy == null || Boolean.FALSE.equals(policy.getEnabled())) return null;
        return BackupSchedule.next(cron(policy), now == null ? LocalDateTime.now() : now);
    }

    /** 策略列表(含最近一次执行与下次执行),一次查询取回所有最新任务 */
    public List<Map<String, Object>> views() {
        List<SysBackupPolicy> policies = list();
        if (policies.isEmpty()) return List.of();
        List<Long> ids = policies.stream().map(SysBackupPolicy::getId).toList();
        Map<Long, SysBackupJob> latest = new HashMap<>();
        for (SysBackupJob job : policyMapper.selectLatestJobs(ids)) {
            latest.putIfAbsent(job.getPolicyId(), job);
        }
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> out = new ArrayList<>(policies.size());
        for (SysBackupPolicy policy : policies) {
            out.add(view(policy, latest.get(policy.getId()), now));
        }
        return out;
    }

    public Map<String, Object> view(SysBackupPolicy policy, SysBackupJob lastJob, LocalDateTime now) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", policy.getId());
        m.put("name", policy.getName());
        m.put("modules", BackupModule.parse(policy.getModules()));
        m.put("scheduleType", policy.getScheduleType());
        m.put("scheduleTime", policy.getScheduleTime());
        m.put("scheduleDay", policy.getScheduleDay());
        m.put("retentionDays", policy.getRetentionDays());
        m.put("enabled", !Boolean.FALSE.equals(policy.getEnabled()));
        m.put("cron", cron(policy));
        LocalDateTime next = nextRun(policy, now);
        m.put("nextRunAt", BackupSchedule.format(next));
        m.put("lastJob", lastJob == null ? null : brief(lastJob));
        return m;
    }

    /** 模块目录:前端按 code 显示本地化名称,这里给表数量与是否含上传文件 */
    public List<Map<String, Object>> moduleCatalog() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (BackupModule module : BackupModule.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", module.name());
            m.put("tableCount", module.tables().size());
            m.put("withFiles", module.withFiles());
            out.add(m);
        }
        return out;
    }

    private Map<String, Object> brief(SysBackupJob job) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", job.getId());
        m.put("status", job.getStatus());
        m.put("triggerType", job.getTriggerType());
        m.put("startedAt", BackupSchedule.format(job.getStartedAt()));
        m.put("finishedAt", BackupSchedule.format(job.getFinishedAt()));
        m.put("durationMs", job.getDurationMs());
        m.put("sizeBytes", job.getSizeBytes());
        m.put("fileCount", job.getFileCount());
        m.put("dbRowCount", job.getDbRowCount());
        m.put("message", job.getMessage());
        return m;
    }

    private void apply(SysBackupPolicy policy, BackupPolicyDTO dto) {
        List<String> errors = new ArrayList<>();
        String name = dto.getName() == null ? "" : dto.getName().trim();
        if (name.isEmpty()) errors.add("策略名称不能为空");
        if (name.length() > 60) errors.add("策略名称不能超过 60 个字符");

        List<String> modules = new ArrayList<>();
        if (dto.getModules() != null) {
            for (String raw : dto.getModules()) {
                if (raw == null || raw.isBlank()) {
                    errors.add("备份模块不能为空");
                    continue;
                }
                String code = raw.trim().toUpperCase(Locale.ROOT);
                if (BackupModule.find(code) == null) {
                    errors.add("未知的备份模块:" + code);
                } else if (!modules.contains(code)) {
                    modules.add(code);
                }
            }
        }
        if (modules.isEmpty()) errors.add("至少选择一个备份模块");

        String type = BackupSchedule.normalizeType(dto.getScheduleType());
        String time = BackupSchedule.normalizeTime(dto.getScheduleTime());
        Integer day = BackupSchedule.normalizeDay(type, dto.getScheduleDay());
        errors.addAll(BackupSchedule.validate(type, time, day, dto.getRetentionDays()));

        if (!errors.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, String.join(";", errors));
        }

        policy.setName(name);
        policy.setModules(BackupModule.csv(BackupModule.normalize(modules)));
        policy.setScheduleType(type);
        policy.setScheduleTime(time);
        policy.setScheduleDay(day);
        policy.setRetentionDays(dto.getRetentionDays());
        policy.setEnabled(dto.getEnabled() == null || dto.getEnabled());
        policy.setUpdatedAt(LocalDateTime.now());
    }
}
