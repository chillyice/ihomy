package com.ihomy.service.backup;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.sql.Types;

/**
 * 纯 JDBC 逻辑备份的 SQL 字面量工具。
 * 不依赖 mysqldump:应用账号只有 DML 权限,但 SELECT + SHOW CREATE TABLE 足以导出可恢复的 SQL。
 */
public final class BackupSql {

    private BackupSql() {
    }

    /** 字符串转 SQL 单引号字面量;MySQL 默认未开启 NO_BACKSLASH_ESCAPES,用反斜杠转义 */
    public static String quote(String s) {
        if (s == null) return "NULL";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        sb.append('\'');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '\'' -> sb.append("\\'");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\0' -> sb.append("\\0");
                case '\u001a' -> sb.append("\\Z");
                default -> sb.append(c);
            }
        }
        return sb.append('\'').toString();
    }

    /** JDBC 取出的值 → SQL 字面量。byte[] 用十六进制,时间统一成 MySQL 可解析的字符串。 */
    public static String literal(Object value) {
        if (value == null) return "NULL";
        if (value instanceof byte[] bytes) return "0x" + hex(bytes);
        if (value instanceof Boolean b) return b ? "1" : "0";
        if (value instanceof Number n) return n.toString();
        if (value instanceof Timestamp ts) {
            String text = ts.toString();
            // Timestamp.toString 形如 2026-11-16 02:30:00.0;去掉无意义的 .0,避免列无小数位时告警
            if (text.endsWith(".0")) text = text.substring(0, text.length() - 2);
            return quote(text);
        }
        if (value instanceof java.util.Date d && !(value instanceof Date) && !(value instanceof Time)) {
            return quote(new Timestamp(d.getTime()).toString().replaceFirst("\\.0$", ""));
        }
        if (value instanceof Date || value instanceof Time) return quote(value.toString());
        if (value instanceof LocalDate ld) return quote(ld.toString());
        if (value instanceof java.time.LocalDateTime ldt) return quote(ldt.toString().replace('T', ' '));
        if (value instanceof java.time.LocalTime lt) return quote(lt.toString());
        return quote(value.toString());
    }

    /** 某些驱动把 BIT(1)/BOOLEAN 报成 BIT,用列类型兜底成 0/1 */
    public static String literal(Object value, int sqlType) {
        if ((sqlType == Types.BIT || sqlType == Types.BOOLEAN) && value != null) {
            if (value instanceof Boolean b) return b ? "1" : "0";
            if (value instanceof Number n) return n.longValue() == 0 ? "0" : "1";
            if (value instanceof byte[] bytes && bytes.length > 0) {
                for (byte b : bytes) {
                    if (b != 0) return "1";
                }
                return "0";
            }
        }
        return literal(value);
    }

    public static String hex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        final char[] digits = "0123456789ABCDEF".toCharArray();
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = digits[v >>> 4];
            out[i * 2 + 1] = digits[v & 0x0F];
        }
        return new String(out);
    }
}
