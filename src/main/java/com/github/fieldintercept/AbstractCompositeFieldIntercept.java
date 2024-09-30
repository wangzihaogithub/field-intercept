package com.github.fieldintercept;

public abstract class AbstractCompositeFieldIntercept<KEY, VALUE, JOIN_POINT> implements CompositeFieldIntercept<KEY, VALUE, JOIN_POINT> {
    private volatile KeyNameFieldIntercept<KEY, JOIN_POINT> keyNameFieldIntercept;
    private volatile KeyValueFieldIntercept<KEY, VALUE, JOIN_POINT> keyValueFieldIntercept;

    @Override
    public KeyNameFieldIntercept<KEY, JOIN_POINT> keyNameFieldIntercept() {
        if (keyNameFieldIntercept == null) {
            synchronized (this) {
                if (keyNameFieldIntercept == null) {
                    keyNameFieldIntercept = newKeyNameFieldIntercept();
                }
            }
        }
        return keyNameFieldIntercept;
    }

    @Override
    public KeyValueFieldIntercept<KEY, VALUE, JOIN_POINT> keyValueFieldIntercept() {
        if (keyValueFieldIntercept == null) {
            synchronized (this) {
                if (keyValueFieldIntercept == null) {
                    keyValueFieldIntercept = newKeyValueFieldIntercept();
                }
            }
        }
        return keyValueFieldIntercept;
    }

    protected abstract KeyNameFieldIntercept<KEY, JOIN_POINT> newKeyNameFieldIntercept();

    protected abstract KeyValueFieldIntercept<KEY, VALUE, JOIN_POINT> newKeyValueFieldIntercept();
}