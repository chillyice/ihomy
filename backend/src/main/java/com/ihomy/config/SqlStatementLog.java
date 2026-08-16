package com.ihomy.config;

import org.apache.ibatis.logging.Log;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * 自定义 MyBatis 日志：仅打印 SQL 语句与参数（==>），
 * 过滤结果集输出（<== 列/行/Total），减少日志占用。
 */
public class SqlStatementLog implements Log {

    // 显式 UTF-8 输出,避免 Windows 控制台 GBK 导致中文 SQL 参数乱码
    private static final PrintStream OUT = new PrintStream(System.out, true, StandardCharsets.UTF_8);
    private static final PrintStream ERR = new PrintStream(System.err, true, StandardCharsets.UTF_8);

    public SqlStatementLog(String clazz) {
    }

    @Override
    public boolean isDebugEnabled() {
        return true;
    }

    @Override
    public boolean isTraceEnabled() {
        return true;
    }

    @Override
    public void error(String s, Throwable e) {
        ERR.println(s);
        e.printStackTrace(ERR);
    }

    @Override
    public void error(String s) {
        ERR.println(s);
    }

    @Override
    public void debug(String s) {
        if (s != null && !s.startsWith("<== ")) {
            OUT.println(s);
        }
    }

    @Override
    public void trace(String s) {
        if (s != null && !s.startsWith("<== ")) {
            OUT.println(s);
        }
    }

    @Override
    public void warn(String s) {
        if (s != null && !s.startsWith("<== ")) {
            OUT.println(s);
        }
    }
}