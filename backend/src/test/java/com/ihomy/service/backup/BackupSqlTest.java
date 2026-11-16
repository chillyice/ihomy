package com.ihomy.service.backup;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class BackupSqlTest {

    @Test
    void escapesQuotesAndBackslashes() {
        assertThat(BackupSql.quote("a'b\\c\n\r\0\u001a"))
                .isEqualTo("'a\\'b\\\\c\\n\\r\\0\\Z'");
        assertThat(BackupSql.quote(null)).isEqualTo("NULL");
        assertThat(BackupSql.quote("")).isEqualTo("''");
    }

    @Test
    void formatsNumbersAndBooleans() {
        assertThat(BackupSql.literal(12)).isEqualTo("12");
        assertThat(BackupSql.literal(true)).isEqualTo("1");
        assertThat(BackupSql.literal(false)).isEqualTo("0");
        assertThat(BackupSql.literal(null)).isEqualTo("NULL");
    }

    @Test
    void binaryUsesHexLiteral() {
        assertThat(BackupSql.literal(new byte[]{0, 1, (byte) 0xFF})).isEqualTo("0x0001FF");
    }

    @Test
    void timestampsDropMeaninglessFraction() {
        assertThat(BackupSql.literal(Timestamp.valueOf("2026-11-16 02:30:00")))
                .isEqualTo("'2026-11-16 02:30:00'");
        assertThat(BackupSql.literal(LocalDate.of(2026, 11, 16))).isEqualTo("'2026-11-16'");
        assertThat(BackupSql.literal(LocalDateTime.of(2026, 11, 16, 2, 30)))
                .isEqualTo("'2026-11-16 02:30'");
    }

    @Test
    void bitTypeIsNormalized() {
        assertThat(BackupSql.literal(new byte[]{0}, java.sql.Types.BIT)).isEqualTo("0");
        assertThat(BackupSql.literal(new byte[]{1}, java.sql.Types.BIT)).isEqualTo("1");
    }
}
