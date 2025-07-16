package com.github.fieldintercept.entity;

import com.github.fieldintercept.annotation.EnumFieldConsumer;
import com.github.fieldintercept.annotation.FieldConsumer;

import static com.github.fieldintercept.entity.Const.CUSTOMER_USER;

public class User {
    private Integer id;
    private String name;
    private Integer status;

    // 测试循环依赖 （互为leader）
    private Integer leaderUserId;
    @FieldConsumer(value = CUSTOMER_USER, keyField = "leaderUserId")
    private User leaderUser;

    @EnumFieldConsumer(enumGroup = UserStatusEnum.class, keyField = "status")
    private String statusName;
    @EnumFieldConsumer(enumGroup = UserStatusEnum.class, keyField = "status")
    private UserStatusEnum statusEnum;

    private String childIds;

    public User(Integer id, String name, Integer status, Integer leaderUserId, String childIds) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.leaderUserId = leaderUserId;
        this.childIds = childIds;
    }
}