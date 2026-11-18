package com.ihomy.common;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 健康数据 CSV 导出(纯文本拼装,便于单测)。
 * 用「长表」而不是按指标分列:kind 区分 SLEEP/STAGE/SAMPLE,与该行无关的列留空,
 * 这样新增指标不用改表头,Excel 透视与脚本读取都能直接吃。
 * 格式按 RFC 4180:含逗号/双引号/换行的字段整体加引号并把内部引号翻倍;
 * 文件头写 UTF-8 BOM(Excel 打开中文才不乱码),换行统一 CRLF。
 */
public final class HealthCsv {

    /** 固定 18 列表头;改这里等于改导出格式,前端/脚本消费方要同步 */
    public static final String HEADER = "kind,metric,period,value,min,max,count,unit,"
            + "deep_min,light_min,rem_min,awake_min,stage_level,sleep_date,start_time,end_time,source,provider";

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private HealthCsv() {
    }

    public static String time(LocalDateTime t) {
        return t == null ? "" : TIME.format(t);
    }

    public static String date(LocalDate d) {
        return d == null ? "" : d.toString();
    }

    /** 数值列:小数去掉多余的 0(72.00 → 72),避免 Excel 把整列当文本 */
    public static String num(BigDecimal v) {
        return v == null ? "" : v.stripTrailingZeros().toPlainString();
    }

    public static String num(Integer v) {
        return v == null ? "" : String.valueOf(v);
    }

    /** 单字段转义:只在必要时加引号,空值(NPE 安全的 null)输出空串 */
    public static String escape(String v) {
        if (v == null || v.isEmpty()) {
            return "";
        }
        boolean needQuote = v.indexOf(',') >= 0 || v.indexOf('"') >= 0
                || v.indexOf('\r') >= 0 || v.indexOf('\n') >= 0;
        return needQuote ? '"' + v.replace("\"", "\"\"") + '"' : v;
    }

    /** 一行:各列转义后以逗号连接 */
    public static String line(String... cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(cells[i]));
        }
        return sb.toString();
    }

    /** 完整文件:BOM + 表头 + 数据行,CRLF 换行 */
    public static byte[] toBytes(List<String> rows) {
        StringBuilder sb = new StringBuilder("\uFEFF").append(HEADER).append("\r\n");
        for (String r : rows) {
            sb.append(r).append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
