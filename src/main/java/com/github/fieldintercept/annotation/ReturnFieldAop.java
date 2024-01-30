package com.github.fieldintercept.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ReturnFieldAop {

    /**
     * 是否开启将N毫秒内的所有线程聚合到一起查询
     *
     * @return true=开启,false=不开启
     */
    boolean batchAggregation() default false;

    /**
     * 使用注解继承
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.ANNOTATION_TYPE})
    @interface Extends {
    }

}
