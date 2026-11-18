package com.ihomy.common;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 健康数据 CSV 导出:字段转义(RFC 4180)、数值去零、BOM 与 CRLF。
 * 这些是 Excel / 脚本消费方唯一依赖的格式约定,改错会让导出的中文变乱码、含逗号的来源名串列。
 */
class HealthCsvTest {

    @Test
    void escapesOnlyWhenNeeded() {
        assertThat(HealthCsv.escape(null)).isEmpty();
        assertThat(HealthCsv.escape("")).isEmpty();
        assertThat(HealthCsv.escape("APPLE_HEALTH")).isEqualTo("APPLE_HEALTH");
        assertThat(HealthCsv.escape("com.garmin.android.app:id=1")).isEqualTo("com.garmin.android.app:id=1");
        assertThat(HealthCsv.escape("a,b")).isEqualTo("\"a,b\"");
        assertThat(HealthCsv.escape("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(HealthCsv.escape("line1\nline2")).isEqualTo("\"line1\nline2\"");
        assertThat(HealthCsv.escape("crlf\r\n")).isEqualTo("\"crlf\r\n\"");
    }

    @Test
    void joinsCellsAsOneRow() {
        String row = HealthCsv.line("SAMPLE", "HEART_RATE", "POINT", "72");
        assertThat(row).isEqualTo("SAMPLE,HEART_RATE,POINT,72");
        // 空串/null 都留空列,不会把后面的列挤位
        assertThat(HealthCsv.line("A", null, "", "D")).isEqualTo("A,,,D");
        assertThat(HealthCsv.line("x,y", "", "z")).isEqualTo("\"x,y\",,z");
    }

    @Test
    void formatsTimeDateAndNumbers() {
        assertThat(HealthCsv.time(LocalDateTime.of(2026, 11, 18, 7, 5, 9))).isEqualTo("2026-11-18 07:05:09");
        assertThat(HealthCsv.time(null)).isEmpty();
        assertThat(HealthCsv.date(LocalDate.of(2026, 11, 8))).isEqualTo("2026-11-08");
        assertThat(HealthCsv.date(null)).isEmpty();
        assertThat(HealthCsv.num(new BigDecimal("72.00"))).isEqualTo("72");
        assertThat(HealthCsv.num(new BigDecimal("36.57"))).isEqualTo("36.57");
        assertThat(HealthCsv.num((BigDecimal) null)).isEmpty();
        assertThat(HealthCsv.num(Integer.valueOf(0))).isEqualTo("0");
        assertThat(HealthCsv.num((Integer) null)).isEmpty();
    }

    @Test
    void writesBomHeaderAndCrlfRows() {
        byte[] bytes = HealthCsv.toBytes(List.of("SAMPLE,STEPS,DAY,8123", "SLEEP,SLEEP,SESSION,460"));
        assertThat(bytes[0] & 0xFF).isEqualTo(0xEF);
        assertThat(bytes[1] & 0xFF).isEqualTo(0xBB);
        assertThat(bytes[2] & 0xFF).isEqualTo(0xBF);

        String text = new String(bytes, StandardCharsets.UTF_8).substring(1);
        String[] lines = text.split("\r\n", -1);
        assertThat(lines[0]).isEqualTo(HealthCsv.HEADER);
        assertThat(lines[1]).isEqualTo("SAMPLE,STEPS,DAY,8123");
        assertThat(lines[2]).isEqualTo("SLEEP,SLEEP,SESSION,460");
        // 末尾也带换行:最后一行为空串
        assertThat(lines[3]).isEmpty();
        assertThat(lines).hasSize(4);
        // 只有 CRLF,没有裸 LF
        assertThat(text.replace("\r\n", "")).doesNotContain("\n");
    }

    @Test
    void headerHasEighteenColumns() {
        assertThat(HealthCsv.HEADER.split(",", -1)).hasSize(18);
        // 数据行必须与表头同列数,否则 Excel 会整体错列
        assertThat(HealthCsv.line("SAMPLE", "STEPS", "DAY", "1", "", "", "", "步",
                "", "", "", "", "", "", "2026-11-18 00:00:00", "", "APPLE", "SHORTCUT")
                .split(",", -1)).hasSize(18);
    }
}
