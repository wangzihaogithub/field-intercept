package com.github.fieldintercept.annotation;

import com.github.fieldintercept.ReturnFieldDispatchAop;
import com.github.fieldintercept.springboot.FieldInterceptImportSelector;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 开启字段拦截
 *
 * @author hao 2021年12月12日23:06:09
 * @see FieldInterceptImportSelector
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Inherited
@Import({FieldInterceptImportSelector.class})
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

    /**
     * 并行查询线程数量
     * 如果并发超过线程数量，超出的部分会在调用者线程上执行
     *
     * @return 线程数量
     */
    int parallelQueryMaxThreads() default 100;

    /**
     * 是否开启将N毫秒内的多个并发请求攒到一起处理
     *
     * @return true=开启,false=不开启
     */
    boolean batchAggregation() default false;

    /**
     * 攒多个并发请求的等待时间（毫秒）
     *
     * @return 将N毫秒内的所有线程聚合到一起查询
     */
    long batchAggregationMilliseconds() default 10L;

    /**
     * 超过这个并发请求的数量后，才开始攒批。 否则立即执行
     *
     * @return 攒批的并发量最低要求
     */
    int batchAggregationMinConcurrentCount() default 1;

    /**
     * 注册自定义注解
     * 1. 自定义注解可以像使用 FieldConsumer注解一样，拦截字段处理逻辑
     * 2. 自定义注解可以覆盖框架注解
     * 前提
     * 1. spring容器里必须有和注解短类名相同的bean。例： com.ig.MyAnnotation的名字是MyAnnotation。 {@link ReturnFieldDispatchAop#getMyAnnotationConsumerName(Class)}
     * 2. bean需要实现接口处理自定义逻辑 {@link ReturnFieldDispatchAop.FieldIntercept}
     *
     * @return 需要添加的自定义注解
     */
    Class<? extends Annotation>[] myAnnotations() default {};
}