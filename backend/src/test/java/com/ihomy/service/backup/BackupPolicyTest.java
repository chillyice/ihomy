package com.ihomy.service.backup;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BackupPolicyTest {

    @Test
    void defaultsAreValid() {
        BackupPolicy p = new BackupPolicy().normalized();
        assertThat(p.validate()).isEmpty();
        assertThat(p.getBasic().getCron()).isEqualTo("0 30 2 * * *");
        assertThat(p.getContent().getCron()).isEqualTo("0 30 3 * * *");
        assertThat(p.getFull().getCron()).isEqualTo("0 30 4 * * 0");
        assertThat(p.getRetention().getFamilyDays()).isEqualTo(30);
    }

    @Test
    void blankCronFallsBackToDefault() {
        BackupPolicy p = new BackupPolicy();
        p.getBasic().setCron("   ");
        p.normalized();
        assertThat(p.getBasic().getCron()).isNotBlank();
        assertThat(p.validate()).isEmpty();
    }

    @Test
    void invalidCronIsRejected() {
        BackupPolicy p = new BackupPolicy();
        p.getBasic().setCron("每天凌晨");
        assertThat(p.validate()).anyMatch(e -> e.contains("基础数据"));
    }

    @Test
    void retentionRangeIsChecked() {
        BackupPolicy p = new BackupPolicy();
        p.getRetention().setBasicDaily(0);
        p.getRetention().setFullWeekly(99);
        assertThat(p.validate()).hasSize(2);
    }

    @Test
    void partialPolicyIsNormalized() {
        BackupPolicy p = new BackupPolicy();
        p.setRetention(null);
        p.setBasic(null);
        p.normalized();
        assertThat(p.getRetention()).isNotNull();
        assertThat(p.getBasic().getCron()).isEqualTo("0 30 2 * * *");
    }
}
