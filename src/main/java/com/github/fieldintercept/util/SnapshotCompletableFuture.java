package com.github.fieldintercept.util;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class SnapshotCompletableFuture<T> extends CompletableFuture<T> {
    private transient PlatformDependentUtil.ThreadSnapshot threadSnapshot;

    public SnapshotCompletableFuture() {
    }

    public static <U> SnapshotCompletableFuture<U> completedFuture(U value) {
        SnapshotCompletableFuture<U> future = new SnapshotCompletableFuture<>();
        future.complete(value);
        return future;
    }

    public static <T> SnapshotCompletableFuture<T> newInstance(Function<Runnable, Runnable> taskDecorate) {
        SnapshotCompletableFuture<T> future = new SnapshotCompletableFuture<>();
        future.snapshot(taskDecorate);//newInstance
        return future;
    }

    public boolean snapshot(Function<Runnable, Runnable> taskDecorate) {
        if (threadSnapshot == null && taskDecorate != null) {
            this.threadSnapshot = new PlatformDependentUtil.ThreadSnapshot(taskDecorate);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean completeExceptionally(Throwable ex) {
        if (isDone()) {
            return false;
        }
        if (threadSnapshot != null) {
            threadSnapshot.replay(() -> super.completeExceptionally(ex));
            return true;
        } else {
            return super.completeExceptionally(ex);
        }
    }

    @Override
    public boolean complete(T value) {
        if (isDone()) {
            return false;
        }
        if (threadSnapshot != null) {
            threadSnapshot.replay(() -> super.complete(value));
            return true;
        } else {
            return super.complete(value);
        }
    }

    public boolean complete(T result, Throwable throwable) {
        if (throwable != null) {
            return completeExceptionally(PlatformDependentUtil.unwrap(throwable));
        } else {
            return complete(result);
        }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (threadSnapshot != null) {
            threadSnapshot.replay(() -> super.cancel(mayInterruptIfRunning));
            return isCancelled();
        } else {
            return super.cancel(mayInterruptIfRunning);
        }
    }

    @Override
    public void obtrudeException(Throwable ex) {
        if (threadSnapshot != null) {
            threadSnapshot.replay(() -> super.obtrudeException(ex));
        } else {
            super.obtrudeException(ex);
        }
    }

    @Override
    public void obtrudeValue(T value) {
        if (threadSnapshot != null) {
            threadSnapshot.replay(() -> super.obtrudeValue(value));
        } else {
            super.obtrudeValue(value);
        }
    }
}
