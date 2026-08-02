package com.ihomy.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {

    String description() default "";

    String operationType() default "OTHER";

    String module() default "OTHER";

    boolean saveArgs() default true;

    boolean saveResult() default false;
}
