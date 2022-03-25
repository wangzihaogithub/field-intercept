package com.github.fieldintercept.entity;

public class ApplyOrder {

    private Integer applyStatus = 1;
    @com.github.fieldintercept.annotation.EnumDBFieldConsumer(value = "apply", keyField = "applyStatus")
    private String applyStatusName;

}
