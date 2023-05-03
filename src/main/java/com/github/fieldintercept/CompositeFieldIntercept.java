package com.github.fieldintercept;

import com.github.fieldintercept.util.TypeUtil;
import org.aspectj.lang.JoinPoint;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 组合字段字段设置名称
 *
 * @author acer01
 */
public class CompositeFieldIntercept<KEY, VALUE> implements ReturnFieldDispatchAop.FieldIntercept {
    protected final KeyNameFieldIntercept<KEY> keyNameFieldIntercept;
    protected final KeyValueFieldIntercept<KEY, VALUE> keyValueFieldIntercept;

    public CompositeFieldIntercept() {
        this(null, 0);
    }

    public CompositeFieldIntercept(Class<KEY> keyClass,
                                   int shareTimeout) {
        if (keyClass == null) {
            if (getClass() != CompositeFieldIntercept.class) {
                try {
                    Class<?> key = TypeUtil.findGenericType(this, CompositeFieldIntercept.class, "KEY");
                    keyClass = (Class<KEY>) key;
                } catch (IllegalStateException ignored) {
                }
            }
            if (keyClass == null) {
                keyClass = (Class<KEY>) Integer.class;
            }
        }
        this.keyNameFieldIntercept = new KeyNameFieldIntercept<>(keyClass, this::selectNameMapByKeys, shareTimeout);
        this.keyValueFieldIntercept = new KeyValueFieldIntercept<>(keyClass, this::selectValueMapByKeys, shareTimeout);
    }

    public CompositeFieldIntercept(Class<KEY> keyClass,
                                   Function<Collection<KEY>, Map<KEY, ?>> selectNameMapByKeys,
                                   Function<Collection<KEY>, Map<KEY, VALUE>> selectValueMapByKeys,
                                   int shareTimeout) {
        if (keyClass == null) {
            if (getClass() != CompositeFieldIntercept.class) {
                try {
                    Class<?> key = TypeUtil.findGenericType(this, CompositeFieldIntercept.class, "T");
                    keyClass = (Class<KEY>) key;
                } catch (IllegalStateException ignored) {
                }
            }
            if (keyClass == null) {
                keyClass = (Class<KEY>) Integer.class;
            }
        }
        this.keyNameFieldIntercept = new KeyNameFieldIntercept<>(keyClass, selectNameMapByKeys, shareTimeout);
        this.keyValueFieldIntercept = new KeyValueFieldIntercept<>(keyClass, selectValueMapByKeys, shareTimeout);
    }

    public Map<KEY, ?> selectNameMapByKeys(Collection<KEY> keys) {
        return null;
    }

    public Map<KEY, VALUE> selectValueMapByKeys(Collection<KEY> keys) {
        return null;
    }

    @Override
    public void accept(JoinPoint joinPoint, List<CField> cFields) {
        List<CField> nameFields = cFields.stream().filter(e -> !e.existPlaceholder() && isString(e))
                .collect(Collectors.toList());
        List<CField> beanFields;
        if (nameFields.size() > 0) {
            keyNameFieldIntercept.accept(joinPoint, nameFields);
            beanFields = new ArrayList<>(cFields);
            beanFields.removeAll(nameFields);
        } else {
            beanFields = cFields;
        }

        if (beanFields.size() > 0) {
            keyValueFieldIntercept.accept(joinPoint, beanFields);
        }
    }

    @Override
    public void begin(JoinPoint joinPoint, List<CField> fieldList, Object result) {
        keyNameFieldIntercept.begin(joinPoint, fieldList, result);
        keyValueFieldIntercept.begin(joinPoint, fieldList, result);
    }

    @Override
    public void stepBegin(int step, JoinPoint joinPoint, List<CField> fieldList, Object result) {
        keyNameFieldIntercept.stepBegin(step, joinPoint, fieldList, result);
        keyValueFieldIntercept.stepBegin(step, joinPoint, fieldList, result);
    }

    @Override
    public void stepEnd(int step, JoinPoint joinPoint, List<CField> fieldList, Object result) {
        keyNameFieldIntercept.stepEnd(step, joinPoint, fieldList, result);
        keyValueFieldIntercept.stepEnd(step, joinPoint, fieldList, result);
    }

    @Override
    public void end(JoinPoint joinPoint, List<CField> allFieldList, Object result) {
        keyNameFieldIntercept.end(joinPoint, allFieldList, result);
        keyValueFieldIntercept.end(joinPoint, allFieldList, result);
    }

    public boolean isString(CField field) {
        return field.getType() == String.class || field.getGenericType() == String.class;
    }

    public void setConfigurableEnvironment(ConfigurableEnvironment configurableEnvironment) {
        keyValueFieldIntercept.setConfigurableEnvironment(configurableEnvironment);
    }

}
