package com.ihomy.service.backup;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BackupTableSpecTest {

    @Test
    void contentPrefixClassification() {
        assertThat(BackupTableSpec.isContentTable("content_photo")).isTrue();
        assertThat(BackupTableSpec.isContentTable("report_weather")).isTrue();
        assertThat(BackupTableSpec.isContentTable("game_info")).isTrue();
        assertThat(BackupTableSpec.isContentTable("family_book_record")).isFalse();
        assertThat(BackupTableSpec.isContentTable("sys_user")).isFalse();
    }

    @Test
    void tierSelection() {
        assertThat(BackupTableSpec.includeInDump("sys_user", BackupKind.BASIC)).isTrue();
        assertThat(BackupTableSpec.includeInDump("content_photo", BackupKind.BASIC)).isFalse();
        assertThat(BackupTableSpec.includeInDump("content_photo", BackupKind.CONTENT)).isTrue();
        assertThat(BackupTableSpec.includeInDump("sys_user", BackupKind.CONTENT)).isFalse();
        assertThat(BackupTableSpec.includeInDump("family_book_record", BackupKind.FULL)).isTrue();
    }

    @Test
    void operationalTablesNeverDumped() {
        assertThat(BackupTableSpec.includeInDump("sys_backup_job", BackupKind.FULL)).isFalse();
        assertThat(BackupTableSpec.includeInDump("sys_password_reset_token", BackupKind.MIGRATION)).isFalse();
    }

    @Test
    void familyFilterUsesFamilyColumnFirst() {
        var filter = BackupTableSpec.familyFilter("family_book_record", Set.of("id", "family_id"));
        assertThat(filter).isNotNull();
        assertThat(filter.where()).isEqualTo("`family_id` = ?");
        assertThat(filter.params()).isEqualTo(1);
    }

    @Test
    void familyFilterSpecialCases() {
        assertThat(BackupTableSpec.familyFilter("sys_family_info", Set.of("id")).where()).isEqualTo("`id` = ?");
        assertThat(BackupTableSpec.familyFilter("sys_user_pref", Set.of("user_id")).params()).isEqualTo(1);
        assertThat(BackupTableSpec.familyFilter("sys_user_group_member", Set.of("group_id")).params()).isEqualTo(1);
        assertThat(BackupTableSpec.familyFilter("sys_chat_dm_message", Set.of("sender_id", "recipient_id")).params())
                .isEqualTo(2);
    }

    @Test
    void familyExportSkipsGlobalSeedsAndUnrelatedTables() {
        assertThat(BackupTableSpec.familyFilter("sys_role", Set.of("id"))).isNull();
        assertThat(BackupTableSpec.familyFilter("sys_parameter", Set.of("name"))).isNull();
        assertThat(BackupTableSpec.familyFilter("report_system", Set.of("id"))).isNull();
        assertThat(BackupTableSpec.familyFilter("sys_backup_job", Set.of("family_id"))).isNull();
    }
}
