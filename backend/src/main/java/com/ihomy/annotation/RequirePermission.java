package com.ihomy.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限码注解:标注在接口方法上,value 为权限码(如 "comment:create"),
 * 由 RequirePermissionAspect 在调用前校验。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /** 所需权限码,须已存在于 sys_auth 并绑定到目标角色 */
    String value();
}
