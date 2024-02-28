package com.github.fieldintercept;

import com.github.fieldintercept.util.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.util.*;
import java.util.function.Function;

/**
 * key name 字段字段设置名称
 *
 * @author acer01
 */
public class KeyNameFieldIntercept<KEY, JOIN_POINT> implements ReturnFieldDispatchAop.FieldIntercept<JOIN_POINT>, ReturnFieldDispatchAop.SelectMethodHolder {
    protected final Class<KEY> keyClass;
    protected Function<Collection<KEY>, Map<KEY, ?>> selectNameMapByKeys;

    public KeyNameFieldIntercept() {
        this(null, null);
    }

    public KeyNameFieldIntercept(Class<KEY> keyClass) {
        this(keyClass, null);
    }

    public KeyNameFieldIntercept(Class<KEY> keyClass, Function<Collection<KEY>, Map<KEY, ?>> selectNameMapByKeys) {
        if (keyClass == null) {
            if (getClass() != KeyNameFieldIntercept.class) {
                try {
                    Class<?> key = TypeUtil.findGenericType(this, KeyNameFieldIntercept.class, "KEY");
                    keyClass = (Class<KEY>) key;
                } catch (IllegalStateException ignored) {
                }
            }
            if (keyClass == null) {
                keyClass = (Class<KEY>) Integer.class;
            }
        }
        this.keyClass = keyClass;
        this.selectNameMapByKeys = selectNameMapByKeys;
    }

    private static String cacheKey(Object key) {
        return String.valueOf(key);
    }

    public Class<KEY> getKeyClass() {
        return keyClass;
    }

    public Function<Collection<KEY>, Map<KEY, ?>> getSelectNameMapByKeys() {
        return selectNameMapByKeys;
    }

    public void setSelectNameMapByKeys(Function<Collection<KEY>, Map<KEY, ?>> selectNameMapByKeys) {
        this.selectNameMapByKeys = selectNameMapByKeys;
    }

    @Override
    public final void accept(JOIN_POINT joinPoint, List<CField> cFields) {
        Set<KEY> keyDataList = getKeyDataByFields(cFields);
        if (keyDataList == null || keyDataList.isEmpty()) {
            return;
        }

        Map<String, Object> currentLocalCache = ReturnFieldDispatchAop.getLocalCache(cFields, this);
        Map<KEY, Object> nameMap = cacheSelectNameMapByKeys(currentLocalCache, cFields, keyDataList);

        SnapshotCompletableFuture<Map<KEY, Object>> future = ReturnFieldDispatchAop.getAsync(cFields, this);
        if (future != null) {
            future.addBeforeCompleteListener((result, throwable) -> {
                if (result != null && !result.isEmpty()) {
                    nameMap.putAll(result);
                    // 放入缓存
                    putCache(currentLocalCache, result);
                }
                if (!nameMap.isEmpty()) {
                    Map<String, Object> attachment = PlatformDependentUtil.removeAttachment(nameMap);
                    setProperty(cFields, nameMap, attachment);
                }
            });
        } else if (!nameMap.isEmpty()) {
            Map<String, Object> attachment = PlatformDependentUtil.removeAttachment(nameMap);
            setProperty(cFields, nameMap, attachment);
        }
    }

    private Map<KEY, Object> cacheSelectNameMapByKeys(Map<String, Object> currentLocalCache, List<CField> cFields, Set<KEY> keys) {
        // 从缓存中加载
        Map<KEY, Object> valueMap = loadCache(currentLocalCache, keys);
        // 全部命中
        if (valueMap.size() == keys.size()) {
            return valueMap;
        }

        // 未命中的查库
        Set<KEY> remainingCacheMissKeys;
        if (valueMap.isEmpty()) {
            remainingCacheMissKeys = keys;
        } else {
            remainingCacheMissKeys = new LinkedHashSet<>(keys);
            remainingCacheMissKeys.removeAll(valueMap.keySet());
        }

        // 查库与缓存数据合并
        Map<KEY, ?> loadValueMap = selectObjectMapByKeys(cFields, remainingCacheMissKeys);
        if (loadValueMap != null) {
            valueMap.putAll(loadValueMap);

            // 放入缓存
            putCache(currentLocalCache, loadValueMap);
        }
        return valueMap;
    }

