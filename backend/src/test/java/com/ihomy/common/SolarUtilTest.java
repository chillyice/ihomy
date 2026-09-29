package com.ihomy.common;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SolarUtil 纯天文算法单测(骨架):时隙结构、日出日落/晨昏先后、月相取值范围。
 */
class SolarUtilTest {

    private static final ZoneId SH = ZoneId.of("Asia/Shanghai");
    private static final double LAT = 39.9;
    private static final double LNG = 116.4;

    @Test
    void buildSlotsCoversWholeDay() {
        List<Map<String, Object>> slots = SolarUtil.buildSlots(LAT, LNG, LocalDate.of(2026, 6, 21), SH);
        assertThat(slots).hasSize(288);
        assertThat(slots.get(0).get("time")).isEqualTo("00:00");
        assertThat(slots.get(287).get("time")).isEqualTo("23:55");
        for (Map<String, Object> s : slots) {
            double alt = ((Number) s.get("altitude")).doubleValue();
            assertThat(alt).isBetween(-90.0, 90.0);
        }
    }

    @Test
    void astroTimesOrderedCorrectlyAtSummerSolstice() {
        Map<String, String> t = SolarUtil.astroTimes(LAT, LNG, LocalDate.of(2026, 6, 21), SH);
        assertThat(t.get("sunrise")).isNotBlank();
        assertThat(t.get("sunset")).isNotBlank();
        assertThat(t.get("sunrise").compareTo(t.get("sunset"))).isLessThan(0);
        assertThat(t.get("civilDawn").compareTo(t.get("sunrise"))).isLessThan(0);
        assertThat(t.get("civilDusk").compareTo(t.get("sunset"))).isGreaterThan(0);
        // 北京夏至太阳正午应落在中午前后(10:00~14:00)
        assertThat(t.get("solarNoon")).isBetween("10:00", "14:00");
    }

    @Test
    void moonPhaseInfoWithinRange() {
        Map<String, Object> m = SolarUtil.moonPhaseInfo(Instant.parse("2026-06-21T12:00:00Z"));
        double phase = ((Number) m.get("moonPhase")).doubleValue();
        int illum = ((Number) m.get("moonIllumination")).intValue();
        assertThat(phase).isBetween(0.0, 1.0);
        assertThat(illum).isBetween(0, 100);
        assertThat((String) m.get("moonPhaseCode")).isNotBlank();
        assertThat(((Number) m.get("moonAge")).doubleValue()).isBetween(0.0, 29.6);
    }
}
