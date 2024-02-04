package com.github.fieldintercept;

import com.github.fieldintercept.util.TypeUtil;

import java.util.List;

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
public interface CompositeFieldIntercept<KEY, VALUE, JoinPoint> extends ReturnFieldDispatchAop.FieldIntercept<JoinPoint>, ReturnFieldDispatchAop.SelectMethodHolder {
    KeyNameFieldIntercept<KEY, JoinPoint> keyNameFieldIntercept();

    KeyValueFieldIntercept<KEY, VALUE, JoinPoint> keyValueFieldIntercept();

    @Override
    default void accept(JoinPoint joinPoint, List<CField> fieldList) {
        ReturnFieldDispatchAop.SplitCFieldList split = ReturnFieldDispatchAop.split(fieldList);
        List<CField> keyNameFieldList = split.getKeyNameFieldList();
        List<CField> keyValueFieldList = split.getKeyValueFieldList();

        if (keyNameFieldList != null) {
            keyNameFieldIntercept().accept(joinPoint, keyNameFieldList);
        }
        if (keyValueFieldList != null) {
            keyValueFieldIntercept().accept(joinPoint, keyValueFieldList);
        }
    }

    @Override
    default void begin(JoinPoint joinPoint, List<CField> fieldList, Object result) {
        ReturnFieldDispatchAop.SplitCFieldList split = ReturnFieldDispatchAop.split(fieldList);
        List<CField> keyNameFieldList = split.getKeyNameFieldList();
        List<CField> keyValueFieldList = split.getKeyValueFieldList();

        if (keyNameFieldList != null) {
            keyNameFieldIntercept().begin(joinPoint, keyNameFieldList, result);
        }
        if (keyValueFieldList != null) {
            keyValueFieldIntercept().begin(joinPoint, keyValueFieldList, result);
        }
    }

    @Override
    default void stepBegin(int step, JoinPoint joinPoint, List<CField> fieldList, Object result) {
        ReturnFieldDispatchAop.SplitCFieldList split = ReturnFieldDispatchAop.split(fieldList);
        List<CField> keyNameFieldList = split.getKeyNameFieldList();
        List<CField> keyValueFieldList = split.getKeyValueFieldList();

        if (keyNameFieldList != null) {
            keyNameFieldIntercept().stepBegin(step, joinPoint, keyNameFieldList, result);
        }
        if (keyValueFieldList != null) {
            keyValueFieldIntercept().stepBegin(step, joinPoint, keyValueFieldList, result);
        }
    }

    @Override
    default void stepEnd(int step, JoinPoint joinPoint, List<CField> fieldList, Object result) {
        ReturnFieldDispatchAop.SplitCFieldList split = ReturnFieldDispatchAop.split(fieldList);
        List<CField> keyNameFieldList = split.getKeyNameFieldList();
        List<CField> keyValueFieldList = split.getKeyValueFieldList();

        if (keyNameFieldList != null) {
            keyNameFieldIntercept().stepEnd(step, joinPoint, keyNameFieldList, result);
        }
        if (keyValueFieldList != null) {
            keyValueFieldIntercept().stepEnd(step, joinPoint, keyValueFieldList, result);
        }
    }

    @Override
    default void end(JoinPoint joinPoint, List<CField> allFieldList, Object result) {
        ReturnFieldDispatchAop.SplitCFieldList split = ReturnFieldDispatchAop.split(allFieldList);
        List<CField> keyNameFieldList = split.getKeyNameFieldList();
        List<CField> keyValueFieldList = split.getKeyValueFieldList();

        if (keyNameFieldList != null) {
            keyNameFieldIntercept().end(joinPoint, keyNameFieldList, result);
        }
        if (keyValueFieldList != null) {
            keyValueFieldIntercept().end(joinPoint, keyValueFieldList, result);
        }
    }

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
}
