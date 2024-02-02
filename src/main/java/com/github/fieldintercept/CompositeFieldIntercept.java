package com.github.fieldintercept;

import com.github.fieldintercept.util.TypeUtil;

import java.util.ArrayList;
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
        KeyNameFieldIntercept<KEY, JoinPoint> keyNameFieldIntercept = keyNameFieldIntercept();
        KeyValueFieldIntercept<KEY, VALUE, JoinPoint> keyValueFieldIntercept = keyValueFieldIntercept();
        Split split = new Split(fieldList);
        if (split.keyNameFields != null) {
            keyNameFieldIntercept.accept(joinPoint, split.keyNameFields);
        }
        if (split.keyValueFields != null) {
            keyValueFieldIntercept.accept(joinPoint, split.keyValueFields);
        }
    }

    @Override
    default void begin(JoinPoint joinPoint, List<CField> fieldList, Object result) {
        KeyNameFieldIntercept<KEY, JoinPoint> keyNameFieldIntercept = keyNameFieldIntercept();
        KeyValueFieldIntercept<KEY, VALUE, JoinPoint> keyValueFieldIntercept = keyValueFieldIntercept();
        Split split = new Split(fieldList);
        if (split.keyNameFields != null) {
            keyNameFieldIntercept.begin(joinPoint, split.keyNameFields, result);
        }
        if (split.keyValueFields != null) {
            keyValueFieldIntercept.begin(joinPoint, split.keyValueFields, result);
        }
    }

    @Override
    default void stepBegin(int step, JoinPoint joinPoint, List<CField> fieldList, Object result) {
        KeyNameFieldIntercept<KEY, JoinPoint> keyNameFieldIntercept = keyNameFieldIntercept();
        KeyValueFieldIntercept<KEY, VALUE, JoinPoint> keyValueFieldIntercept = keyValueFieldIntercept();
        Split split = new Split(fieldList);
        if (split.keyNameFields != null) {
            keyNameFieldIntercept.stepBegin(step, joinPoint, split.keyNameFields, result);
        }
        if (split.keyValueFields != null) {
            keyValueFieldIntercept.stepBegin(step, joinPoint, split.keyValueFields, result);
        }
    }

    @Override
    default void stepEnd(int step, JoinPoint joinPoint, List<CField> fieldList, Object result) {
        KeyNameFieldIntercept<KEY, JoinPoint> keyNameFieldIntercept = keyNameFieldIntercept();
        KeyValueFieldIntercept<KEY, VALUE, JoinPoint> keyValueFieldIntercept = keyValueFieldIntercept();
        Split split = new Split(fieldList);
        if (split.keyNameFields != null) {
            keyNameFieldIntercept.stepEnd(step, joinPoint, split.keyNameFields, result);
        }
        if (split.keyValueFields != null) {
            keyValueFieldIntercept.stepEnd(step, joinPoint, split.keyValueFields, result);
        }
    }

    @Override
    default void end(JoinPoint joinPoint, List<CField> allFieldList, Object result) {
        KeyNameFieldIntercept<KEY, JoinPoint> keyNameFieldIntercept = keyNameFieldIntercept();
        KeyValueFieldIntercept<KEY, VALUE, JoinPoint> keyValueFieldIntercept = keyValueFieldIntercept();
        Split split = new Split(allFieldList);
        if (split.keyNameFields != null) {
            keyNameFieldIntercept.end(joinPoint, split.keyNameFields, result);
        }
        if (split.keyValueFields != null) {
            keyValueFieldIntercept.end(joinPoint, split.keyValueFields, result);
        }
    }

    public static class Split {
        final List<CField> keyNameFields;
        final List<CField> keyValueFields;

        public Split(List<CField> cFields) {
            List<CField> keyNameFields = null;
            List<CField> keyValueFields = null;
            for (CField e : cFields) {
                if (e.existPlaceholder() || !isString(e)) {
                    if (keyValueFields == null) {
                        keyValueFields = new ArrayList<>(Math.min(cFields.size(), 16));
                    }
                    keyValueFields.add(e);
                } else {
                    if (keyNameFields == null) {
                        keyNameFields = new ArrayList<>(Math.min(cFields.size(), 16));
                    }
                    keyNameFields.add(e);
                }
            }
            this.keyNameFields = keyNameFields;
            this.keyValueFields = keyValueFields;
        }

        public static boolean isString(CField field) {
            return field.getType() == String.class || field.getGenericType() == String.class;
        }
    }

    public static <KEY, VALUE, JoinPoint, INSTANCE extends CompositeFieldIntercept<KEY, VALUE, JoinPoint>> Class<KEY> getKeyClass(INSTANCE thisInstance, Class<? super INSTANCE> interceptClass, String genericTypeParamName, Class<?> defaultClass) {
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

    public static <KEY, VALUE, JoinPoint, INSTANCE extends CompositeFieldIntercept<KEY, VALUE, JoinPoint>> Class<VALUE> getValueClass(INSTANCE thisInstance, Class<? super INSTANCE> interceptClass, String genericTypeParamName, Class<?> defaultClass) {
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
