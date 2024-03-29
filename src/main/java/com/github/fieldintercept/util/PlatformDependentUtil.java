package com.github.fieldintercept.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.*;
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
    public static final String ATTACHMENT_PREFIX = "_fieldintercept";
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

    public static boolean isJdkClass(Class type) {
        return type != null && type.getClassLoader() == null;
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

    private static CompletableFuture<Void> submit(Runnable runnable, Executor taskExecutor, Function<Runnable, Runnable> taskDecorate) {
        if (taskExecutor != null) {
            RunnableCompletableFuture runnableFuture = new RunnableCompletableFuture(taskDecorate, runnable);
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

    public static <KEY, VALUE> Map<KEY, VALUE> mergeAttachment(Map<KEY, VALUE> valueMap, Map<String, Object> attachment) {
        if (attachment == null || attachment.isEmpty() || valueMap == null || valueMap.isEmpty()) {
            return valueMap;
        } else {
            LinkedHashMap map = new LinkedHashMap<>(valueMap.size() + ((int) (attachment.size() / 0.75F + 1)));
            map.putAll(valueMap);
            for (Map.Entry<String, Object> entry : attachment.entrySet()) {
                map.put(ATTACHMENT_PREFIX + entry.getKey(), entry.getValue());
            }
            return map;
        }
    }

    public static Map<String, Object> removeAttachment(Map valueMap) {
        Map<String, Object> attachment = null;
        if (valueMap != null && !valueMap.isEmpty()) {
            for (Object key : new ArrayList<>(valueMap.keySet())) {
                if (key instanceof String && ((String) key).startsWith(ATTACHMENT_PREFIX)) {
                    if (attachment == null) {
                        attachment = new LinkedHashMap<>();
                    }
                    attachment.put(((String) key).substring(ATTACHMENT_PREFIX.length()), valueMap.remove(key));
                }
            }
        }
        return attachment == null ? new LinkedHashMap<>() : attachment;
    }

    public static class RunnableCompletableFuture extends CompletableFuture<Void> implements Runnable {
        private final Runnable runnable;
        private final ThreadSnapshot threadSnapshot;

        private RunnableCompletableFuture(Function<Runnable, Runnable> taskDecorate, Runnable runnable) {
            this.threadSnapshot = taskDecorate != null ? new ThreadSnapshot(taskDecorate) : null;
            this.runnable = runnable;
        }

        @Override
        public void run() {
            if (threadSnapshot != null && threadSnapshot.isNeedReplay()) {
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

    public static class ThreadSnapshot implements Runnable {
        private static final ThreadLocal<LinkedList<ThreadSnapshot>> CURRENT = ThreadLocal.withInitial(LinkedList::new);
        private final Thread thread = Thread.currentThread();
        private final Runnable snapshot;
        private Runnable task;
        private String lastTaskName;

        public ThreadSnapshot(Function<Runnable, Runnable> taskDecorate) {
            this.snapshot = taskDecorate != null ? taskDecorate.apply(this) : null;
        }

        public static boolean isUserThread() {
            LinkedList<ThreadSnapshot> snapshots = CURRENT.get();
            if (snapshots.isEmpty()) {
                return true;
            } else {
                return snapshots.getFirst().thread == Thread.currentThread();
            }
        }

        @Override
        public void run() {
            Runnable task = this.task;
            this.task = null;
            lastTaskName = task.toString();
            task.run();
        }

        @Override
        public String toString() {
            return "ThreadSnapshot{" +
                    lastTaskName +
                    '}';
        }

        public boolean isNeedReplay() {
            LinkedList<ThreadSnapshot> snapshots = CURRENT.get();
            if (snapshots.isEmpty()) {
                return true;
            } else {
                return snapshots.getLast().thread != thread;
            }
        }

        public void replay(Runnable task) {
            LinkedList<ThreadSnapshot> snapshots = CURRENT.get();
            if (snapshots.isEmpty() || snapshots.getLast().thread != thread) {
                try {
                    snapshots.addLast(this);
                    if (snapshot != null) {
                        this.task = task;
                        snapshot.run();
                    } else {
                        task.run();
                    }
                } finally {
                    snapshots.removeLast();
                }
            } else {
                task.run();
            }
        }
    }

}
