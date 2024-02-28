package com.github.fieldintercept;

import com.github.fieldintercept.annotation.EnumDBFieldConsumer;
import com.github.fieldintercept.util.AnnotationUtil;
import com.github.fieldintercept.util.BeanUtil;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * 枚举注入 （查数据库版）
 *
 * @author hao
 */
public class EnumDBFieldIntercept<JOIN_POINT> extends KeyValueFieldIntercept<Object, Object, JOIN_POINT> {
    private final Map<Class<? extends EnumDBFieldConsumer.ValueParser>, EnumDBFieldConsumer.ValueParser> valueParserMap = new ConcurrentHashMap<>(2);
    private BiFunction<Set<String>, Collection<Object>, Map<String, Map<String, Object>>> selectEnumGroupKeyValueMap;

    public EnumDBFieldIntercept() {
    }

    public EnumDBFieldIntercept(BiFunction<Set<String>, Collection<Object>, Map<String, Map<String, Object>>> selectEnumGroupKeyValueMap) {
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
        Set<String> groupList = getGroupList(cFields);
        Map<String, Map<String, Object>> groupKeyValueMap = selectEnumGroupKeyValueMap(cFields, groupList, keys);
        if (groupKeyValueMap == null) {
            if (selectEnumGroupKeyValueMap != null) {
                groupKeyValueMap = selectEnumGroupKeyValueMap.apply(groupList, keys);
            }
        }
        return (Map) flatMap(groupKeyValueMap);
    }

    protected Set<String> getGroupList(List<CField> cFields) {
        Set<String> groupList = new LinkedHashSet<>();
        for (CField cField : cFields) {
            String[] groups = getGroups(cField);
            if (groups == null) {
                continue;
            }
            groupList.addAll(Arrays.asList(groups));
        }
        return groupList;
    }

    protected Map<String, Object> flatMap(Map<String, Map<String, Object>> groupKeyValueMap) {
        if (groupKeyValueMap == null) {
            return null;
        }
        Map<String, Object> valueMap = new LinkedHashMap<>((int) (groupKeyValueMap.size() * 3 / 0.75F + 1));
        for (Map.Entry<String, Map<String, Object>> groupEntry : groupKeyValueMap.entrySet()) {
            String group = groupEntry.getKey();
            for (Map.Entry<String, Object> keyValueEntry : groupEntry.getValue().entrySet()) {
                valueMap.putIfAbsent(group + "." + keyValueEntry.getKey(), keyValueEntry.getValue());
            }
        }
        return valueMap;
    }

    /**
     * 从注解上获取组
     *
     * @param cField 字段注解
     * @return 组
     */
    public String[] getGroups(CField cField) {
        Annotation annotation = cField.getAnnotation();
        Class<? extends EnumDBFieldConsumer.ValueParser> valueParserClass = AnnotationUtil.getValue(annotation, "valueParser", Class.class);
        if (valueParserClass == null) {
            valueParserClass = EnumDBFieldConsumer.DefaultValueParser.class;
        }
        EnumDBFieldConsumer.ValueParser valueParser = valueParserMap.computeIfAbsent(valueParserClass, BeanUtil::newInstance);
        return valueParser.apply(cField);
    }

    public Map<String, Map<String, Object>> selectSerializeEnumGroupKeyValueMap(List<CField.SerializeCField> cFields, Set<String> groups, Collection<Object> keys) {
        return selectEnumGroupKeyValueMap(CField.parse(cFields), groups, keys);
    }

    /**
     * 查数据库
     *
     * @param groups 组
     * @param keys   键
     * @return Map（group, Map （ key, value））
     */
    public Map<String, Map<String, Object>> selectEnumGroupKeyValueMap(List<CField> cFields, Set<String> groups, Collection<Object> keys) {
        return selectEnumGroupKeyValueMap(groups, keys);
    }

    public Map<String, Map<String, Object>> selectEnumGroupKeyValueMap(Set<String> groups, Collection<Object> keys) {
        return null;
    }

    @Override
    protected Object[] rewriteKeyDataIfNeed(Object keyData, CField cField, Map<Object, Object> valueMap, Map<String, Object> attachment) {
        String[] groups = getGroups(cField);
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
