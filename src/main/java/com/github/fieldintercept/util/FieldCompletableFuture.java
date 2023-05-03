package com.github.fieldintercept.util;

import java.util.concurrent.CompletableFuture;

public class FieldCompletableFuture<T> extends CompletableFuture<T> {
    private final T value;

    public FieldCompletableFuture(T value) {
        this.value = value;
    }

    public T value() {
        return value;
    }

    public boolean complete() {
        return super.complete(value);
    }
}
