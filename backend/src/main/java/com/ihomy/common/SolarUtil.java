package com.ihomy.common;

import java.time.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * 太阳/月亮位置与日月时刻计算(纯天文算法,纯数学,无外部依赖,不经任何三方接口):
 * 太阳位置与日出日落用 NOAA 算法(地平线取 90.833°,含大气折射+太阳视半径);
 * 民用/航海/天文三档晨昏按 96°/102°/108° 天顶角同法求解;
 * 月球位置用 Meeus 第 47 章简化级数,月出月落按 +0.125° 高度阈值(0.7275π−0.5667°)求交;
 * 月相由日月黄经差求照明比例(Meeus 第 48 章)。
 */
public class SolarUtil {
    private static final double RAD = Math.PI / 180;
    private static final double DEG = 180 / Math.PI;

    /** 一天的 288 个 5 分钟时隙:太阳高度角 + 方位角 */
    public static List<Map<String, Object>> buildSlots(double lat, double lng, LocalDate date, ZoneId tz) {
        List<Map<String, Object>> slots = new ArrayList<>(288);
        for (int i = 0; i < 288; i++) {
            int totalMin = i * 5;
            LocalTime t = LocalTime.of(totalMin / 60, totalMin % 60);
            LocalDateTime localDt = LocalDateTime.of(date, t);
            ZonedDateTime zoned = localDt.atZone(tz);
            double jd = julianDay(zoned);
            double T = julianCentury(jd);
            double dec = sunDeclination(T);
            double eot = eqOfTime(T);
            double solarNoonMin = (720 - 4 * lng - eot); // UTC minutes
            double utcMin = zoned.toInstant().atZone(ZoneOffset.UTC).getHour() * 60.0
                    + zoned.toInstant().atZone(ZoneOffset.UTC).getMinute();
            double ha = (utcMin - solarNoonMin) / 4.0; // degrees
            // 归一化到 -180~180(凌晨时角 >180 需转负,否则方位角反着)
            while (ha > 180) ha -= 360;
            while (ha < -180) ha += 360;
            double[] altAz = sunAltAz(lat, dec, ha);
            slots.add(Map.of(
                    "slot", i,
                    "time", String.format("%02d:%02d", t.getHour(), t.getMinute()),
                    "altitude", Math.round(altAz[0] * 10) / 10.0,
                    "azimuth", Math.round(altAz[1] * 10) / 10.0
            ));
        }
        return slots;
    }

    // ================= 日月时刻:晨昏三档 + 月出月落月相(纯天文计算,不依赖天气 API) =================

    /** 太阳地平线阈值:-0.833° = 日出日落(含大气折射 34′+ 太阳视半径 16′);-6/-12/-18° = 民用/航海/天文晨昏 */
    private static final double SUN_H0_DAY = -0.833;
    private static final double SUN_H0_CIVIL = -6;
    private static final double SUN_H0_NAUTICAL = -12;
    private static final double SUN_H0_ASTRONOMICAL = -18;
    /** 月亮升落阈值(度):0.7275×地平视差 − 0.5667°(视差取均值 0.95°),即月球中心视地平 */
    private static final double MOON_H0 = 0.125;
    /** 月相 8 档代码(与和风 v1 astro.moonPhase 取值一致,前端按同一套 key 取 emoji 与文案) */
    private static final String[] MOON_CODES = {
            "new-moon", "waxing-crescent", "first-quarter", "waxing-gibbous",
            "full-moon", "waning-gibbous", "last-quarter", "waning-crescent"
    };
    private static final double SYNODIC_MONTH = 29.530588853;

