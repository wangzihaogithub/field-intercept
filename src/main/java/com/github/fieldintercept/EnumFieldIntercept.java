package com.github.fieldintercept;

import com.github.fieldintercept.util.AnnotationUtil;

import java.lang.annotation.Annotation;
import java.util.*;

/**
 * 枚举注入 （java enum版）
 *
 * @author hao
 */
public class EnumFieldIntercept extends KeyValueFieldIntercept<Object, Object, Object> {

    public EnumFieldIntercept() {
    }

    public EnumFieldIntercept(int shareTimeout) {
        super(shareTimeout);
    }

    private static String getGroup(Class<? extends java.lang.Enum> type) {
        return type.getName();
    }

    private static Class<? extends java.lang.Enum>[] getEnumClasses(Annotation annotation) {
        return (Class<? extends java.lang.Enum>[]) AnnotationUtil.getValue(annotation);
    }

    @Override
    public Map<Object, Object> selectValueMapByKeys(List<CField> cFields, Collection<Object> keys) {
        Map<Object, Object> nameMap = new HashMap<>(5);
        for (CField cField : cFields) {
            Class<? extends java.lang.Enum>[] enumClasses = getEnumClasses(cField.getAnnotation());
            for (Class<? extends java.lang.Enum> enumClass : enumClasses) {
                String group = getGroup(enumClass);
                Collection<? extends java.lang.Enum> enumSet = EnumSet.allOf(enumClass);
                for (java.lang.Enum itemEnum : enumSet) {
                    Object key;
                    if (itemEnum instanceof Enum) {
                        key = ((Enum) itemEnum).getKey();
                    } else {
                        key = itemEnum.name();
                        nameMap.putIfAbsent(group + "." + itemEnum.ordinal(), itemEnum);
                    }
                    nameMap.putIfAbsent(group + "." + key, itemEnum);
                }
            }
        }
        return nameMap;
    }

    @Override
    protected Object[] rewriteKeyDataIfNeed(Object keyData, CField cField, Map<Object, Object> valueMap) {
        Class<? extends java.lang.Enum>[] baseEnumGroupEnum = getEnumClasses(cField.getAnnotation());
        if (baseEnumGroupEnum == null) {
            return null;
        }
        String[] strings = new String[baseEnumGroupEnum.length];
        for (int i = 0; i < baseEnumGroupEnum.length; i++) {
            strings[i] = getGroup(baseEnumGroupEnum[i]) + "." + keyData;
        }
        return strings;
    }
}
