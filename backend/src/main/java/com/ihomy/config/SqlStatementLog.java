package com.ihomy.config;

import org.apache.ibatis.logging.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 自定义 MyBatis 日志:仅打印 SQL 语句与参数(==>),
 * 过滤结果集输出(<== 列/行/Total),减少日志占用。
 *
 * logger 名统一加 "mybatis.sql." 前缀,可在 application.yml/logback-spring.xml 单独调级
 * (当前 DEBUG 恒开,只进 server 日志文件)。
 *
 * 内部类过滤(mybatis-spring 的 SqlSessionUtils/Transaction 每条 SQL 多打 4-6 行会话管理噪音):
 * org.mybatis / org.apache.ibatis 开头的内部类改挂 "mybatis.sql.internal." 前缀,
 * 该前缀未配置 DEBUG,框架内部 DEBUG 静默,ERROR/WARN 仍进 root(控制台+server 文件)。
 */
public class SqlStatementLog implements Log {

    private final Logger logger;

    public SqlStatementLog(String clazz) {
        if (clazz.startsWith("org.mybatis") || clazz.startsWith("org.apache.ibatis")) {
            this.logger = LoggerFactory.getLogger("mybatis.sql.internal." + clazz);
        } else {
            this.logger = LoggerFactory.getLogger("mybatis.sql." + clazz);
        }
    }

    @Override
    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    @Override
    public boolean isTraceEnabled() {
        return logger.isTraceEnabled();
    }

    @Override
    public void error(String s, Throwable e) {
        logger.error(s, e);
    }

    @Override
    public void error(String s) {
        logger.error(s);
    }

    @Override
    public void debug(String s) {
        // 过滤结果集输出(<== 列/行/Total),只保留 SQL 语句与参数(==>)
        if (s != null && !s.startsWith("<== ")) {
            logger.debug(s);
        }
    }

    @Override
    public void trace(String s) {
        if (s != null && !s.startsWith("<== ")) {
            logger.trace(s);
        }
    }

    @Override
    public void warn(String s) {
        if (s != null && !s.startsWith("<== ")) {
            logger.warn(s);
        }
    }
}
