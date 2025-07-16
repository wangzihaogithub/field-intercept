package com.github.fieldintercept.annotation;

import com.github.fieldintercept.CField;
import com.github.fieldintercept.util.AnnotationUtil;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Function;

/**
 * 枚举数据库字段消费
 *
 * @author hao
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface EnumDBFieldConsumer {
    /**
     * 基础枚举名称
     */
    String NAME = "EnumDBFieldConsumer";

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
    String[] enumGroup();

    /**
     * value解析
     *
     * @return value解析
     */
    Class<? extends ValueParser> valueParser() default DefaultValueParser.class;

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
    String[] valueField() default {"${name}"};

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

    interface ValueParser extends Function<CField, String[]> {

    }

    class DefaultValueParser implements ValueParser {
        @Override
        public String[] apply(CField cField) {
            Object value = AnnotationUtil.getValue(cField.getAnnotation(), "enumGroup");
            if (value instanceof String[]) {
                return (String[]) value;
            } else if (value instanceof String) {
                return new String[]{(String) value};
            } else {
                return null;
            }
        }
    }

}
