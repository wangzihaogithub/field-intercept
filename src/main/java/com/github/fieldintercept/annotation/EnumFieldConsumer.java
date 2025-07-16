package com.github.fieldintercept.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 枚举字段消费
 *
 * @author hao
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface EnumFieldConsumer {
    /**
     * 基础枚举名称
     */
    String NAME = "EnumFieldConsumer";
    /**
     * 基础枚举名称
     *
     * @return 枚举
     */
    String value() default NAME;

    /**
     * 枚举组
     *
     * @return 枚举
     */
    Class<? extends Enum>[] enumGroup();

    /**
     * 通常用于告知aop. id字段,或者key字段
     *
     * @return 字段名称
     */
    String[] keyField();

    /**
     * 通常用于告知aop. id字段,或者key字段
     * 支持占位符 （与spring的yaml相同， 支持spring的所有占位符表达式）， 比如 ‘${talentId} ${talentName} ${ig.env} ${random.int[25000,65000]}’
     * <p>
     * 例: 输入 "姓名${username}/部门${deptName}", 输出 "姓名xxx/部门xxx"
     * 例: valueField = { "${name}" }
     *
     * @return 字段名称
     */
    String[] valueField() default {"${value}"};

    /**
     * 多个拼接间隔符
     *
     * @return 分隔符
     */
    String joinDelimiter() default ",";

    /**
     * 使用注解继承
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.ANNOTATION_TYPE})
    @interface Extends {
    }

}
