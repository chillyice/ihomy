package com.ihomy.common;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 健康数据上报的时间口径:上报端一律带 timezone(如 +08:00),服务端折算成服务器本地时间入库。
 * 全表 DATETIME 沿用项目既有口径(服务器本地时间,无 TZ 列)——家人同住一个时区,不引入 TZ 表。
 * 纯静态工具,便于单测。
 */
public final class HealthTime {

    private HealthTime() {
    }

    private static final Pattern OFFSET_SUFFIX = Pattern.compile("([+-]\\d{2}:?\\d{2})$");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 解析 ±HH:MM / ±HHMM / Z,非法或超出 ±14 小时返回 null */
    public static ZoneOffset parseOffset(String tz) {
        if (tz == null) return null;
        String s = tz.trim().toUpperCase();
        if ("Z".equals(s) || "UTC".equals(s) || "GMT".equals(s)) return ZoneOffset.UTC;
        Matcher m = Pattern.compile("^(?:UTC|GMT)?([+-])(\\d{1,2}):?(\\d{2})$").matcher(s);
        if (!m.matches()) return null;
        int hours = Integer.parseInt(m.group(2));
        int minutes = Integer.parseInt(m.group(3));
        if (hours > 14 || minutes > 59 || (hours == 14 && minutes > 0)) return null;
        int total = (hours * 3600 + minutes * 60) * ("-".equals(m.group(1)) ? -1 : 1);
        return ZoneOffset.ofTotalSeconds(total);
    }

    public static LocalDate parseDate(String raw) {
        if (raw == null) return null;
        try {
            return LocalDate.parse(raw.trim(), DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * 解析上报时刻:支持 2026-11-16T08:00 / 2026-11-16 08:00:00 / 带 Z 或 ±HH:MM 的 ISO 串。
     * 返回「墙钟时间 + 该串自带的偏移」;无自带偏移时用 fallback(上报的 timezone)。
     */
    public record Stamp(LocalDateTime wall, ZoneOffset offset) {
    }

    public static Stamp parseStamp(String raw, ZoneOffset fallback) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        // 自带偏移:交给 OffsetDateTime
        if (s.endsWith("Z") || OFFSET_SUFFIX.matcher(s).find()) {
            try {
                OffsetDateTime odt = OffsetDateTime.parse(s);
                return new Stamp(odt.toLocalDateTime(), odt.getOffset());
            } catch (DateTimeParseException ignore) {
                // 落到下面按无偏移处理
            }
        }
        String t = s.replace(' ', 'T');
        try {
            return new Stamp(LocalDateTime.parse(t), fallback);
        } catch (DateTimeParseException e) {
            try {
                return new Stamp(LocalDateTime.parse(t, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")), fallback);
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }

    /** 把某时区墙钟时间折算成服务器本地时间入库 */
    public static LocalDateTime toServerZone(LocalDateTime wall, ZoneOffset offset) {
        if (wall == null || offset == null) return null;
        return wall.atOffset(offset)
                .atZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    /** 入睡日:凌晨(< 12:00)入睡归前一天,其余归当天(按上报端的当地墙钟时间算) */
    public static LocalDate sleepDateOf(LocalDateTime wallStart) {
        if (wallStart == null) return null;
        return wallStart.toLocalTime().isBefore(LocalTime.NOON)
                ? wallStart.toLocalDate().minusDays(1)
                : wallStart.toLocalDate();
    }

    /** 小睡:09:00–20:00 之间开始的会话(按上报端当地墙钟时间算) */
    public static String sleepKindOf(LocalDateTime wallStart) {
        if (wallStart == null) return "NIGHT";
        LocalTime t = wallStart.toLocalTime();
        return !t.isBefore(LocalTime.of(9, 0)) && t.isBefore(LocalTime.of(20, 0)) ? "NAP" : "NIGHT";
    }
}