    /**
     * 当日(当地日历日 00:00-24:00)的日月与晨昏时刻,值一律为当地 "HH:mm":
     * 日出日落/太阳正午/太阳子夜;民用/航海/天文三档晨昏始末;月出月落/月中天/月下中天。
     * **当日内不发生该事件时值为空串**(极昼极夜没有日出日落、月亮当日不升不落),调用方按空隐藏,不要填占位时间。
     */
    public static Map<String, String> astroTimes(double lat, double lng, LocalDate date, ZoneId tz) {
        Instant dayStart = date.atStartOfDay(tz).toInstant();
        ToDoubleFunction<Instant> sunAlt = t -> sunAltitudeAt(lat, lng, t);
        ToDoubleFunction<Instant> moonAlt = t -> moonAltitudeAt(lat, lng, t);

        double sunrise = crossing(sunAlt, dayStart, tz, SUN_H0_DAY, true);
        double sunset = crossing(sunAlt, dayStart, tz, SUN_H0_DAY, false);
        // 太阳正午 = 日出日落中点(高度角曲线以正午对称),子夜即正午 +12 小时
        double noon = Double.isNaN(sunrise) || Double.isNaN(sunset) ? Double.NaN : (sunrise + sunset) / 2;
        Map<String, String> m = new LinkedHashMap<>();
        m.put("sunrise", hm(sunrise));
        m.put("sunset", hm(sunset));
        m.put("solarNoon", hm(noon));
        m.put("solarMidnight", hm(noon + 720));
        m.put("civilDawn", hm(crossing(sunAlt, dayStart, tz, SUN_H0_CIVIL, true)));
        m.put("civilDusk", hm(crossing(sunAlt, dayStart, tz, SUN_H0_CIVIL, false)));
        m.put("nauticalDawn", hm(crossing(sunAlt, dayStart, tz, SUN_H0_NAUTICAL, true)));
        m.put("nauticalDusk", hm(crossing(sunAlt, dayStart, tz, SUN_H0_NAUTICAL, false)));
        m.put("astronomicalDawn", hm(crossing(sunAlt, dayStart, tz, SUN_H0_ASTRONOMICAL, true)));
        m.put("astronomicalDusk", hm(crossing(sunAlt, dayStart, tz, SUN_H0_ASTRONOMICAL, false)));
        m.put("moonrise", hm(crossing(moonAlt, dayStart, tz, MOON_H0, true)));
        m.put("moonset", hm(crossing(moonAlt, dayStart, tz, MOON_H0, false)));
        m.put("moonTransit", hm(extremum(moonAlt, dayStart, true)));
        m.put("moonUnderfoot", hm(extremum(moonAlt, dayStart, false)));
        return m;
    }

    /** 当日分钟数 → "HH:mm"(事件当日不发生即 NaN → 空串) */
    private static String hm(double minutes) {
        return Double.isNaN(minutes) ? "" : toHm(minutes);
    }

    /**
     * 指定时刻的月相:{moonPhase 0~1(0=新月/0.5=满月)、moonPhaseCode 8 档代码、
     * moonIllumination 照明百分比 0~100、moonAge 月龄(天)}。
     */
    public static Map<String, Object> moonPhaseInfo(Instant instant) {
        double jd = julianDay(instant);
        double T = julianCentury(jd);
        double elong = norm360(moonEclipticLatLon(T)[0] - sunApparentLong(T)); // 日月黄经差(距角)
        double phase = elong / 360.0;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("moonPhase", Math.round(phase * 1000) / 1000.0);
        m.put("moonPhaseCode", MOON_CODES[(int) Math.floor((phase + 1.0 / 16) % 1 * 8)]);
        m.put("moonIllumination", (int) Math.round((1 - Math.cos(elong * RAD)) / 2 * 100));
        m.put("moonAge", Math.round(phase * SYNODIC_MONTH * 10) / 10.0);
        return m;
    }

    // ---- 日月时刻求解(采样 + 二分/三分细化) ----

