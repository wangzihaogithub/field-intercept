package com.github.fieldintercept.entity;

public class ApplyOrder {

    private Integer applyStatus = 1;
    @EnumDBFieldConsumer(value = BaseEnumGroupEnum.APPLY, keyField = "applyStatus")
    private String applyStatusName;

}
