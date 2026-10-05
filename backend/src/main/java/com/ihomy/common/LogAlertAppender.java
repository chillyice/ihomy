package com.ihomy.common;

import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import com.ihomy.filter.TraceIdFilter;
import org.slf4j.MDC;

/**
 * 同类异常聚合预警的日志采集端(logback appender,见 logback-spring.xml):
 *
 * 只做两件极轻的事——取异常类型/首个应用栈帧 + 把事件交给已注册的 sink(LogAlertService),
 * 自身不做归一化以外的任何 IO;真正落库/发信在 sink 的后台线程完成,不阻塞业务线程。
 *
 * 三条防护:
 *   1. 未注册 sink(Spring 上下文尚未就绪)时直接丢弃,启动期日志不参与聚合;
 *   2. 预警自身的后台线程(名字以 CONSUMER_THREAD_PREFIX 开头)产生的日志不采集,防自噬死循环;
 *   3. append() 内部吞掉一切异常——预警链路出问题绝不能让业务日志打不出来。
 */
public class LogAlertAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    /** 预警后台消费者线程名前缀(LogAlertService 用同名线程,用于防自噬) */
    public static final String CONSUMER_THREAD_PREFIX = "ihomy-log-alert";

    /** 判为「应用栈帧」的包前缀:只认业务包,框架/网络栈帧对同类判定没有区分度 */
    private static final String APP_PACKAGE = "com.ihomy.";

    /** 事件接收端(由 LogAlertService 在启动时注册) */
    public interface Sink {
        void onEvent(LogAlertFingerprint.Event event);
    }

    private static volatile Sink sink;

    /** 由 LogAlertService @PostConstruct 注册;传 null 表示停止采集(关闭时) */
    public static void setSink(Sink s) {
        sink = s;
    }

    @Override
    protected void append(ILoggingEvent event) {
        try {
            Sink s = sink;
            if (s == null || event == null) {
                return;
            }
            String thread = Thread.currentThread().getName();
            if (thread != null && thread.startsWith(CONSUMER_THREAD_PREFIX)) {
                return;
            }
            IThrowableProxy tp = event.getThrowableProxy();
            s.onEvent(new LogAlertFingerprint.Event(
                    event.getLevel() == null ? null : event.getLevel().toString(),
                    event.getLoggerName(),
                    event.getFormattedMessage(),
                    tp == null ? null : tp.getClassName(),
                    tp == null ? null : tp.getMessage(),
                    firstAppFrame(tp),
                    MDC.get(TraceIdFilter.TRACE_ID)));
        } catch (Throwable ignored) {
            // 见类注释第 3 条:不得影响业务日志
        }
    }

    /** 首个应用栈帧(类#方法):取最靠近抛出点的一帧,不含行号以免改代码换行就重新预警 */
    private static String firstAppFrame(IThrowableProxy tp) {
        if (tp == null) {
            return null;
        }
        try {
            StackTraceElementProxy[] frames = tp.getStackTraceElementProxyArray();
            if (frames == null) {
                return null;
            }
            for (StackTraceElementProxy p : frames) {
                StackTraceElement el = p.getStackTraceElement();
                if (el == null || el.getClassName() == null) {
                    continue;
                }
                if (el.getClassName().startsWith(APP_PACKAGE)) {
                    return el.getClassName() + "#" + el.getMethodName();
                }
            }
        } catch (Throwable ignored) {
            // 栈帧解析失败退回「无栈帧」,仍可按异常类型聚合
        }
        return null;
    }
}
