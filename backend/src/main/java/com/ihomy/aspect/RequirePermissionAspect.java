package com.ihomy.aspect;

import com.ihomy.annotation.RequirePermission;
import com.ihomy.common.BizException;
import com.ihomy.common.ResultCode;
import com.ihomy.security.SecurityHelper;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * @RequirePermission 注解的切面:执行前校验当前用户权限码,
 * 无权限直接抛 FORBIDDEN,不进入业务方法。
 */
@Aspect
@Component
@RequiredArgsConstructor
public class RequirePermissionAspect {

    private final SecurityHelper securityHelper;

    /** 拦截所有带 @RequirePermission 的方法,权限不足抛 403 */
    @Around("@annotation(permission)")
    public Object around(ProceedingJoinPoint pjp, RequirePermission permission) throws Throwable {
        if (!securityHelper.hasPermission(permission.value())) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        return pjp.proceed();
    }
}
