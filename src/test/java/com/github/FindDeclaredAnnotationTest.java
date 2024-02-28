package com.github;

import com.github.fieldintercept.annotation.FieldConsumer;
import com.github.fieldintercept.annotation.ReturnFieldAop;
import com.github.fieldintercept.annotation.RouterFieldConsumer;
import com.github.fieldintercept.util.AnnotationUtil;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;

public class FindDeclaredAnnotationTest {
    public static void main(String[] args) throws NoSuchMethodException, NoSuchFieldException {
        f();
        System.out.println("declaredAnnotation = ");
    }

    private static void f() throws NoSuchMethodException, NoSuchFieldException {
        FindDeclaredAnnotationTest test = new FindDeclaredAnnotationTest();
        Method mm = test.getClass().getDeclaredMethod("mm");

        VO mm1 = test.mm();
        Field userName = mm1.getClass().getDeclaredField("userName");

        RouterFieldConsumer declaredAnnotation = AnnotationUtil.cast(AnnotationUtil.findExtendsAnnotation(userName.getDeclaredAnnotations(), Arrays.asList(RouterFieldConsumer.class, RouterFieldConsumer.Extends.class), new HashMap<>()), RouterFieldConsumer.class);
        FieldConsumer[] value = declaredAnnotation.value();
        for (FieldConsumer fieldConsumer : value) {
            String[] strings = fieldConsumer.keyField();
            System.out.println("strings = " + strings);
        }
        String s = declaredAnnotation.routerField();

        declaredAnnotation.toString();
    }

    public VO mm() {
        VO vo = new VO();
        vo.userId = "31";
        return vo;
    }

    public static class VO {
        private String deptId;
        private String userId;

        @RouterFieldConsumer1(routerField = 1, value = @FieldConsumer1(keyField = "23"))
        @FieldConsumer1(value = "USER", keyField = "userId")
        private String userName;

    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @ReturnFieldAop.Extends
    public @interface ReturnFieldAop1 {

        /**
         * 是否开启将N毫秒内的所有线程聚合到一起查询
         *
         * @return true=开启,false=不开启
         */
        boolean batchAggregation() default true;
    }


    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD})
    @FieldConsumer.Extends
    public static @interface FieldConsumer1 {

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
         *
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
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD})
    @RouterFieldConsumer.Extends
    public @interface RouterFieldConsumer1 {

        /**
         * 消费
         *
         * @return 消费注解
         */
        FieldConsumer1[] value();

        /**
         * 路由的字段.
         * 例: 如果字段[objectType]的值为 'p_user_id', 则用@FieldConsumer(value = P_USER_NAME,keyField = "p_user_id")
         * 例: 如果字段[objectType]的值为 'p_dept_id', 则用@FieldConsumer(value = P_DEPT_NAME,keyField = "p_dept_id")
         *
         * @return 哪个字段用于路由
         */
        int routerField();

        /**
         * 如果都不匹配， 使用这个
         *
         * @return
         */
        FieldConsumer1 defaultElse() default @FieldConsumer1;
    }
}
