package com.github.fieldintercept.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段消费
 *
 * @author hao 2021年12月12日19:40:42
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface FieldConsumer {
    /*
     * 返回key名称一致
     */
    String NAME_USED_KEY = "UsedKeyFieldIntercept";

    /**
     * 类型
     * 例: UserServiceImpl
     *
     * @return bean的名字
     */
    String value() default "";

    /**
     * 通常用于告知aop. id字段,或者key字段
     * 例: userId
     *
     * @return 字段名称
     */
    String[] keyField() default "";

    /**
     * 通常用于告知aop. id字段,或者key字段
     * 支持占位符 （与spring的yaml相同， 支持spring的所有占位符表达式）， 比如 ‘${talentId} ${talentName} ${ig.env} ${random.int[25000,65000]}’
     * <p>
     * 例: 输入 "姓名${username}/部门${deptName}", 输出 "姓名xxx/部门xxx"
     * .或者./表示以当前字段名称为解构
     * @return 字段名称
     */
    String[] valueField() default {};

    /**
     * 如果注入的字段类型为String, 并且出现了多条记录, 则用这个符号拼接
     *
     * @return 分隔符
     */
    String joinDelimiter() default ",";

    /**
     * 分类, 用于字段的路由
     *
     * @return 路由分类
     */
    String type() default "";

    /**
     * 使用注解继承
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.ANNOTATION_TYPE})
    @interface Extends {
    }

}
