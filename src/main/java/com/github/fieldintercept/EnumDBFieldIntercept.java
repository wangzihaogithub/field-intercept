package com.github.fieldintercept;

import org.springframework.core.annotation.AnnotationUtils;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.function.BiFunction;

/**
 * 枚举注入 （查数据库版）
 *
 * @author hao
 */
public class EnumDBFieldIntercept extends KeyValueFieldIntercept<Object, Object> {
    private BiFunction<Set<String>, Collection<Object>, Map<String, Map<String, Object>>> selectEnumGroupKeyValueMap;

    public EnumDBFieldIntercept() {
    }

    public EnumDBFieldIntercept(int shareTimeout) {
        super(shareTimeout);
    }

    public EnumDBFieldIntercept(BiFunction<Set<String>, Collection<Object>, Map<String, Map<String, Object>>> selectEnumGroupKeyValueMap) {
        this.selectEnumGroupKeyValueMap = selectEnumGroupKeyValueMap;
    }

    public EnumDBFieldIntercept(int shareTimeout, BiFunction<Set<String>, Collection<Object>, Map<String, Map<String, Object>>> selectEnumGroupKeyValueMap) {
        super(shareTimeout);
        this.selectEnumGroupKeyValueMap = selectEnumGroupKeyValueMap;
    }

    public BiFunction<Set<String>, Collection<Object>, Map<String, Map<String, Object>>> getSelectEnumGroupKeyValueMap() {
        return selectEnumGroupKeyValueMap;
    }

    public void setSelectEnumGroupKeyValueMap(BiFunction<Set<String>, Collection<Object>, Map<String, Map<String, Object>>> selectEnumGroupKeyValueMap) {
        this.selectEnumGroupKeyValueMap = selectEnumGroupKeyValueMap;
    }

    @Override
    public Map<Object, Object> selectValueMapByKeys(List<CField> cFields, Collection<Object> keys) {
        Map<Object, Object> valueMap = new LinkedHashMap<>(5);
        Set<String> groupList = new LinkedHashSet<>();
        for (CField cField : cFields) {
            String[] groups = getGroups(cField.getAnnotation());
            groupList.addAll(Arrays.asList(groups));
        }

        Map<String, Map<String, Object>> groupKeyValueMap = selectEnumGroupKeyValueMap(groupList, keys);
        if (groupKeyValueMap == null) {
            if (selectEnumGroupKeyValueMap == null) {
                throw new UnsupportedOperationException("您的selectEnumGroupKeyValueMap方法未实现完全");
            }
            groupKeyValueMap = selectEnumGroupKeyValueMap.apply(groupList, keys);
        }
        if (groupKeyValueMap != null) {
            for (Map.Entry<String, Map<String, Object>> groupEntry : groupKeyValueMap.entrySet()) {
                String group = groupEntry.getKey();
                for (Map.Entry<String, Object> keyValueEntry : groupEntry.getValue().entrySet()) {
                    valueMap.putIfAbsent(group + "." + keyValueEntry.getKey(), keyValueEntry.getValue());
                }
            }
        }
        return valueMap;
    }

    /**
     * 从注解上获取组
     *
     * @param annotation 字段注解
     * @return 组
     */
    public String[] getGroups(Annotation annotation) {
        return (String[]) AnnotationUtils.getValue(annotation);
    }

    /**
     * 查数据库
     *
     * @param groups 组
     * @param keys   键
     * @return Map<group, Map < key, value>>
     */
    public Map<String, Map<String, Object>> selectEnumGroupKeyValueMap(Set<String> groups, Collection<Object> keys) {
        return null;
    }

    @Override
    protected Object[] rewriteKeyDataIfNeed(Object keyData, CField cField, Map<Object, Object> valueMap) {
        String[] groups = getGroups(cField.getAnnotation());
        if (groups == null) {
            return null;
        }
        String[] strings = new String[groups.length];
        for (int i = 0; i < groups.length; i++) {
            strings[i] = groups[i] + "." + keyData;
        }
        return strings;
    }
}
