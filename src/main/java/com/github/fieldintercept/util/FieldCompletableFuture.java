package com.github.fieldintercept.util;

public class FieldCompletableFuture<T> extends SnapshotCompletableFuture<T> {
    private final T value;

    public FieldCompletableFuture(T value) {
        this.value = value;
    }

    public T value() {
        return value;
    }

    public boolean complete() {
        return super.complete(value());
    }

    @Override
    public boolean complete(T result, Throwable throwable) {
        return super.complete(value(), throwable);
    }

    @Override
    public boolean complete(T value) {
        return super.complete(value());
    }

}
