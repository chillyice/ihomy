package com.ihomy.config;

import org.apache.ibatis.logging.Log;

/**
 * 自定义 MyBatis 日志：仅打印 SQL 语句与参数（==>），
 * 过滤结果集输出（<== 列/行/Total），减少日志占用。
 */
public class SqlStatementLog implements Log {

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
        System.err.println(s);
        e.printStackTrace(System.err);
    }

    @Override
    public void error(String s) {
        System.err.println(s);
    }

    @Override
    public void debug(String s) {
        if (s != null && !s.startsWith("<== ")) {
            System.out.println(s);
        }
    }

    @Override
    public void trace(String s) {
        if (s != null && !s.startsWith("<== ")) {
            System.out.println(s);
        }
    }

    @Override
    public void warn(String s) {
        if (s != null && !s.startsWith("<== ")) {
            System.out.println(s);
        }
    }
}