    private Map<KEY, Object> loadCache(Map<String, Object> currentLocalCache, Set<KEY> keys) {
        Map<KEY, Object> valueMap = new LinkedHashMap<>((int) (keys.size() / 0.75F + 1));
        if (currentLocalCache != null && !currentLocalCache.isEmpty()) {
            for (KEY key : keys) {
                String stringKey = cacheKey(key);
                if (currentLocalCache.containsKey(stringKey)) {
                    valueMap.put(key, currentLocalCache.get(stringKey));
                }
            }
        }
        return valueMap;
    }

    private void putCache(Map<String, Object> currentLocalCache, Map<KEY, ?> valueMap) {
        // 放入缓存
        if (currentLocalCache != null) {
            for (Map.Entry<KEY, ?> entry : valueMap.entrySet()) {
                currentLocalCache.put(cacheKey(entry.getKey()), entry.getValue());
            }
        }
    }

    /**
     * 查询名称by keys
     *
     * @param keys 多个key
     * @return key 与名称的映射
     */
    public Map<KEY, String> selectNameMapByKeys(Collection<KEY> keys) {
        return null;
    }

    public Map<KEY, String> selectNameMapByKeys(List<CField> cFields, Collection<KEY> keys) {
        return selectNameMapByKeys(keys);
    }

    public Map<KEY, ?> selectObjectMapByKeys(List<CField> cFields, Collection<KEY> keys) {
        Map<KEY, ?> nameMap = selectNameMapByKeys(cFields, keys);
        if (nameMap == null) {
            nameMap = selectNameListMapByKeys(cFields, keys);
        }
        if (nameMap == null && selectNameMapByKeys != null) {
            nameMap = selectNameMapByKeys.apply(keys);
        }
        if (nameMap == null) {
            throw new UnsupportedOperationException("您的selectNameMapByKeys方法未实现完全");
        }
        return nameMap;
    }

    public Map<KEY, ?> selectSerializeNameMapByKeys(List<CField.SerializeCField> cFields, Collection<KEY> keys) {
        List<CField> cFieldList = new ArrayList<>(cFields.size());
        for (CField.SerializeCField cField : cFields) {
            try {
                cFieldList.add(new CField(cField));
            } catch (ClassNotFoundException | NoSuchFieldException e) {
                PlatformDependentUtil.sneakyThrows(e);
            }
        }
        Map<KEY, ?> nameMap = selectNameMapByKeys(cFieldList, keys);
        if (nameMap == null) {
            nameMap = selectNameListMapByKeys(cFieldList, keys);
        }
        if (nameMap == null && selectNameMapByKeys != null) {
            nameMap = selectNameMapByKeys.apply(keys);
        }
        Map<String, Object> attachment = newtSerializeAttachment(cFields, (Map<KEY, Object>) nameMap);
        return PlatformDependentUtil.mergeAttachment(nameMap, attachment);
    }

    protected Map<String, Object> newtSerializeAttachment(List<CField.SerializeCField> cFields, Map<KEY, Object> nameMap) {
        return null;
    }

    public Map<KEY, Collection<String>> selectNameListMapByKeys(List<CField> cFields, Collection<KEY> keys) {
        return selectNameListMapByKeys(keys);
    }

    public Map<KEY, Collection<String>> selectNameListMapByKeys(Collection<KEY> keys) {
        return null;
    }

    protected KEY[] rewriteKeyDataIfNeed(KEY keyData, CField cField, Map<KEY, Object> nameMap, Map<String, Object> attachment) {
        Object[] attachmentValue;
        if (attachment != null && !attachment.isEmpty() && (attachmentValue = TypeUtil.cast(attachment.get(cField.keyDataId(keyData)), Object[].class)) != null) {
            return (KEY[]) attachmentValue;
        } else {
            KEY[] arr = (KEY[]) Array.newInstance(keyData.getClass(), 1);
            arr[0] = keyData;
            return arr;
        }
    }

    protected Set<KEY> getKeyDataByFields(List<CField> cFields) {
        Set<KEY> totalKeyDataList = new LinkedHashSet<>();
        for (CField cField : cFields) {
            String[] keyFieldNames = getKeyFieldName(cField.getAnnotation());
            Object keyData = getKeyDataByField(cField.getBeanHandler(), keyFieldNames);
            Collection<KEY> keyDataList = splitKeyData(keyData);
            if (keyDataList != null) {
                totalKeyDataList.addAll(keyDataList);
                cField.setKeyDataList(keyDataList);
            }
        }
        return totalKeyDataList;
    }

