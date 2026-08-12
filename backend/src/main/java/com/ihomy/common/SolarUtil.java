package com.ihomy.common;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 太阳/月亮位置计算(NOAA 算法)。
 * 纯数学,无外部依赖。日出日落基于 90.833°(含大气折射+太阳视半径)。
 */
public class SolarUtil {
    private static final double RAD = Math.PI / 180;
    private static final double DEG = 180 / Math.PI;

    /** 一天的 96 个 15 分钟时隙:太阳高度角 + 方位角 */
    public static List<Map<String, Object>> buildSlots(double lat, double lng, LocalDate date, ZoneId tz) {
        List<Map<String, Object>> slots = new ArrayList<>(96);
        for (int i = 0; i < 96; i++) {
            int totalMin = i * 15;
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

    /** 日出/日落/正午(返回 local time HH:mm) */
    public static Map<String, String> sunTimes(double lat, double lng, LocalDate date, ZoneId tz) {
        double jd = julianDay(date.atStartOfDay(tz).toInstant());
        double T = julianCentury(jd);
        double dec = sunDeclination(T);
        double eot = eqOfTime(T);
        double solarNoonUtc = (720 - 4 * lng - eot); // minutes UTC
        double latR = lat * RAD, decR = dec * RAD;
        double cosHa = (Math.cos(90.833 * RAD) / (Math.cos(latR) * Math.cos(decR))) - Math.tan(latR) * Math.tan(decR);
        cosHa = Math.max(-1, Math.min(1, cosHa));
        double ha = Math.acos(cosHa) * DEG;
        double sunriseUtc = solarNoonUtc - ha * 4;
        double sunsetUtc = solarNoonUtc + ha * 4;
        ZoneOffset offset = tz.getRules().getOffset(date.atStartOfDay());
        int offMin = offset.getTotalSeconds() / 60;
        return Map.of(
                "sunrise", toHm(sunriseUtc + offMin),
                "sunset", toHm(sunsetUtc + offMin),
                "solarNoon", toHm(solarNoonUtc + offMin)
        );
    }

    /** 月相 0~1 (0=新月, 0.5=满月) */
    public static double moonPhase(LocalDate date) {
        ZonedDateTime knownNewMoon = ZonedDateTime.of(2000, 1, 6, 18, 14, 0, 0, ZoneOffset.UTC);
        double days = (date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                - knownNewMoon.toInstant().toEpochMilli()) / 86400000.0;
        double phase = (days % 29.53059) / 29.53059;
        if (phase < 0) phase += 1;
        return Math.round(phase * 1000) / 1000.0;
    }

    /** 月出/月落(简化:基于月相推算,精度约±30分钟) */
    public static Map<String, String> moonTimes(double lat, double lng, LocalDate date, ZoneId tz) {
        double phase = moonPhase(date);
        Map<String, String> sun = sunTimes(lat, lng, date, tz);
        // 新月月出≈日出,满月月出≈日落,线性插值
        double sunriseMin = parseHm(sun.get("sunrise"));
        double sunsetMin = parseHm(sun.get("sunset"));
        double moonriseMin = sunriseMin + phase * (sunsetMin - sunriseMin + 720);
        double moonsetMin = moonriseMin + 720; // 约12小时后
        moonriseMin = ((moonriseMin % 1440) + 1440) % 1440;
        moonsetMin = ((moonsetMin % 1440) + 1440) % 1440;
        return Map.of("moonrise", toHm(moonriseMin), "moonset", toHm(moonsetMin));
    }

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

    private static double parseHm(String hm) {
        if (hm == null || !hm.contains(":")) return 0;
        String[] p = hm.split(":");
        return Integer.parseInt(p[0]) * 60.0 + Integer.parseInt(p[1]);
    }
}
