package com.github.fieldintercept;

import com.github.fieldintercept.util.AnnotationUtil;
import com.github.fieldintercept.util.BeanMap;
import com.github.fieldintercept.util.SnapshotCompletableFuture;
import com.github.fieldintercept.util.TypeUtil;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * key value 字段字段设置名称
 *
 * @author acer01
 */
public class KeyValueFieldIntercept<KEY, VALUE, JOIN_POINT> implements ReturnFieldDispatchAop.FieldIntercept<JOIN_POINT>, ReturnFieldDispatchAop.SelectMethodHolder {
    protected final Class<KEY> keyClass;
    protected final Class<VALUE> valueClass;
    protected final Function<Collection<KEY>, Map<KEY, VALUE>> selectValueMapByKeys;
    protected Object configurableEnvironment;

    public KeyValueFieldIntercept() {
        this(null, null);
    }

    public KeyValueFieldIntercept(Class<KEY> keyClass) {
        this(keyClass, null);
    }

    public KeyValueFieldIntercept(Class<KEY> keyClass, Class<VALUE> valueClass) {
        this(keyClass, valueClass, null);
    }

    public KeyValueFieldIntercept(Class<KEY> keyClass, Class<VALUE> valueClass, Function<Collection<KEY>, Map<KEY, VALUE>> selectValueMapByKeys) {
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
        if (valueClass == null) {
            try {
                valueClass = (Class<VALUE>) TypeUtil.findGenericType(this, KeyValueFieldIntercept.class, "VALUE");
            } catch (Exception e) {
                valueClass = (Class<VALUE>) Object.class;
            }
        }
        this.keyClass = keyClass;
        this.valueClass = valueClass;
        this.selectValueMapByKeys = selectValueMapByKeys;
    }

    private static String cacheKey(Object key) {
        return String.valueOf(key);
    }

    public Class<VALUE> getValueClass() {
        return valueClass;
    }

    public Class<KEY> getKeyClass() {
        return keyClass;
    }

    public Function<Collection<KEY>, Map<KEY, VALUE>> getSelectValueMapByKeys() {
        return selectValueMapByKeys;
    }

    @Override
    public final void accept(JOIN_POINT joinPoint, List<CField> cFields) {
        Set<KEY> keyDataList = getKeyDataByFields(cFields);
        if (keyDataList == null || keyDataList.isEmpty()) {
            return;
        }
        Map<String, VALUE> currentLocalCache = ReturnFieldDispatchAop.getLocalCache(cFields, this);
        Map<KEY, VALUE> valueMap = cacheSelectValueMapByKeys(currentLocalCache, cFields, keyDataList);

        SnapshotCompletableFuture<Map<KEY, VALUE>> future = ReturnFieldDispatchAop.getAsync(cFields, this);
        if (future != null) {
            future.addBeforeCompleteListener((result, throwable) -> {
                if (result != null && !result.isEmpty()) {
                    valueMap.putAll(result);
                    putCache(currentLocalCache, result);
                }
                if (!valueMap.isEmpty()) {
                    setProperty(cFields, valueMap);
                }
            });
        } else if (!valueMap.isEmpty()) {
            setProperty(cFields, valueMap);
        }
    }

    private Map<KEY, VALUE> cacheSelectValueMapByKeys(Map<String, VALUE> currentLocalCache, List<CField> cFields, Set<KEY> keys) {
        // 从缓存中加载
        Map<KEY, VALUE> valueMap = loadCache(currentLocalCache, keys);
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
        Map<KEY, VALUE> loadValueMap = selectValueMapByKeys(cFields, remainingCacheMissKeys);
        if (loadValueMap != null) {
            valueMap.putAll(loadValueMap);

            // 放入缓存
            putCache(currentLocalCache, loadValueMap);
        }
        return valueMap;
    }

