package com.github.fieldintercept.entity;

public class ApplyOrder {

    private Integer applyStatus = 1;
    @com.github.fieldintercept.annotation.EnumDBFieldConsumer(enumGroup = "apply", keyField = "applyStatus")
    private String applyStatusName;

}
