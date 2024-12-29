package com.github.fieldintercept;

import com.github.fieldintercept.util.TypeUtil;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 组合字段字段设置名称
 * 例子：
 * <pre>
 *
 *     public abstract class AbstractService<REPOSITORY extends AbstractMapper<PO, ID>,
 *         PO extends AbstractPO<ID>,
 *         ID extends Number> implements CompositeFieldIntercept<ID, PO, Object> {
 *
 *          private final Class<ID> keyClass = CompositeFieldIntercept.getKeyClass(this, AbstractService.class, "ID", Integer.class);
 *
 *          private final Class<PO> valueClass = CompositeFieldIntercept.getValueClass(this, AbstractService.class, "PO", Object.class);
 *
 *          private final KeyNameFieldIntercept<ID, Object> keyNameFieldIntercept = new KeyNameFieldIntercept<>(keyClass, this::selectNameMapByKeys, 0);
 *
 *          private final KeyValueFieldIntercept<ID, PO, Object> keyValueFieldIntercept = new KeyValueFieldIntercept<>(keyClass, valueClass, this::selectValueMapByKeys, 0);
 *
 *          \@Override
 *          public KeyNameFieldIntercept<ID, Object> keyNameFieldIntercept() {
 *              return keyNameFieldIntercept;
 *          }
 *
 *          \@Override
 *          public KeyValueFieldIntercept<ID, PO, Object> keyValueFieldIntercept() {
 *              return keyValueFieldIntercept;
 *          }
 *     }
 * </pre>
 *
 * @author acer01
 */
public interface CompositeFieldIntercept<KEY, VALUE, JOIN_POINT> extends ReturnFieldDispatchAop.FieldIntercept<JOIN_POINT>, ReturnFieldDispatchAop.SelectMethodHolder {
    static <KEY, VALUE, JoinPoint, INSTANCE extends CompositeFieldIntercept<KEY, VALUE, JoinPoint>> Class<KEY> getKeyClass(INSTANCE thisInstance, Class<? super INSTANCE> interceptClass, String genericTypeParamName, Class<?> defaultClass) {
        Class<KEY> keyClass = null;
        if (thisInstance.getClass() != interceptClass) {
            try {
                Class<?> key = TypeUtil.findGenericType(thisInstance, interceptClass, genericTypeParamName);
                keyClass = (Class) key;
            } catch (IllegalStateException ignored) {
            }
        }
        if (keyClass == null) {
            keyClass = (Class<KEY>) defaultClass;
        }
        return keyClass;
    }

    static <KEY, VALUE, JoinPoint, INSTANCE extends CompositeFieldIntercept<KEY, VALUE, JoinPoint>> Class<VALUE> getValueClass(INSTANCE thisInstance, Class<? super INSTANCE> interceptClass, String genericTypeParamName, Class<?> defaultClass) {
        Class<VALUE> genericType;
        try {
            genericType = (Class<VALUE>) TypeUtil.findGenericType(thisInstance, interceptClass, genericTypeParamName);
        } catch (IllegalStateException ignored) {
            genericType = null;
        }
        if (genericType == null) {
            genericType = (Class<VALUE>) defaultClass;
        }
        return genericType;
    }

    static <KEY, VALUE, JOIN_POINT> CompositeFieldIntercept<KEY, VALUE, JOIN_POINT> newInstance(KeyNameFieldIntercept<KEY, JOIN_POINT> keyNameFieldIntercept,
                                                                                                KeyValueFieldIntercept<KEY, VALUE, JOIN_POINT> keyValueFieldIntercept) {
        return new DefaultCompositeFieldIntercept<>(keyNameFieldIntercept, keyValueFieldIntercept);
    }