    private Map<KEY, VALUE> loadCache(Map<String, VALUE> currentLocalCache, Set<KEY> keys) {
        Map<KEY, VALUE> valueMap = new LinkedHashMap<>((int) (keys.size() / 0.75F + 1));
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

    private void putCache(Map<String, VALUE> currentLocalCache, Map<KEY, VALUE> valueMap) {
        // 放入缓存
        if (currentLocalCache != null) {
            for (Map.Entry<KEY, VALUE> entry : valueMap.entrySet()) {
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
    public Map<KEY, VALUE> selectValueMapByKeys(Collection<KEY> keys) {
        return null;
    }

    public Map<KEY, VALUE> selectValueMapByKeys(List<CField> cFields, Collection<KEY> keys) {
        Map<KEY, VALUE> valueMap = selectValueMapByKeys(keys);
        if (valueMap == null) {
            if (selectValueMapByKeys == null) {
                throw new UnsupportedOperationException("您的selectValueMapByKeys方法未实现完全");
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

    protected <T> void addList(CField cField, Object value, Class<T> genericType, Consumer<T> list) {
        if (value == null) {
            return;
        }
        if (value instanceof Collection && !Collection.class.isAssignableFrom(genericType)) {
            for (Object o : (Collection) value) {
                addList(cField, o, genericType, list);
            }
        } else {
            String resolveValue = cField.resolvePlaceholders(value);
            if (resolveValue != null) {
                value = resolveValue;
            }
            if (cField.getType() != String.class && value instanceof String && ((String) value).contains(",")) {
                for (String s : ((String) value).split(",")) {
                    list.accept(cast(s, genericType));
                }
            } else {
                list.accept(cast(value, genericType));
            }
        }
    }

    protected void setProperty(List<CField> cFieldList, Map<KEY, VALUE> valueMap) {
        Map<String, VALUE> stringKeyMap = null;
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
                if (value == null && rewriteKeyDataList != null && rewriteKeyDataList.length > 0) {
                    if (stringKeyMap == null) {
                        stringKeyMap = toStringKeyMap(valueMap);
                    }
                    value = choseValue((Map<KEY, VALUE>) stringKeyMap, (KEY[]) toStringKey(rewriteKeyDataList));
                }
                if (List.class.isAssignableFrom(fieldType)) {
                    Collection list = new ArrayList<>(1);
                    addList(cField, value, genericType, list::add);
                    value = (VALUE) list;
                } else if (Set.class.isAssignableFrom(fieldType)) {
                    Collection list = new LinkedHashSet<>(1);
                    addList(cField, value, genericType, list::add);
                    value = (VALUE) list;
                } else if (fieldType.isArray()) {
                    Object array = Array.newInstance(genericType, 1);
                    String resolveValue = cField.resolvePlaceholders(value);
                    if (resolveValue != null) {
                        value = (VALUE) cast(resolveValue, genericType);
                    }
                    Array.set(array, 0, value);
                    value = (VALUE) array;
                }
            } else {
                List list = null;
                Set set = null;
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
                    if (eachValue == null && rewriteKeyDataList != null && rewriteKeyDataList.length > 0) {
                        if (stringKeyMap == null) {
                            stringKeyMap = toStringKeyMap(valueMap);
                        }
                        eachValue = choseValue((Map<KEY, VALUE>) stringKeyMap, (KEY[]) toStringKey(rewriteKeyDataList));
                    }
                    if (eachValue == null) {
                        continue;
                    }
                    if (list != null) {
                        addList(cField, eachValue, genericType, list::add);
                    } else if (set != null) {
                        addList(cField, eachValue, genericType, set::add);
                    } else if (array != null) {
                        String resolveValue = cField.resolvePlaceholders(eachValue);
                        if (resolveValue != null) {
                            eachValue = (VALUE) cast(resolveValue, genericType);
                        }
                        Array.set(array, i, eachValue);
                    } else if (joiner != null && !isNull(eachValue)) {
                        addList(cField, eachValue, String.class, joiner::add);
                    } else {
                        value = eachValue;
                        break;
                    }
                }
            }

            if (joiner != null && joiner.length() > 0) {
                value = (VALUE) joiner.toString();
            }
            if (value != null) {
                if (fieldType.isEnum() && value.getClass().isEnum()) {
                    cField.setValue(value);
                } else if (cField.existPlaceholder()) {
                    String resolveValue = cField.resolvePlaceholders(value);
                    if (resolveValue != null) {
                        cField.setValue(resolveValue);
                    }
                } else {
                    cField.setValue(value);
                }
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
        if (Enum.class.isAssignableFrom(type)) {
            if (type.isEnum()) {
                return (TYPE) castEnum(object, (Class) type);
            } else {
                throw new IllegalStateException("cast need " + type + " extends java.lang.Enum");
            }
        }
        return TypeUtil.castIfBeanCast(object, type);
    }

    protected <TYPE extends Enum> TYPE castEnum(Object object, Class<TYPE> type) {
        Collection<Enum> enumSet = (Collection) EnumSet.allOf((Class) type);
        if (enumSet.isEmpty()) {
            return null;
        }
        Class<?> keyClass = enumSet.stream()
                .map(Enum::getKey)
                .filter(Objects::nonNull)
                .findFirst()
                .map(Object::getClass)
                .orElse(null);
        Object keyCast;
        if (keyClass == null) {
            keyCast = null;
        } else {
            keyCast = TypeUtil.castIfBeanCast(object, keyClass);
        }
        for (Enum o : enumSet) {
            Object key = o.getKey();
            if (Objects.equals(key, keyCast)) {
                return (TYPE) o;
            }
        }
        return null;
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
        Object joinDelimiter = AnnotationUtil.getValue(annotation, "joinDelimiter");
        if (joinDelimiter == null) {
            return ",";
        } else {
            return joinDelimiter.toString();
        }
    }

    public void setConfigurableEnvironment(Object configurableEnvironment) {
        this.configurableEnvironment = configurableEnvironment;
    }

    private Map<String, VALUE> toStringKeyMap(Map<KEY, VALUE> nameMap) {
        Map<String, VALUE> result = new HashMap<>();
        for (Map.Entry<KEY, VALUE> entry : nameMap.entrySet()) {
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
