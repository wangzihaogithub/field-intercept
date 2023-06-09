package com.github.fieldintercept;

import com.github.fieldintercept.util.BeanMap;
import com.github.fieldintercept.util.ShareThreadMap;
import com.github.fieldintercept.util.TypeUtil;
import org.aspectj.lang.JoinPoint;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * key name 字段字段设置名称
 *
 * @author acer01
 */
public class KeyNameFieldIntercept<T> implements ReturnFieldDispatchAop.FieldIntercept {
    protected final Class<T> keyClass;
    protected final ShareThreadMap<T, Object> shareThreadMap;
    protected final Map<Integer, List<Thread>> threadMap = new ConcurrentHashMap<>();
    protected Function<Collection<T>, Map<T, ?>> selectNameMapByKeys;

    public KeyNameFieldIntercept() {
        this(null, null, 0);
    }

    public KeyNameFieldIntercept(int shareTimeout) {
        this(null, null, shareTimeout);
    }

    public KeyNameFieldIntercept(Class<T> keyClass, int shareTimeout) {
        this(keyClass, null, shareTimeout);
    }

    public KeyNameFieldIntercept(Class<T> keyClass, Function<Collection<T>, Map<T, ?>> selectNameMapByKeys, int shareTimeout) {
        if (keyClass == null) {
            if (getClass() != KeyNameFieldIntercept.class) {
                try {
                    Class<?> key = TypeUtil.findGenericType(this, KeyNameFieldIntercept.class, "T");
                    keyClass = (Class<T>) key;
                } catch (IllegalStateException ignored) {
                }
            }
            if (keyClass == null) {
                keyClass = (Class<T>) Integer.class;
            }
        }
        this.keyClass = keyClass;
        this.selectNameMapByKeys = selectNameMapByKeys;
        this.shareThreadMap = new ShareThreadMap<>(shareTimeout);
    }

    public Class<T> getKeyClass() {
        return keyClass;
    }

    public void setSelectNameMapByKeys(Function<Collection<T>, Map<T, ?>> selectNameMapByKeys) {
        this.selectNameMapByKeys = selectNameMapByKeys;
    }

    @Override
    public final void accept(JoinPoint joinPoint, List<CField> cFields) {
        Set<T> keyDataList = getKeyDataByFields(cFields);
        if (keyDataList == null || keyDataList.isEmpty()) {
            return;
        }

        Map<T, Object> nameMap = cacheSelectNameMapByKeys(cFields, keyDataList);
        if (nameMap == null || nameMap.isEmpty()) {
            return;
        }

        setProperty(cFields, nameMap);
    }

    @Override
    public void stepBegin(int step, JoinPoint joinPoint, List<CField> fieldList, Object result) {
        threadMap.computeIfAbsent(id(result), e -> new ArrayList<>())
                .add(Thread.currentThread());
    }

    @Override
    public void end(JoinPoint joinPoint, List<CField> allFieldList, Object result) {
        List<Thread> threadList = threadMap.remove(id(result));
        if (threadList == null) {
            return;
        }
        for (Thread thread : threadList) {
            shareThreadMap.remove(thread);
        }
    }

    private int id(Object result) {
        return System.identityHashCode(result);
    }

    public Map<T, Object> cacheSelectNameMapByKeys(List<CField> cFields, Set<T> keys) {
        Map<T, Object> valueMap = new LinkedHashMap<>();
        for (T key : keys) {
            Object value = shareThreadMap.get(key);
            if (value != null) {
                valueMap.put(key, value);
            }
        }
        // 全部命中
        if (valueMap.size() == keys.size()) {
            return valueMap;
        }

        // 未命中的查库
        Set<T> remainingCacheMissKeys = new LinkedHashSet<>(keys);
        remainingCacheMissKeys.removeAll(valueMap.keySet());

        // 查库与缓存数据合并
        Map<T, ?> loadValueMap = selectObjectMapByKeys(cFields, remainingCacheMissKeys);
        valueMap.putAll(loadValueMap);

        // 放入缓存
        shareThreadMap.putAll((Map<T, Object>) loadValueMap);
        return valueMap;
    }

