package com.github.fieldintercept;

import com.github.fieldintercept.util.BeanMap;
import com.github.fieldintercept.util.ShareThreadMap;
import com.github.fieldintercept.util.TypeUtil;
import org.aspectj.lang.JoinPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.ConfigurableEnvironment;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.util.*;
import java.util.function.Function;

/**
 * key value 字段字段设置名称
 *
 * @author acer01
 */
public class KeyValueFieldIntercept<KEY, VALUE> implements ReturnFieldDispatchAop.FieldIntercept {
    private final Class<KEY> keyClass;
    private final Class<KEY> valueClass;
    private final ShareThreadMap<KEY, VALUE> shareThreadMap = new ShareThreadMap<>(5000);
    private Function<Collection<KEY>, Map<KEY, VALUE>> selectValueMapByKeys;
    private ConfigurableEnvironment configurableEnvironment;

    public KeyValueFieldIntercept() {
        this(null, null);
    }

    public KeyValueFieldIntercept(Class<KEY> keyClass) {
        this(keyClass, null);
    }

    public KeyValueFieldIntercept(Class<KEY> keyClass, Function<Collection<KEY>, Map<KEY, VALUE>> selectValueMapByKeys) {
        if (keyClass == null) {
            if (getClass() != KeyValueFieldIntercept.class) {
                try {
                    Class<?> key = TypeUtil.findGenericType(this, KeyValueFieldIntercept.class, "KEY");
                    keyClass = (Class<KEY>) key;
                } catch (IllegalStateException ignored) {
                }
            }
            if (keyClass == null) {
                keyClass = (Class<KEY>) Integer.class;
            }
        }
        Class valueClass;
        try {
            valueClass = TypeUtil.findGenericType(this, KeyValueFieldIntercept.class, "VALUE");
        } catch (Exception e) {
            valueClass = Object.class;
        }
        this.keyClass = keyClass;
        this.valueClass = valueClass;
        this.selectValueMapByKeys = selectValueMapByKeys;
    }

    public Class<KEY> getValueClass() {
        return valueClass;
    }

    public Class<KEY> getKeyClass() {
        return keyClass;
    }

    @Override
    public final void accept(JoinPoint joinPoint, List<CField> cFields) {
        Set<KEY> keyDataList = getKeyDataByFields(cFields);
        if (keyDataList == null || keyDataList.isEmpty()) {
            return;
        }
        Map<KEY, VALUE> valueMap = cacheSelectValueMapByKeys(cFields, keyDataList);
        if (valueMap == null || valueMap.isEmpty()) {
            return;
        }

        setProperty(cFields, valueMap);
    }

    @Override
    public void end(JoinPoint joinPoint, List<CField> allFieldList, Object result) {
        shareThreadMap.remove(Thread.currentThread());
    }

    private Map<KEY, VALUE> cacheSelectValueMapByKeys(List<CField> cFields, Set<KEY> keys) {
        Map<KEY, VALUE> valueMap = new LinkedHashMap<>();
        for (KEY key : keys) {
            VALUE value = shareThreadMap.get(key);
            if (value != null) {
                valueMap.put(key, value);
            }
        }
        // 全部命中
        if (valueMap.size() == keys.size()) {
            return valueMap;
        }

        // 未命中的查库
        Set<KEY> remainingCacheMissKeys = new LinkedHashSet<>(keys);
        remainingCacheMissKeys.removeAll(valueMap.keySet());

        // 查库与缓存数据合并
        Map<KEY, VALUE> loadValueMap = selectValueMapByKeys(cFields, remainingCacheMissKeys);
        valueMap.putAll(loadValueMap);

        // 放入缓存
        shareThreadMap.putAll(loadValueMap);
        return valueMap;
    }

    /**
     * 查询名称by keys
     *
     * @param keys 多个key
     * @return key 与名称的映射
     */
    public Map<KEY, VALUE> selectValueMapByKeys(Collection<KEY> keys) {
        return null;
    }

    public Map<KEY, VALUE> selectValueMapByKeys(List<CField> cFields, Collection<KEY> keys) {
        Map<KEY, VALUE> valueMap = selectValueMapByKeys(keys);
        if (valueMap == null) {
            if (selectValueMapByKeys == null) {
                throw new UnsupportedOperationException("您的方法未实现完全");
            }
            valueMap = selectValueMapByKeys.apply(keys);
        }
        return valueMap;
    }

