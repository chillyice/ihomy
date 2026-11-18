package com.ihomy.common;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 上报时间口径:时区偏移解析(±14h 上限)、时刻字符串兼容快捷指令的两种写法、
 * 入睡日跨零点归前一天、白天会话记小睡。
 */
class HealthTimeTest {

    @Test
    void parsesCommonOffsets() {
        assertThat(HealthTime.parseOffset("+08:00")).isEqualTo(ZoneOffset.ofHours(8));
        assertThat(HealthTime.parseOffset("-0530")).isEqualTo(ZoneOffset.ofHoursMinutes(-5, -30));
        assertThat(HealthTime.parseOffset("Z")).isEqualTo(ZoneOffset.UTC);
        assertThat(HealthTime.parseOffset("+14:00")).isEqualTo(ZoneOffset.ofHours(14));
    }

    @Test
    void rejectsInvalidOrOutOfRangeOffsets() {
        assertThat(HealthTime.parseOffset("+14:30")).isNull();
        assertThat(HealthTime.parseOffset("+15:00")).isNull();
        assertThat(HealthTime.parseOffset("asia/shanghai")).isNull();
        assertThat(HealthTime.parseOffset(null)).isNull();
        assertThat(HealthTime.parseOffset("")).isNull();
    }

    @Test
    void parsesIsoAndSpaceSeparatedTimestamps() {
        ZoneOffset fallback = ZoneOffset.ofHours(8);
        HealthTime.Stamp a = HealthTime.parseStamp("2026-11-15T23:30:00", fallback);
        assertThat(a.wall()).isEqualTo(LocalDateTime.of(2026, 11, 15, 23, 30));
        assertThat(a.offset()).isEqualTo(fallback);

        HealthTime.Stamp b = HealthTime.parseStamp("2026-11-15 23:30", fallback);
        assertThat(b.wall()).isEqualTo(LocalDateTime.of(2026, 11, 15, 23, 30));

        // 自带偏移的串以自带为准(快捷指令偶尔会带 Z)
        HealthTime.Stamp c = HealthTime.parseStamp("2026-11-15T15:30:00Z", fallback);
        assertThat(c.wall()).isEqualTo(LocalDateTime.of(2026, 11, 15, 15, 30));
        assertThat(c.offset()).isEqualTo(ZoneOffset.UTC);

        assertThat(HealthTime.parseStamp("不是时间", fallback)).isNull();
        assertThat(HealthTime.parseStamp(null, fallback)).isNull();
    }

    @Test
    void dateOnlyParsing() {
        assertThat(HealthTime.parseDate("2026-11-16")).isEqualTo(LocalDate.of(2026, 11, 16));
        assertThat(HealthTime.parseDate("2026/11/16")).isNull();
    }

    @Test
    void sleepDateBeforeNoonBelongsToPreviousDay() {
        assertThat(HealthTime.sleepDateOf(LocalDateTime.of(2026, 11, 16, 1, 30)))
                .isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(HealthTime.sleepDateOf(LocalDateTime.of(2026, 11, 16, 11, 59)))
                .isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(HealthTime.sleepDateOf(LocalDateTime.of(2026, 11, 16, 12, 0)))
                .isEqualTo(LocalDate.of(2026, 11, 16));
        assertThat(HealthTime.sleepDateOf(LocalDateTime.of(2026, 11, 16, 23, 30)))
                .isEqualTo(LocalDate.of(2026, 11, 16));
    }

    @Test
    void sleepKindNapBetweenNineAndTwenty() {
        assertThat(HealthTime.sleepKindOf(LocalDateTime.of(2026, 11, 16, 8, 59))).isEqualTo("NIGHT");
        assertThat(HealthTime.sleepKindOf(LocalDateTime.of(2026, 11, 16, 9, 0))).isEqualTo("NAP");
        assertThat(HealthTime.sleepKindOf(LocalDateTime.of(2026, 11, 16, 14, 0))).isEqualTo("NAP");
        assertThat(HealthTime.sleepKindOf(LocalDateTime.of(2026, 11, 16, 20, 0))).isEqualTo("NIGHT");
    }
}
