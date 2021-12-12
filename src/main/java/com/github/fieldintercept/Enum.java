package com.github.fieldintercept;

public interface Enum<KEY, VALUE> {
    KEY getKey();

    VALUE getValue();

}