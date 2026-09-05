package com.ihomy.common;

import com.ihomy.aspect.OperationLogAspect;
import com.ihomy.entity.SysOperationLog;
import com.ihomy.filter.TraceIdFilter;
import com.ihomy.security.LoginUser;
import com.ihomy.security.SecurityHelper;
import com.ihomy.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常处理器:统一捕获控制器抛出的各类异常,
 * 转换为 { code, message } 结构返回,避免异常堆栈直接暴露给前端。
 * 同时补记"无 @OperationLog 注解接口(读接口等)"的异常到操作日志,让运维操作日志页能按 tid 看到读接口报错。
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final OperationLogService operationLogService;
    private final SecurityHelper securityHelper;

    /** 业务异常:直接透传错误码与提示 */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        log.warn("业务异常: {}", e.getMessage());
        recordIfNotAnnotated(e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /** @RequestBody 校验失败:取第一个字段错误提示 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst().map(f -> f.getField() + ": " + f.getDefaultMessage())
                .orElse("参数校验失败");
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), msg);
    }

    @ExceptionHandler(BindException.class)
    public Result<Void> handleBind(BindException e) {
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), e.getMessage());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraint(ConstraintViolationException e) {
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), e.getMessage());
    }

    /** 路径/请求参数类型转换失败(如 Long 参数收到 "abc"):400,不再走兜底 500 + 全堆栈 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型错误: {}", e.getMessage());
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), "参数类型错误: " + e.getName());
    }

    /** 请求体 JSON 解析失败(格式非法/编码错误):400 而非 500 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), "请求体格式错误");
    }

    /** 必填请求参数缺失:400 而非 500 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("缺少请求参数: {}", e.getParameterName());
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), "缺少参数: " + e.getParameterName());
    }

    /** 兜底异常:记录完整堆栈后返回 500,避免泄露内部细节 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("系统异常", e);
        recordIfNotAnnotated(e.getMessage());
        return Result.fail(ResultCode.INTERNAL_ERROR);
    }

    /**
     * 补记无 @OperationLog 注解接口(读接口等)的异常到操作日志。
     * 有 @OperationLog 的写接口异常已由切面记录并打上 OPLOG_RECORDED_ATTR 标记,此处据此跳过,避免重复。
     */
    private void recordIfNotAnnotated(String errorMsg) {
        try {
            HttpServletRequest req = currentRequest();
            if (req == null || Boolean.TRUE.equals(req.getAttribute(OperationLogAspect.OPLOG_RECORDED_ATTR))) {
                return;
            }
            SysOperationLog entry = new SysOperationLog();
            entry.setModule("REQUEST");
            entry.setOperationType(req.getMethod());
            entry.setDescription("接口异常");
            entry.setResultStatus(DictConst.LOG_FAILED);
            entry.setErrorMsg(truncate(errorMsg, 1000));
            entry.setRequestMethod(req.getMethod());
            entry.setRequestUrl(req.getRequestURI());
            entry.setIp(Ips.realIp(req));
            entry.setTraceId(MDC.get(TraceIdFilter.TRACE_ID));
            LoginUser u = securityHelper.current();
            if (u != null) {
                entry.setOperatorId(u.getUserId());
                entry.setOperatorName(u.getUsername());
            }
            operationLogService.save(entry);
        } catch (Exception ex) {
            log.warn("补记接口异常操作日志失败: {}", ex.getMessage());
        }
    }

    private HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            return sra.getRequest();
        }
        return null;
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
