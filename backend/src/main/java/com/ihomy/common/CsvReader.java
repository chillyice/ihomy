package com.ihomy.common;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * 极简流式 CSV 读取器(手写,不引第三方库):按 RFC4180 处理引号转义与引号内换行。
 *
 * 逐条读取、不把整个文件读进内存(上传限制 2MB 也按流处理);每条记录记住起始行号,
 * 导入报错时能定位到用户 Excel 里看到的行。分隔符固定为英文逗号;空行跳过。
 */
public final class CsvReader implements AutoCloseable {

    /** 单条记录最多字符数:防止畸形文件把内存撑爆 */
    private static final int MAX_RECORD_CHARS = 20_000;

    private final Reader in;
    private int line = 1;
    /** 已读字符的回退位(读越界时把 EOF 记下来,EOF 后仍然要能收尾最后一条记录) */
    private int lookahead = Integer.MIN_VALUE;

    public CsvReader(Reader in) {
        this.in = in;
    }

    /** 一条记录:cells 已去掉引号,startLine 为记录在文件中的起始行号(从 1 开始) */
    public record Record(int startLine, List<String> cells) {
    }

    /** 读下一条记录;文件结束返回 null(完全空白的行被跳过) */
    public Record next() throws IOException {
        while (true) {
            int c = read();
            while (c != -1 && (c == '\r' || c == '\n')) {
                if (c == '\r') {
                    int next = read();
                    if (next != '\n' && next != -1) lookahead = next;
                }
                line++;
                c = read();
            }
            if (c == -1) return null;

            int startLine = line;
            List<String> cells = new ArrayList<>();
            StringBuilder cell = new StringBuilder();
            boolean quoted = false;
            int total = 0;

            while (c != -1) {
                if (quoted) {
                    if (c == '"') {
                        int next = read();
                        if (next == '"') {
                            cell.append('"');
                        } else {
                            quoted = false;
                            c = next;
                            continue;
                        }
                    } else {
                        if (c == '\n') line++;
                        cell.append((char) c);
                    }
                } else if (c == '"' && cell.isEmpty()) {
                    quoted = true;
                } else if (c == ',') {
                    cells.add(cell.toString());
                    cell.setLength(0);
                } else if (c == '\r' || c == '\n') {
                    if (c == '\r') {
                        int next = read();
                        if (next != '\n' && next != -1) lookahead = next;
                    }
                    line++;
                    break;
                } else {
                    cell.append((char) c);
                }
                if (++total > MAX_RECORD_CHARS) {
                    throw new IOException("单行内容过长");
                }
                c = read();
            }
            cells.add(cell.toString());
            // 纯空白行(单个空单元格)不算记录,继续读下一条
            if (cells.size() == 1 && cells.get(0).isBlank()) continue;
            return new Record(startLine, cells);
        }
    }

    private int read() throws IOException {
        if (lookahead != Integer.MIN_VALUE) {
            int v = lookahead;
            lookahead = Integer.MIN_VALUE;
            return v;
        }
        return in.read();
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