    static <KEY, V, JOIN_POINT> CompositeFieldIntercept<KEY, V, JOIN_POINT> newInstance(Class<KEY> keyClass,
                                                                                        Function<Collection<KEY>, Map<KEY, ?>> selectNameMapByKeys,
                                                                                        Function<Collection<KEY>, Map<KEY, V>> selectValueMapByKeys) {
        return new DefaultCompositeFieldIntercept<>(keyClass, null, selectNameMapByKeys, selectValueMapByKeys);
    }

    static <KEY, VALUE, JOIN_POINT> CompositeFieldIntercept<KEY, VALUE, JOIN_POINT> newInstance(Class<KEY> keyClass,
                                                                                                Class<VALUE> valueClass,
                                                                                                Function<Collection<KEY>, Map<KEY, ?>> selectNameMapByKeys,
                                                                                                Function<Collection<KEY>, Map<KEY, VALUE>> selectValueMapByKeys) {
        return new DefaultCompositeFieldIntercept<>(keyClass, valueClass, selectNameMapByKeys, selectValueMapByKeys);
    }

    KeyNameFieldIntercept<KEY, JOIN_POINT> keyNameFieldIntercept();

    KeyValueFieldIntercept<KEY, VALUE, JOIN_POINT> keyValueFieldIntercept();

    @Override
    default void accept(JOIN_POINT joinPoint, List<CField> fieldList) {
        ReturnFieldDispatchAop.SplitCFieldList split = ReturnFieldDispatchAop.split(fieldList);
        List<CField> keyNameFieldList = split.getKeyNameFieldList();
        List<CField> keyValueFieldList = split.getKeyValueFieldList();

        ReturnFieldDispatchAop<JOIN_POINT> aop;
        if (keyNameFieldList != null && keyValueFieldList != null && (aop = ReturnFieldDispatchAop.getAop(fieldList)) != null) {
            List<Runnable> runnableList;
            if (keyValueFieldList.size() > keyNameFieldList.size()) {
                runnableList = Arrays.asList(
                        () -> keyValueFieldIntercept().accept(joinPoint, keyValueFieldList),
                        () -> keyNameFieldIntercept().accept(joinPoint, keyNameFieldList)
                );
            } else {
                runnableList = Arrays.asList(
                        () -> keyNameFieldIntercept().accept(joinPoint, keyNameFieldList),
                        () -> keyValueFieldIntercept().accept(joinPoint, keyValueFieldList)
                );
            }
            // await
            aop.await(runnableList);
        } else {
            if (keyNameFieldList != null) {
                keyNameFieldIntercept().accept(joinPoint, keyNameFieldList);
            }
            if (keyValueFieldList != null) {
                keyValueFieldIntercept().accept(joinPoint, keyValueFieldList);
            }
        }
    }

    @Override
    default void begin(String beanName, ReturnFieldDispatchAop.GroupCollect<JOIN_POINT> collect, List<CField> fieldList) {
        ReturnFieldDispatchAop.SplitCFieldList split = ReturnFieldDispatchAop.split(fieldList);
        List<CField> keyNameFieldList = split.getKeyNameFieldList();
        List<CField> keyValueFieldList = split.getKeyValueFieldList();

        if (keyNameFieldList != null) {
            keyNameFieldIntercept().begin(beanName, collect, keyNameFieldList);
        }
        if (keyValueFieldList != null) {
            keyValueFieldIntercept().begin(beanName, collect, keyValueFieldList);
        }
    }

    @Override
    default void end(String beanName, ReturnFieldDispatchAop.GroupCollect<JOIN_POINT> collect, List<CField> fieldList) {
        ReturnFieldDispatchAop.SplitCFieldList split = ReturnFieldDispatchAop.split(fieldList);
        List<CField> keyNameFieldList = split.getKeyNameFieldList();
        List<CField> keyValueFieldList = split.getKeyValueFieldList();

        if (keyNameFieldList != null) {
            keyNameFieldIntercept().end(beanName, collect, keyNameFieldList);
        }
        if (keyValueFieldList != null) {
            keyValueFieldIntercept().end(beanName, collect, keyValueFieldList);
        }
    }
}