    protected String[] getKeyFieldName(Annotation annotation) {
        Object keyField = AnnotationUtil.getValue(annotation, "keyField");
        if (keyField instanceof String[]) {
            return (String[]) keyField;
        } else if (keyField instanceof String) {
            return new String[]{(String) keyField};
        } else {
            return null;
        }
    }

    protected boolean isNull(Object value) {
        return value == null || "".equals(value) || "null".equals(value);
    }

    protected Object getKeyDataByField(BeanMap beanHandler, String[] keyFieldNames) {
        if (keyFieldNames != null) {
            for (String keyFieldName : keyFieldNames) {
                Object keyData = beanHandler.get(keyFieldName);
                if (!isNull(keyData)) {
                    return keyData;
                }
            }
        }
        return null;
    }

    protected Collection<KEY> splitKeyData(Object keyData) {
        Collection<KEY> keyDataList = null;
        if (isNull(keyData)) {
            return null;
        } else if (keyData.getClass().isArray()) {
            int length = Array.getLength(keyData);
            for (int i = 0; i < length; i++) {
                Object e = Array.get(keyData, i);
                if (isNull(e)) {
                    continue;
                }
                KEY key = cast(e, keyClass);
                if (key != null) {
                    if (keyDataList == null) {
                        keyDataList = new ArrayList<>();
                    }
                    keyDataList.add(key);
                }
            }
        } else if (keyData instanceof Iterable) {
            for (Object e : (Iterable) keyData) {
                if (isNull(e)) {
                    continue;
                }
                KEY key = cast(e, keyClass);
                if (key != null) {
                    if (keyDataList == null) {
                        keyDataList = new ArrayList<>();
                    }
                    keyDataList.add(key);
                }
            }
        } else if (keyData instanceof CharSequence) {
            for (String e : keyData.toString().split(",")) {
                if (isNull(e)) {
                    continue;
                }
                KEY key = cast(e, keyClass);
                if (key != null) {
                    if (keyDataList == null) {
                        keyDataList = new ArrayList<>();
                    }
                    keyDataList.add(key);
                }
            }
        } else {
            try {
                KEY key = cast(keyData, keyClass);
                if (key != null) {
                    keyDataList = Collections.singletonList(key);
                }
            } catch (Exception e) {
                //skip
            }
        }
        return keyDataList;
    }

