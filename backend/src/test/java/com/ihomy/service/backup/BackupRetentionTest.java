package com.ihomy.service.backup;

import com.ihomy.entity.SysBackupJob;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BackupRetentionTest {

    private static SysBackupJob job(long id, String startedAt) {
        SysBackupJob job = new SysBackupJob();
        job.setId(id);
        job.setStartedAt(LocalDateTime.parse(startedAt));
        return job;
    }

    @Test
    void alwaysKeepsNewestEvenWithoutGenerations() {
        List<SysBackupJob> jobs = List.of(
                job(3, "2026-11-16T03:00:00"),
                job(2, "2026-11-15T03:00:00"),
                job(1, "2026-11-14T03:00:00"));
        var selection = BackupRetention.select(jobs, 0, 0, 0);
        assertThat(selection.keep()).containsExactly(3L);
        assertThat(selection.purge()).containsExactly(2L, 1L);
    }

    @Test
    void keepsNewestPerDayWeekAndMonth() {
        List<SysBackupJob> jobs = new ArrayList<>();
        // 每天两次,连续 10 天
        long id = 100;
        for (int d = 15; d >= 6; d--) {
            jobs.add(job(id--, String.format("2026-11-%02dT03:00:00", d)));
            jobs.add(job(id--, String.format("2026-11-%02dT09:00:00", d)));
        }
        var selection = BackupRetention.select(jobs, 3, 2, 1);
        // 最新一天(11-15 09:00)永远保留;日代 3 天(15/14/13),周代再补上一周(11-09 09:00),月代与日代重合
        assertThat(selection.keep()).containsExactly(100L, 98L, 96L, 86L);
        assertThat(selection.purge()).doesNotContain(100L);
        assertThat(selection.keep().size() + selection.purge().size()).isEqualTo(jobs.size());
    }

    @Test
    void newerJobInSameDayWins() {
        List<SysBackupJob> jobs = List.of(
                job(7, "2026-11-16T09:00:00"),
                job(6, "2026-11-16T03:00:00"),
                job(5, "2026-11-15T03:00:00"));
        var selection = BackupRetention.select(jobs, 1, 0, 0);
        assertThat(selection.keep()).contains(7L);
        assertThat(selection.keep()).doesNotContain(6L);
    }

    @Test
    void familySelectionKeepsNewestAndCapsCount() {
        List<SysBackupJob> jobs = List.of(
                job(9, "2026-11-16T09:00:00"),
                job(8, "2026-11-10T09:00:00"),
                job(7, "2026-10-01T09:00:00"),
                job(6, "2026-09-01T09:00:00"));
        var selection = BackupRetention.selectFamily(jobs, 2, 30, LocalDateTime.parse("2026-11-16T12:00:00"));
        assertThat(selection.keep()).containsExactly(9L, 8L);
        assertThat(selection.purge()).containsExactly(7L, 6L);
    }

    @Test
    void familySelectionAlwaysKeepsNewestEvenIfExpired() {
        List<SysBackupJob> jobs = List.of(job(9, "2020-01-01T09:00:00"));
        var selection = BackupRetention.selectFamily(jobs, 3, 30, LocalDateTime.parse("2026-11-16T12:00:00"));
        assertThat(selection.keep()).containsExactly(9L);
        assertThat(selection.purge()).isEmpty();
    }
}
