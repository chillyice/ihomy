package com.ihomy.aspect;

import cn.hutool.json.JSONUtil;
import com.ihomy.annotation.OperationLog;
import com.ihomy.entity.SysOperationLog;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;

/**
 * @OperationLog 注解的切面:环绕记录操作日志——
 * 记录操作人/请求信息/耗时/成败,可选保存入参与结果(截断防超长)。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final OperationLogService operationLogService;
    private final SecurityHelper securityHelper;

    /** 环绕增强:无论成功失败都落库一条日志,失败时保存错误信息并继续抛出 */
    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint pjp, OperationLog operationLog) throws Throwable {
        long start = System.currentTimeMillis();
        SysOperationLog logEntry = buildBaseLog(operationLog, pjp);

        Object result = null;
        Throwable thrown = null;
        try {
            result = pjp.proceed();
            logEntry.setResultStatus(1);
            if (operationLog.saveResult() && result != null) {
                String ret = safeJson(result);
                if (ret != null && ret.length() > 2000) {
                    ret = ret.substring(0, 2000);
                }
                logEntry.setDescription(appendResult(logEntry.getDescription(), ret));
            }
        } catch (Throwable e) {
            thrown = e;
            logEntry.setResultStatus(0);
            String msg = e.getMessage();
            if (msg != null && msg.length() > 1000) {
                msg = msg.substring(0, 1000);
            }
            logEntry.setErrorMsg(msg);
            throw e;
        } finally {
            logEntry.setCostTime(System.currentTimeMillis() - start);
            try {
                operationLogService.save(logEntry);
            } catch (Exception e) {
                OperationLogAspect.log.warn("操作日志切面保存失败: {}", e.getMessage());
            }
        }
        return result;
    }

    /** 组装日志基础信息:注解内容 + 请求 IP/URL + 操作人(未登录时尝试从参数反推用户名) */
    private SysOperationLog buildBaseLog(OperationLog ann, ProceedingJoinPoint pjp) {
        SysOperationLog logEntry = new SysOperationLog();
        logEntry.setOperationType(ann.operationType());
        logEntry.setModule(ann.module());
        logEntry.setDescription(ann.description());
        logEntry.setTraceId(org.slf4j.MDC.get(com.ihomy.filter.TraceIdFilter.TRACE_ID));

        HttpServletRequest req = currentRequest();
        if (req != null) {
            logEntry.setRequestMethod(req.getMethod());
            logEntry.setRequestUrl(req.getRequestURI());
            logEntry.setIp(resolveIp(req));
        }

        LoginUser u = securityHelper.current();
        if (u != null) {
            logEntry.setOperatorId(u.getUserId());
            logEntry.setOperatorName(u.getUsername());
        } else {
            Object[] args = pjp.getArgs();
            if (args != null && args.length > 0 && args[0] != null) {
                String guess = tryGuessUsername(args[0]);
                if (guess != null) {
                    logEntry.setOperatorName(guess);
                }
            }
        }

        if (ann.saveArgs()) {
            try {
                String params = safeJson(pjp.getArgs());
                if (params != null && params.length() > 4000) {
                    params = params.substring(0, 4000);
                }
                logEntry.setRequestParams(params);
            } catch (Exception ignored) {
            }
        }
        return logEntry;
    }

    /** 通过反射尝试取第一个参数的 username(登录场景) */
    private String tryGuessUsername(Object arg) {
        try {
            return (String) arg.getClass().getMethod("getUsername").invoke(arg);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 依次取代理头中的真实 IP,取不到回退 remoteAddr,多级代理取首个地址 */
    private String resolveIp(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = req.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = req.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = req.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            return sra.getRequest();
        }
        return null;
    }

    /** 序列化对象为 JSON,排除请求/响应对象,序列化失败时降级为 toString */
    private String safeJson(Object obj) {
        try {
            if (obj == null) return null;
            if (obj instanceof Object[] arr) {
                return JSONUtil.toJsonStr(Arrays.stream(arr)
                        .filter(x -> !(x instanceof HttpServletRequest)
                                && !(x instanceof jakarta.servlet.http.HttpServletResponse))
                        .toList());
            }
            return JSONUtil.toJsonStr(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }

    private String appendResult(String desc, String result) {
        if (desc == null || desc.isEmpty()) return "结果: " + result;
        return desc + " | 结果: " + result;
    }
}
