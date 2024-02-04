package com.github.fieldintercept;

import com.github.fieldintercept.annotation.EnumFieldConsumer;
import com.github.fieldintercept.annotation.FieldConsumer;
import com.github.fieldintercept.annotation.ReturnFieldAop;
import com.github.fieldintercept.annotation.RouterFieldConsumer;
import com.github.fieldintercept.util.AnnotationUtil;
import com.github.fieldintercept.util.BeanMap;
import com.github.fieldintercept.util.FieldCompletableFuture;
import com.github.fieldintercept.util.PlatformDependentUtil;

import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 返回字段aop (可以将id 转为中文名 ,keyField 转中文， 支持占位符)
 *
 * @author acer01
 */
public abstract class ReturnFieldDispatchAop<JOIN_POINT> {
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
    public static Predicate<Class> DEFAULT_SKIP_FIELD_CLASS_PREDICATE = type -> SPRING_INDEXED_ANNOTATION != null && AnnotationUtil.findDeclaredAnnotation(type, SPRING_INDEXED_ANNOTATION, SKIP_FIELD_CLASS_CACHE_MAP) != null;

    /**
     * 实体类包名一样, 就认为是业务实体类
     */
    private final Set<List<String>> myProjectPackagePaths = new LinkedHashSet<>();
    /**
     * 动态注解 或 用户自定义注解
     */
    private final Set<Class<? extends Annotation>> annotations = new LinkedHashSet<>();
    private final List<Object> pendingList = new ArrayList<>(100);
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
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
    private Object configurableEnvironment;
    private Predicate<Class> skipFieldClassPredicate = DEFAULT_SKIP_FIELD_CLASS_PREDICATE;
    private final Map<Class<?>, Boolean> skipFieldClassPredicateCache = Collections.synchronizedMap(new LinkedHashMap<Class<?>, Boolean>(201, 1F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return size() > 200;
        }
    });
    private long batchAggregationMilliseconds = 10;
    private boolean batchAggregation;
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

    public void setTaskExecutor(Function<Runnable, Future> taskExecutor) {
        this.taskExecutor = taskExecutor;
    }

    public Function<Runnable, Future> getTaskExecutor() {
        return taskExecutor;
    }

    public void addBeanPackagePaths(String paths) {
        getMyProjectPackagePaths().add(Arrays.asList(paths.split("[./]")));
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
        } catch (Exception e) {
            sneakyThrows(e);
        } finally {
            after();
        }
    }

    public <T> T autowiredFieldValue(T result) {
        before();
        try {
            returningAfter(null, result);
        } catch (Exception e) {
            sneakyThrows(e);
        } finally {
            after();
        }
        return result;
    }

    protected Object objectId(Object object) {
        return System.identityHashCode(object);
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

    protected void returningAfter(JOIN_POINT joinPoint, Object result) throws InvocationTargetException, IllegalAccessException, ExecutionException, InterruptedException {
        BiPredicate<JOIN_POINT, Object> enabled = getEnabled();
        if (enabled != null && !enabled.test(joinPoint, result)) {
            return;
        }

        PlatformDependentUtil.logTrace(ReturnFieldDispatchAop.class, "afterReturning into. joinPoint={}, result={}", joinPoint, result);

        if (isNeedPending(joinPoint, result)) {
            addPendingList(result);
            if (!(result instanceof FieldCompletableFuture)) {
                pending();
            }
        } else {
            if (result instanceof FieldCompletableFuture) {
                taskExecutor.apply(() -> {
                    FieldCompletableFuture<?> future = (FieldCompletableFuture) result;
                    try {
                        collectAndAutowired(joinPoint, result);
                    } catch (ExecutionException | InvocationTargetException e) {
                        future.completeExceptionally(e.getCause());
                    } catch (InterruptedException e) {
                        future.complete();
                    } catch (Throwable e) {
                        future.completeExceptionally(e);
                    }
                });
            } else {
                collectAndAutowired(joinPoint, result);
            }
        }
    }

    protected boolean isInSignalThread() {
        return pendingSignalThreadRef.get() == Thread.currentThread();
    }

    protected void collectAndAutowired(JOIN_POINT joinPoint, Object result) throws ExecutionException, InterruptedException, InvocationTargetException, IllegalAccessException {
        if (result == null) {
            return;
        }

        List<FieldCompletableFuture<?>> completableFutureList = new LinkedList<>();
        Map<String, List<CField>> groupCollectMap = new LinkedHashMap<>();

        Map<String, FieldIntercept<JOIN_POINT>> aopFieldInterceptMap = new LinkedHashMap<>();
        Set<Object> visitObjectIdSet = new HashSet<>();
        visitObjectIdSet.add(objectId(result));

        List<CField> allFieldList = new SplitCFieldList();
        int step = 1;
        Object next = result;
        try {
            while (true) {
                //收集返回值中的所有实体类
                collectBean(next, groupCollectMap, completableFutureList);
                if (groupCollectMap.isEmpty()) {
                    break;
                }
                // 注入
                List<CField> fieldList = autowired(joinPoint, groupCollectMap, aopFieldInterceptMap, step, result);
                allFieldList.addAll(fieldList);

                // 检查注入后的是否需要继续注入
                next = groupCollectMap.values().stream()
                        .flatMap(Collection::stream)
                        .map(CField::getValue)
                        // 去掉循环依赖的对象 (防止递归循环依赖, 比如用户表的创建者是自己)
                        .filter(e -> !visitObjectIdSet.contains(objectId(e)))
                        // 放入访问记录
                        .peek(e -> visitObjectIdSet.add(objectId(e)))
                        .collect(Collectors.toList());
                // 清空处理过的
                groupCollectMap.clear();
                step++;
            }
        } finally {
            for (FieldCompletableFuture<?> future : completableFutureList) {
                future.complete();
            }
            for (FieldIntercept<JOIN_POINT> intercept : aopFieldInterceptMap.values()) {
                try {
                    intercept.end(joinPoint, allFieldList, result);
                } catch (Exception e) {
                    sneakyThrows(e);
                }
            }
        }
    }

    protected List<CField> autowired(JOIN_POINT joinPoint, Map<String, List<CField>> groupCollectMap, Map<String, FieldIntercept<JOIN_POINT>> aopFieldInterceptMap, int step, Object result) throws ExecutionException, InterruptedException {
        //  通知实现
        List<Runnable> callableList = new LinkedList<>();
        List<CField> allFieldList = new ArrayList<>();
        for (Map.Entry<String, List<CField>> entry : groupCollectMap.entrySet()) {
            String key = entry.getKey();
            List<CField> fieldList = entry.getValue();
            if (key == null || key.isEmpty()) {
                continue;
            }
            allFieldList.addAll(fieldList);
            FieldIntercept<JOIN_POINT> fieldIntercept = aopFieldInterceptMap.get(key);
            BiConsumer<JOIN_POINT, List<CField>> consumer;
            if (fieldIntercept != null) {
                consumer = fieldIntercept;
            } else {
                consumer = consumerFactory.apply(key);
                if (consumer instanceof FieldIntercept) {
                    fieldIntercept = (FieldIntercept<JOIN_POINT>) consumer;
                    aopFieldInterceptMap.put(key, fieldIntercept);
                    fieldIntercept.begin(joinPoint, fieldList, result);
                }
            }
            if (consumer == null) {
                throw new IllegalArgumentException("ReturnFieldDispatchAop autowired consumer '" + key + "' not found!");
            }
            callableList.add(new AutowiredRunnable<>(this, joinPoint, result, step, fieldList, key, consumer));
        }

        // 执行
        try {
            switch (callableList.size()) {
                case 0: {
                    break;
                }
                case 1: {
                    callableList.get(0).run();
                    break;
                }
                default: {
                    Function<Runnable, Future> taskExecutor = this.taskExecutor;
                    if (taskExecutor != null) {
                        List<Future> futureList = new ArrayList<>();
                        Runnable blockOnCurrentThread = isInSignalThread() ? null : callableList.remove(0);
                        for (Runnable runnable : callableList) {
                            futureList.add(taskExecutor.apply(runnable));
                        }
                        if (blockOnCurrentThread != null) {
                            blockOnCurrentThread.run();
                        }
                        for (Future f : futureList) {
                            f.get();
                        }
                    } else {
                        for (Runnable callable : callableList) {
                            callable.run();
                        }
                    }
                    break;
                }
            }
        } finally {
            for (List<CField> cFieldList : groupCollectMap.values()) {
                for (CField cField : cFieldList) {
                    if (cField.isSetValue()) {
                        continue;
                    }
                    //解析占位符
                    String resolve = cField.resolvePlaceholders(configurableEnvironment, cField.getBeanHandler());
                    if (resolve == null) {
                        continue;
                    }
                    cField.setValue(resolve);
                }
            }
        }
        return allFieldList;
    }

    public static class AutowiredRunnable<JOIN_POINT> implements Runnable {
        private final ReturnFieldDispatchAop<JOIN_POINT> aop;
        private final JOIN_POINT joinPoint;
        private final Object result;
        private final int step;
        private final List<CField> fieldList;
        private final String consumerName;
        private final BiConsumer<JOIN_POINT, List<CField>> consumer;

        public AutowiredRunnable(ReturnFieldDispatchAop<JOIN_POINT> aop, JOIN_POINT joinPoint, Object result,
                                 int step, List<CField> fieldList,
                                 String consumerName, BiConsumer<JOIN_POINT, List<CField>> consumer) {
            this.aop = aop;
            this.joinPoint = joinPoint;
            this.result = result;
            this.step = step;
            this.fieldList = fieldList;
            this.consumerName = consumerName;
            this.consumer = consumer;
        }

        @Override
        public String toString() {
            return "AutowiredRunnable{" +
                    consumerName +
                    '}';
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

        public int getStep() {
            return step;
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
                    fieldIntercept.stepBegin(step, joinPoint, fieldList, result);
                }
                PlatformDependentUtil.logTrace(AutowiredRunnable.class, "start Consumer ={}, value={}", consumer, fieldList);
                consumer.accept(joinPoint, fieldList);
                PlatformDependentUtil.logTrace(AutowiredRunnable.class, "end Consumer ={}", consumer);
            } catch (Exception e) {
                PlatformDependentUtil.logTrace(AutowiredRunnable.class, "error Consumer ={},message={}", consumer, e.getMessage(), e);
                aop.sneakyThrows(e);
            } finally {
                if (fieldIntercept != null) {
                    fieldIntercept.stepEnd(step, joinPoint, fieldList, result);
                }
            }
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
        return typeEntryCacheMap.computeIfAbsent(type, e -> {
            Package typePackage = e.getPackage();
            if (typePackage == null) {
                return false;
            }

            String[] packagePaths = typePackage.getName().split("[.]");
            for (List<String> myProjectPackagePath : getMyProjectPackagePaths()) {
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
    protected void collectBean(Object bean,
                               Map<String, List<CField>> groupCollectMap,
                               List<FieldCompletableFuture<?>> completableFutureList) throws InvocationTargetException, IllegalAccessException {
        if (bean == null || bean instanceof Class) {
            return;
        }
        if (bean instanceof FieldCompletableFuture) {
            completableFutureList.add((FieldCompletableFuture) bean);
            collectBean(((FieldCompletableFuture<?>) bean).value(), groupCollectMap, completableFutureList);
            return;
        }
        Class<?> rootClass = bean.getClass();
        if (isBasicType(rootClass)) {
            return;
        }

        if (bean instanceof Iterable) {
            for (Object each : (Iterable) bean) {
                collectBean(each, groupCollectMap, completableFutureList);
            }
            return;
        }

        if (rootClass.isArray()) {
            for (int i = 0, length = Array.getLength(bean); i < length; i++) {
                Object each = Array.get(bean, i);
                collectBean(each, groupCollectMap, completableFutureList);
            }
            return;
        }

        boolean isRootEntity = isEntity(rootClass);

        if (!isRootEntity && bean instanceof Map) {
            for (Object each : ((Map) bean).values()) {
                collectBean(each, groupCollectMap, completableFutureList);
            }
            return;
        }

        BeanMap beanHandler = null;
        Map<String, PropertyDescriptor> propertyDescriptor = BeanMap.findPropertyDescriptor(rootClass);
        for (PropertyDescriptor descriptor : propertyDescriptor.values()) {
            // 支持getter方法明确表示get返回的结果需要注入
            Method readMethod = descriptor.getReadMethod();
            if (isRootEntity && readMethod != null && readMethod.getDeclaredAnnotations().length > 0
                    && findDeclaredAnnotation(readMethod, returnFieldAopCache) != null) {
                Object fieldData = readMethod.invoke(bean);
                collectBean(fieldData, groupCollectMap, completableFutureList);
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
            if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)) {
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
                    addCollectField(groupCollectMap, choseFieldConsumer.value(), beanHandler, field, choseFieldConsumer, configurableEnvironment);
                }
            }

            //普通消费字段
            FieldConsumer fieldConsumer = findDeclaredAnnotation(field, fieldConsumerCache);
            if (fieldConsumer != null) {
                if (beanHandler == null) {
                    beanHandler = new BeanMap(bean);
                }
                addCollectField(groupCollectMap, fieldConsumer.value(), beanHandler, field, fieldConsumer, configurableEnvironment);
                continue;
            }

            //枚举消费字段
            EnumFieldConsumer enumFieldConsumer = findDeclaredAnnotation(field, enumFieldConsumerCache);
            if (enumFieldConsumer != null) {
                if (beanHandler == null) {
                    beanHandler = new BeanMap(bean);
                }
                addCollectField(groupCollectMap, EnumFieldConsumer.NAME, beanHandler, field, enumFieldConsumer, configurableEnvironment);
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
                    addCollectField(groupCollectMap, name, beanHandler, field, myAnnotation, configurableEnvironment);
                }
            }


            Class<?> fieldType = field.getType();
            boolean isMultiple = isMultiple(fieldType);
            if (isMultiple) {
                try {
                    // 防止触发 getter方法, 忽略private, 强行取字段值
                    Object fieldData = getFieldValue(field, bean);
                    collectBean(fieldData, groupCollectMap, completableFutureList);
                    continue;
                } catch (Exception e) {
                    sneakyThrows(e);
                }
            }

            boolean isEntity = !isBasicType(fieldType) && isEntity(fieldType);
            if (isEntity) {
                try {
                    // 防止触发 getter方法, 忽略private, 强行取字段值
                    Object fieldData = getFieldValue(field, bean);
                    if (fieldData == null) {
                        continue;
                    }
                    Class<?> fieldDataClass = fieldData.getClass();
                    if (skipFieldClassPredicateCache.computeIfAbsent(fieldDataClass, type -> skipFieldClassPredicate.test(fieldDataClass))) {
                        continue;
                    }
                    collectBean(fieldData, groupCollectMap, completableFutureList);
                } catch (Exception e) {
                    sneakyThrows(e);
                }
            }
        }
    }

    /**
     * 添加收集到的字段
     */
    private static void addCollectField(Map<String, List<CField>> groupCollectMap,
                                        String consumerName, BeanMap beanHandler, Field field,
                                        Annotation annotation, Object configurableEnvironment) {
        CField cField = new CField(consumerName, beanHandler, field, annotation, configurableEnvironment);
        groupCollectMap.computeIfAbsent(consumerName, e -> new SplitCFieldList())
                .add(cField);
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

    public void setBatchAggregation(boolean batchAggregation) {
        this.batchAggregation = batchAggregation;
    }

    public boolean isBatchAggregation() {
        return batchAggregation;
    }

    protected boolean isNeedPending(JOIN_POINT joinPoint, Object returnResult) {
        if (!batchAggregation) {
            return false;
        }
        long concurrentThreadCount = concurrentThreadCounter.sum();
        if (concurrentThreadCount <= batchAggregationMinConcurrentCount) {
            return false;
        }
        return joinPoint == null;
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

    protected void addPendingList(Object returnResult) {
        startPendingSignalThreadIfNeed();
        synchronized (pendingList) {
            pendingList.add(returnResult);
        }
    }

    protected void pending() throws InterruptedException {
        lock.lock();
        try {
            condition.await();
        } finally {
            lock.unlock();
        }
    }

    public boolean existPending() {
        return !pendingList.isEmpty();
    }

    protected Future signalAll() throws ExecutionException, InterruptedException, InvocationTargetException, IllegalAccessException {
        List<Object> poll = pollPending();
        if (poll.isEmpty()) {
            return null;
        }
        Function<Runnable, Future> taskExecutor = this.taskExecutor;
        if (taskExecutor == null) {
            lock.lock();
            try {
                collectAndAutowired(null, poll);
                condition.signalAll();
            } finally {
                lock.unlock();
            }
            return null;
        } else {
            return taskExecutor.apply(() -> {
                lock.lock();
                try {
                    collectAndAutowired(null, poll);
                    condition.signalAll();
                } catch (Exception e) {
                    sneakyThrows(e);
                } finally {
                    lock.unlock();
                }
            });
        }
    }

    public static <T extends Annotation> T findDeclaredAnnotation(AnnotatedElement element, AnnotationCache<T> cache) {
        return cache.instanceCache.computeIfAbsent(element, e -> AnnotationUtil.findExtendsAnnotation(element, cache.alias, cache.type, cache.findCache));
    }

    protected List<Object> pollPending() {
        synchronized (pendingList) {
            if (pendingList.isEmpty()) {
                return Collections.emptyList();
            }
            ArrayList<Object> objects = new ArrayList<>(pendingList);
            pendingList.clear();
            return objects;
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

        default void stepBegin(int step, JOIN_POINT joinPoint, List<CField> fieldList, Object result) {

        }

        default void stepEnd(int step, JOIN_POINT joinPoint, List<CField> fieldList, Object result) {

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
        E cause = (E) t;
        if (t instanceof ExecutionException || t instanceof UndeclaredThrowableException || t instanceof InvocationTargetException) {
            Throwable c = t.getCause();
            if (c != null) {
                cause = (E) c;
            }
        }
        throw cause;
    }

    public static SplitCFieldList split(List<CField> cFieldList) {
        if (cFieldList instanceof ReturnFieldDispatchAop.SplitCFieldList) {
            return (SplitCFieldList) cFieldList;
        } else {
            return new SplitCFieldList(cFieldList);
        }
    }

    public static class SplitCFieldList extends ArrayList<CField> {
        private final AtomicBoolean parseFlag = new AtomicBoolean();
        private List<CField> keyNameFieldList;
        private List<CField> keyValueFieldList;

        public SplitCFieldList(List<CField> fieldList) {
            super(fieldList);
        }

        public SplitCFieldList() {
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
                            keyValueFields = new ArrayList<>(Math.min(size(), 16));
                        }
                        keyValueFields.add(e);
                    } else {
                        if (keyNameFields == null) {
                            keyNameFields = new ArrayList<>(Math.min(size(), 16));
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

}
