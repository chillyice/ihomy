package com.ihomy.service.backup;

import com.ihomy.entity.SysBackupJob;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GFS(祖父-父-子)保留策略:按日/周/月分代,每代只留最新一份,取并集保留,其余清理。
 * 纯函数,输入须已按开始时间倒序且只含成功任务。
 */
public final class BackupRetention {

    private BackupRetention() {
    }

    public record Selection(Set<Long> keep, List<Long> purge) {
    }

    /** 实例级备份:日 + 周 + 月三代;最新一份永远保留 */
    public static Selection select(List<SysBackupJob> successDesc, int daily, int weekly, int monthly) {
        Set<Long> keep = new LinkedHashSet<>();
        if (successDesc == null || successDesc.isEmpty()) return new Selection(keep, List.of());
        keep.add(successDesc.get(0).getId());

        keep.addAll(pick(successDesc, daily, job -> job.getStartedAt().toLocalDate()));
        keep.addAll(pick(successDesc, weekly, job -> weekKey(job.getStartedAt().toLocalDate())));
        keep.addAll(pick(successDesc, monthly, job -> YearMonthKey.of(job.getStartedAt().toLocalDate())));

        List<Long> purge = new ArrayList<>();
        for (SysBackupJob job : successDesc) {
            if (!keep.contains(job.getId())) purge.add(job.getId());
        }
        return new Selection(keep, purge);
    }

    /**
     * 单家庭导出:最多保留 keep 份,且只保留最近 days 天内;最新一份永不自动清理。
     */
    public static Selection selectFamily(List<SysBackupJob> successDesc, int keepCount, int days, LocalDateTime now) {
        Set<Long> keep = new LinkedHashSet<>();
        if (successDesc == null || successDesc.isEmpty()) return new Selection(keep, List.of());
        keep.add(successDesc.get(0).getId());
        LocalDateTime cutoff = now.minusDays(Math.max(1, days));
        int counted = 0;
        for (SysBackupJob job : successDesc) {
            if (counted >= Math.max(1, keepCount)) break;
            counted++;
            if (job.getStartedAt() != null && job.getStartedAt().isAfter(cutoff)) {
                keep.add(job.getId());
            }
        }
        List<Long> purge = new ArrayList<>();
        for (SysBackupJob job : successDesc) {
            if (!keep.contains(job.getId())) purge.add(job.getId());
        }
        return new Selection(keep, purge);
    }

    /**
     * 策略备份:只保留最近 days 天内且不是最新一份的;最新一份永不自动清理。
     * days 来自策略的 retention_days,为空时调用方不应调用本方法。
     */
    public static Selection selectByDays(List<SysBackupJob> successDesc, int days, LocalDateTime now) {
        Set<Long> keep = new LinkedHashSet<>();
        if (successDesc == null || successDesc.isEmpty()) return new Selection(keep, List.of());
        keep.add(successDesc.get(0).getId());
        LocalDateTime cutoff = now.minusDays(Math.max(1, days));
        for (SysBackupJob job : successDesc) {
            if (job.getStartedAt() != null && !job.getStartedAt().isBefore(cutoff)) {
                keep.add(job.getId());
            }
        }
        List<Long> purge = new ArrayList<>();
        for (SysBackupJob job : successDesc) {
            if (!keep.contains(job.getId())) purge.add(job.getId());
        }
        return new Selection(keep, purge);
    }

    private static Set<Long> pick(List<SysBackupJob> jobs, int limit, BucketFn fn) {
        Set<Long> out = new LinkedHashSet<>();
        if (limit <= 0) return out;
        Map<Object, SysBackupJob> firstPerBucket = new LinkedHashMap<>();
        for (SysBackupJob job : jobs) {
            if (job.getStartedAt() == null) continue;
            firstPerBucket.putIfAbsent(fn.bucket(job), job);
        }
        int n = 0;
        for (SysBackupJob job : firstPerBucket.values()) {
            if (n++ >= limit) break;
            out.add(job.getId());
        }
        return out;
    }

    private static String weekKey(LocalDate date) {
        WeekFields iso = WeekFields.ISO;
        return date.get(iso.weekBasedYear()) + "-W" + date.get(iso.weekOfWeekBasedYear());
    }

    private interface BucketFn {
        Object bucket(SysBackupJob job);
    }

    private record YearMonthKey(int year, int month) {
        static YearMonthKey of(LocalDate date) {
            return new YearMonthKey(date.getYear(), date.getMonthValue());
        }
    }

    /** 测试/展示用:按某条规则算出保留下限,便于前端提示 */
    public static Set<Long> keepIds(List<SysBackupJob> jobs, int daily, int weekly, int monthly) {
        return new HashSet<>(select(jobs, daily, weekly, monthly).keep());
    }
}
