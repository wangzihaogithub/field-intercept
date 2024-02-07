package com.github.fieldintercept.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;

public class FieldCompletableFuture<T> extends CompletableFuture<T> {
    private final T value;

    public FieldCompletableFuture(T value) {
        this.value = value;
    }

    public static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof ExecutionException || throwable instanceof InvocationTargetException || throwable instanceof UndeclaredThrowableException) {
            return throwable.getCause();
        } else {
            return throwable;
        }
    }

    public T value() {
        return value;
    }

    public boolean complete() {
        return super.complete(value);
    }

    public BiConsumer<Object, Throwable> thenComplete() {
        return (unused, throwable) -> {
            if (throwable != null) {
                completeExceptionally(unwrap(throwable));
            } else {
                complete();
            }
        };
    }
}
