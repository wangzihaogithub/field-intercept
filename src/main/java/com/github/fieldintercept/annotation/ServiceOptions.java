package com.github.fieldintercept.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 服务配置
 *
 * @author hao 2024年2月4日
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface ServiceOptions {

    /**
     * 是否作为远程方法
     *
     * @return true=是
     */
    boolean rpc() default true;

    /**
     * 使用注解继承
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.ANNOTATION_TYPE})
    @interface Extends {
    }

}