    /**
     * 高度角升/落穿过阈值 h0 的当地时刻(当地当日分钟数,窗口内无穿越返回 NaN;不预先格式化成字符串,
     * 好让调用方能对时刻做算术,如日出日落取中点为太阳正午)。
     * 按 4 分钟采样(地平附近月亮每小时才走约 10°,4 分钟不会漏穿),命中后二分细化到约 1 秒。
     */
    private static double crossing(ToDoubleFunction<Instant> alt, Instant dayStart, ZoneId tz, double h0, boolean rising) {
        final int step = 4;
        double prev = alt.applyAsDouble(dayStart);
        Instant prevT = dayStart;
        for (int min = step; min <= 1440; min += step) {
            Instant t = dayStart.plusSeconds(min * 60L);
            double cur = alt.applyAsDouble(t);
            if (rising ? (prev < h0 && cur >= h0) : (prev >= h0 && cur < h0)) {
                Instant lo = prevT, hi = t;
                for (int k = 0; k < 24; k++) {
                    long half = (hi.getEpochSecond() - lo.getEpochSecond()) / 2;
                    if (half <= 0) break;
                    Instant mid = lo.plusSeconds(half);
                    double a = alt.applyAsDouble(mid);
                    if (rising ? a < h0 : a >= h0) lo = mid; else hi = mid;
                }
                return localMinutes(hi, tz);
            }
            prev = cur;
            prevT = t;
        }
        return Double.NaN;
    }

    /** 一天内高度角最高/最低时刻(月中天/月下中天),返回当地当日分钟数:5 分钟粗扫 + 极值邻域三分细化 */
    private static double extremum(ToDoubleFunction<Instant> alt, Instant dayStart, boolean max) {
        int bestMin = 0;
        double best = max ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        for (int min = 0; min <= 1440; min += 5) {
            double a = alt.applyAsDouble(dayStart.plusSeconds(min * 60L));
            if (max ? a > best : a < best) {
                best = a;
                bestMin = min;
            }
        }
        double lo = Math.max(0, bestMin - 6), hi = Math.min(1440, bestMin + 6);
        for (int k = 0; k < 40; k++) {
            double m1 = lo + (hi - lo) / 3, m2 = hi - (hi - lo) / 3;
            double a1 = alt.applyAsDouble(dayStart.plusSeconds((long) (m1 * 60)));
            double a2 = alt.applyAsDouble(dayStart.plusSeconds((long) (m2 * 60)));
            if (max ? a1 < a2 : a1 > a2) lo = m1; else hi = m2;
        }
        return lo + (hi - lo) / 2;
    }

    /** 太阳高度角(度)。与 buildSlots 同一套 NOAA 公式,只是按任意时刻求值 */
    private static double sunAltitudeAt(double lat, double lng, Instant instant) {
        double jd = julianDay(instant);
        double T = julianCentury(jd);
        double dec = sunDeclination(T);
        double eot = eqOfTime(T);
        ZonedDateTime utc = instant.atZone(ZoneOffset.UTC);
        double utcMin = utc.getHour() * 60.0 + utc.getMinute() + utc.getSecond() / 60.0;
        double ha = (utcMin - (720 - 4 * lng - eot)) / 4.0;
        while (ha > 180) ha -= 360;
        while (ha < -180) ha += 360;
        return sunAltAz(lat, dec, ha)[0];
    }

    /** 月球高度角(度):Meeus 47 月球黄道坐标 → 赤道坐标 → 真时角(LST−α) */
    private static double moonAltitudeAt(double lat, double lng, Instant instant) {
        double jd = julianDay(instant);
        double T = julianCentury(jd);
        double[] lb = moonEclipticLatLon(T);
        double[] ad = eclipticToEquatorial(lb[0], lb[1], obliquityCorrection(T));
        double ha = gmstDeg(jd, T) + lng - ad[0];
        ha = ((ha % 360) + 540) % 360 - 180;
        double latR = lat * RAD, decR = ad[1] * RAD, haR = ha * RAD;
        double sinAlt = Math.sin(latR) * Math.sin(decR) + Math.cos(latR) * Math.cos(decR) * Math.cos(haR);
        return Math.asin(Math.max(-1, Math.min(1, sinAlt))) * DEG;
    }

