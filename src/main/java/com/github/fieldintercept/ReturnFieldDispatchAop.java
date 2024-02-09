package com.github.fieldintercept;

import com.github.fieldintercept.annotation.EnumFieldConsumer;
import com.github.fieldintercept.annotation.FieldConsumer;
import com.github.fieldintercept.annotation.ReturnFieldAop;
import com.github.fieldintercept.annotation.RouterFieldConsumer;
import com.github.fieldintercept.util.*;
import com.github.fieldintercept.util.PlatformDependentUtil.ThreadSnapshot;

import java.beans.PropertyDescriptor;
import java.io.UnsupportedEncodingException;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.net.URLDecoder;
import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 返回字段aop (可以将id 转为中文名 ,keyField 转中文， 支持占位符)
 *
 * @author acer01
 */
public abstract class ReturnFieldDispatchAop<JOIN_POINT> {
    /**
     * groupKeyStaticMethod
     * 在调用方上执行groupKey静态方法
     */
    public static final String BEAN_NAME_ARG_GROUP_METHOD = "groupKeyStaticMethod";
    private static final Pattern QUERY_PATTERN = Pattern.compile("[?]");
    private static final Pattern DOT_PATTERN = Pattern.compile("[.]");
    private static final Map<Class<?>, Boolean> SKIP_FIELD_CLASS_CACHE_MAP = Collections.synchronizedMap(new LinkedHashMap<Class<?>, Boolean>((int) ((6 / 0.75F) + 1), 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Class<?>, Boolean> eldest) {
            return size() > 6;
        }
    });
    private static final Collection<Class<? extends Annotation>> SPRING_INDEXED_ANNOTATION_LIST = PlatformDependentUtil.SPRING_INDEXED_ANNOTATION != null ? Collections.singletonList(PlatformDependentUtil.SPRING_INDEXED_ANNOTATION) : null;
    public static final Predicate<Class> DEFAULT_SKIP_FIELD_CLASS_PREDICATE = type -> PlatformDependentUtil.SPRING_INDEXED_ANNOTATION != null && AnnotationUtil.findDeclaredAnnotation(type, SPRING_INDEXED_ANNOTATION_LIST, SKIP_FIELD_CLASS_CACHE_MAP) != null;
    private static ReturnFieldDispatchAop INSTANCE;
    protected final AnnotationCache<ReturnFieldAop> returnFieldAopCache = new AnnotationCache<>(ReturnFieldAop.class, Arrays.asList(ReturnFieldAop.class, ReturnFieldAop.Extends.class), 100);
    protected final AnnotationCache<RouterFieldConsumer> routerFieldConsumerCache = new AnnotationCache<>(RouterFieldConsumer.class, Arrays.asList(RouterFieldConsumer.class, RouterFieldConsumer.Extends.class), 100);
    protected final AnnotationCache<FieldConsumer> fieldConsumerCache = new AnnotationCache<>(FieldConsumer.class, Arrays.asList(FieldConsumer.class, FieldConsumer.Extends.class), 100);
    protected final AnnotationCache<EnumFieldConsumer> enumFieldConsumerCache = new AnnotationCache<>(EnumFieldConsumer.class, Arrays.asList(EnumFieldConsumer.class, EnumFieldConsumer.Extends.class), 100);
    /**
     * 实体类包名一样, 就认为是业务实体类
     */
    private final Set<List<String>> myProjectPackagePaths = new LinkedHashSet<>();
    /**
     * 动态注解 或 用户自定义注解
     */
    private final Set<Class<? extends Annotation>> annotations = new LinkedHashSet<>();
    // 当前提交任务数量
    private final AtomicInteger currentSubmitRunnableCounter = new AtomicInteger();
    // 总共提交任务数量
    private final LongAdder totalSubmitRunnableCounter = new LongAdder();
    private final Lock autowiredRunnableLock = new ReentrantLock();
    private final Condition autowiredRunnableCondition = autowiredRunnableLock.newCondition();
    private final LongAdder concurrentThreadCounter = new LongAdder();
    // 当前信号数量
    private final LongAdder currentSignalCounter = new LongAdder();
    private final Map<Thread, AtomicInteger> concurrentThreadMap = new ConcurrentHashMap<>();
    private final LinkedHashMap<Class, Boolean> typeBasicCacheMap = new LinkedHashMap<Class, Boolean>(16) {
        @Override
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return size() > 100;
        }
    };
    private final Map<Class, Boolean> typeEntryCacheMap = new LinkedHashMap<Class, Boolean>(64) {
        @Override
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return size() > 200;
        }
    };
    private final Map<Class, Boolean> typeMultipleCacheMap = new LinkedHashMap<Class, Boolean>(16) {
        @Override
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return size() > 100;
        }
    };
    private final Map<Class<?>, Boolean> skipFieldClassPredicateCache = Collections.synchronizedMap(new LinkedHashMap<Class<?>, Boolean>(201, 1F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return size() > 200;
        }
    });
    private final AtomicBoolean pendingSignalThreadCreateFlag = new AtomicBoolean();
    private LinkedBlockingDeque<Pending<JOIN_POINT>> pendingList;
    private Function<String, BiConsumer<JOIN_POINT, List<CField>>> consumerFactory;
    private Function<Runnable, Future> taskExecutor;
    private Function<Runnable, Runnable> taskDecorate;
    private Object configurableEnvironment;
    private Predicate<Class> skipFieldClassPredicate = DEFAULT_SKIP_FIELD_CLASS_PREDICATE;
    /**
     * 自动注入同步调用时的超时时间
     */
    private int blockGetterTimeoutMilliseconds = 30_000;
    /**
     * 控制提交AutowiredRunnable的数量，如果超过这个数量，就会阻塞
     */
    private int maxRunnableConcurrentCount = Integer.MAX_VALUE;
    /**
     * 控制批量聚合信号最大并发量，如果超过这个并发量，并且超过了队列长度(pendingQueueCapacity)，则会阻塞调用方继续生产自动注入任务。
     */
    private int batchAggregationMaxSignalConcurrentCount = 200;
    private int batchAggregationThresholdMinConcurrentCount = 1;
    private long batchAggregationPollMilliseconds = 100;
    private int batchAggregationPollMinSize = 1;
    private int batchAggregationPollMaxSize = 500;
    private int batchAggregationPendingSignalThreadCount = 1;
    final List<Thread> pendingSignalThreadList = new ArrayList<>(batchAggregationPendingSignalThreadCount);
    private int batchAggregationPendingQueueCapacity = 200;
    private boolean batchAggregationPendingNonBlock = false;
    private BatchAggregationEnum batchAggregation;

    private BiPredicate<JOIN_POINT, Object> enabled = null;

    public ReturnFieldDispatchAop() {
        this.pendingList = new LinkedBlockingDeque<>(batchAggregationPendingQueueCapacity);
        if (INSTANCE == null || !(this instanceof SimpleReturnFieldDispatchAop)) {
            INSTANCE = this;
        }
    }

    public ReturnFieldDispatchAop(Map<String, ? extends BiConsumer<JOIN_POINT, List<CField>>> map) {
        this();
        this.consumerFactory = map::get;
    }

    public ReturnFieldDispatchAop(Function<String, BiConsumer<JOIN_POINT, List<CField>>> consumerProvider) {
        this();
        this.consumerFactory = consumerProvider;
    }

    public static <T> ReturnFieldDispatchAop<T> getInstance() {
        return INSTANCE;
    }

    public static void staticAutowiredFieldValue(Object... result) {
        if (INSTANCE != null) {
            INSTANCE.autowiredFieldValue(result);
        }
    }

    public static <T> T staticAutowiredFieldValue(T result) {
        if (INSTANCE != null) {
            return (T) INSTANCE.autowiredFieldValue(result);
        } else {
            return result;
        }
    }

    public static <JOIN_POINT> ReturnFieldDispatchAop<JOIN_POINT> newInstance(Function<String, BiConsumer<JOIN_POINT, List<CField>>> consumerProvider) {
        return new SimpleReturnFieldDispatchAop<>(consumerProvider);
    }

    public static <JOIN_POINT> ReturnFieldDispatchAop<JOIN_POINT> newInstance(Map<String, ? extends BiConsumer<JOIN_POINT, List<CField>>> map) {
        return new SimpleReturnFieldDispatchAop<>(map);
    }

    public static void checkStaticMethodAccessor(Collection<String> beanNames) throws NoSuchMethodException, IllegalArgumentException {
        for (String beanName : beanNames) {
            getStaticMethodAccessor(beanName);
        }
    }

    public static String getBeanName(String beanName) {
        return QUERY_PATTERN.split(beanName, 2)[0];
    }

    public static String getBeanNameArgValue(String beanName, String argName) {
        String[] split = QUERY_PATTERN.split(beanName, 2);
        if (split.length == 2) {
            String[] pairs = split[1].split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                try {
                    String key = URLDecoder.decode(keyValue[0], "UTF-8");
                    if (argName.equals(key)) {
                        return URLDecoder.decode(keyValue[1], "UTF-8");
                    }
                } catch (UnsupportedEncodingException ignored) {

                }
            }
        }
        return null;
    }

    public static StaticMethodAccessor getStaticMethodAccessor(String beanName) {
        String argValue = getBeanNameArgValue(beanName, BEAN_NAME_ARG_GROUP_METHOD);
        if (argValue != null && argValue.length() > 0) {
            return StaticMethodAccessor.newInstance(argValue);
        } else {
            return null;
        }
    }

    public static SplitCFieldList split(List<CField> cFieldList) {
        if (cFieldList instanceof ReturnFieldDispatchAop.SplitCFieldList) {
            return (SplitCFieldList) cFieldList;
        } else {
            return new SplitCFieldList(cFieldList);
        }
    }

    public static <JOIN_POINT> ReturnFieldDispatchAop<JOIN_POINT> getAop(List<CField> cFieldList) {
        if (cFieldList instanceof ReturnFieldDispatchAop.SplitCFieldList) {
            return (ReturnFieldDispatchAop<JOIN_POINT>) ((SplitCFieldList) cFieldList).groupCollect.aop;
        } else {
            return null;
        }
    }

    public static <T> SnapshotCompletableFuture<T> startAsync(List<CField> cFieldList, Object cacheKey) {
        if (cFieldList instanceof ReturnFieldDispatchAop.SplitCFieldList) {
            return ((SplitCFieldList) cFieldList).groupCollect.startAsync(((SplitCFieldList) cFieldList).beanName, cacheKey);
        } else {
            return null;
        }
    }

    public static <T> SnapshotCompletableFuture<T> getAsync(List<CField> cFieldList, Object cacheKey) {
        if (cFieldList instanceof ReturnFieldDispatchAop.SplitCFieldList) {
            return ((SplitCFieldList) cFieldList).groupCollect.getAsync(((SplitCFieldList) cFieldList).beanName, cacheKey);
        } else {
            return null;
        }
    }

    public static <KEY, VALUE> Map<KEY, VALUE> getLocalCache(List<CField> cFieldList, Object cacheKey) {
        if (cFieldList instanceof ReturnFieldDispatchAop.SplitCFieldList) {
            return (Map<KEY, VALUE>) ((SplitCFieldList) cFieldList).groupCollect.getLocalCache(((SplitCFieldList) cFieldList).beanName, cacheKey);
        } else {
            return null;
        }
    }

    private static <JOIN_POINT> Object replayGroupBy(String beanName, BiConsumer<JOIN_POINT, List<CField>> consumer, Pending<JOIN_POINT> pending, Pending<JOIN_POINT>[] pendings, ReturnFieldDispatchAop<JOIN_POINT> aop) {
        StaticMethodAccessor staticMethodAccessor = getStaticMethodAccessor(beanName);
        GroupCollect<JOIN_POINT> groupCollectMap = pending.groupCollectMap;
        if (staticMethodAccessor != null) {
            Object[] objects = new Object[1];
            pending.snapshotGroupKeyRunnable.replay(() -> {
                try {
                    objects[0] = staticMethodAccessor.invoke(getParameterValues(staticMethodAccessor.getParameterTypes(), staticMethodAccessor.getParameterNames(), beanName, consumer, groupCollectMap.joinPoint, groupCollectMap.result, groupCollectMap, pending, pendings, aop));
                } catch (InvocationTargetException | IllegalAccessException e) {
                    aop.sneakyThrows(e);
                }
            });
            return objects[0] == null ? "" : objects[0];
        } else {
            return "";
        }
    }

    private static <JOIN_POINT> Object[] getParameterValues(Class<?>[] parameterTypes, String[] parameterNames,
                                                            String beanName, BiConsumer<?, List<CField>> consumer, Object joinPoint,
                                                            Object result, ReturnFieldDispatchAop.GroupCollect<JOIN_POINT> groupCollect,
                                                            ReturnFieldDispatchAop.Pending<JOIN_POINT> pending, Pending<JOIN_POINT>[] pendings,
                                                            ReturnFieldDispatchAop<JOIN_POINT> aop) throws InvocationTargetException, IllegalAccessException {
        Object[] args = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];
            String parameterName = parameterNames == null ? null : parameterNames[i];

            Object arg;
            if ("result".equalsIgnoreCase(parameterName) && parameterType.isAssignableFrom(result.getClass())) {
                arg = result;
            } else if (parameterType == String.class) {
                arg = beanName;
            } else if (parameterType == Object.class) {
                if ("result".equalsIgnoreCase(parameterName)) {
                    arg = result;
                } else if ("joinPoint".equalsIgnoreCase(parameterName) || "point".equalsIgnoreCase(parameterName)) {
                    arg = joinPoint;
                } else {
                    arg = result;
                }
            } else if (parameterType == Pending[].class) {
                arg = pendings;
            } else if (parameterType.isAssignableFrom(consumer.getClass())) {
                arg = consumer;
            } else if (parameterType.isAssignableFrom(pending.getClass())) {
                arg = pending;
            } else if (parameterType.isAssignableFrom(groupCollect.getClass())) {
                arg = groupCollect;
            } else if (parameterType.isAssignableFrom(aop.getClass())) {
                arg = aop;
            } else if (joinPoint != null && parameterType.isAssignableFrom(joinPoint.getClass())) {
                arg = joinPoint;
            } else if (parameterType.isAssignableFrom(result.getClass())) {
                arg = result;
            } else {
                arg = null;
            }
            args[i] = arg;
        }
        return args;
    }

    private static <JOIN_POINT> List<Runnable> groupByMerge(Pending<JOIN_POINT>[] pendings, ReturnFieldDispatchAop<JOIN_POINT> aop) {
        Map<Object, List<PendingKey<JOIN_POINT>>> groupByMap = new HashMap<>();
        for (Pending<JOIN_POINT> pending : pendings) {
            for (Map.Entry<String, List<CField>> entry : pending.groupCollectMap.groupCollectMap.entrySet()) {
                String beanName = entry.getKey();
                BiConsumer<JOIN_POINT, List<CField>> consumer = pending.groupCollectMap.getConsumer(beanName);

                Object groupKey = replayGroupBy(beanName, consumer, pending, pendings, aop);
                groupByMap.computeIfAbsent(groupKey, e -> new ArrayList<>())
                        .add(new PendingKey<>(groupKey, beanName, consumer, pending, aop));
            }
        }

        int size = groupByMap.size();
        if (size > 1) {
            List<Runnable> list = new ArrayList<>(size);
            for (Map.Entry<Object, List<PendingKey<JOIN_POINT>>> entry : groupByMap.entrySet()) {
                List<PendingKey<JOIN_POINT>> pendingKeyList = entry.getValue();
                if (pendingKeyList.size() == 1) {
                    list.add(pendingKeyList.get(0).pending);
                } else {
                    list.add(new PartMergePendingRunnable<>(entry.getKey(), pendingKeyList, aop));
                }
            }
            return list;
        } else {
            return Collections.singletonList(new AllMergePendingRunnable<>(pendings, aop));
        }
    }

    private static <JOIN_POINT> void autowiredAsyncNext(CompletableFuture<Void> future, GroupCollect<JOIN_POINT> groupCollectMap, Function<Runnable, Future> taskExecutor, Function<Runnable, Runnable> taskDecorate) {
        try {
            // 检查注入后的是否需要继续注入
            Object next = groupCollectMap.next();
            //收集返回值中的所有实体类
            groupCollectMap.aop.collectBean(next, groupCollectMap);
            if (groupCollectMap.isEmpty()) {
                future.complete(null);
            } else {
                autowiredAsync(future, groupCollectMap, taskExecutor, taskDecorate);
            }
        } catch (Throwable e) {
            future.completeExceptionally(e);
        }
    }

    private static <JOIN_POINT> void autowiredAsync(CompletableFuture<Void> future, GroupCollect<JOIN_POINT> groupCollectMap, Function<Runnable, Future> taskExecutor, Function<Runnable, Runnable> taskDecorate) {
        try {
            List<AutowiredRunnable<JOIN_POINT>> partition = groupCollectMap.partition();
            if (partition == null || partition.isEmpty()) {
                future.complete(null);
            } else {
                PlatformDependentUtil.submit(partition, taskExecutor, taskDecorate).whenComplete(((unused, throwable) -> {
                    try {
                        if (throwable != null) {
                            future.completeExceptionally(throwable);
                        } else if (groupCollectMap.isExistAsync()) {
                            PlatformDependentUtil.allOf(groupCollectMap.getAsyncList(), taskDecorate).whenComplete(((unused1, asyncThrowable) -> {
                                if (asyncThrowable != null) {
                                    future.completeExceptionally(asyncThrowable);
                                } else {
                                    // 检查注入后的是否需要继续注入
                                    autowiredAsyncNext(future, groupCollectMap, taskExecutor, taskDecorate);
                                }
                            }));
                        } else {
                            // 检查注入后的是否需要继续注入
                            autowiredAsyncNext(future, groupCollectMap, taskExecutor, taskDecorate);
                        }
                    } catch (Throwable t) {
                        future.completeExceptionally(t);
                    }
                }));
            }
        } catch (Throwable t) {
            future.completeExceptionally(t);
        }
    }

    protected abstract void aopBefore();

    protected abstract void aopAfter();

    protected abstract void aopReturningAfter(JOIN_POINT joinPoint, Object result);

    public void autowiredFieldValue(Object... result) {
        before();
        try {
            returningAfter(null, result);
        } finally {
            after();
        }
    }

    public <T> T autowiredFieldValue(T result) {
        before();
        try {
            returningAfter(null, result);
        } finally {
            after();
        }
        return result;
    }

    public void await(List<? extends Runnable> runnableList) {
        PlatformDependentUtil.await(runnableList, taskExecutor, taskDecorate);
    }

    public CompletableFuture<Void> submit(List<? extends Runnable> runnableList) {
        return PlatformDependentUtil.submit(runnableList, taskExecutor, taskDecorate);
    }

    protected void before() {
        if (concurrentThreadMap.computeIfAbsent(Thread.currentThread(), e -> new AtomicInteger()).getAndIncrement() == 0) {
            concurrentThreadCounter.increment();
        }
    }

    protected void after() {
        Thread currentThread = Thread.currentThread();
        if (concurrentThreadMap.get(currentThread).decrementAndGet() == 0) {
            concurrentThreadCounter.decrement();
            concurrentThreadMap.remove(currentThread);
        }
    }

    protected void returningAfter(JOIN_POINT joinPoint, Object result) {
        if (result == null) {
            return;
        }
        if (result instanceof FieldCompletableFuture && ((FieldCompletableFuture<?>) result).value() == null) {
            ((FieldCompletableFuture<?>) result).complete();
            return;
        }
        BiPredicate<JOIN_POINT, Object> enabledPredicate = getEnabled();
        if (enabledPredicate != null && !enabledPredicate.test(joinPoint, result)) {
            return;
        }

        PlatformDependentUtil.logTrace(ReturnFieldDispatchAop.class, "afterReturning into. joinPoint={}, result={}", joinPoint, result);
        GroupCollect<JOIN_POINT> groupCollectMap = new GroupCollect<>(joinPoint, result, this);
        try {
            //收集返回值中的所有实体类
            collectBean(result, groupCollectMap);
            if (isNeedPending(joinPoint, result)) {
                checkStaticMethodAccessor(groupCollectMap.getBeanNames());
                Pending<JOIN_POINT> pending = addPendingList(groupCollectMap);
                if (result instanceof FieldCompletableFuture) {
                    returnPendingAsync(joinPoint, (FieldCompletableFuture) result, pending);
                } else {
                    returnPendingSync(joinPoint, result, pending);
                }
            } else {
                // 在用户线程上，先拆分好任务
                groupCollectMap.partition();
                if (result instanceof FieldCompletableFuture) {
                    returnRunAsync(joinPoint, (FieldCompletableFuture) result, groupCollectMap);
                } else {
                    returnRunSync(joinPoint, result, groupCollectMap);
                }
            }
        } catch (Exception e) {
            groupCollectMap.close();
            sneakyThrows(e);
        }
    }

    protected void returnPendingSync(JOIN_POINT joinPoint, Object result, Pending<JOIN_POINT> pending) throws ExecutionException, InterruptedException, TimeoutException {
        if (batchAggregationPendingNonBlock) {
            boolean startAsync = false;
            if (PlatformDependentUtil.isProxyDubboProviderMethod(joinPoint)) {
                ApacheDubboUtil.startAsync(pending);
                startAsync = true;
            }
            if (PlatformDependentUtil.isProxySpringWebControllerMethod(joinPoint)) {
                SpringWebUtil.setPendingRequestAttribute(pending);
                startAsync = true;
            }
            if (!startAsync) {
                pending.get(blockGetterTimeoutMilliseconds, TimeUnit.MILLISECONDS);
            }
        } else {
            pending.get(blockGetterTimeoutMilliseconds, TimeUnit.MILLISECONDS);
        }
    }

    protected void returnPendingAsync(JOIN_POINT joinPoint, FieldCompletableFuture<Object> result, Pending<JOIN_POINT> pending) {

    }

    protected void returnRunSync(JOIN_POINT joinPoint, Object result, GroupCollect<JOIN_POINT> groupCollectMap) throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<Void> future = autowired(groupCollectMap, taskExecutor, taskDecorate);
        future.get(blockGetterTimeoutMilliseconds, TimeUnit.MILLISECONDS);
    }

    protected void returnRunAsync(JOIN_POINT joinPoint, FieldCompletableFuture<Object> result, GroupCollect<JOIN_POINT> groupCollectMap) {
        CompletableFuture<Void> future = autowired(groupCollectMap, taskExecutor, taskDecorate);
        result.snapshot(taskDecorate);//returnRunAsync
        future.whenComplete(result::complete);
    }

    protected boolean isNeedPending(JOIN_POINT joinPoint, Object returnResult) {
        if (batchAggregation == BatchAggregationEnum.disabled) {
            return false;
        }
        long concurrentThreadCount = concurrentThreadCounter.sum();
        if (concurrentThreadCount <= batchAggregationThresholdMinConcurrentCount) {
            return false;
        }
        if (batchAggregation == BatchAggregationEnum.manual) {
            if (joinPoint == null) {
                return false;
            } else {
                ReturnFieldAop returnFieldAop = getAnnotationReturnFieldAop(joinPoint);
                return returnFieldAop != null && returnFieldAop.batchAggregation();
            }
        } else {
            return batchAggregation == BatchAggregationEnum.auto;
        }
    }

    protected ReturnFieldAop getAnnotationReturnFieldAop(JOIN_POINT joinPoint) {
        return returnFieldAopCache.findDeclaredAnnotation(PlatformDependentUtil.aspectjMethodSignatureGetMethod(joinPoint));
    }

    protected Pending<JOIN_POINT> addPendingList(GroupCollect<JOIN_POINT> groupCollectMap) {
        startPendingSignalThreadIfNeed();
        Pending<JOIN_POINT> pending = new Pending<>(groupCollectMap, taskDecorate);
        try {
            pendingList.putLast(pending);
        } catch (InterruptedException e) {
            sneakyThrows(e);
        }
        return pending;
    }

    public int getBatchAggregationPendingQueueCapacity() {
        return batchAggregationPendingQueueCapacity;
    }

    public void setBatchAggregationPendingQueueCapacity(int batchAggregationPendingQueueCapacity) {
        if (this.batchAggregationPendingQueueCapacity != batchAggregationPendingQueueCapacity) {
            LinkedBlockingDeque<Pending<JOIN_POINT>> oldList = this.pendingList;
            LinkedBlockingDeque<Pending<JOIN_POINT>> newList = new LinkedBlockingDeque<>(batchAggregationPendingQueueCapacity);
            this.pendingList = newList;
            this.batchAggregationPendingQueueCapacity = batchAggregationPendingQueueCapacity;
            Pending<JOIN_POINT> pending;
            while ((pending = oldList.pollFirst()) != null) {
                try {
                    newList.putLast(pending);
                } catch (InterruptedException e) {
                    sneakyThrows(e);
                }
            }
        }
    }

    public int getBatchAggregationPendingSignalThreadCount() {
        return batchAggregationPendingSignalThreadCount;
    }

    public void setBatchAggregationPendingSignalThreadCount(int batchAggregationPendingSignalThreadCount) {
        int newCount = Math.max(1, batchAggregationPendingSignalThreadCount);
        int oldCount = this.batchAggregationPendingSignalThreadCount;
        if (oldCount != newCount) {
            if (pendingSignalThreadCreateFlag.get()) {
                synchronized (pendingSignalThreadList) {
                    int addCount = oldCount - newCount;
                    for (int i = 0, len = Math.abs(addCount); i < len; i++) {
                        if (addCount > 0) {
                            Thread remove = pendingSignalThreadList.remove(pendingSignalThreadList.size() - 1);
                            remove.interrupt();
                        } else {
                            PendingSignalThread<JOIN_POINT> thread = new PendingSignalThread<>(this);
                            pendingSignalThreadList.add(thread);
                        }
                    }
                }
            }
            this.batchAggregationPendingSignalThreadCount = newCount;
        }
    }

    protected void pollPending(List<Pending<JOIN_POINT>> get, int minSize, int maxSize, long timeout) throws InterruptedException {
        int count = pendingList.drainTo(get, maxSize);
        if (count < minSize) {
            long timeoutTimestamp = System.currentTimeMillis() + timeout;
            long currTimeout = timeout;
            while (true) {
                Pending<JOIN_POINT> poll;
                if ((poll = pendingList.pollFirst(currTimeout, TimeUnit.MILLISECONDS)) == null) {
                    break;
                }
                get.add(poll);
                count++;
                count += pendingList.drainTo(get, maxSize - count);
                if (count >= minSize) {
                    break;
                }
                if ((currTimeout = timeoutTimestamp - System.currentTimeMillis()) <= 0) {
                    break;
                }
            }
        }
    }

    protected void sneakyThrows(Throwable t) {
        if (t instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        PlatformDependentUtil.sneakyThrows(PlatformDependentUtil.unwrap(t));
    }

    private CompletableFuture<Void> autowired(GroupCollect<JOIN_POINT> groupCollectMap, Function<Runnable, Future> taskExecutor, Function<Runnable, Runnable> taskDecorate) {
        SnapshotCompletableFuture<Void> future = SnapshotCompletableFuture.newInstance(taskDecorate);//autowired
        autowiredAsync(future, groupCollectMap, taskExecutor, taskDecorate);
        future.whenComplete(((unused, throwable) -> groupCollectMap.close()));
        return future;
    }

    private boolean isMultiple(Class type) {
        return typeMultipleCacheMap.computeIfAbsent(type, e -> {
            if (Iterable.class.isAssignableFrom(e)) {
                return true;
            }
            if (Map.class.isAssignableFrom(e)) {
                return true;
            }
            return e.isArray();
        });
    }

    private boolean isBasicType(Class type) {
        return typeBasicCacheMap.computeIfAbsent(type, e -> e.isPrimitive()
                || e == String.class
                || Type.class.isAssignableFrom(e)
                || Number.class.isAssignableFrom(e)
                || Date.class.isAssignableFrom(e)
                || Boolean.class == e
                || TemporalAccessor.class.isAssignableFrom(e)
                || e.isEnum());
    }

    private boolean isEntity(Class type) {
        if (type.isInterface()) {
            return false;
        }

        Set<List<String>> myProjectPackagePaths = getMyProjectPackagePaths();
        if (myProjectPackagePaths.isEmpty()) {
            return true;
        }
        return typeEntryCacheMap.computeIfAbsent(type, e -> {
            Package typePackage = e.getPackage();
            if (typePackage == null) {
                return false;
            }

            String[] packagePaths = DOT_PATTERN.split(typePackage.getName());
            for (List<String> myProjectPackagePath : myProjectPackagePaths) {
                if (packagePaths.length < myProjectPackagePath.size()) {
                    continue;
                }
                boolean isEntity = true;
                for (int i = 0; i < myProjectPackagePath.size(); i++) {
                    if (!myProjectPackagePath.get(i).equals(packagePaths[i])) {
                        isEntity = false;
                        break;
                    }
                }
                if (isEntity) {
                    return true;
                }
            }
            return false;
        });
    }

    /**
     * 收集数据中的所有实体类
     *
     * @param root            数据
     * @param groupCollectMap 分组收集器
     */
    private void collectBean(Object root, GroupCollect<JOIN_POINT> groupCollectMap) throws InvocationTargetException, IllegalAccessException {
        ArrayList<Object> stack = new ArrayList<>();
        Set<Object> uniqueCollectSet = Collections.newSetFromMap(new IdentityHashMap<>());

        stack.add(root);
        do {
            Object bean = stack.remove(stack.size() - 1);
            if (bean != null && uniqueCollectSet.add(bean)) {
                addCollect(bean, groupCollectMap, stack);
            }
        } while (!stack.isEmpty());
        groupCollectMap.collectAfter();
    }

    private void addCollect(Object bean, GroupCollect<JOIN_POINT> groupCollectMap, ArrayList<Object> stack) throws InvocationTargetException, IllegalAccessException {
        if (bean instanceof Type) {
            return;
        }
        Class<?> rootClass = bean.getClass();
        if (isBasicType(rootClass)) {
            return;
        }

        if (bean instanceof FieldCompletableFuture) {
            groupCollectMap.addFuture((FieldCompletableFuture) bean);
            stack.add(((FieldCompletableFuture<?>) bean).value());
            return;
        }

        if (bean instanceof Iterable) {
            if (bean instanceof Collection) {
                stack.addAll((Collection<?>) bean);
            } else {
                for (Object each : (Iterable) bean) {
                    stack.add(each);
                }
            }
            return;
        }

        if (rootClass.isArray()) {
            for (int i = 0, length = Array.getLength(bean); i < length; i++) {
                Object each = Array.get(bean, i);
                stack.add(each);
            }
            return;
        }

        boolean isEntity = isEntity(rootClass);

        if (!isEntity && bean instanceof Map) {
            stack.addAll(((Map) bean).values());
            return;
        }

        BeanMap beanHandler = null;
        Map<String, PropertyDescriptor> propertyDescriptor = BeanMap.findPropertyDescriptor(rootClass);
        for (PropertyDescriptor descriptor : propertyDescriptor.values()) {
            // 支持getter方法明确表示get返回的结果需要注入
            Method readMethod = descriptor.getReadMethod();
            if (isEntity && readMethod != null && readMethod.getDeclaredAnnotations().length > 0
                    && returnFieldAopCache.findDeclaredAnnotation(readMethod) != null) {
                stack.add(readMethod.invoke(bean));
                continue;
            }

            Field field = BeanMap.getField(descriptor);
            if (field == null) {
                continue;
            }
            Class<?> declaringClass = field.getDeclaringClass();
            if (declaringClass == Object.class) {
                continue;
            }

            if (declaringClass != rootClass && !isEntity(declaringClass)) {
                continue;
            }

            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers) || Modifier.isTransient(modifiers)) {
                continue;
            }

            //路由消费字段
            RouterFieldConsumer routerFieldConsumer = routerFieldConsumerCache.findDeclaredAnnotation(field);
            String routerField;
            if (routerFieldConsumer != null && (routerField = routerFieldConsumer.routerField()).length() > 0) {
                if (beanHandler == null) {
                    beanHandler = new BeanMap(bean);
                }
                if (!beanHandler.containsKey(routerField)) {
                    PlatformDependentUtil.logWarn(ReturnFieldDispatchAop.class, "RouterFieldConsumer not found field, class={},routerField={}, data={}", rootClass, routerField, bean);
                }
                Object routerFieldData = beanHandler.get(routerField);
                String routerFieldDataStr = routerFieldData == null ? null : routerFieldData.toString();
                if (Objects.equals(routerFieldDataStr, "null")) {
                    routerFieldDataStr = null;
                }
                FieldConsumer choseFieldConsumer = null;
                for (FieldConsumer fieldConsumer : routerFieldConsumer.value()) {
                    String type = fieldConsumer.type();
                    if (Objects.equals(routerFieldDataStr, type)) {
                        choseFieldConsumer = fieldConsumer;
                        break;
                    }
                }
                if (choseFieldConsumer == null) {
                    choseFieldConsumer = routerFieldConsumer.defaultElse();
                }
                if (choseFieldConsumer.value().length() > 0) {
                    groupCollectMap.addField(choseFieldConsumer.value(), beanHandler, field, choseFieldConsumer);
                }
            }

            //普通消费字段
            FieldConsumer fieldConsumer = fieldConsumerCache.findDeclaredAnnotation(field);
            if (fieldConsumer != null) {
                if (beanHandler == null) {
                    beanHandler = new BeanMap(bean);
                }
                groupCollectMap.addField(fieldConsumer.value(), beanHandler, field, fieldConsumer);
                continue;
            }

            //枚举消费字段
            EnumFieldConsumer enumFieldConsumer = enumFieldConsumerCache.findDeclaredAnnotation(field);
            if (enumFieldConsumer != null) {
                if (beanHandler == null) {
                    beanHandler = new BeanMap(bean);
                }
                groupCollectMap.addField(EnumFieldConsumer.NAME, beanHandler, field, enumFieldConsumer);
                continue;
            }

            //自定义消费字段
            for (Class<? extends Annotation> myAnnotationClass : annotations) {
                Annotation myAnnotation = field.getDeclaredAnnotation(myAnnotationClass);
                if (myAnnotation != null) {
                    if (beanHandler == null) {
                        beanHandler = new BeanMap(bean);
                    }
                    String name = getMyAnnotationConsumerName(myAnnotationClass);
                    groupCollectMap.addField(name, beanHandler, field, myAnnotation);
                }
            }


            Class<?> fieldType = field.getType();
            boolean isMultiple = isMultiple(fieldType);
            if (isMultiple) {
                try {
                    // 防止触发 getter方法, 忽略private, 强行取字段值
                    Object fieldData = getFieldValue(field, bean);
                    stack.add(fieldData);
                    continue;
                } catch (Exception e) {
                    sneakyThrows(e);
                }
            }

            boolean isFieldEntity = !isBasicType(fieldType) && isEntity(fieldType);
            if (isFieldEntity) {
                try {
                    // 防止触发 getter方法, 忽略private, 强行取字段值
                    Object fieldData = getFieldValue(field, bean);
                    if (fieldData == null) {
                        continue;
                    }
                    Class<?> fieldDataClass = fieldData.getClass();
                    if (Boolean.TRUE.equals(skipFieldClassPredicateCache.computeIfAbsent(fieldDataClass, type -> skipFieldClassPredicate.test(fieldDataClass)))) {
                        continue;
                    }
                    stack.add(fieldData);
                } catch (Exception e) {
                    sneakyThrows(e);
                }
            }
        }
    }

    private void startPendingSignalThreadIfNeed() {
        if (pendingSignalThreadCreateFlag.compareAndSet(false, true)) {
            for (int i = 0, len = getBatchAggregationPendingSignalThreadCount(); i < len; i++) {
                PendingSignalThread<JOIN_POINT> thread = new PendingSignalThread<>(this);
                thread.start();
                this.pendingSignalThreadList.add(thread);
            }
        }
    }

    /**
     * 防止触发 getter方法, 忽略private, 强行取字段值
     *
     * @param field
     * @param target
     * @return
     * @throws IllegalAccessException
     */
    private Object getFieldValue(Field field, Object target) throws IllegalAccessException {
//        field.setAccessible(true);
        return field.get(target);
    }

    public Function<String, BiConsumer<JOIN_POINT, List<CField>>> getConsumerFactory() {
        return consumerFactory;
    }

    public void setConsumerFactory(Function<String, BiConsumer<JOIN_POINT, List<CField>>> consumerFactory) {
        this.consumerFactory = consumerFactory;
    }

    public String getMyAnnotationConsumerName(Class<? extends Annotation> myAnnotationClass) {
        return myAnnotationClass.getSimpleName();
    }

    public Object getConfigurableEnvironment() {
        return configurableEnvironment;
    }

    public void setConfigurableEnvironment(Object configurableEnvironment) {
        this.configurableEnvironment = configurableEnvironment;
    }

    public Function<Runnable, Runnable> getTaskDecorate() {
        return taskDecorate;
    }

    public void setTaskDecorate(Function<Runnable, Runnable> taskDecorate) {
        this.taskDecorate = taskDecorate;
    }

    public Function<Runnable, Future> getTaskExecutor() {
        return taskExecutor;
    }

    public void setTaskExecutor(Function<Runnable, Future> taskExecutor) {
        this.taskExecutor = taskExecutor;
    }

    public void addBeanPackagePaths(String paths) {
        if (paths != null && !paths.isEmpty()) {
            getMyProjectPackagePaths().add(Arrays.asList(paths.split("[./]")));
        }
    }

    public BiPredicate<JOIN_POINT, Object> getEnabled() {
        return enabled;
    }

    public void setEnabled(BiPredicate<JOIN_POINT, Object> enabled) {
        this.enabled = enabled;
    }

    public Set<List<String>> getMyProjectPackagePaths() {
        return myProjectPackagePaths;
    }

    public Set<Class<? extends Annotation>> getAnnotations() {
        return annotations;
    }

    public Predicate<Class> getSkipFieldClassPredicate() {
        return skipFieldClassPredicate;
    }

    /**
     * 是否是spring对象
     *
     * @param skipFieldClassPredicate 跳过判断, true=跳过
     */
    public void setSkipFieldClassPredicate(Predicate<Class> skipFieldClassPredicate) {
        this.skipFieldClassPredicate = skipFieldClassPredicate;
    }

    public BatchAggregationEnum getBatchAggregation() {
        return batchAggregation;
    }

    public void setBatchAggregation(BatchAggregationEnum batchAggregation) {
        this.batchAggregation = batchAggregation;
    }

    public long getConcurrentThreadCount() {
        return concurrentThreadCounter.sum();
    }

    public long getCurrentSubmitRunnableCount() {
        return currentSubmitRunnableCounter.get();
    }

    public long getTotalSubmitRunnableCount() {
        return totalSubmitRunnableCounter.sum();
    }

    public int getMaxRunnableConcurrentCount() {
        return maxRunnableConcurrentCount;
    }

    public void setMaxRunnableConcurrentCount(int maxRunnableConcurrentCount) {
        this.maxRunnableConcurrentCount = maxRunnableConcurrentCount;
    }

    public int getBlockGetterTimeoutMilliseconds() {
        return blockGetterTimeoutMilliseconds;
    }

    public void setBlockGetterTimeoutMilliseconds(int blockGetterTimeoutMilliseconds) {
        this.blockGetterTimeoutMilliseconds = blockGetterTimeoutMilliseconds;
    }

    public int getBatchAggregationMaxSignalConcurrentCount() {
        return batchAggregationMaxSignalConcurrentCount;
    }

    public void setBatchAggregationMaxSignalConcurrentCount(int batchAggregationMaxSignalConcurrentCount) {
        this.batchAggregationMaxSignalConcurrentCount = batchAggregationMaxSignalConcurrentCount;
    }

    public long getBatchAggregationPollMilliseconds() {
        return batchAggregationPollMilliseconds;
    }

    public void setBatchAggregationPollMilliseconds(long batchAggregationPollMilliseconds) {
        this.batchAggregationPollMilliseconds = batchAggregationPollMilliseconds;
    }

    public int getBatchAggregationThresholdMinConcurrentCount() {
        return batchAggregationThresholdMinConcurrentCount;
    }

    public void setBatchAggregationThresholdMinConcurrentCount(int batchAggregationThresholdMinConcurrentCount) {
        this.batchAggregationThresholdMinConcurrentCount = batchAggregationThresholdMinConcurrentCount;
    }

    public int getBatchAggregationPollMinSize() {
        return batchAggregationPollMinSize;
    }

    public void setBatchAggregationPollMinSize(int batchAggregationPollMinSize) {
        this.batchAggregationPollMinSize = batchAggregationPollMinSize;
    }

    public int getBatchAggregationPollMaxSize() {
        return batchAggregationPollMaxSize;
    }

    public void setBatchAggregationPollMaxSize(int batchAggregationPollMaxSize) {
        this.batchAggregationPollMaxSize = batchAggregationPollMaxSize;
    }

    public boolean isBatchAggregationPendingNonBlock() {
        return batchAggregationPendingNonBlock;
    }

    public void setBatchAggregationPendingNonBlock(boolean batchAggregationPendingNonBlock) {
        this.batchAggregationPendingNonBlock = batchAggregationPendingNonBlock;
    }

    public boolean existPending() {
        return !pendingList.isEmpty();
    }

    public enum BatchAggregationEnum {
        disabled,
        auto,
        manual
    }

    public interface SelectMethodHolder {

    }

    /**
     * 字段拦截器 (可以处理字段注入, 加缓存等)
     *
     * @author hao
     */
    public interface FieldIntercept<JOIN_POINT> extends BiConsumer<JOIN_POINT, List<CField>> {
        @Override
        void accept(JOIN_POINT joinPoint, List<CField> fieldList);

        default void begin(String beanName, GroupCollect<JOIN_POINT> collect, List<CField> fieldList) {

        }

        default void end(String beanName, GroupCollect<JOIN_POINT> collect, List<CField> fieldList) {

        }
    }

    public static class AutowiredRunnable<JOIN_POINT> implements Runnable {
        private final ReturnFieldDispatchAop<JOIN_POINT> aop;
        private final JOIN_POINT joinPoint;
        private final Object result;
        private final int depth;
        private final List<CField> fieldList;
        private final String consumerName;
        private final BiConsumer<JOIN_POINT, List<CField>> consumer;
        private final Runnable autowiredAfter;

        private AutowiredRunnable(ReturnFieldDispatchAop<JOIN_POINT> aop, JOIN_POINT joinPoint, Object result,
                                  int depth, List<CField> fieldList,
                                  String consumerName, BiConsumer<JOIN_POINT, List<CField>> consumer, Runnable autowiredAfter) throws InterruptedException {
            this.aop = aop;
            this.joinPoint = joinPoint;
            this.result = result;
            this.depth = depth;
            this.fieldList = fieldList;
            this.consumerName = consumerName;
            this.consumer = consumer;
            this.autowiredAfter = autowiredAfter;

            // 获取 new AutowiredRunnable许可
            acquire(aop);
        }

        private void release(ReturnFieldDispatchAop<JOIN_POINT> aop) {
            aop.currentSubmitRunnableCounter.decrementAndGet();
            try {
                aop.autowiredRunnableLock.lock();
                aop.autowiredRunnableCondition.signalAll();
            } finally {
                aop.autowiredRunnableLock.unlock();
            }
        }

        private void acquire(ReturnFieldDispatchAop<JOIN_POINT> aop) throws InterruptedException {
            AtomicInteger current = aop.currentSubmitRunnableCounter;
            int max = aop.getMaxRunnableConcurrentCount();
            int rCnt;
            while (true) {
                if ((rCnt = current.get()) > max) {
                    try {
                        aop.autowiredRunnableLock.lock();
                        aop.autowiredRunnableCondition.await(100, TimeUnit.MILLISECONDS);
                    } finally {
                        aop.autowiredRunnableLock.unlock();
                    }
                } else if (current.compareAndSet(rCnt, rCnt + 1)) {
                    aop.totalSubmitRunnableCounter.increment();
                    break;
                }
            }
        }

        public ReturnFieldDispatchAop<JOIN_POINT> getAop() {
            return aop;
        }

        public Object getJoinPoint() {
            return joinPoint;
        }

        public Object getResult() {
            return result;
        }

        public int getDepth() {
            return depth;
        }

        public List<CField> getFieldList() {
            return fieldList;
        }

        public String getConsumerName() {
            return consumerName;
        }

        public BiConsumer<JOIN_POINT, List<CField>> getConsumer() {
            return consumer;
        }

        @Override
        public void run() {
            try {
                consumer.accept(joinPoint, fieldList);
            } catch (Exception e) {
                aop.sneakyThrows(e);
            } finally {
                release(aop);
                autowiredAfter.run();
            }
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder("AutowiredRunnable{");
            builder.append(consumerName).append('}');
            if (result != null) {
                builder.append(", result=");
                if (result instanceof FieldCompletableFuture) {
                    builder.append(((FieldCompletableFuture<?>) result).value().getClass());
                } else {
                    builder.append(result.getClass());
                }
            }
            if (joinPoint != null) {
                builder.append(", ").append(joinPoint);
            }
            return builder.toString();
        }
    }

    public static class MergeGroupCollect<JOIN_POINT> extends GroupCollect<JOIN_POINT> {
        private final Pending<JOIN_POINT>[] pendingList;
        private final List<PendingKey<JOIN_POINT>> pendingKeyList;

        private MergeGroupCollect(Pending<JOIN_POINT>[] pendingList, ReturnFieldDispatchAop<JOIN_POINT> aop) {
            super(null, Stream.of(pendingList).map(e -> e.groupCollectMap.result).toArray(), aop);
            this.pendingList = pendingList;
            this.pendingKeyList = null;

            for (Pending<JOIN_POINT> pending : pendingList) {
                for (Map.Entry<String, List<CField>> entry : pending.groupCollectMap.groupCollectMap.entrySet()) {
                    String beanName = entry.getKey();
                    groupCollectMap.computeIfAbsent(beanName, e -> newList(this, beanName))
                            .addAll(entry.getValue());
                    completableFutureList.addAll(pending.groupCollectMap.completableFutureList);
                    visitObjectIdSet.add(objectId(pending.groupCollectMap.result));
                    interceptCacheMap.putAll(pending.groupCollectMap.interceptCacheMap);
                }
                pending.groupCollectMap.destroy();
            }
        }

        private MergeGroupCollect(List<PendingKey<JOIN_POINT>> pendingKeyList, ReturnFieldDispatchAop<JOIN_POINT> aop) {
            super(null, pendingKeyList.stream().map(e -> e.pending).distinct().map(e -> e.groupCollectMap.result).toArray(), aop);
            this.pendingList = null;
            this.pendingKeyList = pendingKeyList;

            Set<Pending<JOIN_POINT>> pendingSet = new LinkedHashSet<>(Math.min(pendingKeyList.size(), 16));
            for (PendingKey<JOIN_POINT> pendingKey : pendingKeyList) {
                List<CField> cFields = pendingKey.pending.groupCollectMap.groupCollectMap.get(pendingKey.beanName);
                if (cFields != null) {
                    groupCollectMap.computeIfAbsent(pendingKey.beanName, e -> newList(this, pendingKey.beanName))
                            .addAll(cFields);
                }
                pendingSet.add(pendingKey.pending);
            }
            for (Pending<JOIN_POINT> pending : pendingSet) {
                completableFutureList.addAll(pending.groupCollectMap.completableFutureList);
                visitObjectIdSet.add(objectId(pending.groupCollectMap.result));
                interceptCacheMap.putAll(pending.groupCollectMap.interceptCacheMap);
                pending.groupCollectMap.destroy();
                pending.groupCollectMap.closeFlag.set(true);
            }
        }

        public List<PendingKey<JOIN_POINT>> getPendingKeyList() {
            return pendingKeyList;
        }

        public Pending<JOIN_POINT>[] getPendingList() {
            return pendingList;
        }
    }

    public static class GroupCollect<JOIN_POINT> {
        protected final Map<String, List<CField>> groupCollectMap = new LinkedHashMap<>(5);
        protected final List<FieldCompletableFuture<?>> completableFutureList = new LinkedList<>();
        protected final Set<Object> visitObjectIdSet = new HashSet<>();
        protected final Map<String, BiConsumer<JOIN_POINT, List<CField>>> interceptCacheMap = new HashMap<>(5);
        protected final ReturnFieldDispatchAop<JOIN_POINT> aop;

        protected final Map<String, ConcurrentHashMap<Object, Map<Object, Object>>> interceptLocalCacheMap = new ConcurrentHashMap<>(5);
        protected final Map<String, ConcurrentHashMap<Object, SnapshotCompletableFuture<Object>>> interceptAsyncMap = new ConcurrentHashMap<>(5);
        private final JOIN_POINT joinPoint;
        private final Object result;
        private final AtomicInteger partitionCounter = new AtomicInteger();
        private final AtomicBoolean closeFlag = new AtomicBoolean();
        private int depth = 1;
        private List<AutowiredRunnable<JOIN_POINT>> partition;

        public GroupCollect(JOIN_POINT joinPoint, Object result, ReturnFieldDispatchAop<JOIN_POINT> aop) {
            this.joinPoint = joinPoint;
            this.visitObjectIdSet.add(objectId(result));
            this.result = result;
            this.aop = aop;
        }

        static <JOIN_POINT> List<CField> newList(GroupCollect<JOIN_POINT> groupCollect, String beanName) {
            return new SplitCFieldList(groupCollect, beanName);
        }

        static Object objectId(Object object) {
            return object == null ? 0 : System.identityHashCode(object);
        }

        public ReturnFieldDispatchAop<JOIN_POINT> getAop() {
            return aop;
        }

        public int getDepth() {
            return depth;
        }

        public Object getResult() {
            return result;
        }

        public JOIN_POINT getJoinPoint() {
            return joinPoint;
        }

        public Map<String, BiConsumer<JOIN_POINT, List<CField>>> getInterceptMap() {
            return Collections.unmodifiableMap(interceptCacheMap);
        }

        public Map<String, List<CField>> getGroupCollectMap() {
            return Collections.unmodifiableMap(groupCollectMap);
        }

        public List<FieldCompletableFuture<?>> getCompletableFutureList() {
            return Collections.unmodifiableList(completableFutureList);
        }

        public Collection<String> getBeanNames() {
            return groupCollectMap.keySet();
        }

        public boolean isEmpty() {
            return groupCollectMap.isEmpty();
        }

        /**
         * 获取消费者
         * 单线程执行
         */
        protected BiConsumer<JOIN_POINT, List<CField>> getConsumer(String beanName) {
            BiConsumer<JOIN_POINT, List<CField>> consumer = interceptCacheMap.computeIfAbsent(beanName, e -> aop.consumerFactory.apply(e));
            if (consumer == null) {
                throw new IllegalArgumentException("ReturnFieldDispatchAop autowired consumer '" + beanName + "' not found!");
            }
            return consumer;
        }

        /**
         * 获取消费者的缓存
         * 多线程执行
         */
        public Map<Object, Object> getLocalCache(String beanName, Object cacheKey) {
            ConcurrentHashMap<Object, Map<Object, Object>> map = interceptLocalCacheMap.computeIfAbsent(beanName, e -> new ConcurrentHashMap<>(2));
            return map.computeIfAbsent(cacheKey, e -> new ConcurrentHashMap<>(2));
        }

        /**
         * 启动异步
         * 多线程执行
         */
        public <T> SnapshotCompletableFuture<T> startAsync(String beanName, Object cacheKey) {
            ConcurrentHashMap<Object, SnapshotCompletableFuture<Object>> map = interceptAsyncMap.computeIfAbsent(beanName, e -> new ConcurrentHashMap<>(2));
            SnapshotCompletableFuture<Object> r = map.computeIfAbsent(cacheKey, e -> SnapshotCompletableFuture.newInstance(aop.taskDecorate));//startAsync
            return (SnapshotCompletableFuture<T>) r;
        }

        /**
         * 获取异步
         * 多线程执行
         */
        public <T> SnapshotCompletableFuture<T> getAsync(String beanName, Object cacheKey) {
            ConcurrentHashMap<Object, SnapshotCompletableFuture<Object>> map = interceptAsyncMap.get(beanName);
            if (map == null) {
                return null;
            } else {
                return (SnapshotCompletableFuture<T>) map.get(cacheKey);
            }
        }

        public boolean isExistAsync() {
            if (interceptAsyncMap.isEmpty()) {
                return false;
            }
            for (ConcurrentHashMap<Object, SnapshotCompletableFuture<Object>> cache : interceptAsyncMap.values()) {
                for (CompletableFuture<Object> future : cache.values()) {
                    if (!future.isDone()) {
                        return true;
                    }
                }
            }
            return true;
        }

        public Collection<SnapshotCompletableFuture<Object>> getAsyncList() {
            List<SnapshotCompletableFuture<Object>> list = new ArrayList<>(interceptAsyncMap.size());
            for (ConcurrentHashMap<Object, SnapshotCompletableFuture<Object>> cache : interceptAsyncMap.values()) {
                for (SnapshotCompletableFuture<Object> future : cache.values()) {
                    if (!future.isDone()) {
                        list.add(future);
                    }
                }
            }
            return list;
        }

        /**
         * 添加收集到的字段
         * 单线程执行
         */
        private void addField(String consumerName, BeanMap beanHandler, Field field,
                              Annotation annotation) {
            CField cField = new CField(consumerName, beanHandler, field, annotation, aop.configurableEnvironment);
            groupCollectMap.computeIfAbsent(consumerName, e -> newList(this, consumerName))
                    .add(cField);
        }

        /**
         * 添加收集到的异步字段
         * 单线程执行
         */
        private void addFuture(FieldCompletableFuture<?> bean) {
            bean.snapshot(aop.taskDecorate);//addFuture
            completableFutureList.add(bean);
        }

        /**
         * 分配任务
         * 单线程执行
         */
        private List<AutowiredRunnable<JOIN_POINT>> partition() throws InterruptedException {
            if (partition == null) {
                //  通知实现
                List<AutowiredRunnable<JOIN_POINT>> partition = new ArrayList<>(groupCollectMap.size());
                for (Map.Entry<String, List<CField>> entry : groupCollectMap.entrySet()) {
                    String beanName = entry.getKey();
                    BiConsumer<JOIN_POINT, List<CField>> consumer = getConsumer(beanName);

                    partition.add(new AutowiredRunnable<>(aop, joinPoint, result, depth, entry.getValue(), beanName, consumer, () -> {
                        // 全部任务执行完成了
                        if (partitionCounter.decrementAndGet() == 0) {
                            this.partition = null;
                            resolvePlaceholders();
                        }
                    }));
                }
                partitionCounter.set(partition.size());
                this.partition = partition;
            }
            return partition;
        }

        public int getPartitionCount() {
            return partitionCounter.get();
        }

        /**
         * 解析填写的表达式
         * 单线程执行
         */
        private void resolvePlaceholders() {
            for (List<CField> cFieldList : groupCollectMap.values()) {
                for (CField cField : cFieldList) {
                    if (cField.isSetValue()) {
                        continue;
                    }
                    //解析占位符
                    String resolve = cField.resolvePlaceholders(cField.getBeanHandler());
                    if (resolve == null) {
                        continue;
                    }
                    cField.setValue(resolve);
                }
            }
        }

        public List<CField> getFieldList(String beanName) {
            return groupCollectMap.get(beanName);
        }

        /**
         * 收集后回掉
         * 单线程执行
         */
        private void collectAfter() {
            for (Map.Entry<String, BiConsumer<JOIN_POINT, List<CField>>> entry : interceptCacheMap.entrySet()) {
                Object intercept;
                String beanName = entry.getKey();
                List<CField> cFields = groupCollectMap.get(beanName);
                if (cFields != null && (intercept = entry.getValue()) instanceof FieldIntercept) {
                    try {
                        ((FieldIntercept<JOIN_POINT>) intercept).begin(beanName, this, cFields);
                    } catch (Exception e) {
                        aop.sneakyThrows(e);
                    }
                }
            }
        }

        /**
         * 检查注入后的是否需要继续注入
         * 单线程执行
         */
        private List<Object> next() {
            for (Map.Entry<String, BiConsumer<JOIN_POINT, List<CField>>> entry : interceptCacheMap.entrySet()) {
                Object intercept;
                String beanName = entry.getKey();
                List<CField> cFields = groupCollectMap.get(beanName);
                if (cFields != null && (intercept = entry.getValue()) instanceof FieldIntercept) {
                    try {
                        ((FieldIntercept<JOIN_POINT>) intercept).end(beanName, this, cFields);
                    } catch (Exception e) {
                        aop.sneakyThrows(e);
                    }
                }
            }

            // 检查注入后的是否需要继续注入
            List<Object> next = groupCollectMap.values().stream()
                    .flatMap(Collection::stream)
                    .map(CField::getValue)
                    // 去掉循环依赖的对象 (防止递归循环依赖, 比如用户表的创建者是自己)
                    .filter(e -> !visitObjectIdSet.contains(objectId(e)))
                    // 放入访问记录
                    .peek(e -> visitObjectIdSet.add(objectId(e)))
                    .collect(Collectors.toList());

            // 清空处理过的
            groupCollectMap.clear();
            depth++;
            return next;
        }

        /**
         * 销毁
         * 单线程执行
         */
        private void destroy() {
            // call gc
            groupCollectMap.clear();
            visitObjectIdSet.clear();
            completableFutureList.clear();
            interceptCacheMap.clear();
            interceptLocalCacheMap.clear();
        }

        private void close() {
            if (closeFlag.compareAndSet(false, true)) {
                for (FieldCompletableFuture<?> future : completableFutureList) {
                    future.complete();
                }
                destroy();
            }
        }
    }

    private static class AllMergePendingRunnable<JOIN_POINT> implements Runnable {
        private final Pending<JOIN_POINT>[] pendingList;
        private final ReturnFieldDispatchAop<JOIN_POINT> aop;
        private final MergeGroupCollect<JOIN_POINT> groupCollectMap;

        private AllMergePendingRunnable(Pending<JOIN_POINT>[] pendingList, ReturnFieldDispatchAop<JOIN_POINT> aop) {
            this.pendingList = pendingList;
            this.aop = aop;
            this.groupCollectMap = new MergeGroupCollect<>(pendingList, aop);
        }

        @Override
        public void run() {
            Pending<JOIN_POINT> pending0 = pendingList[0];
            pending0.threadSnapshot.replay(() -> {
                CompletableFuture<Void> future = aop.autowired(groupCollectMap, aop.taskExecutor, aop.taskDecorate);
                future.whenComplete(((unused, throwable) -> {
                    if (throwable != null) {
                        for (Pending<JOIN_POINT> pending : pendingList) {
                            pending.completeExceptionally(throwable);
                        }
                    } else {
                        for (Pending<JOIN_POINT> pending : pendingList) {
                            pending.complete();
                        }
                    }
                }));
            });
        }

        @Override
        public String toString() {
            return "AllMergePendingRunnable{" +
                    "size=" + pendingList.length +
                    '}';
        }
    }

    private static class PartMergePendingRunnable<JOIN_POINT> implements Runnable {
        private final Object groupKey;
        private final List<PendingKey<JOIN_POINT>> pendingKeyList;
        private final ReturnFieldDispatchAop<JOIN_POINT> aop;
        private final MergeGroupCollect<JOIN_POINT> groupCollectMap;

        private PartMergePendingRunnable(Object groupKey, List<PendingKey<JOIN_POINT>> pendingKeyList, ReturnFieldDispatchAop<JOIN_POINT> aop) {
            this.pendingKeyList = pendingKeyList;
            this.groupKey = groupKey;
            this.aop = aop;
            this.groupCollectMap = new MergeGroupCollect<>(pendingKeyList, aop);
        }

        @Override
        public void run() {
            PendingKey<JOIN_POINT> pendingKey0 = pendingKeyList.get(0);
            pendingKey0.pending.threadSnapshot.replay(() -> {
                CompletableFuture<Void> future = aop.autowired(groupCollectMap, aop.taskExecutor, aop.taskDecorate);
                future.whenComplete(((unused, throwable) -> {
                    if (throwable != null) {
                        for (PendingKey<JOIN_POINT> pendingKey : pendingKeyList) {
                            pendingKey.pending.completeExceptionally(throwable);
                        }
                    } else {
                        for (PendingKey<JOIN_POINT> pendingKey : pendingKeyList) {
                            pendingKey.pending.complete();
                        }
                    }
                }));
            });
        }

        @Override
        public String toString() {
            return "PartMergePendingRunnable{" +
                    "groupKey=" + groupKey +
                    ", size=" + pendingKeyList.size() +
                    '}';
        }
    }

    private static class PendingKey<JOIN_POINT> {
        private final Object groupKey;
        private final String beanName;
        private final Pending<JOIN_POINT> pending;
        private final BiConsumer<JOIN_POINT, List<CField>> consumer;
        private final ReturnFieldDispatchAop<JOIN_POINT> aop;

        private PendingKey(Object groupKey, String beanName, BiConsumer<JOIN_POINT, List<CField>> consumer, Pending<JOIN_POINT> pending, ReturnFieldDispatchAop<JOIN_POINT> aop) {
            this.groupKey = groupKey;
            this.beanName = beanName;
            this.consumer = consumer;
            this.pending = pending;
            this.aop = aop;
        }

        @Override
        public String toString() {
            return Objects.toString(groupKey);
        }
    }

    public static class Pending<JOIN_POINT> extends FieldCompletableFuture<Object> implements Runnable {
        private final GroupCollect<JOIN_POINT> groupCollectMap;
        private final transient ThreadSnapshot threadSnapshot;
        private final transient ThreadSnapshot snapshotGroupKeyRunnable;
        private boolean snapshotNotDoneStatus = false;

        public Pending(GroupCollect<JOIN_POINT> groupCollectMap, Function<Runnable, Runnable> taskDecorate) {
            super(groupCollectMap.result);
            this.groupCollectMap = groupCollectMap;
            this.threadSnapshot = new ThreadSnapshot(taskDecorate);
            this.snapshotGroupKeyRunnable = new ThreadSnapshot(taskDecorate);
            snapshot(taskDecorate);//Pending
        }

        public boolean isDoneAndSnapshot() {
            if (snapshotNotDoneStatus) {
                return false;
            }
            if (super.isDone()) {
                return true;
            } else {
                snapshotNotDoneStatus = true;
                return false;
            }
        }

        public GroupCollect<JOIN_POINT> getGroupCollect() {
            return groupCollectMap;
        }

        public ThreadSnapshot getSnapshotGroupKeyRunnable() {
            return snapshotGroupKeyRunnable;
        }

        public ThreadSnapshot getThreadSnapshot() {
            return threadSnapshot;
        }

        @Override
        public void run() {
            threadSnapshot.replay(() -> {
                CompletableFuture<Void> future = groupCollectMap.aop.autowired(groupCollectMap, groupCollectMap.aop.taskExecutor, groupCollectMap.aop.taskDecorate);
                future.whenComplete(this::complete);
            });
        }
    }

    public static class AnnotationCache<ANNOTATION extends Annotation> {
        private final Class<ANNOTATION> type;
        private final Collection<Class<? extends Annotation>> alias;
        private final int cacheSize;
        private final Map<Class<?>, Boolean> findCache = new ConcurrentHashMap<>(32);
        private final Map<AnnotatedElement, ANNOTATION> instanceCache;

        private AnnotationCache(Class<ANNOTATION> type, Collection<Class<? extends Annotation>> alias, int cacheSize) {
            this.type = type;
            this.alias = alias;
            this.cacheSize = cacheSize;
            this.instanceCache = Collections.synchronizedMap(new LinkedHashMap<AnnotatedElement, ANNOTATION>((int) ((cacheSize / 0.75F) + 1), 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<AnnotatedElement, ANNOTATION> eldest) {
                    return size() > AnnotationCache.this.cacheSize;
                }
            });
        }

        public ANNOTATION findDeclaredAnnotation(AnnotatedElement element) {
            if (element == null) {
                return null;
            }
            return instanceCache.computeIfAbsent(element, e -> AnnotationUtil.findExtendsAnnotation(element, alias, type, findCache));
        }
    }

    private static class PendingSignalThread<JOIN_POINT> extends Thread {
        private static final AtomicInteger id = new AtomicInteger();
        private final ReturnFieldDispatchAop<JOIN_POINT> aop;
        private final ArrayList<Pending<JOIN_POINT>> pollList;
        private final Lock lock = new ReentrantLock();
        private final Condition condition = lock.newCondition();
        // 当前信号数量
        private final LongAdder currentSignalCounter;
        // 总共信号数量
        private long totalSignalCounter = 0L;
        // 总共聚合的接口数量
        private long totalSignalPendingCounter = 0L;
        // 总共聚合的接口数量
        private long totalPollTimeMillis = 0L;
        private long totalSignalQueueCounter = 0L;

        private PendingSignalThread(ReturnFieldDispatchAop<JOIN_POINT> aop) {
            this.aop = aop;
            this.pollList = new ArrayList<>(aop.getBatchAggregationPollMaxSize());
            this.currentSignalCounter = aop.currentSignalCounter;
            setName("ReturnFieldDispatchAop-PendingSignal-" + id.getAndIncrement());
            setDaemon(true);
        }

        @Override
        public String toString() {
            //            {pollMilliseconds}毫秒内，有{pollMinSize}个就发车，一趟车最多{pollMaxSize}人，最多同时发{maxSignalConcurrentCount}辆车，等下次发车的排队人数为{pendingQueueCapacity}
            return "PendingSignalThread{" +
                    "currentSignalCounter=" + currentSignalCounter +
                    ", totalSignalCounter=" + totalSignalCounter +
                    ", totalSignalPendingCounter=" + totalSignalPendingCounter +
                    ", totalPollTimeMillis=" + totalPollTimeMillis +
                    ", totalSignalQueueCounter=" + totalSignalQueueCounter +
                    '}';
        }

        @Override
        public void run() {
            while (!isInterrupted()) {
                try {
                    int min = aop.getBatchAggregationPollMinSize();
                    int max = aop.getBatchAggregationPollMaxSize();
                    long timestamp = System.currentTimeMillis();
                    aop.pollPending(pollList, Math.min(min, max), Math.max(min, max), aop.getBatchAggregationPollMilliseconds());

                    totalSignalQueueCounter += aop.pendingList.size();

                    if (pollList.isEmpty()) {
                        Thread.sleep(1);
                    } else {
                        totalPollTimeMillis += System.currentTimeMillis() - timestamp;

                        currentSignalCounter.increment();
                        totalSignalCounter++;
                        Pending<JOIN_POINT>[] pendings = pollList.toArray(new Pending[pollList.size()]);
                        totalSignalPendingCounter += pendings.length;
                        pollList.clear();
                        try {
                            if (pendings.length == 1) {
                                pendings[0].run();
                            } else {
                                List<Runnable> runnableList = groupByMerge(pendings, aop);
                                for (Runnable runnable : runnableList) {
                                    runnable.run();
                                }
                            }
                        } finally {
                            CompletableFuture.allOf(pendings).whenComplete((unused, throwable) -> {
                                currentSignalCounter.decrement();
                                if (throwable != null) {
                                    PlatformDependentUtil.logWarn(ReturnFieldDispatchAop.class, "collectAndAutowired Throwable error = {}", throwable, throwable);
                                }
                                try {
                                    lock.lock();
                                    condition.signalAll();
                                } finally {
                                    lock.unlock();
                                }
                            });
                        }

                        while (currentSignalCounter.sum() > aop.getBatchAggregationMaxSignalConcurrentCount()) {
                            try {
                                lock.lock();
                                condition.await(10, TimeUnit.MILLISECONDS);
                            } finally {
                                lock.unlock();
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    return;
                } catch (Throwable e) {
                    PlatformDependentUtil.logWarn(ReturnFieldDispatchAop.class, "PendingSignal Throwable error = {}", e, e);
                }
            }
        }
    }

    private static class SimpleReturnFieldDispatchAop<JOIN_POINT> extends ReturnFieldDispatchAop<JOIN_POINT> {
        private SimpleReturnFieldDispatchAop() {
            super();
        }

        private SimpleReturnFieldDispatchAop(Map<String, ? extends BiConsumer<JOIN_POINT, List<CField>>> map) {
            super(map);
        }

        private SimpleReturnFieldDispatchAop(Function<String, BiConsumer<JOIN_POINT, List<CField>>> consumerProvider) {
            super(consumerProvider);
        }

        @Override
        protected void aopBefore() {
            throw new UnsupportedOperationException("aopBefore");
        }

        @Override
        protected void aopAfter() {
            throw new UnsupportedOperationException("aopAfter");
        }

        @Override
        protected void aopReturningAfter(JOIN_POINT joinPoint, Object result) {
            throw new UnsupportedOperationException("aopReturningAfter");
        }
    }

    public static class SplitCFieldList extends ArrayList<CField> {
        private transient final AtomicBoolean parseFlag = new AtomicBoolean();
        private transient final GroupCollect<?> groupCollect;
        private transient final String beanName;
        private transient List<CField> keyNameFieldList;
        private transient List<CField> keyValueFieldList;

        private SplitCFieldList(List<CField> fieldList) {
            super(fieldList);
            if (fieldList instanceof SplitCFieldList) {
                this.groupCollect = ((SplitCFieldList) fieldList).groupCollect;
                this.beanName = ((SplitCFieldList) fieldList).beanName;
            } else {
                this.groupCollect = null;
                this.beanName = null;
            }
        }

        public SplitCFieldList() {
            this.groupCollect = null;
            this.beanName = null;
        }

        public SplitCFieldList(GroupCollect<?> groupCollect, String beanName) {
            this.groupCollect = groupCollect;
            this.beanName = beanName;
        }

        public SplitCFieldList(SplitCFieldList parent, int size) {
            super(size);
            this.groupCollect = parent.groupCollect;
            this.beanName = parent.beanName;
        }

        private static boolean isString(CField field) {
            return field.getType() == String.class || field.getGenericType() == String.class;
        }

        @Override
        public void clear() {
            parseFlag.set(false);
            super.clear();
        }

        @Override
        public boolean remove(Object o) {
            parseFlag.set(false);
            return super.remove(o);
        }

        @Override
        public boolean removeIf(Predicate<? super CField> filter) {
            parseFlag.set(false);
            return super.removeIf(filter);
        }

        @Override
        public CField remove(int index) {
            parseFlag.set(false);
            return super.remove(index);
        }

        @Override
        public boolean add(CField cField) {
            parseFlag.set(false);
            return super.add(cField);
        }

        @Override
        public boolean addAll(Collection<? extends CField> c) {
            parseFlag.set(false);
            return super.addAll(c);
        }

        @Override
        public boolean addAll(int index, Collection<? extends CField> c) {
            parseFlag.set(false);
            return super.addAll(index, c);
        }

        @Override
        public void add(int index, CField element) {
            parseFlag.set(false);
            super.add(index, element);
        }

        private void parse() {
            if (parseFlag.compareAndSet(false, true)) {
                List<CField> keyNameFields = null;
                List<CField> keyValueFields = null;
                for (CField e : this) {
                    if (e.existPlaceholder() || !isString(e)) {
                        if (keyValueFields == null) {
                            keyValueFields = new SplitCFieldList(this, Math.min(size(), 16));
                        }
                        keyValueFields.add(e);
                    } else {
                        if (keyNameFields == null) {
                            keyNameFields = new SplitCFieldList(this, Math.min(size(), 16));
                        }
                        keyNameFields.add(e);
                    }
                }
                this.keyNameFieldList = keyNameFields;
                this.keyValueFieldList = keyValueFields;
            }
        }

        public List<CField> getKeyNameFieldList() {
            parse();
            return keyNameFieldList;
        }

        public List<CField> getKeyValueFieldList() {
            parse();
            return keyValueFieldList;
        }
    }

}
