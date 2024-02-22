package com.github.fieldintercept.entity;

import com.github.fieldintercept.annotation.FieldConsumer;

import java.util.List;
import java.util.Set;

import static com.github.fieldintercept.entity.Const.CUSTOMER_USER;

public class Department {
    private String memberUserIds = "1,2";
    private Integer leaderUserId = 1;
    @FieldConsumer(value = CUSTOMER_USER, keyField = "memberUserIds", valueField = "${status}")
    private Set<Integer> statusList;
    @FieldConsumer(value = CUSTOMER_USER, keyField = "memberUserIds", valueField = "${status}")
    private Set<UserStatusEnum> statusEnumSet;
    @FieldConsumer(value = CUSTOMER_USER, keyField = "memberUserIds")
    private List<User> members;
    @FieldConsumer(value = CUSTOMER_USER, keyField = "leaderUserId")
    private User leader;
    @FieldConsumer(value = CUSTOMER_USER, keyField = "leaderUserId", valueField = "${id}")
    private Integer id1;
    @FieldConsumer(value = CUSTOMER_USER, keyField = "leaderUserId", valueField = "${childIds}")
    private Set<Integer> childIds;

    private String name;
    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
