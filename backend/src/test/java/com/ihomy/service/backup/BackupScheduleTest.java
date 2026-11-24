package com.ihomy.service.backup;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class BackupScheduleTest {

    @Test
    void dailyCron() {
        assertThat(BackupSchedule.cron("DAILY", "03:30", null)).isEqualTo("0 30 3 * * *");
        assertThat(BackupSchedule.cron("daily", "00:05", 3)).isEqualTo("0 5 0 * * *");
        assertThat(BackupSchedule.normalizeDay("DAILY", 5)).isNull();
    }

    @Test
    void weeklyCronUsesMondayFirst() {
        assertThat(BackupSchedule.cron("WEEKLY", "04:30", 1)).isEqualTo("0 30 4 * * 1");
        assertThat(BackupSchedule.cron("WEEKLY", "04:30", 7)).isEqualTo("0 30 4 * * 7");
        assertThat(BackupSchedule.cron("WEEKLY", "04:30", null)).isEqualTo("0 30 4 * * 7");
    }

    @Test
    void monthlyCron() {
        assertThat(BackupSchedule.cron("MONTHLY", "02:00", 1)).isEqualTo("0 0 2 1 * *");
        assertThat(BackupSchedule.cron("MONTHLY", "02:00", 28)).isEqualTo("0 0 2 28 * *");
        assertThat(BackupSchedule.cron("MONTHLY", "02:00", null)).isEqualTo("0 0 2 1 * *");
    }

    @Test
    void validationRejectsBadTimeAndDay() {
        assertThat(BackupSchedule.validate("DAILY", "25:00", null, null)).anyMatch(e -> e.contains("执行时间"));
        assertThat(BackupSchedule.validate("WEEKLY", "03:00", 8, null)).anyMatch(e -> e.contains("周一至周日"));
        assertThat(BackupSchedule.validate("MONTHLY", "03:00", 29, null)).anyMatch(e -> e.contains("1–28"));
        assertThat(BackupSchedule.validate("YEARLY", "03:00", null, null)).anyMatch(e -> e.contains("每天、每周或每月"));
        assertThat(BackupSchedule.validate("DAILY", "03:00", null, 0)).anyMatch(e -> e.contains("保留天数"));
        assertThat(BackupSchedule.validate("DAILY", "03:00", null, null)).isEmpty();
        assertThat(BackupSchedule.validate("WEEKLY", "03:00", 7, 30)).isEmpty();
        assertThat(BackupSchedule.validate("MONTHLY", "03:00", 28, 3650)).isEmpty();
    }

    @Test
    void nextRun() {
        assertThat(BackupSchedule.next("0 30 3 * * *", LocalDateTime.parse("2026-11-24T04:00:00")))
                .isEqualTo(LocalDateTime.parse("2026-11-25T03:30:00"));
        assertThat(BackupSchedule.next("broken", LocalDateTime.now())).isNull();
    }
}
