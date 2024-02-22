package com.github.fieldintercept.entity;

import com.github.fieldintercept.CField;
import com.github.fieldintercept.EnumFieldIntercept;
import com.github.fieldintercept.KeyValueFieldIntercept;
import com.github.fieldintercept.ReturnFieldDispatchAop;
import com.github.fieldintercept.annotation.EnumFieldConsumer;
import com.github.securityfilter.util.AccessUserUtil;
import org.aspectj.lang.JoinPoint;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class Const {
    public static final String CUSTOMER_USER = "CUSTOMER_USER?groupKeyStaticMethod=com.github.fieldintercept.entity.Const#groupBy";
    public static final String CUSTOMER_DEPT = "CUSTOMER_DEPT?groupKeyStaticMethod=com.github.fieldintercept.entity.Const#groupBy";
    public static final Map<String, BiConsumer<Object, List<CField>>> BEAN_FACTORY = newBeanFactory();

    public static <JOIN_POINT> Object groupBy(String beanName, BiConsumer<?, List<CField>> consumer, JoinPoint joinPoint,
                                              Department department, ReturnFieldDispatchAop.GroupCollect<JOIN_POINT> groupCollect,
                                              ReturnFieldDispatchAop.Pending<JOIN_POINT> pending, List<ReturnFieldDispatchAop.Pending<JOIN_POINT>> pendings,
                                              ReturnFieldDispatchAop<JOIN_POINT> aop, int q,boolean f) {
        return AccessUserUtil.getAccessUser();
    }

    public static class Department1 {
        private String name;
        public void setName(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    private static Map<String, BiConsumer<Object, List<CField>>> newBeanFactory() {
        Map<String, BiConsumer<Object, List<CField>>> beanFactory = new HashMap<>();
        // 枚举
        beanFactory.put(EnumFieldConsumer.NAME, new EnumFieldIntercept());
        // 用户
        beanFactory.put(CUSTOMER_USER, new KeyValueFieldIntercept<Integer, User, Object>() {
            @Override
            public Map<Integer, User> selectValueMapByKeys(Collection<Integer> ids) {
                Map<Integer, User> databaseMap = new HashMap<>();
                databaseMap.put(1, new User(1, "用户1", 0, 2, "1,2"));
                databaseMap.put(2, new User(2, "用户2", 1, 1, "2,3"));
                return databaseMap;
            }
        });
        // 部门
        beanFactory.put(CUSTOMER_DEPT, new KeyValueFieldIntercept<Integer, Department1, Object>() {
            @Override
            public Map<Integer, Department1> selectValueMapByKeys(Collection<Integer> ids) {
                Map<Integer, Department1> databaseMap = new HashMap<>();
                Department1 department = new Department1();
                department.setName("1");
                databaseMap.put(1, department);
                Department1 department2 = new Department1();
                department2.setName("2");
                databaseMap.put(2, department2);
                return databaseMap;
            }
        });

        return beanFactory;
    }

}
