package com.github.fieldintercept;

import java.util.Objects;

public class DefaultCompositeFieldIntercept<KEY, VALUE, JOIN_POINT> implements CompositeFieldIntercept<KEY, VALUE, JOIN_POINT> {
    private final KeyNameFieldIntercept<KEY, JOIN_POINT> keyNameFieldIntercept;
    private final KeyValueFieldIntercept<KEY, VALUE, JOIN_POINT> keyValueFieldIntercept;

    public DefaultCompositeFieldIntercept(KeyNameFieldIntercept<KEY, JOIN_POINT> keyNameFieldIntercept,
                                          KeyValueFieldIntercept<KEY, VALUE, JOIN_POINT> keyValueFieldIntercept) {
        this.keyNameFieldIntercept = Objects.requireNonNull(keyNameFieldIntercept, "keyNameFieldIntercept");
        this.keyValueFieldIntercept = Objects.requireNonNull(keyValueFieldIntercept, "keyValueFieldIntercept");
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