    /** 月球黄道坐标(λ,β,度)。Meeus 表 47.A/47.B 主项(取到 3.5e-3 度量级,月出月落时刻误差 < 10 秒) */
    private static double[] moonEclipticLatLon(double T) {
        double lp = 218.3164477 + 481267.88123421 * T - 0.0015786 * T * T + T * T * T / 538841 - T * T * T * T / 65194000;
        double d = 297.8501921 + 445267.1114034 * T - 0.0018819 * T * T + T * T * T / 545868 - T * T * T * T / 113065000;
        double m = 357.5291092 + 35999.0502909 * T - 0.0001536 * T * T + T * T * T / 24490000;
        double mp = 134.9633964 + 477198.8675055 * T + 0.0087414 * T * T + T * T * T / 69699 - T * T * T * T / 14712000;
        double f = 93.2720950 + 483202.0175233 * T - 0.0036539 * T * T - T * T * T / 3526000 + T * T * T * T / 863310000;
        double a1 = 119.75 + 131.849 * T;
        double a2 = 53.09 + 479264.290 * T;
        double a3 = 313.45 + 481266.484 * T;
        // 地球轨道偏心率对含 M 项的影响(M 一次项 ×E,二次项 ×E²)
        double e = 1 - 0.002516 * T - 0.0000074 * T * T;

        double sl = 0, sb = 0;
        for (double[] t : MOON_LON_TERMS) {
            double arg = t[0] * d + t[1] * m + t[2] * mp + t[3] * f;
            sl += t[4] * eccFactor(t[1], e) * Math.sin(arg * RAD);
        }
        for (double[] t : MOON_LAT_TERMS) {
            double arg = t[0] * d + t[1] * m + t[2] * mp + t[3] * f;
            sb += t[4] * eccFactor(t[1], e) * Math.sin(arg * RAD);
        }
        // 金星/木星摄动与地球扁率附加项(Meeus 47 附项)
        sl += 3958 * Math.sin(a1 * RAD) + 1962 * Math.sin((lp - f) * RAD) + 318 * Math.sin(a2 * RAD);
        sb += -2235 * Math.sin(lp * RAD) + 382 * Math.sin(a3 * RAD)
                + 175 * Math.sin((a1 - f) * RAD) + 175 * Math.sin((a1 + f) * RAD)
                + 127 * Math.sin((lp - mp) * RAD) - 115 * Math.sin((lp + mp) * RAD);

        return new double[]{ norm360(lp + sl / 1e6), sb / 1e6 };
    }

    private static double eccFactor(double mArg, double e) {
        double abs = Math.abs(mArg);
        if (abs == 1) return e;
        if (abs == 2) return e * e;
        return 1;
    }

    /** 黄道坐标(λ,β,度)→ 赤道坐标(α 归一到 0~360,δ 度),eps 为黄赤交角 */
    private static double[] eclipticToEquatorial(double lambda, double beta, double eps) {
        double l = lambda * RAD, b = beta * RAD, e = eps * RAD;
        double alpha = Math.atan2(Math.sin(l) * Math.cos(e) - Math.tan(b) * Math.sin(e), Math.cos(l)) * DEG;
        double delta = Math.asin(Math.sin(b) * Math.cos(e) + Math.cos(b) * Math.sin(e) * Math.sin(l)) * DEG;
        return new double[]{ norm360(alpha), delta };
    }

    /** 格林尼治平恒星时(度):月球时角必须用它,不能用太阳的"距正午分钟数"近似 */
    private static double gmstDeg(double jd, double T) {
        double theta = 280.46061837 + 360.98564736629 * (jd - 2451545.0)
                + 0.000387933 * T * T - T * T * T / 38710000.0;
        return norm360(theta);
    }

    private static double localMinutes(Instant instant, ZoneId tz) {
        ZonedDateTime z = instant.atZone(tz);
        return z.getHour() * 60.0 + z.getMinute() + z.getSecond() / 60.0;
    }

    private static double norm360(double deg) {
        return ((deg % 360) + 360) % 360;
    }

