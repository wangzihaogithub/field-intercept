package com.github.fieldintercept.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 路由字段消费者.
 *
 *  代码示例
 * <code>
 *     \@RouterFieldConsumer(
 *          routerField = "objectType",
 *          value = {
 *                  \@FieldConsumer(value = RETURN_KEY,keyField = "objectId",type = "all"),
 *                  \@FieldConsumer(value = P_USER_NAME,keyField = "objectId",type = "p_user_id"),
 *                  \@FieldConsumer(value = P_DEPT_NAME,keyField = "objectId",type = "p_dept_id"),
 *                  \@FieldConsumer(value = RETURN_KEY,keyField = "objectId",type = "p_user_level")
 *     })
 *     private String shareObjectNames;
 *
 * </code>
 *
 * @author acer01 2019年11月19日 16:06:45
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface RouterFieldConsumer {

    /**
     *  消费
     * @return 消费注解
     */
    FieldConsumer[] value();

    /**
     * 路由的字段.
     * 例: 如果字段[objectType]的值为 'p_user_id', 则用@FieldConsumer(value = P_USER_NAME,keyField = "p_user_id")
     * 例: 如果字段[objectType]的值为 'p_dept_id', 则用@FieldConsumer(value = P_DEPT_NAME,keyField = "p_dept_id")
     *
     * @return 哪个字段用于路由
     */
    String routerField();

    /**
     * 如果都不匹配， 使用这个
     *
     * @return
     */
    FieldConsumer defaultElse() default @FieldConsumer;
}
