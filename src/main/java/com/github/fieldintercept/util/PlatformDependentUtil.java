package com.github.fieldintercept.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class PlatformDependentUtil {
    public static final boolean EXIST_APACHE_DUBBO;
    public static final boolean EXIST_SPRING;
    public static final boolean EXIST_SPRING_WEB;
    public static final Class<? extends Annotation> SPRING_INDEXED_ANNOTATION;
    private static final Method METHOD_GET_LOGGER;
    private static final Method METHOD_LOGGER_ERROR;
    private static final Method METHOD_LOGGER_TRACE;
    private static final Method METHOD_LOGGER_WARN;
    private static final Method METHOD_ASPECTJ_JOIN_POINT_GET_SIGNATURE;
    private static final Method METHOD_ASPECTJ_METHOD_SIGNATURE_GET_METHOD;
    private static final CompletableFuture<Void> COMPLETED = CompletableFuture.completedFuture(null);

    static {
        boolean existApacheDubbo;
        try {
            Class.forName("org.apache.dubbo.rpc.AsyncContext");
            existApacheDubbo = true;
        } catch (Throwable e) {
            existApacheDubbo = false;
        }
        EXIST_APACHE_DUBBO = existApacheDubbo;

        boolean existSpringWeb;
        try {
            Class.forName("org.springframework.web.method.support.AsyncHandlerMethodReturnValueHandler");
            Class.forName("org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter");
            Class.forName("org.springframework.web.context.request.RequestContextHolder");
            existSpringWeb = true;
        } catch (Throwable e) {
            existSpringWeb = false;
        }
        EXIST_SPRING_WEB = existSpringWeb;

        Class springIndexedAnnotation;
        try {
            springIndexedAnnotation = Class.forName("org.springframework.stereotype.Indexed");
        } catch (Throwable e) {
            springIndexedAnnotation = null;
        }
        SPRING_INDEXED_ANNOTATION = springIndexedAnnotation;

        boolean existSpringBoot;
        try {
            Class.forName("org.springframework.core.env.ConfigurableEnvironment");
            existSpringBoot = true;
        } catch (Throwable e) {
            existSpringBoot = false;
        }
        EXIST_SPRING = existSpringBoot;

        Method loggerFactoryGetLogger;
        Method loggerError;
        Method loggerTrace;
        Method loggerWarn;
        try {
            loggerFactoryGetLogger = Class.forName("org.slf4j.LoggerFactory").getDeclaredMethod("getLogger", Class.class);
            loggerError = Class.forName("org.slf4j.Logger").getDeclaredMethod("error", String.class, Object[].class);
            loggerTrace = Class.forName("org.slf4j.Logger").getDeclaredMethod("trace", String.class, Object[].class);
            loggerWarn = Class.forName("org.slf4j.Logger").getDeclaredMethod("warn", String.class, Object[].class);
        } catch (Throwable e) {
            loggerFactoryGetLogger = null;
            loggerError = null;
            loggerTrace = null;
            loggerWarn = null;
        }
        METHOD_GET_LOGGER = loggerFactoryGetLogger;
        METHOD_LOGGER_ERROR = loggerError;
        METHOD_LOGGER_TRACE = loggerTrace;
        METHOD_LOGGER_WARN = loggerWarn;

        Method aspectjJoinPointGetSignature;
        Method aspectjMethodSignatureGetMethod;
        try {
            aspectjJoinPointGetSignature = Class.forName("org.aspectj.lang.JoinPoint").getDeclaredMethod("getSignature");
            aspectjMethodSignatureGetMethod = Class.forName("org.aspectj.lang.reflect.MethodSignature").getDeclaredMethod("getMethod");
        } catch (Throwable e) {
            aspectjJoinPointGetSignature = null;
            aspectjMethodSignatureGetMethod = null;
        }
        METHOD_ASPECTJ_JOIN_POINT_GET_SIGNATURE = aspectjJoinPointGetSignature;
        METHOD_ASPECTJ_METHOD_SIGNATURE_GET_METHOD = aspectjMethodSignatureGetMethod;
    }

    public static Method aspectjMethodSignatureGetMethod(Object methodSignature) {
        if (methodSignature != null && METHOD_ASPECTJ_JOIN_POINT_GET_SIGNATURE != null && METHOD_ASPECTJ_JOIN_POINT_GET_SIGNATURE.getDeclaringClass().isAssignableFrom(methodSignature.getClass())) {
            try {
                Object signature = METHOD_ASPECTJ_JOIN_POINT_GET_SIGNATURE.invoke(methodSignature);
                if (METHOD_ASPECTJ_METHOD_SIGNATURE_GET_METHOD != null && signature != null && METHOD_ASPECTJ_METHOD_SIGNATURE_GET_METHOD.getDeclaringClass().isAssignableFrom(signature.getClass())) {
                    Object method = METHOD_ASPECTJ_METHOD_SIGNATURE_GET_METHOD.invoke(signature);
                    if (method instanceof Method) {
                        return (Method) method;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public static boolean logError(Class<?> clazz, String format, Object... args) {
        if (METHOD_LOGGER_ERROR != null) {
            try {
                Object logger = METHOD_GET_LOGGER.invoke(null, clazz);
                METHOD_LOGGER_ERROR.invoke(logger, format, args);
                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    public static boolean logTrace(Class<?> clazz, String format, Object... args) {
        if (METHOD_LOGGER_TRACE != null) {
            try {
                Object logger = METHOD_GET_LOGGER.invoke(null, clazz);
                METHOD_LOGGER_TRACE.invoke(logger, format, args);
                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    public static boolean logWarn(Class<?> clazz, String format, Object... args) {
        if (METHOD_LOGGER_WARN != null) {
            try {
                Object logger = METHOD_GET_LOGGER.invoke(null, clazz);
                METHOD_LOGGER_WARN.invoke(logger, format, args);
                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    public static boolean isProxyDubboProviderMethod(Object joinPoint) {
        return joinPoint != null
                && PlatformDependentUtil.EXIST_APACHE_DUBBO
                && ApacheDubboUtil.isProxyDubboProviderMethod(PlatformDependentUtil.aspectjMethodSignatureGetMethod(joinPoint));
    }

    public static boolean isProxySpringWebControllerMethod(Object joinPoint) {
        return joinPoint != null
                && PlatformDependentUtil.EXIST_SPRING_WEB
                && SpringWebUtil.isProxySpringWebControllerMethod(PlatformDependentUtil.aspectjMethodSignatureGetMethod(joinPoint));
    }

    public static <E extends Throwable> void sneakyThrows(Throwable t) throws E {
        throw (E) t;
    }

    public static void await(List<? extends Runnable> runnableList, Executor taskExecutor, Function<Runnable, Runnable> taskDecorate) {
        if (runnableList != null && !runnableList.isEmpty()) {
            int size = runnableList.size();
            CompletableFuture<Void>[] futures = new CompletableFuture[size - 1];
            for (int i = 1; i < size; i++) {
                futures[i - 1] = submit(runnableList.get(i), taskExecutor, taskDecorate);
            }
            // await run
            runnableList.get(0).run();
            CompletableFuture<Void> allOf = CompletableFuture.allOf(futures);
            try {
                // await get
                allOf.get();
            } catch (Exception e) {
                PlatformDependentUtil.sneakyThrows(PlatformDependentUtil.unwrap(e));
            }
        }
    }

    public static CompletableFuture<Void> submit(List<? extends Runnable> runnableList, Executor taskExecutor, Function<Runnable, Runnable> taskDecorate, boolean threadRetry) {
        if (runnableList == null) {
            return COMPLETED;
        }
        int size = runnableList.size();
        if (size == 0) {
            return COMPLETED;
        } else {
            int runSize;
            int runIndex;
            // 减少切换线程次数（如果已经在异步线程里，当前线程跑第一个，开新线程跑其他的）
            if (!threadRetry || ThreadSnapshot.isUserThread()) {
                if (size == 1) {
                    return submit(runnableList.get(0), taskExecutor, taskDecorate);
                }
                runSize = size;
                runIndex = -1;
            } else {
                runIndex = 0;
                runSize = size - 1;
                Runnable runnable = runnableList.get(runIndex);
                runnable.run();
                if (runSize == 0) {
                    return COMPLETED;
                } else if (runSize == 1) {
                    return submit(runnableList.get(1), taskExecutor, taskDecorate);
                }
            }
            CompletableFuture<Void> end = new CompletableFuture<>();
            AtomicInteger count = new AtomicInteger(runSize);
            int i = 0;
            for (Runnable runnable : runnableList) {
                if (i++ == runIndex) {
                    continue;
                }
                submit(runnable, taskExecutor, taskDecorate).whenComplete(((unused, throwable) -> {
                    // 这里有上下文
                    if (!end.isDone()) {
                        if (throwable != null) {
                            end.completeExceptionally(throwable);
                        } else if (count.decrementAndGet() == 0) {
                            end.complete(null);
                        }
                    }
                }));
            }
            return end;
        }
    }

    public static <T> CompletableFuture<Void> allOf(Collection<SnapshotCompletableFuture<T>> futureList, Function<Runnable, Runnable> taskDecorate) {
        if (futureList == null || futureList.isEmpty()) {
            return COMPLETED;
        }
        CompletableFuture<Void> end = new CompletableFuture<>();//futureList是SnapshotCompletableFuture
        AtomicInteger count = new AtomicInteger(futureList.size());
        for (CompletableFuture<?> f : futureList) {
            f.whenComplete(((unused, throwable) -> {
                if (!end.isDone()) {
                    if (throwable != null) {
                        end.completeExceptionally(throwable);
                    } else if (count.decrementAndGet() == 0) {
                        end.complete(null);
                    }
                }
            }));
        }
        return end;
    }

    private static CompletableFuture<Void> submit(Runnable runnable, Executor taskExecutor, Function<Runnable, Runnable> taskDecorate) {
        if (taskExecutor != null) {
            RunnableCompletableFuture<Void> runnableFuture = new RunnableCompletableFuture<>(taskDecorate, runnable);
            taskExecutor.execute(runnableFuture);
            return runnableFuture;
        } else {
            runnable.run();
            return COMPLETED;
        }
    }

    public static Throwable unwrap(Throwable throwable) {
        Throwable cause = throwable;
        while (cause instanceof CompletionException || cause instanceof ExecutionException || cause instanceof InvocationTargetException || cause instanceof UndeclaredThrowableException) {
            Throwable next = cause.getCause();
            if (next == null) {
                break;
            } else {
                cause = next;
            }
        }
        return cause;
    }

    public static class RunnableCompletableFuture<T> extends CompletableFuture<T> implements Runnable {
        private final Runnable runnable;
        private final ThreadSnapshot threadSnapshot;
        private final Thread thread = Thread.currentThread();

        private RunnableCompletableFuture(Function<Runnable, Runnable> taskDecorate, Runnable runnable) {
            this.threadSnapshot = taskDecorate != null ? new ThreadSnapshot(taskDecorate) : null;
            this.runnable = runnable;
        }

        @Override
        public void run() {
            if (threadSnapshot != null && thread != Thread.currentThread()) {
                threadSnapshot.replay(() -> {
                    try {
                        runnable.run();
                        complete(null);
                    } catch (Throwable t) {
                        completeExceptionally(t);
                    }
                });
            } else {
                try {
                    runnable.run();
                    complete(null);
                } catch (Throwable t) {
                    completeExceptionally(t);
                }
            }
        }

        @Override
        public String toString() {
            return "RunnableCompletableFuture{" +
                    "runnable=" + runnable +
                    ", threadSnapshot=" + threadSnapshot +
                    '}';
        }
    }

    public static class ThreadSnapshot {
        private static final ThreadLocal<ThreadSnapshot> CURRENT = new ThreadLocal<>();
        private final Runnable snapshot;
        private final Thread userThread;
        private Runnable task;

        public ThreadSnapshot(Function<Runnable, Runnable> taskDecorate) {
            this.snapshot = taskDecorate != null ? taskDecorate.apply(this::runTask) : null;
            ThreadSnapshot parent = CURRENT.get();
            if (parent != null) {
                userThread = parent.userThread;
            } else {
                userThread = Thread.currentThread();
            }
        }

        public static boolean isUserThread() {
            ThreadSnapshot threadSnapshot = CURRENT.get();
            if (threadSnapshot != null) {
                return threadSnapshot.userThread == Thread.currentThread();
            } else {
                return true;
            }
        }

        private void runTask() {
            Runnable task = this.task;
            this.task = null;
            task.run();
        }

        public boolean isAsyncThread() {
            return userThread != Thread.currentThread();
        }

        public void replay(Runnable task) {
            if (userThread == Thread.currentThread()) {
                task.run();
            } else {
                try {
                    CURRENT.set(this);
                    if (snapshot != null) {
                        this.task = task;
                        snapshot.run();
                    } else {
                        task.run();
                    }
                } finally {
                    CURRENT.remove();
                }
            }
        }
    }

}