    // Meeus 表 47.A 主项:{D, M, M', F, Σl(1e-6 度)}
    private static final double[][] MOON_LON_TERMS = {
            {0, 0, 1, 0, 6288774}, {2, 0, -1, 0, 1274027}, {2, 0, 0, 0, 658314},
            {0, 0, 2, 0, 213618}, {0, 1, 0, 0, -185116}, {0, 0, 0, 2, -114332},
            {2, 0, -2, 0, 58793}, {2, -1, -1, 0, 57066}, {2, 0, 1, 0, 53322},
            {2, -1, 0, 0, 45758}, {0, 1, -1, 0, -40923}, {1, 0, 0, 0, -34720},
            {0, 1, 1, 0, -30383}, {2, 0, 0, -2, 15327}, {0, 0, 1, 2, -12528},
            {0, 0, 1, -2, 10980}, {4, 0, -1, 0, 10675}, {0, 0, 3, 0, 10034},
            {4, 0, -2, 0, 8548}, {2, 1, -1, 0, -7888}, {2, 1, 0, 0, -6766},
            {1, 0, -1, 0, -5163}, {1, 1, 0, 0, 4987}, {2, -1, 1, 0, 4036},
            {2, 0, 2, 0, 3994}, {4, 0, 0, 0, 3861}, {2, 0, -3, 0, 3665},
            {0, 1, -2, 0, -2689}, {2, 0, -1, 2, -2602}, {2, -1, -2, 0, 2390},
            {1, 0, 1, 0, -2348}, {2, -2, 0, 0, 2236}, {0, 1, 2, 0, -2120},
            {0, 2, 0, 0, -2069}
    };

    // Meeus 表 47.B 主项:{D, M, M', F, Σb(1e-6 度)}
    private static final double[][] MOON_LAT_TERMS = {
            {0, 0, 0, 1, 5128122}, {0, 0, 1, 1, 280602}, {0, 0, 1, -1, 277693},
            {2, 0, 0, -1, 173237}, {2, 0, -1, 1, 55413}, {2, 0, -1, -1, 46271},
            {2, 0, 0, 1, 32573}, {0, 0, 2, 1, 17198}, {2, 0, 1, -1, 9266},
            {0, 0, 2, -1, 8822}, {2, -1, 0, -1, 8216}, {2, 0, -2, -1, 4324},
            {2, 0, 1, 1, 4200}, {2, 1, 0, -1, -3359}, {2, -1, -1, 1, 2463},
            {2, -1, 0, 1, 2211}, {2, -1, -1, -1, 2065}, {0, 1, -1, -1, -1870},
            {4, 0, -1, -1, 1828}, {0, 1, 0, 1, -1794}, {0, 0, 0, 3, -1749},
            {0, 1, -1, 1, -1565}, {1, 0, 0, 1, -1491}, {0, 1, 1, 1, -1475},
            {0, 1, 1, -1, -1410}, {0, 1, 0, -1, -1344}, {1, 0, 0, -1, -1335},
            {0, 0, 3, 1, 1107}, {4, 0, 0, -1, 1021}, {4, 0, -1, 1, 833},
            {4, 0, -2, 1, 777}, {0, 0, 1, -3, 777}, {4, 0, -2, -1, 733}
    };

    // ---- NOAA 内部算法 ----

    private static double julianDay(ZonedDateTime zdt) {
        Instant instant = zdt.toInstant();
        ZonedDateTime utc = instant.atZone(ZoneOffset.UTC);
        int y = utc.getYear(), m = utc.getMonthValue(), d = utc.getDayOfMonth();
        double hour = utc.getHour() + utc.getMinute() / 60.0 + utc.getSecond() / 3600.0;
        return julianDay(y, m, d, hour);
    }

    private static double julianDay(Instant instant) {
        return julianDay(instant.atZone(ZoneOffset.UTC));
    }

    private static double julianDay(int y, int m, int d, double hour) {
        if (m <= 2) { y--; m += 12; }
        int a = y / 100;
        int b = 2 - a + a / 4;
        return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + d + hour / 24.0 + b - 1524.5;
    }

    private static double julianCentury(double jd) {
        return (jd - 2451545.0) / 36525.0;
    }

    private static double sunGeomMeanLong(double T) {
        double l = 280.46646 + T * (36000.76983 + T * 0.0003032);
        return ((l % 360) + 360) % 360;
    }

