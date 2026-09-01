package com.ihomy.aspect;

import cn.hutool.json.JSONUtil;
import com.ihomy.annotation.OperationLog;
import com.ihomy.common.DictConst;
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
            logEntry.setResultStatus(DictConst.LOG_SUCCESS);
            if (operationLog.saveResult() && result != null) {
                String ret = safeJson(result);
                if (ret != null && ret.length() > 2000) {
                    ret = ret.substring(0, 2000);
                }
                logEntry.setDescription(appendResult(logEntry.getDescription(), ret));
            }
        } catch (Throwable e) {
            thrown = e;
            logEntry.setResultStatus(DictConst.LOG_FAILED);
            String msg = e.getMessage();
            if (msg != null && msg.length() > 1000) {
                msg = msg.substring(0, 1000);
            }
            logEntry.setErrorMsg(msg);
            throw e;
        } finally {
            logEntry.setCostTime(System.currentTimeMillis() - start);
            // 业务操作日志双写:落库(原有,运维"操作日志"页)+ server 日志一行(带 tid,
            // 所有 @OperationLog 端点的关键业务节点自动进六要素日志,无需业务代码手动打)
            bizLog(logEntry);
            try {
                operationLogService.save(logEntry);
            } catch (Exception e) {
                OperationLogAspect.log.warn("操作日志切面保存失败: {}", e.getMessage());
            }
        }
        return result;
    }

    /** server 日志一行:成功 INFO / 失败 WARN(带错误摘要),格式见 docs/日志规范.md */
    private void bizLog(SysOperationLog logEntry) {
        String operator = logEntry.getOperatorId() != null
                ? logEntry.getOperatorName() + "#" + logEntry.getOperatorId() : "anonymous";
        String action = logEntry.getModule() + "." + logEntry.getOperationType()
                + (logEntry.getDescription() == null || logEntry.getDescription().isEmpty()
                        ? "" : " " + logEntry.getDescription());
        if (DictConst.LOG_FAILED.equals(logEntry.getResultStatus())) {
            String err = logEntry.getErrorMsg();
            OperationLogAspect.log.warn("[操作] {} 用户={} 结果=FAILED 耗时={}ms 错误={}",
                    action, operator, logEntry.getCostTime(),
                    err != null && err.length() > 200 ? err.substring(0, 200) : err);
        } else {
            OperationLogAspect.log.info("[操作] {} 用户={} 结果=SUCCESS 耗时={}ms",
                    action, operator, logEntry.getCostTime());
        }
    }

    /** 组装日志基础信息:注解内容 + 请求 IP/URL + 操作人(登录接口未登录,操作人留空) */
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
            logEntry.setIp(com.ihomy.common.Ips.realIp(req));
        }

        LoginUser u = securityHelper.current();
        if (u != null) {
            logEntry.setOperatorId(u.getUserId());
            logEntry.setOperatorName(u.getUsername());
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
