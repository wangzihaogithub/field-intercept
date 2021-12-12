package com.github.fieldintercept;

import com.github.fieldintercept.annotation.EnumFieldConsumer;
import com.github.fieldintercept.annotation.FieldConsumer;
import org.aspectj.lang.JoinPoint;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class MainTest {
    public static final String CUSTOMER_USER = "CUSTOMER_USER";

    public static void main(String[] args) {
        class User {
            private Integer id;
            private String name;
            private Integer status;

            // 测试循环依赖 （互为leader）
            private Integer leaderUserId;
            @FieldConsumer(value = CUSTOMER_USER, keyField = "leaderUserId")
            private User leaderUser;

            @EnumFieldConsumer(value = CustomerUserStatusEnum.class, keyField = "status")
            private String statusName;
            @EnumFieldConsumer(value = CustomerUserStatusEnum.class, keyField = "status")
            private CustomerUserStatusEnum statusEnum;

            public User(Integer id, String name, Integer status, Integer leaderUserId) {
                this.id = id;
                this.name = name;
                this.status = status;
                this.leaderUserId = leaderUserId;
            }
        }
        class Department {
            private String memberUserIds = "1,2";
            private Integer leaderUserId = 1;

            @FieldConsumer(value = CUSTOMER_USER, keyField = "memberUserIds")
            private List<User> members;
            @FieldConsumer(value = CUSTOMER_USER, keyField = "leaderUserId")
            private User leader;
            @FieldConsumer(value = CUSTOMER_USER, keyField = "leaderUserId", valueField = "${id}")
            private Integer id1;
        }

        Map<String, BiConsumer<JoinPoint, List<CField>>> beanFactory = new HashMap<>();
        beanFactory.put(EnumFieldConsumer.NAME, new EnumFieldIntercept());
        beanFactory.put(CUSTOMER_USER, new KeyValueFieldIntercept<Integer, User>() {
            Map<Integer, User> databaseMap = new HashMap<>();

            {
                databaseMap.put(1, new User(1, "用户1", 0, 2));
                databaseMap.put(2, new User(2, "用户2", 1, 1));
            }

            @Override
            public Map<Integer, User> selectValueMapByKeys(Collection<Integer> ids) {
                return databaseMap;
            }
        });

        Department department = new Department();

        ReturnFieldDispatchAop dispatchAop = new ReturnFieldDispatchAop(beanFactory);
        dispatchAop.autowiredFieldValue(department);

        System.out.println("department = " + department);
    }

    public enum CustomerUserStatusEnum implements Enum<Integer, String> {
        WAIT_ACTIVATED("待激活"),
        NORMAL("正常"),
        DISABLE("停用"),
        ;
        private final String value;

        CustomerUserStatusEnum(String value) {
            this.value = value;
        }

        @Override
        public Integer getKey() {
            return ordinal();
        }

        @Override
        public String getValue() {
            return value;
        }
    }
}