    protected void setProperty(List<CField> cFieldList, Map<KEY, Object> nameMap, Map<String, Object> attachment) {
        Map<String, Object> stringKeyMap = null;
        for (CField cField : cFieldList) {
            Class genericType = cField.getGenericType();
            Class<?> fieldType = cField.getField().getType();
            Collection<KEY> keyDataList = cField.getKeyDataList();
            Object value = null;
            StringJoiner joiner = null;
            if (keyDataList == null || keyDataList.isEmpty()) {
                if (List.class.isAssignableFrom(fieldType)) {
                    value = new ArrayList();
                } else if (Set.class.isAssignableFrom(fieldType)) {
                    value = new LinkedHashSet(1);
                } else if (fieldType.isArray()) {
                    value = Array.newInstance(genericType, 0);
                }
            } else if (keyDataList.size() == 1) {
                KEY[] rewriteKeyDataList = rewriteKeyDataIfNeed(keyDataList.iterator().next(), cField, nameMap, attachment);
                setKeyData(cField, rewriteKeyDataList);
                value = choseValue(nameMap, rewriteKeyDataList);
                if (value == null && rewriteKeyDataList != null && rewriteKeyDataList.length > 0) {
                    if (stringKeyMap == null) {
                        stringKeyMap = toStringKeyMap(nameMap);
                    }
                    value = choseValue((Map<KEY, Object>) stringKeyMap, (KEY[]) toStringKey(rewriteKeyDataList));
                }
                if (List.class.isAssignableFrom(fieldType)) {
                    List list = new ArrayList(1);
                    if (value != null) {
                        if (value instanceof Collection) {
                            list.addAll((Collection) value);
                        } else {
                            list.add(value);
                        }
                    }
                    value = list;
                } else if (Set.class.isAssignableFrom(fieldType)) {
                    Set set = new LinkedHashSet(1);
                    if (value != null) {
                        if (value instanceof Collection) {
                            set.addAll((Collection) value);
                        } else {
                            set.add(value);
                        }
                    }
                    value = set;
                } else if (fieldType.isArray()) {
                    Object array = Array.newInstance(genericType, 1);
                    Array.set(array, 0, value);
                    value = array;
                }
            } else {
                List<String> list = null;
                Set<String> set = null;
                Object array = null;
                if (List.class.isAssignableFrom(fieldType)) {
                    list = new ArrayList<>(10);
                    value = list;
                } else if (Set.class.isAssignableFrom(fieldType)) {
                    set = new LinkedHashSet<>(10);
                    value = set;
                } else if (fieldType.isArray()) {
                    array = Array.newInstance(genericType, keyDataList.size());
                    value = array;
                } else if (fieldType == String.class) {
                    String joinDelimiter = getAnnotationJoinDelimiter(cField.getAnnotation());
                    joiner = new StringJoiner(joinDelimiter);
                }
                int i = 0;
                for (KEY keyData : keyDataList) {
                    i++;
                    KEY[] rewriteKeyDataList = rewriteKeyDataIfNeed(keyData, cField, nameMap, attachment);
                    setKeyData(cField, rewriteKeyDataList);
                    Object eachValue = choseValue(nameMap, rewriteKeyDataList);
                    if (eachValue == null && rewriteKeyDataList != null && rewriteKeyDataList.length > 0) {
                        if (stringKeyMap == null) {
                            stringKeyMap = toStringKeyMap(nameMap);
                        }
                        eachValue = choseValue((Map<KEY, Object>) stringKeyMap, (KEY[]) toStringKey(rewriteKeyDataList));
                    }
                    if (eachValue == null) {
                        continue;
                    }
                    if (list != null) {
                        if (eachValue instanceof Collection) {
                            list.addAll((Collection) eachValue);
                        } else {
                            list.add(eachValue.toString());
                        }
                    } else if (set != null) {
                        if (eachValue instanceof Collection) {
                            set.addAll((Collection) eachValue);
                        } else {
                            set.add(eachValue.toString());
                        }
                    } else if (array != null) {
                        Array.set(array, i, eachValue);
                    } else if (joiner != null && !isNull(eachValue)) {
                        if (eachValue instanceof Collection) {
                            for (Object e : (Collection) eachValue) {
                                joiner.add(Objects.toString(e, null));
                            }
                        } else {
                            joiner.add(eachValue.toString());
                        }
                    } else {
                        value = Objects.toString(eachValue, null);
                        break;
                    }
                }
            }
            if (joiner != null) {
                value = joiner.toString();
            }
            if (value != null) {
                cField.setValue(value);
            }
        }
    }

    private void setKeyData(CField cField, KEY[] rewriteKeyDataList) {
        if (rewriteKeyDataList == null) {
            return;
        }
        Object keyData;
        if (rewriteKeyDataList.length == 1) {
            keyData = rewriteKeyDataList[0];
        } else if (rewriteKeyDataList.length == 0) {
            keyData = null;
        } else {
            keyData = new ArrayList<>(Arrays.asList(rewriteKeyDataList));
        }
        cField.setKeyData(keyData);
    }

    protected <TYPE> TYPE cast(Object object, Class<TYPE> type) {
        return TypeUtil.castIfBeanCast(object, type);
    }

    protected Object choseValue(Map<KEY, Object> nameMap, KEY[] keyDataList) {
        if (keyDataList == null) {
            return null;
        }
        for (KEY nameMapKey : keyDataList) {
            Object name = nameMap.get(nameMapKey);
            if (name != null) {
                return name;
            }
        }
        return null;
    }

    protected String getAnnotationJoinDelimiter(Annotation annotation) {
        Object joinDelimiter = AnnotationUtil.getValue(annotation, "joinDelimiter");
        if (joinDelimiter == null) {
            return ",";
        } else {
            return joinDelimiter.toString();
        }
    }

    private Map<String, Object> toStringKeyMap(Map<KEY, Object> nameMap) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<KEY, Object> entry : nameMap.entrySet()) {
            result.put(Objects.toString(entry.getKey(), null), entry.getValue());
        }
        return result;
    }

    private String[] toStringKey(KEY[] rewriteKeyDataList) {
        String[] strings = new String[rewriteKeyDataList.length];
        for (int i = 0; i < rewriteKeyDataList.length; i++) {
            strings[i] = Objects.toString(rewriteKeyDataList[i], null);
        }
        return strings;
    }

}
