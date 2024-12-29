package com.github.fieldintercept;

import com.github.fieldintercept.util.TypeUtil;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public class DefaultCompositeFieldIntercept<KEY, VALUE, JOIN_POINT> implements CompositeFieldIntercept<KEY, VALUE, JOIN_POINT> {
    private final KeyNameFieldIntercept<KEY, JOIN_POINT> keyNameFieldIntercept;
    private final KeyValueFieldIntercept<KEY, VALUE, JOIN_POINT> keyValueFieldIntercept;

    public DefaultCompositeFieldIntercept(KeyNameFieldIntercept<KEY, JOIN_POINT> keyNameFieldIntercept,
                                          KeyValueFieldIntercept<KEY, VALUE, JOIN_POINT> keyValueFieldIntercept) {
        this.keyNameFieldIntercept = Objects.requireNonNull(keyNameFieldIntercept, "keyNameFieldIntercept");
        this.keyValueFieldIntercept = Objects.requireNonNull(keyValueFieldIntercept, "keyValueFieldIntercept");
    }

    public DefaultCompositeFieldIntercept(Function<Collection<KEY>, Map<KEY, ?>> selectNameMapByKeys,
                                          Function<Collection<KEY>, Map<KEY, VALUE>> selectValueMapByKeys) {
        this(null, null, selectNameMapByKeys, selectValueMapByKeys);
    }

    public DefaultCompositeFieldIntercept(Class<KEY> keyClass,
                                          Function<Collection<KEY>, Map<KEY, ?>> selectNameMapByKeys,
                                          Function<Collection<KEY>, Map<KEY, VALUE>> selectValueMapByKeys) {
        this(keyClass, null, selectNameMapByKeys, selectValueMapByKeys);
    }

    public DefaultCompositeFieldIntercept(Class<KEY> keyClass,
                                          Class<VALUE> valueClass,
                                          Function<Collection<KEY>, Map<KEY, ?>> selectNameMapByKeys,
                                          Function<Collection<KEY>, Map<KEY, VALUE>> selectValueMapByKeys) {
        if (keyClass == null) {
            if (getClass() != DefaultCompositeFieldIntercept.class) {
                try {
                    Class<?> key = TypeUtil.findGenericType(this, DefaultCompositeFieldIntercept.class, "KEY");
                    keyClass = (Class<KEY>) key;
                } catch (IllegalStateException ignored) {
                }
            }
            if (keyClass == null) {
                keyClass = (Class<KEY>) Integer.class;
            }
        }
        if (valueClass == null) {
            if (getClass() != DefaultCompositeFieldIntercept.class) {
                try {
                    valueClass = (Class<VALUE>) TypeUtil.findGenericType(this, DefaultCompositeFieldIntercept.class, "VALUE");
                } catch (Exception e) {
                    valueClass = (Class<VALUE>) Object.class;
                }
            } else {
                valueClass = (Class<VALUE>) Object.class;
            }
        }
        this.keyNameFieldIntercept = new KeyNameFieldIntercept<>(keyClass, selectNameMapByKeys);
        this.keyValueFieldIntercept = new KeyValueFieldIntercept<>(keyClass, valueClass, selectValueMapByKeys);
    }

    @Override
    public KeyNameFieldIntercept<KEY, JOIN_POINT> keyNameFieldIntercept() {
        return keyNameFieldIntercept;
    }

    @Override
    public KeyValueFieldIntercept<KEY, VALUE, JOIN_POINT> keyValueFieldIntercept() {
        return keyValueFieldIntercept;
    }
}