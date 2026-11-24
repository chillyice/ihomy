package com.ihomy.service.backup;

import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 备份策略的周期换算与校验(纯函数,便于单测)。
 *
 * <p>用户只需要选「每天 / 每周 / 每月 + 时间(+星期几 / 几号)」,
 * 这里统一换成 Spring 六段式 cron(秒 分 时 日 月 周),时区取服务器本地时区。</p>
 *
 * <ul>
 *   <li>每天:0 30 3 * * *</li>
 *   <li>每周:0 30 3 * * 1(周一=1 … 周日=7)</li>
 *   <li>每月:0 30 3 1 * *(1-28,避开不存在的日期)</li>
 * </ul>
 */
public final class BackupSchedule {

    public static final String DAILY = "DAILY";
    public static final String WEEKLY = "WEEKLY";
    public static final String MONTHLY = "MONTHLY";

    public static final String DEFAULT_TIME = "03:30";
    public static final int DEFAULT_WEEKLY_DAY = 7;
    public static final int DEFAULT_MONTHLY_DAY = 1;

    private static final Pattern TIME = Pattern.compile("^([01]\\d|2[0-3]):([0-5]\\d)$");
    private static final DateTimeFormatter HUMAN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private BackupSchedule() {
    }

    public static String normalizeType(String type) {
        if (type == null || type.isBlank()) return DAILY;
        String value = type.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case DAILY, WEEKLY, MONTHLY -> value;
            default -> value;
        };
    }

    public static String normalizeTime(String time) {
        if (time == null || time.isBlank()) return DEFAULT_TIME;
        return time.trim();
    }

    /** 每天不用日期;每周默认周日;每月默认 1 号 */
    public static Integer normalizeDay(String type, Integer day) {
        String t = normalizeType(type);
        if (DAILY.equals(t)) return null;
        if (day != null) return day;
        return WEEKLY.equals(t) ? DEFAULT_WEEKLY_DAY : DEFAULT_MONTHLY_DAY;
    }

    /** 策略 → Spring cron;调用前应先通过 {@link #validate} 校验 */
    public static String cron(String type, String time, Integer day) {
        String t = normalizeType(type);
        Matcher m = TIME.matcher(normalizeTime(time));
        if (!m.matches()) {
            throw new IllegalArgumentException("执行时间格式不正确");
        }
        String minute = String.valueOf(Integer.parseInt(m.group(2)));
        String hour = String.valueOf(Integer.parseInt(m.group(1)));
        String prefix = "0 " + minute + " " + hour + " ";
        return switch (t) {
            case WEEKLY -> prefix + "* * " + normalizeDay(t, day);
            case MONTHLY -> prefix + normalizeDay(t, day) + " * *";
            case DAILY -> prefix + "* * *";
            default -> throw new IllegalArgumentException("执行周期不正确");
        };
    }

    /** 返回全部校验错误;空列表表示可用 */
    public static List<String> validate(String type, String time, Integer day, Integer retentionDays) {
        List<String> errors = new ArrayList<>();
        String t = normalizeType(type);
        if (!DAILY.equals(t) && !WEEKLY.equals(t) && !MONTHLY.equals(t)) {
            errors.add("执行周期需为每天、每周或每月");
        }
        if (!TIME.matcher(normalizeTime(time)).matches()) {
            errors.add("执行时间格式不正确(需 HH:mm,例如 03:30)");
        }
        if (WEEKLY.equals(t)) {
            int d = day == null ? DEFAULT_WEEKLY_DAY : day;
            if (d < 1 || d > 7) errors.add("每周执行需选择周一至周日");
        } else if (MONTHLY.equals(t)) {
            int d = day == null ? DEFAULT_MONTHLY_DAY : day;
            if (d < 1 || d > 28) errors.add("每月执行日需在 1–28 之间");
        }
        if (retentionDays != null && (retentionDays < 1 || retentionDays > 3650)) {
            errors.add("保留天数需在 1–3650 之间,留空表示不自动清理");
        }
        if (errors.isEmpty()) {
            try {
                CronExpression.parse(cron(t, time, day));
            } catch (IllegalArgumentException e) {
                errors.add("执行周期格式不正确");
            }
        }
        return errors;
    }

    public static LocalDateTime next(String cron, LocalDateTime now) {
        if (cron == null || cron.isBlank()) return null;
        try {
            return CronExpression.parse(cron.trim()).next(now);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static String format(LocalDateTime time) {
        return time == null ? null : time.format(HUMAN);
    }

    public static boolean validTime(String time) {
        return TIME.matcher(normalizeTime(time)).matches();
    }

    public static LocalTime parseTime(String time) {
        Matcher m = TIME.matcher(normalizeTime(time));
        if (!m.matches()) return null;
        return LocalTime.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
    }
}
