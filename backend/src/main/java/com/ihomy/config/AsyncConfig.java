package com.ihomy.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * @Async 异步线程配置:虚拟线程 + MDC 装饰器。
 * 没有它,@Async 方法(操作日志落库/设备目录映射任务)会丢失 tid,
 * 异步线程里打的日志和 SQL 全部串不到发起请求的链路上。
 */
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    /** 复制提交线程的 MDC(含 traceId)到异步线程,执行完还原 */
    static class MdcTaskDecorator implements TaskDecorator {
        @Override
        public Runnable decorate(Runnable runnable) {
            Map<String, String> context = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                if (context != null) {
                    MDC.setContextMap(context);
                } else {
                    MDC.clear();
                }
                try {
                    runnable.run();
                } finally {
                    if (previous != null) {
                        MDC.setContextMap(previous);
                    } else {
                        MDC.clear();
                    }
                }
            };
        }
    }

    @Override
    public Executor getAsyncExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("ihomy-async-");
        executor.setVirtualThreads(true);
        executor.setTaskDecorator(new MdcTaskDecorator());
        return executor;
    }
}
