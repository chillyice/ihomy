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

    // ---- 直射点与太阳位置(晨昏地图的数据源) ----

    /** 直射点纬度 = 赤纬:春分/秋分落在赤道,夏至落在北回归线 */
    @Test
    void subsolarLatitudeFollowsDeclination() {
        assertThat(subsolarLat("2026-03-20T12:00:00Z")).isCloseTo(0.0, within(1.0));
        assertThat(subsolarLat("2026-09-23T12:00:00Z")).isCloseTo(0.0, within(1.0));
        assertThat(subsolarLat("2026-06-21T12:00:00Z")).isCloseTo(23.4, within(1.0));
        assertThat(subsolarLat("2026-12-21T12:00:00Z")).isCloseTo(-23.4, within(1.0));
    }

    /** 直射经度 = (720 − UTC分钟 − 均时差)/4;UTC 12:00 时均时差最多约 16 分钟 → 约 4° */
    @Test
    void subsolarLongitudeNearPrimeMeridianAtUtcNoon() {
        assertThat(SolarUtil.subsolarPoint(Instant.parse("2026-03-20T12:00:00Z")).get("lng").doubleValue())
                .isCloseTo(0.0, within(4.5));
        assertThat(SolarUtil.subsolarPoint(Instant.parse("2026-06-21T12:00:00Z")).get("lng").doubleValue())
                .isCloseTo(0.0, within(4.5));
    }

    /** 直射点处太阳在天顶(90°);其对跖点(镜像纬度 + 经度反相)在地平线下最低(−90°) */
    @Test
    void sunPositionIsZenithAtSubsolarPoint() {
        Instant t = Instant.parse("2026-10-18T04:00:00Z");
        Map<String, Double> ss = SolarUtil.subsolarPoint(t);
        assertThat(SolarUtil.sunPosition(ss.get("lat"), ss.get("lng"), t).get("altitude"))
                .isCloseTo(90.0, within(0.5));
        double antiLng = ss.get("lng") >= 0 ? ss.get("lng") - 180 : ss.get("lng") + 180;
        assertThat(SolarUtil.sunPosition(-ss.get("lat"), antiLng, t).get("altitude"))
                .isCloseTo(-90.0, within(0.5));
    }

    /** 同一时刻同一纬度,经度每差 15° 高度角约差 15°(日落方向) */
    @Test
    void sunPositionBeijingNoonIsUpAndAzimuthSouth() {
        Instant t = Instant.parse("2026-06-21T04:00:00Z"); // 北京时间正午
        Map<String, Double> p = SolarUtil.sunPosition(39.9, 116.4, t);
        double alt = p.get("altitude");
        assertThat(alt).isGreaterThan(70.0).isLessThan(90.0);
        assertThat(p.get("azimuth")).isBetween(150.0, 210.0); // 正南
    }

    // ---- 个人主页舞台:月球位置与日月快照 ----

    @Test
    void moonPositionAndAstroNowAreFinite() {
        Instant t = Instant.parse("2026-10-21T12:00:00Z");
        Map<String, Double> moon = SolarUtil.moonPosition(36.6512, 117.1201, t);
        assertThat(moon.get("altitude")).isBetween(-90.0, 90.0);
        assertThat(moon.get("azimuth")).isBetween(0.0, 360.0);

        Map<String, Object> now = SolarUtil.astroNow(36.6512, 117.1201, t);
        assertThat(now).containsKeys("now", "sun", "moon", "moonPhase", "moonPhaseCode");
        assertThat(((Map<?, ?>) now.get("moon")).get("altitude")).isEqualTo(moon.get("altitude"));
    }

    @Test
    void astroDayTrackCoversWholeDayAtRequestedStep() {
        Map<String, Object> day = SolarUtil.astroDay(36.6512, 117.1201, LocalDate.of(2026, 10, 22), SH, 10);
        assertThat(day).containsKeys("date", "times", "sun", "moon");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sun = (List<Map<String, Object>>) day.get("sun");
        assertThat(sun).hasSize(144);
        assertThat(sun.get(0).get("t")).isEqualTo(0);
        assertThat(sun.get(143).get("t")).isEqualTo(1430);
        for (Map<String, Object> p : sun) {
            assertThat(((Number) p.get("alt")).doubleValue()).isBetween(-90.0, 90.0);
            assertThat(((Number) p.get("az")).doubleValue()).isBetween(0.0, 360.0);
        }
        // 正午太阳应明显高于午夜
        double noon = ((Number) sun.get(72).get("alt")).doubleValue();
        double midnight = ((Number) sun.get(0).get("alt")).doubleValue();
        assertThat(noon).isGreaterThan(midnight + 20);
    }

    private static double subsolarLat(String iso) {
        return SolarUtil.subsolarPoint(Instant.parse(iso)).get("lat").doubleValue();
    }

    private static org.assertj.core.data.Offset<Double> within(double v) {
        return org.assertj.core.data.Offset.offset(v);
    }
}