    /**
     * 查询名称by keys
     *
     * @param keys 多个key
     * @return key 与名称的映射
     */
    public Map<T, String> selectNameMapByKeys(Collection<T> keys) {
        return null;
    }

    public Map<T, String> selectNameMapByKeys(List<CField> cFields, Collection<T> keys) {
        Map<T, String> nameMap = selectNameMapByKeys(keys);
        return nameMap;
    }

    public Map<T, ?> selectObjectMapByKeys(List<CField> cFields, Collection<T> keys) {
        Map<T, ?> nameMap = selectNameMapByKeys(cFields, keys);
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

    public Map<T, Collection<String>> selectNameListMapByKeys(List<CField> cFields, Collection<T> keys) {
        return selectNameListMapByKeys(keys);
    }

    public Map<T, Collection<String>> selectNameListMapByKeys(Collection<T> keys) {
        return null;
    }

    protected T[] rewriteKeyDataIfNeed(T key, CField cField, Map<T, Object> nameMap) {
        T[] arr = (T[]) Array.newInstance(key.getClass(), 1);
        arr[0] = key;
        return arr;
    }

    protected Set<T> getKeyDataByFields(List<CField> cFields) {
        Set<T> totalKeyDataList = new LinkedHashSet<>();
        for (CField cField : cFields) {
            String[] keyFieldNames = getKeyFieldName(cField.getAnnotation());
            Object keyData = getKeyDataByField(cField.getBeanHandler(), keyFieldNames);
            Collection<T> keyDataList = splitKeyData(keyData);
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

    protected Collection<T> splitKeyData(Object keyData) {
        Collection<T> keyDataList = null;
        if (isNull(keyData)) {
            return null;
        } else if (keyData.getClass().isArray()) {
            int length = Array.getLength(keyData);
            for (int i = 0; i < length; i++) {
                Object e = Array.get(keyData, i);
                if (isNull(e)) {
                    continue;
                }
                T key = cast(e, keyClass);
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
                T key = cast(e, keyClass);
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
                T key = cast(e, keyClass);
                if (key != null) {
                    if (keyDataList == null) {
                        keyDataList = new ArrayList<>();
                    }
                    keyDataList.add(key);
                }
            }
        } else {
            try {
                T key = cast(keyData, keyClass);
                if (key != null) {
                    keyDataList = Collections.singletonList(key);
                }
            } catch (Exception e) {
                //skip
            }
        }
        return keyDataList;
    }

    protected void setProperty(List<CField> cFieldList, Map<T, Object> nameMap) {
        for (CField cField : cFieldList) {
            Class genericType = cField.getGenericType();
            Class<?> fieldType = cField.getField().getType();
            Collection<T> keyDataList = cField.getKeyDataList();
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
                T[] rewriteKeyDataList = rewriteKeyDataIfNeed(keyDataList.iterator().next(), cField, nameMap);
                setKeyData(cField, rewriteKeyDataList);
                value = choseValue(nameMap, rewriteKeyDataList);
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
                for (T keyData : keyDataList) {
                    i++;
                    T[] rewriteKeyDataList = rewriteKeyDataIfNeed(keyData, cField, nameMap);
                    setKeyData(cField, rewriteKeyDataList);
                    Object eachValue = choseValue(nameMap, rewriteKeyDataList);
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

    private void setKeyData(CField cField, T[] rewriteKeyDataList) {
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

    protected <TYPE> TYPE cast(Object object, Class<TYPE> type) {
        return TypeUtil.castIfBeanCast(object, type);
    }

    protected Object choseValue(Map<T, Object> nameMap, T[] keyDataList) {
        if (keyDataList == null) {
            return null;
        }
        for (T nameMapKey : keyDataList) {
            Object name = nameMap.get(nameMapKey);
            if (name != null) {
                return name;
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

}
