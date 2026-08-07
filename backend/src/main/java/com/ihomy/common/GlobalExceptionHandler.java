package com.ihomy.common;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器:统一捕获控制器抛出的各类异常,
 * 转换为 { code, message } 结构返回,避免异常堆栈直接暴露给前端。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常:直接透传错误码与提示 */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        log.warn("业务异常: {}", e.getMessage());
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

    /** 认证失败(未登录/凭证失效)统一返回 401 */
    @ExceptionHandler(AuthenticationException.class)
    public Result<Void> handleAuth(AuthenticationException e) {
        return Result.fail(ResultCode.UNAUTHORIZED);
    }

    /** 权限不足统一返回 403 */
    @ExceptionHandler(AccessDeniedException.class)
    public Result<Void> handleDenied(AccessDeniedException e) {
        return Result.fail(ResultCode.FORBIDDEN);
    }

    /** 兜底异常:记录完整堆栈后返回 500,避免泄露内部细节 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("系统异常", e);
        return Result.fail(ResultCode.INTERNAL_ERROR);
    }
}
