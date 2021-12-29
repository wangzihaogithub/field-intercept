package com.github.fieldintercept.annotation;

import com.github.fieldintercept.springboot.FieldInterceptAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 开启字段拦截
 *
 * @author hao 2021年12月12日23:06:09
 * @see FieldInterceptAutoConfiguration
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Inherited
@Import({FieldInterceptAutoConfiguration.class})
public @interface EnableFieldIntercept {
    /**
     * 业务实体类的包路径
     * <p>
     * 用于快速判断是否是业务实体类 ,如果是业务实体类,则会深度遍历访问内部字段
     *
     * @return 包路径. 例如 {"com.ig", "com.xx"}
     */
    String[] beanBasePackages() default {};

    /**
     * 是否并行查询
     *
     * @return true=用线程池并行,false=在调用者线程上串行
     */
    boolean parallelQuery() default true;
}