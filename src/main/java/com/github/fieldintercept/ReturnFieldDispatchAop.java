package com.github.fieldintercept;

import com.github.fieldintercept.annotation.EnumFieldConsumer;
import com.github.fieldintercept.annotation.FieldConsumer;
import com.github.fieldintercept.annotation.ReturnFieldAop;
import com.github.fieldintercept.annotation.RouterFieldConsumer;
import com.github.fieldintercept.util.*;

import java.beans.PropertyDescriptor;
import java.io.UnsupportedEncodingException;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.net.URLDecoder;
import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
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
    private static final Collection<Class<? extends Annotation>> SPRING_INDEXED_ANNOTATION;

    static {
        Class springIndexedAnnotation;
        try {
            springIndexedAnnotation = Class.forName("org.springframework.stereotype.Indexed");
        } catch (ClassNotFoundException e) {
            springIndexedAnnotation = null;
        }
        SPRING_INDEXED_ANNOTATION = springIndexedAnnotation != null ? Collections.singletonList(springIndexedAnnotation) : null;
    }

    private static final Map<Class<?>, Boolean> SKIP_FIELD_CLASS_CACHE_MAP = Collections.synchronizedMap(new LinkedHashMap<Class<?>, Boolean>((int) ((6 / 0.75F) + 1), 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Class<?>, Boolean> eldest) {
            return size() > 6;
        }
    });
    public static final Predicate<Class> DEFAULT_SKIP_FIELD_CLASS_PREDICATE = type -> SPRING_INDEXED_ANNOTATION != null && AnnotationUtil.findDeclaredAnnotation(type, SPRING_INDEXED_ANNOTATION, SKIP_FIELD_CLASS_CACHE_MAP) != null;

    /**
     * 实体类包名一样, 就认为是业务实体类
     */
    private final Set<List<String>> myProjectPackagePaths = new LinkedHashSet<>();
    /**
     * 动态注解 或 用户自定义注解
     */
    private final Set<Class<? extends Annotation>> annotations = new LinkedHashSet<>();
    private final List<Pending<JOIN_POINT>> pendingList = new ArrayList<>(100);
    private final AtomicReference<Thread> pendingSignalThreadRef = new AtomicReference<>();
    private final LongAdder concurrentThreadCounter = new LongAdder();
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
    protected final AnnotationCache<ReturnFieldAop> returnFieldAopCache = new AnnotationCache<>(ReturnFieldAop.class, Arrays.asList(ReturnFieldAop.class, ReturnFieldAop.Extends.class), 100);
    protected final AnnotationCache<RouterFieldConsumer> routerFieldConsumerCache = new AnnotationCache<>(RouterFieldConsumer.class, Arrays.asList(RouterFieldConsumer.class, RouterFieldConsumer.Extends.class), 100);
    protected final AnnotationCache<FieldConsumer> fieldConsumerCache = new AnnotationCache<>(FieldConsumer.class, Arrays.asList(FieldConsumer.class, FieldConsumer.Extends.class), 100);
    protected final AnnotationCache<EnumFieldConsumer> enumFieldConsumerCache = new AnnotationCache<>(EnumFieldConsumer.class, Arrays.asList(EnumFieldConsumer.class, EnumFieldConsumer.Extends.class), 100);

    private Function<String, BiConsumer<JOIN_POINT, List<CField>>> consumerFactory;
    private Function<Runnable, Future> taskExecutor;
    private Function<Runnable, Runnable> taskDecorate;
    private Object configurableEnvironment;
    private Predicate<Class> skipFieldClassPredicate = DEFAULT_SKIP_FIELD_CLASS_PREDICATE;
    private final Map<Class<?>, Boolean> skipFieldClassPredicateCache = Collections.synchronizedMap(new LinkedHashMap<Class<?>, Boolean>(201, 1F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return size() > 200;
        }
    });
    private long batchAggregationMilliseconds = 10;
    private BatchAggregationEnum batchAggregation;
    private int batchAggregationMinConcurrentCount = 1;
    private BiPredicate<JOIN_POINT, Object> enabled = null;

    public ReturnFieldDispatchAop() {
    }

    public static <JOIN_POINT> ReturnFieldDispatchAop<JOIN_POINT> newInstance(Function<String, BiConsumer<JOIN_POINT, List<CField>>> consumerProvider) {
        return new SimpleReturnFieldDispatchAop<>(consumerProvider);
    }

    public static <JOIN_POINT> ReturnFieldDispatchAop<JOIN_POINT> newInstance(Map<String, ? extends BiConsumer<JOIN_POINT, List<CField>>> map) {
        return new SimpleReturnFieldDispatchAop<>(map);
    }

    public ReturnFieldDispatchAop(Map<String, ? extends BiConsumer<JOIN_POINT, List<CField>>> map) {
        this.consumerFactory = map::get;
    }

    public ReturnFieldDispatchAop(Function<String, BiConsumer<JOIN_POINT, List<CField>>> consumerProvider) {
        this.consumerFactory = consumerProvider;
    }

    public void setConsumerFactory(Function<String, BiConsumer<JOIN_POINT, List<CField>>> consumerFactory) {
        this.consumerFactory = consumerFactory;
    }

    public Function<String, BiConsumer<JOIN_POINT, List<CField>>> getConsumerFactory() {
        return consumerFactory;
    }

    public String getMyAnnotationConsumerName(Class<? extends Annotation> myAnnotationClass) {
        return myAnnotationClass.getSimpleName();
    }

    public void setConfigurableEnvironment(Object configurableEnvironment) {
        this.configurableEnvironment = configurableEnvironment;
    }

    public Object getConfigurableEnvironment() {
        return configurableEnvironment;
    }

    public void setTaskDecorate(Function<Runnable, Runnable> taskDecorate) {
        this.taskDecorate = taskDecorate;
    }

    public Function<Runnable, Runnable> getTaskDecorate() {
        return taskDecorate;
    }

    public void setTaskExecutor(Function<Runnable, Future> taskExecutor) {
        this.taskExecutor = taskExecutor;
    }

    public Function<Runnable, Future> getTaskExecutor() {
        return taskExecutor;
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

    //    @Before(value = "@annotation(com.github.fieldintercept.annotation.ReturnFieldAop)")
    protected abstract void aopBefore();
//    {
//        before();
//    }

    //    @After(value = "@annotation(com.github.fieldintercept.annotation.ReturnFieldAop)")
    protected abstract void aopAfter();
//    {
//        after();
//    }

    //    @AfterReturning(value = "@annotation(com.github.fieldintercept.annotation.ReturnFieldAop)",
//            returning = "result")
    protected abstract void aopReturningAfter(JOIN_POINT joinPoint, Object result) throws InvocationTargetException, IllegalAccessException, ExecutionException, InterruptedException;
//    {
//        returningAfter(joinPoint, result);
//    }

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
            groupCollectMap.collectAfter();

            if (isNeedPending(joinPoint, result)) {
                checkStaticMethodAccessor(groupCollectMap.getBeanNames());

                Pending<JOIN_POINT> pending = addPendingList(groupCollectMap);
                if (!(result instanceof FieldCompletableFuture)) {
                    pending.get();
                }
            } else {
                // 在用户线程上，先拆分好任务
                groupCollectMap.partition();
                if (result instanceof FieldCompletableFuture) {
                    submit(() -> autowired(groupCollectMap, taskExecutor, taskDecorate), taskExecutor, taskDecorate)
                            .whenComplete(((FieldCompletableFuture<?>) result).thenComplete());
                } else {
                    autowired(groupCollectMap, taskExecutor, taskDecorate);
                }
            }
        } catch (Exception e) {
            groupCollectMap.close();
            sneakyThrows(e);
        }
    }

    private void autowired(GroupCollect<JOIN_POINT> groupCollectMap, Function<Runnable, Future> taskExecutor, Function<Runnable, Runnable> taskDecorate) {
        try {
            Object next;
            do {
                // 拆分任务
                List<AutowiredRunnable<JOIN_POINT>> runnableList = groupCollectMap.partition();
                // 提交阻塞住任务
                await(runnableList, taskExecutor, taskDecorate);

                // 检查注入后的是否需要继续注入
                next = groupCollectMap.next();
                //收集返回值中的所有实体类
                collectBean(next, groupCollectMap);
            } while (!groupCollectMap.isEmpty());
        } catch (Throwable e) {
            sneakyThrows(e);
        } finally {
            groupCollectMap.close();
        }
    }

    public void await(List<? extends Runnable> runnableList) {
        await(runnableList, taskExecutor, taskDecorate);
    }

    public CompletableFuture<Void> submit(List<? extends Runnable> runnableList) {
        return submit(runnableList, taskExecutor, taskDecorate);
    }

    private static void await(List<? extends Runnable> runnableList, Function<Runnable, Future> taskExecutor, Function<Runnable, Runnable> taskDecorate) {
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
            } catch (InterruptedException | ExecutionException e) {
                PlatformDependentUtil.sneakyThrows(e);
            }
        }
    }

    private static CompletableFuture<Void> submit(List<? extends Runnable> runnableList, Function<Runnable, Future> taskExecutor, Function<Runnable, Runnable> taskDecorate) {
        if (runnableList == null || runnableList.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        } else {
            int size = runnableList.size();
            CompletableFuture<Void>[] futures = new CompletableFuture[size];
            for (int i = 0; i < size; i++) {
                futures[i] = submit(runnableList.get(i), taskExecutor, taskDecorate);
            }
            return CompletableFuture.allOf(futures);
        }
    }

    public static CompletableFuture<Void> submit(Runnable runnable, Function<Runnable, Future> taskExecutor, Function<Runnable, Runnable> taskDecorate) {
        if (taskDecorate != null) {
            runnable = taskDecorate.apply(runnable);
        }
        if (taskExecutor != null) {
            return CompletableFuture.runAsync(runnable, taskExecutor::apply);
        } else {
            runnable.run();
            return CompletableFuture.completedFuture(null);
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

        public AutowiredRunnable(ReturnFieldDispatchAop<JOIN_POINT> aop, JOIN_POINT joinPoint, Object result,
                                 int depth, List<CField> fieldList,
                                 String consumerName, BiConsumer<JOIN_POINT, List<CField>> consumer, Runnable autowiredAfter) {
            this.aop = aop;
            this.joinPoint = joinPoint;
            this.result = result;
            this.depth = depth;
            this.fieldList = fieldList;
            this.consumerName = consumerName;
            this.consumer = consumer;
            this.autowiredAfter = autowiredAfter;
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
            FieldIntercept<JOIN_POINT> fieldIntercept = consumer instanceof FieldIntercept ? (FieldIntercept<JOIN_POINT>) consumer : null;
            try {
                if (fieldIntercept != null) {
                    fieldIntercept.nextBegin(depth, joinPoint, fieldList, result);
                }
                PlatformDependentUtil.logTrace(AutowiredRunnable.class, "start Consumer ={}, value={}", consumer, fieldList);
                consumer.accept(joinPoint, fieldList);
                PlatformDependentUtil.logTrace(AutowiredRunnable.class, "end Consumer ={}", consumer);
            } catch (Exception e) {
                PlatformDependentUtil.logTrace(AutowiredRunnable.class, "error Consumer ={},message={}", consumer, e.getMessage(), e);
                aop.sneakyThrows(e);
            } finally {
                if (fieldIntercept != null) {
                    fieldIntercept.nextEnd(depth, joinPoint, fieldList, result);
                }
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

    protected boolean isMultiple(Class type) {
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

    protected boolean isBasicType(Class type) {
        return typeBasicCacheMap.computeIfAbsent(type, e -> e.isPrimitive()
                || e == String.class
                || Type.class.isAssignableFrom(e)
                || Number.class.isAssignableFrom(e)
                || Date.class.isAssignableFrom(e)
                || Boolean.class == e
                || TemporalAccessor.class.isAssignableFrom(e)
                || e.isEnum());
    }

    protected boolean isEntity(Class type) {
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
     * @param bean            数据
     * @param groupCollectMap 分组收集器
     */
    protected void collectBean(Object bean, GroupCollect<JOIN_POINT> groupCollectMap) throws InvocationTargetException, IllegalAccessException {
        if (bean == null || bean instanceof Class) {
            return;
        }
        if (bean instanceof FieldCompletableFuture) {
            groupCollectMap.addFuture((FieldCompletableFuture) bean);
            collectBean(((FieldCompletableFuture<?>) bean).value(), groupCollectMap);
            return;
        }
        Class<?> rootClass = bean.getClass();
        if (isBasicType(rootClass)) {
            return;
        }

        if (bean instanceof Iterable) {
            for (Object each : (Iterable) bean) {
                collectBean(each, groupCollectMap);
            }
            return;
        }

        if (rootClass.isArray()) {
            for (int i = 0, length = Array.getLength(bean); i < length; i++) {
                Object each = Array.get(bean, i);
                collectBean(each, groupCollectMap);
            }
            return;
        }

        boolean isEntity = isEntity(rootClass);

        if (!isEntity && bean instanceof Map) {
            for (Object each : ((Map) bean).values()) {
                collectBean(each, groupCollectMap);
            }
            return;
        }

        BeanMap beanHandler = null;
        Map<String, PropertyDescriptor> propertyDescriptor = BeanMap.findPropertyDescriptor(rootClass);
        for (PropertyDescriptor descriptor : propertyDescriptor.values()) {
            // 支持getter方法明确表示get返回的结果需要注入
            Method readMethod = descriptor.getReadMethod();
            if (isEntity && readMethod != null && readMethod.getDeclaredAnnotations().length > 0
                    && findDeclaredAnnotation(readMethod, returnFieldAopCache) != null) {
                collectBean(readMethod.invoke(bean), groupCollectMap);
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
            RouterFieldConsumer routerFieldConsumer = findDeclaredAnnotation(field, routerFieldConsumerCache);
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
            FieldConsumer fieldConsumer = findDeclaredAnnotation(field, fieldConsumerCache);
            if (fieldConsumer != null) {
                if (beanHandler == null) {
                    beanHandler = new BeanMap(bean);
                }
                groupCollectMap.addField(fieldConsumer.value(), beanHandler, field, fieldConsumer);
                continue;
            }

            //枚举消费字段
            EnumFieldConsumer enumFieldConsumer = findDeclaredAnnotation(field, enumFieldConsumerCache);
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
                    collectBean(fieldData, groupCollectMap);
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
                    collectBean(fieldData, groupCollectMap);
                } catch (Exception e) {
                    sneakyThrows(e);
                }
            }
        }
    }


    public static class GroupCollectMerge<JOIN_POINT> extends GroupCollect<JOIN_POINT> {
        private final Pending<JOIN_POINT>[] pendingList;
        private final List<PendingKey<JOIN_POINT>> pendingKeyList;

        public GroupCollectMerge(Pending<JOIN_POINT>[] pendingList, ReturnFieldDispatchAop<JOIN_POINT> aop) {
            super(null, Stream.of(pendingList).map(e -> e.groupCollectMap.result).toArray(), aop);
            this.pendingList = pendingList;
            this.pendingKeyList = null;

            for (Pending<JOIN_POINT> pending : pendingList) {
                for (Map.Entry<String, List<CField>> entry : pending.groupCollectMap.groupCollectMap.entrySet()) {
                    groupCollectMap.computeIfAbsent(entry.getKey(), e -> newList(this))
                            .addAll(entry.getValue());
                    completableFutureList.addAll(pending.groupCollectMap.completableFutureList);
                    visitObjectIdSet.add(objectId(pending.groupCollectMap.result));
                    interceptCacheMap.putAll(pending.groupCollectMap.interceptCacheMap);
                }
                pending.groupCollectMap.destroy();
            }
        }

        public GroupCollectMerge(List<PendingKey<JOIN_POINT>> pendingKeyList, ReturnFieldDispatchAop<JOIN_POINT> aop) {
            super(null, pendingKeyList.stream().map(e -> e.pending).distinct().map(e -> e.groupCollectMap.result).toArray(), aop);
            this.pendingList = null;
            this.pendingKeyList = pendingKeyList;

            Set<Pending<JOIN_POINT>> pendingSet = new LinkedHashSet<>(Math.min(pendingKeyList.size(), 16));
            for (PendingKey<JOIN_POINT> pendingKey : pendingKeyList) {
                List<CField> cFields = pendingKey.pending.groupCollectMap.groupCollectMap.get(pendingKey.beanName);
                if (cFields != null) {
                    groupCollectMap.computeIfAbsent(pendingKey.beanName, e -> newList(this))
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

        protected final Map<Object, Map<Object, Object>> interceptQueryCacheMap = new HashMap<>(5);
        private final List<CField> allFieldList = newList(this);
        private final JOIN_POINT joinPoint;
        private final Object result;
        private int depth = 1;
        private List<AutowiredRunnable<JOIN_POINT>> partition;
        private final AtomicInteger partitionCounter = new AtomicInteger();
        private final AtomicBoolean closeFlag = new AtomicBoolean();

        public GroupCollect(JOIN_POINT joinPoint, Object result, ReturnFieldDispatchAop<JOIN_POINT> aop) {
            this.joinPoint = joinPoint;
            this.visitObjectIdSet.add(objectId(result));
            this.result = result;
            this.aop = aop;
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

        private void collectAfter() {
            for (Map.Entry<String, List<CField>> entry : groupCollectMap.entrySet()) {
                BiConsumer<JOIN_POINT, List<CField>> consumer = getConsumer(entry.getKey());
                if (consumer instanceof FieldIntercept) {
                    ((FieldIntercept<JOIN_POINT>) consumer).begin(joinPoint, entry.getValue(), result);
                }
            }
        }

        static <JOIN_POINT> List<CField> newList(GroupCollect<JOIN_POINT> groupCollect) {
            return new SplitCFieldList(groupCollect);
        }

        /**
         * 添加收集到的字段
         */
        private void addField(String consumerName, BeanMap beanHandler, Field field,
                              Annotation annotation) {
            CField cField = new CField(consumerName, beanHandler, field, annotation, aop.configurableEnvironment);
            groupCollectMap.computeIfAbsent(consumerName, e -> newList(this))
                    .add(cField);
        }

        private void addFuture(FieldCompletableFuture<?> bean) {
            completableFutureList.add(bean);
        }

        public boolean isEmpty() {
            return groupCollectMap.isEmpty();
        }

        private List<AutowiredRunnable<JOIN_POINT>> partition() {
            if (partition == null) {
                //  通知实现
                List<AutowiredRunnable<JOIN_POINT>> partition = new LinkedList<>();
                for (Map.Entry<String, List<CField>> entry : groupCollectMap.entrySet()) {
                    String beanName = entry.getKey();
                    if (beanName == null || beanName.isEmpty()) {
                        continue;
                    }
                    List<CField> fieldList = entry.getValue();
                    allFieldList.addAll(fieldList);
                    partition.add(newRunnable(beanName, fieldList));
                }
                partitionCounter.set(partition.size());
                this.partition = partition;
            }
            return partition;
        }

        protected BiConsumer<JOIN_POINT, List<CField>> getConsumer(String beanName) {
            BiConsumer<JOIN_POINT, List<CField>> consumer = interceptCacheMap.computeIfAbsent(beanName, e -> aop.consumerFactory.apply(e));
            if (consumer == null) {
                throw new IllegalArgumentException("ReturnFieldDispatchAop autowired consumer '" + beanName + "' not found!");
            }
            return consumer;
        }

        protected Map<Object, Object> getConsumerQueryCache(Object cacheKey) {
            return interceptQueryCacheMap.computeIfAbsent(cacheKey, e -> new HashMap<>(6));
        }

        private AutowiredRunnable<JOIN_POINT> newRunnable(String beanName, List<CField> fieldList) {
            BiConsumer<JOIN_POINT, List<CField>> consumer = getConsumer(beanName);
            return new AutowiredRunnable<>(aop, joinPoint, result, depth, fieldList, beanName, consumer, () -> {
                if (partitionCounter.decrementAndGet() == 0) {
                    this.partition = null;
                    resolvePlaceholders();
                }
            });
        }

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

        static Object objectId(Object object) {
            return object == null ? 0 : System.identityHashCode(object);
        }

        private List<Object> next() {
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

        private void destroy() {
            // call gc
            groupCollectMap.clear();
            visitObjectIdSet.clear();
            completableFutureList.clear();
            interceptCacheMap.clear();
            interceptQueryCacheMap.clear();
            allFieldList.clear();
        }

        private void close() {
            if (closeFlag.compareAndSet(false, true)) {
                for (FieldCompletableFuture<?> future : completableFutureList) {
                    future.complete();
                }
                for (BiConsumer<JOIN_POINT, List<CField>> intercept : interceptCacheMap.values()) {
                    if (intercept instanceof FieldIntercept) {
                        try {
                            ((FieldIntercept<JOIN_POINT>) intercept).end(joinPoint, allFieldList, result);
                        } catch (Exception e) {
                            aop.sneakyThrows(e);
                        }
                    }
                }
                destroy();
            }
        }
    }

    public static void checkStaticMethodAccessor(Collection<String> beanNames) throws NoSuchMethodException, IllegalArgumentException {
        for (String beanName : beanNames) {
            getStaticMethodAccessor(beanName);
        }
    }

    /**
     * 是否是spring对象
     *
     * @param skipFieldClassPredicate 跳过判断, true=跳过
     */
    public void setSkipFieldClassPredicate(Predicate<Class> skipFieldClassPredicate) {
        this.skipFieldClassPredicate = skipFieldClassPredicate;
    }

    public Predicate<Class> getSkipFieldClassPredicate() {
        return skipFieldClassPredicate;
    }

    /**
     * 防止触发 getter方法, 忽略private, 强行取字段值
     *
     * @param field
     * @param target
     * @return
     * @throws IllegalAccessException
     */
    protected Object getFieldValue(Field field, Object target) throws IllegalAccessException {
//        field.setAccessible(true);
        return field.get(target);
    }

    public void setBatchAggregation(BatchAggregationEnum batchAggregation) {
        this.batchAggregation = batchAggregation;
    }

    public BatchAggregationEnum getBatchAggregation() {
        return batchAggregation;
    }

    protected boolean isNeedPending(JOIN_POINT joinPoint, Object returnResult) {
        if (batchAggregation == BatchAggregationEnum.disabled) {
            return false;
        }
        long concurrentThreadCount = concurrentThreadCounter.sum();
        if (concurrentThreadCount <= batchAggregationMinConcurrentCount) {
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
        return null;
    }

    public void startPendingSignalThreadIfNeed() {
        if (pendingSignalThreadRef.get() != null) {
            return;
        }
        Thread thread;
        if (pendingSignalThreadRef.compareAndSet(null, thread = new PendingSignalThread(this))) {
            thread.start();
        }
    }

    public void setBatchAggregationMilliseconds(long batchAggregationMilliseconds) {
        this.batchAggregationMilliseconds = batchAggregationMilliseconds;
    }

    public long getBatchAggregationMilliseconds() {
        return batchAggregationMilliseconds;
    }

    public void setBatchAggregationMinConcurrentCount(int batchAggregationMinConcurrentCount) {
        this.batchAggregationMinConcurrentCount = batchAggregationMinConcurrentCount;
    }

    public int getBatchAggregationMinConcurrentCount() {
        return batchAggregationMinConcurrentCount;
    }

    public static class ThreadSnapshotRunnable implements Runnable {
        private Runnable task;
        private final Runnable snapshot;

        public ThreadSnapshotRunnable(Function<Runnable, Runnable> taskDecorate) {
            this.snapshot = taskDecorate != null ? taskDecorate.apply(this) : null;
        }

        @Override
        public void run() {
            task.run();
        }

        public void replay(Runnable task) {
            if (snapshot != null) {
                this.task = task;
                snapshot.run();
            } else {
                task.run();
            }
        }
    }

    protected Pending<JOIN_POINT> addPendingList(GroupCollect<JOIN_POINT> groupCollectMap) {
        startPendingSignalThreadIfNeed();
        Pending<JOIN_POINT> pending = new Pending<>(groupCollectMap, new ThreadSnapshotRunnable(taskDecorate), new ThreadSnapshotRunnable(taskDecorate));
        synchronized (pendingList) {
            pendingList.add(pending);
        }
        return pending;
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

    public static class PendingGroup<JOIN_POINT> implements Runnable {
        private final List<PendingKey<JOIN_POINT>> pendingKeyList;
        private final Pending<JOIN_POINT>[] pendingList;
        private final ReturnFieldDispatchAop<JOIN_POINT> aop;
        private final Object groupKey;

        public PendingGroup(Pending<JOIN_POINT>[] pendingList, ReturnFieldDispatchAop<JOIN_POINT> aop) {
            this.pendingList = pendingList;
            this.pendingKeyList = null;
            this.groupKey = null;
            this.aop = aop;
        }

        public PendingGroup(Object groupKey, List<PendingKey<JOIN_POINT>> pendingKeyList, ReturnFieldDispatchAop<JOIN_POINT> aop) {
            this.pendingList = null;
            this.pendingKeyList = pendingKeyList;
            this.groupKey = groupKey;
            this.aop = aop;
        }

        @Override
        public String toString() {
            return "PendingGroup{" +
                    "groupKey=" + groupKey +
                    ", size=" + (pendingKeyList != null ? pendingKeyList.size() : pendingList != null ? pendingList.length : 0) +
                    '}';
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

        public static <JOIN_POINT> Object[] getParameterValues(Class<?>[] parameterTypes, String[] parameterNames,
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

        protected static <JOIN_POINT> List<PendingGroup<JOIN_POINT>> merge(Pending<JOIN_POINT>[] pendings, ReturnFieldDispatchAop<JOIN_POINT> aop) {
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
            if (size == 1) {
                return Collections.singletonList(new PendingGroup<>(pendings, aop));
            } else {
                List<PendingGroup<JOIN_POINT>> list = new ArrayList<>(size);
                for (Map.Entry<Object, List<PendingKey<JOIN_POINT>>> entry : groupByMap.entrySet()) {
                    list.add(new PendingGroup<>(entry.getKey(), entry.getValue(), aop));
                }
                return list;
            }
        }

        @Override
        public void run() {
            if (pendingList != null) {
                Pending<JOIN_POINT> pending0 = pendingList[0];
                pending0.snapshotRunnable.replay(() -> {
                    try {
                        aop.autowired(new GroupCollectMerge<>(pendingList, aop), aop.taskExecutor, aop.taskDecorate);
                    } catch (Exception e) {
                        aop.sneakyThrows(e);
                    } finally {
                        for (Pending<JOIN_POINT> pending : pendingList) {
                            pending.complete();
                        }
                    }
                });
            } else if (pendingKeyList != null) {
                PendingKey<JOIN_POINT> pendingKey0 = pendingKeyList.get(0);
                int size = pendingKeyList.size();
                if (size == 1) {
                    pendingKey0.pending.run();
                } else {
                    pendingKey0.pending.snapshotRunnable.replay(() -> {
                        try {
                            aop.autowired(new GroupCollectMerge<>(pendingKeyList, aop), aop.taskExecutor, aop.taskDecorate);
                        } catch (Exception e) {
                            aop.sneakyThrows(e);
                        } finally {
                            for (PendingKey<JOIN_POINT> pendingKey : pendingKeyList) {
                                pendingKey.pending.complete();
                            }
                        }
                    });
                }
            }
        }
    }

    private static class PendingKey<JOIN_POINT> {
        Object groupKey;
        String beanName;
        BiConsumer<JOIN_POINT, List<CField>> consumer;
        Pending<JOIN_POINT> pending;
        ReturnFieldDispatchAop<JOIN_POINT> aop;

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
        private final transient ThreadSnapshotRunnable snapshotRunnable;
        private final transient ThreadSnapshotRunnable snapshotGroupKeyRunnable;

        public Pending(GroupCollect<JOIN_POINT> groupCollectMap, ThreadSnapshotRunnable snapshotRunnable, ThreadSnapshotRunnable snapshotGroupKeyRunnable) {
            super(groupCollectMap.result);
            this.groupCollectMap = groupCollectMap;
            this.snapshotRunnable = snapshotRunnable;
            this.snapshotGroupKeyRunnable = snapshotGroupKeyRunnable;
        }

        public GroupCollect<JOIN_POINT> getGroupCollect() {
            return groupCollectMap;
        }

        public ThreadSnapshotRunnable getSnapshotGroupKeyRunnable() {
            return snapshotGroupKeyRunnable;
        }

        public ThreadSnapshotRunnable getSnapshotRunnable() {
            return snapshotRunnable;
        }

        @Override
        public void run() {
            snapshotRunnable.replay(() -> {
                try {
                    groupCollectMap.aop.autowired(groupCollectMap, groupCollectMap.aop.taskExecutor, groupCollectMap.aop.taskDecorate);
                } catch (Exception e) {
                    groupCollectMap.aop.sneakyThrows(e);
                } finally {
                    complete();
                }
            });
        }
    }

    public boolean existPending() {
        return !pendingList.isEmpty();
    }

    protected Future signalAll() throws ExecutionException, InterruptedException, InvocationTargetException, IllegalAccessException {
        Pending<JOIN_POINT>[] pendings = pollPending();
        if (pendings == null || pendings.length == 0) {
            return null;
        } else if (pendings.length == 1) {
            return submit(pendings[0], taskExecutor, null);
        } else {
            return submit(PendingGroup.merge(pendings, this), taskExecutor, null);
        }
    }

    public static <T extends Annotation> T findDeclaredAnnotation(AnnotatedElement element, AnnotationCache<T> cache) {
        return cache.instanceCache.computeIfAbsent(element, e -> AnnotationUtil.findExtendsAnnotation(element, cache.alias, cache.type, cache.findCache));
    }

    protected Pending<JOIN_POINT>[] pollPending() {
        if (pendingList.isEmpty()) {
            return null;
        }
        synchronized (pendingList) {
            if (pendingList.isEmpty()) {
                return null;
            }
            Pending<JOIN_POINT>[] pendings = pendingList.toArray(new Pending[pendingList.size()]);
            pendingList.clear();
            return pendings;
        }
    }

    private static class AnnotationCache<ANNOTATION extends Annotation> {
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

    }

    public static class PendingSignalThread extends Thread {
        private final ReturnFieldDispatchAop aop;

        public PendingSignalThread(ReturnFieldDispatchAop aop) {
            this.aop = aop;
            setName("ReturnFieldDispatchAop-PendingSignal" + getId());
            setDaemon(true);
        }

        @Override
        public void run() {
            while (true) {
                long start = System.currentTimeMillis();
                try {
                    Future future = aop.signalAll();
                    long executeTime = System.currentTimeMillis() - start;
                    long sleepTime = aop.getBatchAggregationMilliseconds() - executeTime;
                    if (sleepTime > 1) {
                        if (future == null || future.isDone()) {
                            if (aop.pendingList.isEmpty()) {
                                Thread.sleep(sleepTime);
                            }
                        } else {
                            Thread.sleep(sleepTime);
                        }
                    }
                } catch (InterruptedException e) {
                    return;
                } catch (ExecutionException | InvocationTargetException | IllegalAccessException e) {
                    PlatformDependentUtil.logWarn(ReturnFieldDispatchAop.class, "collectAndAutowired Execution error = {}", e, e);
                } catch (Throwable e) {
                    PlatformDependentUtil.logWarn(ReturnFieldDispatchAop.class, "collectAndAutowired Throwable error = {}", e, e);
                }
            }
        }
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

        default void begin(JOIN_POINT joinPoint, List<CField> fieldList, Object result) {

        }

        default void nextBegin(int depth, JOIN_POINT joinPoint, List<CField> fieldList, Object result) {

        }

        default void nextEnd(int depth, JOIN_POINT joinPoint, List<CField> fieldList, Object result) {

        }

        default void end(JOIN_POINT joinPoint, List<CField> allFieldList, Object result) {

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
            throw new IllegalStateException("not support aop");
        }

        @Override
        protected void aopAfter() {
            throw new IllegalStateException("not support aop");
        }

        @Override
        protected void aopReturningAfter(JOIN_POINT joinPoint, Object result) throws InvocationTargetException, IllegalAccessException, ExecutionException, InterruptedException {
            throw new IllegalStateException("not support aop");
        }
    }

    protected <E extends Throwable> void sneakyThrows(Throwable t) throws E {
        if (t instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        E cause = (E) FieldCompletableFuture.unwrap(t);
        throw cause;
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

    public static <KEY, VALUE> Map<KEY, VALUE> getCurrentLocalCache(List<CField> cFieldList, Object cacheKey) {
        if (cFieldList instanceof ReturnFieldDispatchAop.SplitCFieldList) {
            return (Map<KEY, VALUE>) ((SplitCFieldList) cFieldList).groupCollect.getConsumerQueryCache(cacheKey);
        } else {
            return null;
        }
    }

    public static class SplitCFieldList extends ArrayList<CField> {
        private transient final AtomicBoolean parseFlag = new AtomicBoolean();
        private transient GroupCollect<?> groupCollect;
        private transient List<CField> keyNameFieldList;
        private transient List<CField> keyValueFieldList;

        public SplitCFieldList(List<CField> fieldList) {
            super(fieldList);
        }

        public SplitCFieldList() {
        }

        public SplitCFieldList(GroupCollect<?> groupCollect) {
            this.groupCollect = groupCollect;
        }

        public SplitCFieldList(GroupCollect<?> groupCollect, int size) {
            super(size);
            this.groupCollect = groupCollect;
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
                            keyValueFields = new SplitCFieldList(groupCollect, Math.min(size(), 16));
                        }
                        keyValueFields.add(e);
                    } else {
                        if (keyNameFields == null) {
                            keyNameFields = new SplitCFieldList(groupCollect, Math.min(size(), 16));
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

        private static boolean isString(CField field) {
            return field.getType() == String.class || field.getGenericType() == String.class;
        }
    }

    public enum BatchAggregationEnum {
        disabled,
        auto,
        manual
    }
}
