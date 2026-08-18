package com.ihomy.config;

import org.apache.ibatis.logging.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 自定义 MyBatis 日志:仅打印 SQL 语句与参数(==>),
 * 过滤结果集输出(<== 列/行/Total),减少日志占用。
 *
 * 改造点(2026-08-17):原版 isDebugEnabled() 恒 true + System.out.println 同步打印,
 * 生产环境每条 SQL 都打 stdout,既有 I/O 开销又污染日志。改为 SLF4J 后由 logging.level
 * 控制:`logging.level.com.ihomy.config.SqlStatementLog=debug` 才会输出。
 *
 * 日志类别名前缀 "mybatis.sql.",可在 application.yml 单独调级。
 */
public class SqlStatementLog implements Log {

    private final Logger logger;

    public SqlStatementLog(String clazz) {
        // 用统一 logger 名,便于在 application.yml 通过 logging.level.mybatis.sql 控制
        this.logger = LoggerFactory.getLogger("mybatis.sql." + clazz);
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
