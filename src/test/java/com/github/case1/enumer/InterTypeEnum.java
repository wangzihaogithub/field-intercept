package com.github.case1.enumer;

import com.github.fieldintercept.Enum;

/**
 * @author free.cao
 * @Desc 面试类型
 * @date 2021/9/28 14:18
 **/
public enum InterTypeEnum implements Enum<Integer, String> {
    PIPELINE_ITNTER_TYPE_NO("现场面试", 1, "#3AA1F7", "现场"),
    PIPELINE_ITNTER_TYPE_PART("电话面试", 2, "#F6B253", "电话"),
    PIPELINE_ITNTER_TYPE_ALL("视频面试", 3, "#F17F52", "视频"),
    ;
    private String value;
    private Integer key;
    private String color;
    private String display;

    InterTypeEnum(String value, Integer key, String color, String display) {
        this.value = value;
        this.key = key;
        this.color = color;
        this.display = display;
    }

    public String getColor() {
        return color;
    }

    public String getDisplay() {
        return display;
    }

    @Override
    public Integer getKey() {
        return key;
    }

    @Override
    public String getValue() {
        return value;
    }
}