    protected KEY[] rewriteKeyDataIfNeed(KEY keyData, CField cField, Map<KEY, VALUE> valueMap) {
        KEY[] arr = (KEY[]) Array.newInstance(keyData.getClass(), 1);
        arr[0] = keyData;
        return arr;
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
        Object keyField = AnnotationUtils.getValue(annotation, "keyField");
        if (keyField instanceof String[]) {
            return (String[]) keyField;
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
                KEY key = cast(e, keyClass, null);
                if (key != null) {
                    if (keyDataList == null) {
                        keyDataList = new LinkedHashSet<>();
                    }
                    keyDataList.add(key);
                }
            }
        } else if (keyData instanceof Iterable) {
            for (Object e : (Iterable) keyData) {
                if (isNull(e)) {
                    continue;
                }
                KEY key = cast(e, keyClass, null);
                if (key != null) {
                    if (keyDataList == null) {
                        keyDataList = new LinkedHashSet<>();
                    }
                    keyDataList.add(key);
                }
            }
        } else if (keyData instanceof CharSequence) {
            for (String e : keyData.toString().split(",")) {
                if (isNull(e)) {
                    continue;
                }
                KEY key = cast(e, keyClass, null);
                if (key != null) {
                    if (keyDataList == null) {
                        keyDataList = new LinkedHashSet<>();
                    }
                    keyDataList.add(key);
                }
            }
        } else {
            try {
                KEY key = cast(keyData, keyClass, null);
                if (key != null) {
                    keyDataList = Collections.singletonList(key);
                }
            } catch (Exception e) {
                //skip
            }
        }
        return keyDataList;
    }

    protected void setProperty(List<CField> cFieldList, Map<KEY, VALUE> valueMap) {
        for (CField cField : cFieldList) {
            Class genericType = cField.getGenericType();
            Class<?> fieldType = cField.getField().getType();
            Collection<KEY> keyDataList = cField.getKeyDataList();
            VALUE value = null;
            StringJoiner joiner = null;
            if (keyDataList == null || keyDataList.isEmpty()) {
                if (List.class.isAssignableFrom(fieldType)) {
                    value = (VALUE) new ArrayList();
                } else if (Set.class.isAssignableFrom(fieldType)) {
                    value = (VALUE) new LinkedHashSet(1);
                } else if (fieldType.isArray()) {
                    value = (VALUE) Array.newInstance(genericType, 0);
                }
            } else if (keyDataList.size() == 1) {
                KEY[] rewriteKeyDataList = rewriteKeyDataIfNeed(keyDataList.iterator().next(), cField, valueMap);
                setKeyData(cField, rewriteKeyDataList);
                value = choseValue(valueMap, rewriteKeyDataList);
                if (List.class.isAssignableFrom(fieldType)) {
                    List list = new ArrayList(1);
                    if (value instanceof Collection && !Collection.class.isAssignableFrom(genericType)) {
                        for (Object o : (Collection) value) {
                            list.add(cast(o, genericType, cField));
                        }
                    } else if (value != null) {
                        list.add(cast(value, genericType, cField));
                    }
                    value = (VALUE) list;
                } else if (Set.class.isAssignableFrom(fieldType)) {
                    Set set = new LinkedHashSet(1);
                    if (value instanceof Collection && !Collection.class.isAssignableFrom(genericType)) {
                        for (Object o : (Collection) value) {
                            set.add(cast(o, genericType, cField));
                        }
                    } else if (value != null) {
                        set.add(cast(value, genericType, cField));
                    }
                    value = (VALUE) set;
                } else if (fieldType.isArray()) {
                    Object array = Array.newInstance(genericType, 1);
                    Array.set(array, 0, cast(value, genericType, cField));
                    value = (VALUE) array;
                }
            } else {
                List<VALUE> list = null;
                Set<VALUE> set = null;
                Object array = null;
                if (List.class.isAssignableFrom(fieldType)) {
                    list = new ArrayList<>(10);
                    value = (VALUE) list;
                } else if (Set.class.isAssignableFrom(fieldType)) {
                    set = new LinkedHashSet<>(10);
                    value = (VALUE) set;
                } else if (fieldType.isArray()) {
                    array = Array.newInstance(genericType, keyDataList.size());
                    value = (VALUE) array;
                } else if (fieldType == String.class) {
                    String joinDelimiter = getAnnotationJoinDelimiter(cField.getAnnotation());
                    joiner = new StringJoiner(joinDelimiter);
                }
                int i = 0;
                for (KEY keyData : keyDataList) {
                    i++;
                    KEY[] rewriteKeyDataList = rewriteKeyDataIfNeed(keyData, cField, valueMap);
                    setKeyData(cField, rewriteKeyDataList);
                    VALUE eachValue = choseValue(valueMap, rewriteKeyDataList);
                    if (eachValue == null) {
                        continue;
                    }
                    if (list != null) {
                        if (eachValue instanceof Collection && !Collection.class.isAssignableFrom(genericType)) {
                            for (Object o : (Collection) eachValue) {
                                list.add((VALUE) cast(o, genericType, cField));
                            }
                        } else {
                            eachValue = (VALUE) cast(eachValue, genericType, cField);
                            list.add(eachValue);
                        }
                    } else if (set != null) {
                        if (eachValue instanceof Collection && !Collection.class.isAssignableFrom(genericType)) {
                            for (Object o : (Collection) eachValue) {
                                set.add((VALUE) cast(o, genericType, cField));
                            }
                        } else {
                            eachValue = (VALUE) cast(eachValue, genericType, cField);
                            set.add(eachValue);
                        }
                    } else if (array != null) {
                        eachValue = (VALUE) cast(eachValue, genericType, cField);
                        Array.set(array, i, eachValue);
                    } else if (joiner != null && !isNull(eachValue)) {
                        if (eachValue instanceof Collection && !Collection.class.isAssignableFrom(genericType)) {
                            for (Object o : (Collection) eachValue) {
                                joiner.add(Objects.toString(o, null));
                            }
                        } else {
                            joiner.add(Objects.toString(eachValue, null));
                        }
                    } else {
                        value = eachValue;
                        break;
                    }
                }
            }

            if (joiner != null) {
                value = (VALUE) joiner.toString();
            }
            if (value != null) {
                if (fieldType.isEnum() && value.getClass().isEnum()) {
                    cField.setValue(value);
                } else {
                    String resolveValue = cField.resolvePlaceholders(configurableEnvironment, value);
                    if (resolveValue != null) {
                        cField.setValue(resolveValue);
                    }
                    if (!cField.isSetValue()) {
                        cField.setValue(value);
                    }
                }
            }
        }
    }

    private void setKeyData(CField cField, KEY[] rewriteKeyDataList) {
        if (rewriteKeyDataList == null) {
            return;
        }
        if (rewriteKeyDataList.length == 1) {
            cField.setKeyData(rewriteKeyDataList[0]);
        } else if (rewriteKeyDataList.length == 0) {
            cField.setKeyData(null);
        } else {
            cField.setKeyData(new ArrayList<>(Arrays.asList(rewriteKeyDataList)));
        }
    }

    protected <TYPE> TYPE cast(Object object, Class<TYPE> type, CField cField) {
        if (cField != null) {
            String resolveValue = cField.resolvePlaceholders(configurableEnvironment, object);
            if (resolveValue != null) {
                return (TYPE) resolveValue;
            }
        }
        return TypeUtil.cast(object, type);
    }

    protected VALUE choseValue(Map<KEY, VALUE> valueMap, KEY[] keyDataList) {
        if (keyDataList == null) {
            return null;
        }
        for (KEY keyData : keyDataList) {
            VALUE value = valueMap.get(keyData);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    protected String getAnnotationJoinDelimiter(Annotation annotation) {
        Object joinDelimiter = AnnotationUtils.getValue(annotation, "joinDelimiter");
        if (joinDelimiter == null) {
            return ",";
        } else {
            return joinDelimiter.toString();
        }
    }

    @Autowired
    public void setConfigurableEnvironment(ConfigurableEnvironment configurableEnvironment) {
        this.configurableEnvironment = configurableEnvironment;
    }


}
