package com.github.fieldintercept.util;

import com.github.fieldintercept.ReturnFieldDispatchAop;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public class FieldCompletableFuture<T> extends SnapshotCompletableFuture<T> {
    private transient final FieldCompletableFuture<T> root;
    private transient final FieldCompletableFuture<T> parent;
    private transient volatile ReturnFieldDispatchAop.GroupCollect<?> groupCollect;
    private transient boolean parentDone;
    private transient Boolean useAggregation;
    private T value;

    public FieldCompletableFuture() {
        this.value = null;
        this.parent = null;
        this.root = null;
    }

    public FieldCompletableFuture(T value) {
        this.value = value;
        this.parent = null;
        this.root = null;
    }

    private FieldCompletableFuture(FieldCompletableFuture<T> root, FieldCompletableFuture<T> parent, CompletionStage<T> stage) {
        this.value = null;
        this.root = root;
        this.parent = parent;
        stage.whenComplete(this::next);
    }

    public static <T> FieldCompletableFuture<T> completableFuture(T value) {
        return new FieldCompletableFuture<>(value);
    }

    public static FieldCompletableFuture<Object[]> completableFuture(Object... value) {
        return new FieldCompletableFuture<>(value);
    }

    public FieldCompletableFuture<T> useAggregation() {
        this.useAggregation = true;
        return this;
    }

    public FieldCompletableFuture<T> disableAggregation() {
        this.useAggregation = false;
        return this;
    }

    public boolean isUseAggregation() {
        return useAggregation != null && useAggregation;
    }

    public boolean isChainCall() {
        return parent != null;
    }

    private void next(T result, Throwable throwable) {
        this.value = result;
        this.parentDone = true;
        if (throwable != null) {
            super.completeExceptionally(PlatformDependentUtil.unwrap(throwable));
            return;
        }
        if (!isNeedAutowired(result)) {
            super.complete(result);
            return;
        }

        try {
            FieldCompletableFuture<T> future = new FieldCompletableFuture<>(result);
            if (threadSnapshot != null && threadSnapshot.isNeedReplay()) {
                threadSnapshot.replay(() -> groupCollect.autowiredFieldValue(future));
            } else {
                groupCollect.autowiredFieldValue(future);
            }
            if (future.isDone() && !future.isCompletedExceptionally()) {
                super.complete(result);
            } else {
                future.whenComplete((unused, throwable1) -> {
                    if (throwable1 != null) {
                        FieldCompletableFuture.super.completeExceptionally(PlatformDependentUtil.unwrap(throwable1));
                    } else {
                        FieldCompletableFuture.super.complete(result);
                    }
                });
            }
        } catch (Throwable e) {
            super.completeExceptionally(PlatformDependentUtil.unwrap(e));
        }
    }

    private boolean isNeedAutowired(T result) {
        try {
            return result != null && groupCollect != null && parent.get() != result && !groupCollect.isBasicType(result.getClass());
        } catch (Exception e) {
            return false;
        }
    }

    public T value() {
        return isDone() || parentDone || root == null ? value : root.value();
    }

    public boolean complete() {
        if (parentDone && !isDone()) {
            return false;
        } else {
            return complete(value());
        }
    }

    public void access(ReturnFieldDispatchAop.GroupCollect<?> groupCollect) {
        this.groupCollect = groupCollect;
        if (useAggregation == null) {
            this.useAggregation = groupCollect.getAop().isChainCallUseAggregation();
        }
        if (parent != null) {
            parent.access(groupCollect);
        }
    }

    @Override
    public boolean snapshot(Function<Runnable, Runnable> taskDecorate) {
        if (parent != null) {
            parent.snapshot(taskDecorate);
        }
        return super.snapshot(taskDecorate);
    }

    @Override
    public boolean complete(T result, Throwable throwable) {
        if (root != null) {
            return root.complete(result, throwable);
        } else {
            return super.complete(value(), throwable);
        }
    }

    @Override
    public boolean complete(T value) {
        if (root != null) {
            return root.complete(value);
        } else {
            return super.complete(value());
        }
    }

    @Override
    public boolean completeExceptionally(Throwable ex) {
        if (root != null) {
            return root.completeExceptionally(ex);
        } else {
            return super.completeExceptionally(ex);
        }
    }

    @Override
    public <U> FieldCompletableFuture<U> thenApply(Function<? super T, ? extends U> fn) {
        return wrap(super.thenApply(fn));
    }

    @Override
    public <U> FieldCompletableFuture<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
        return wrap(super.thenApplyAsync(fn));
    }

    @Override
    public <U> FieldCompletableFuture<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor) {
        return wrap(super.thenApplyAsync(fn, executor));
    }

    @Override
    public FieldCompletableFuture<Void> thenAccept(Consumer<? super T> action) {
        return wrap(super.thenAccept(action));
    }

    @Override
    public FieldCompletableFuture<Void> thenAcceptAsync(Consumer<? super T> action) {
        return wrap(super.thenAcceptAsync(action));
    }

    @Override
    public FieldCompletableFuture<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
        return wrap(super.thenAcceptAsync(action, executor));
    }

    @Override
    public FieldCompletableFuture<Void> thenRun(Runnable action) {
        return wrap(super.thenRun(action));
    }

    @Override
    public FieldCompletableFuture<Void> thenRunAsync(Runnable action) {
        return wrap(super.thenRunAsync(action));
    }

    @Override
    public FieldCompletableFuture<Void> thenRunAsync(Runnable action, Executor executor) {
        return wrap(super.thenRunAsync(action, executor));
    }

    @Override
    public <U, V> FieldCompletableFuture<V> thenCombine(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return wrap(super.thenCombine(other, fn));
    }

    @Override
    public <U, V> FieldCompletableFuture<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
        return wrap(super.thenCombineAsync(other, fn));
    }

    @Override
    public <U, V> FieldCompletableFuture<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn, Executor executor) {
        return wrap(super.thenCombineAsync(other, fn, executor));
    }

    @Override
    public <U> FieldCompletableFuture<Void> thenAcceptBoth(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        return wrap(super.thenAcceptBoth(other, action));
    }

    @Override
    public <U> FieldCompletableFuture<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
        return wrap(super.thenAcceptBothAsync(other, action));
    }

    @Override
    public <U> FieldCompletableFuture<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action, Executor executor) {
        return wrap(super.thenAcceptBothAsync(other, action, executor));
    }

    @Override
    public FieldCompletableFuture<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
        return wrap(super.runAfterBoth(other, action));
    }

    @Override
    public FieldCompletableFuture<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
        return wrap(super.runAfterBothAsync(other, action));
    }

    @Override
    public FieldCompletableFuture<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action, Executor executor) {
        return wrap(super.runAfterBothAsync(other, action, executor));
    }

    @Override
    public <U> FieldCompletableFuture<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return wrap(super.applyToEither(other, fn));
    }

    @Override
    public <U> FieldCompletableFuture<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn) {
        return wrap(super.applyToEitherAsync(other, fn));
    }

    @Override
    public <U> FieldCompletableFuture<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn, Executor executor) {
        return wrap(super.applyToEitherAsync(other, fn, executor));
    }

    @Override
    public FieldCompletableFuture<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return wrap(super.acceptEither(other, action));
    }

    @Override
    public FieldCompletableFuture<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action) {
        return wrap(super.acceptEitherAsync(other, action));
    }

    @Override
    public FieldCompletableFuture<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action, Executor executor) {
        return wrap(super.acceptEitherAsync(other, action, executor));
    }

    @Override
    public FieldCompletableFuture<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
        return wrap(super.runAfterEither(other, action));
    }

    @Override
    public FieldCompletableFuture<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
        return wrap(super.runAfterEitherAsync(other, action));
    }

    @Override
    public FieldCompletableFuture<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action, Executor executor) {
        return wrap(super.runAfterEitherAsync(other, action, executor));
    }

    @Override
    public <U> FieldCompletableFuture<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
        return wrap(super.thenCompose(fn));
    }

    @Override
    public <U> FieldCompletableFuture<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
        return wrap(super.thenComposeAsync(fn));
    }

    @Override
    public <U> FieldCompletableFuture<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor) {
        return wrap(super.thenComposeAsync(fn, executor));
    }

    @Override
    public FieldCompletableFuture<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
        return wrap(super.whenComplete(action));
    }

    @Override
    public FieldCompletableFuture<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
        return wrap(super.whenCompleteAsync(action));
    }

    @Override
    public FieldCompletableFuture<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor) {
        return wrap(super.whenCompleteAsync(action, executor));
    }

    @Override
    public <U> FieldCompletableFuture<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
        return wrap(super.handle(fn));
    }

    @Override
    public <U> FieldCompletableFuture<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
        return wrap(super.handleAsync(fn));
    }

    @Override
    public <U> FieldCompletableFuture<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
        return wrap(super.handleAsync(fn, executor));
    }

    @Override
    public FieldCompletableFuture<T> exceptionally(Function<Throwable, ? extends T> fn) {
        return wrap(super.exceptionally(fn));
    }

    @Override
    public FieldCompletableFuture<T> toCompletableFuture() {
        return this;
    }

    protected <U> FieldCompletableFuture<U> wrap(CompletableFuture future) {
        return new FieldCompletableFuture<>(root == null ? this : root, this, future);
    }
}