    private static double sunGeomMeanAnom(double T) {
        return 357.52911 + T * (35999.05029 - 0.0001537 * T);
    }

    private static double earthEccentricity(double T) {
        return 0.016708634 - T * (0.000042037 + 0.0000001267 * T);
    }

    private static double sunEqCenter(double T) {
        double m = sunGeomMeanAnom(T) * RAD;
        return Math.sin(m) * (1.914602 - T * (0.004817 + 0.000014 * T))
                + Math.sin(2 * m) * (0.019993 - 0.000101 * T)
                + Math.sin(3 * m) * 0.000289;
    }

    private static double sunTrueLong(double T) {
        return sunGeomMeanLong(T) + sunEqCenter(T);
    }

    private static double sunApparentLong(double T) {
        double omega = 125.04 - 1934.136 * T;
        return sunTrueLong(T) - 0.00569 - 0.00478 * Math.sin(omega * RAD);
    }

    private static double meanObliquity(double T) {
        double sec = 21.448 - T * (46.815 + T * (0.00059 - T * 0.001813));
        return 23.0 + (26.0 + sec / 60.0) / 60.0;
    }

    private static double obliquityCorrection(double T) {
        double omega = 125.04 - 1934.136 * T;
        return meanObliquity(T) + 0.00256 * Math.cos(omega * RAD);
    }

    private static double sunDeclination(double T) {
        double e = obliquityCorrection(T) * RAD;
        double lambda = sunApparentLong(T) * RAD;
        return Math.asin(Math.sin(e) * Math.sin(lambda)) * DEG;
    }

    private static double eqOfTime(double T) {
        double e0 = obliquityCorrection(T);
        double l0 = sunGeomMeanLong(T);
        double m = sunGeomMeanAnom(T);
        double e = earthEccentricity(T);
        double y = Math.tan(e0 / 2 * RAD);
        y = y * y;
        double sin2l0 = Math.sin(2 * l0 * RAD);
        double sinm = Math.sin(m * RAD);
        double cos2l0 = Math.cos(2 * l0 * RAD);
        double sin4l0 = Math.sin(4 * l0 * RAD);
        double sin2m = Math.sin(2 * m * RAD);
        double Etime = y * sin2l0 - 2 * e * sinm + 4 * e * y * sinm * cos2l0
                - 0.5 * y * y * sin4l0 - 1.25 * e * e * sin2m;
        return Math.toDegrees(Etime) * 4.0; // minutes
    }

    /** 太阳高度角 + 方位角 (degrees),用 atan2 避免象限歧义 */
    private static double[] sunAltAz(double lat, double dec, double haDeg) {
        double latR = lat * RAD, decR = dec * RAD, haR = haDeg * RAD;
        double sinAlt = Math.sin(latR) * Math.sin(decR) + Math.cos(latR) * Math.cos(decR) * Math.cos(haR);
        sinAlt = Math.max(-1, Math.min(1, sinAlt));
        double alt = Math.asin(sinAlt);
        double cosAlt = Math.cos(alt);
        if (Math.abs(cosAlt) < 1e-10) {
            return new double[]{ alt * DEG, 0 };
        }
        // atan2(y, x): y=东分量, x=北分量 → 方位角从正北顺时针
        double y = -Math.sin(haR);
        double x = (Math.sin(decR) - Math.sin(latR) * sinAlt) / (Math.cos(latR) * cosAlt);
        double az = Math.atan2(y, x) * DEG;
        // atan2 返回 -180~180(正北=0,东=90,西=-90),转为 0~360
        if (az < 0) az += 360;
        return new double[]{ alt * DEG, az };
    }

    private static String toHm(double minutes) {
        minutes = ((minutes % 1440) + 1440) % 1440;
        int h = (int) minutes / 60;
        int m = (int) Math.round(minutes % 60);
        if (m == 60) { h++; m = 0; }
        h = h % 24;
        return String.format("%02d:%02d", h, m);
    }
}
