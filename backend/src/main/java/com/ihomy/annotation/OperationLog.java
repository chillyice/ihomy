package com.ihomy.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解:标注在接口方法上,由 OperationLogAspect 自动记录
 * 操作类型/模块/描述,可配置是否保存入参与结果。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {

    /** 操作描述,如 "新增博客" */
    String description() default "";

    /** 操作类型(LOGIN/LOGOUT/CREATE/UPDATE/DELETE...),默认 OTHER */
    String operationType() default "OTHER";

    /** 所属模块(BLOG/DIARY/ALBUM...),默认 OTHER */
    String module() default "OTHER";

    /** 是否保存方法入参 */
    boolean saveArgs() default true;

    /** 是否保存方法返回值 */
    boolean saveResult() default false;
}
