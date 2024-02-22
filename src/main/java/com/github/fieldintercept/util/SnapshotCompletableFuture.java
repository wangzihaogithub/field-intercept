package com.github.fieldintercept.util;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class SnapshotCompletableFuture<T> extends CompletableFuture<T> {
    protected transient PlatformDependentUtil.ThreadSnapshot threadSnapshot;
    private transient List<BiConsumer<T, Throwable>> beforeCompleteListenerList;

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

    public void addBeforeCompleteListener(BiConsumer<T, Throwable> listener) {
        if (isDone()) {
            try {
                listener.accept(get(), null);
            } catch (Throwable e) {
                listener.accept(null, PlatformDependentUtil.unwrap(e));
            }
        } else {
            if (beforeCompleteListenerList == null) {
                beforeCompleteListenerList = new LinkedList<>();
            }
            beforeCompleteListenerList.add(listener);
        }
    }

    @Override
    public boolean completeExceptionally(Throwable ex) {
        if (isDone()) {
            return false;
        }
        if (threadSnapshot != null && threadSnapshot.isNeedReplay()) {
            threadSnapshot.replay(() -> {
                try {
                    List<BiConsumer<T, Throwable>> beforeCompleteListenerList = this.beforeCompleteListenerList;
                    if (beforeCompleteListenerList != null) {
                        ArrayList<BiConsumer<T, Throwable>> biConsumers = new ArrayList<>(beforeCompleteListenerList);
                        beforeCompleteListenerList.clear();
                        for (BiConsumer<T, Throwable> listener : biConsumers) {
                            listener.accept(null, ex);
                        }
                    }
                } finally {
                    super.completeExceptionally(ex);
                }
            });
            return true;
        } else {
            boolean b;
            try {
                List<BiConsumer<T, Throwable>> beforeCompleteListenerList = this.beforeCompleteListenerList;
                if (beforeCompleteListenerList != null) {
                    ArrayList<BiConsumer<T, Throwable>> biConsumers = new ArrayList<>(beforeCompleteListenerList);
                    beforeCompleteListenerList.clear();
                    for (BiConsumer<T, Throwable> listener : biConsumers) {
                        listener.accept(null, ex);
                    }
                }
            } finally {
                b = super.completeExceptionally(ex);
            }
            return b;
        }
    }

    @Override
    public boolean complete(T value) {
        if (isDone()) {
            return false;
        }
        if (threadSnapshot != null && threadSnapshot.isNeedReplay()) {
            threadSnapshot.replay(() -> {
                try {
                    List<BiConsumer<T, Throwable>> beforeCompleteListenerList = this.beforeCompleteListenerList;
                    if (beforeCompleteListenerList != null) {
                        ArrayList<BiConsumer<T, Throwable>> biConsumers = new ArrayList<>(beforeCompleteListenerList);
                        beforeCompleteListenerList.clear();
                        for (BiConsumer<T, Throwable> listener : biConsumers) {
                            listener.accept(value, null);
                        }
                    }
                    super.complete(value);
                } catch (Throwable t) {
                    super.completeExceptionally(t);
                }
            });
            return true;
        } else {
            boolean b;
            try {
                List<BiConsumer<T, Throwable>> beforeCompleteListenerList = this.beforeCompleteListenerList;
                if (beforeCompleteListenerList != null) {
                    ArrayList<BiConsumer<T, Throwable>> biConsumers = new ArrayList<>(beforeCompleteListenerList);
                    beforeCompleteListenerList.clear();
                    for (BiConsumer<T, Throwable> listener : biConsumers) {
                        listener.accept(value, null);
                    }
                }
                b = super.complete(value);
            } catch (Throwable t) {
                b = super.completeExceptionally(t);
            }
            return b;
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
        if (threadSnapshot != null && threadSnapshot.isNeedReplay()) {
            threadSnapshot.replay(() -> super.cancel(mayInterruptIfRunning));
            return isCancelled();
        } else {
            return super.cancel(mayInterruptIfRunning);
        }
    }

    @Override
    public void obtrudeException(Throwable ex) {
        if (threadSnapshot != null && threadSnapshot.isNeedReplay()) {
            threadSnapshot.replay(() -> super.obtrudeException(ex));
        } else {
            super.obtrudeException(ex);
        }
    }

    @Override
    public void obtrudeValue(T value) {
        if (threadSnapshot != null && threadSnapshot.isNeedReplay()) {
            threadSnapshot.replay(() -> super.obtrudeValue(value));
        } else {
            super.obtrudeValue(value);
        }
    }
}
