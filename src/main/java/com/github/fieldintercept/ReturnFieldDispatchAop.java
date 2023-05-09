package com.github.fieldintercept;

import com.github.fieldintercept.annotation.EnumFieldConsumer;
import com.github.fieldintercept.annotation.FieldConsumer;
import com.github.fieldintercept.annotation.ReturnFieldAop;
import com.github.fieldintercept.annotation.RouterFieldConsumer;
import com.github.fieldintercept.util.BeanMap;
import com.github.fieldintercept.util.FieldCompletableFuture;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.ConfigurableEnvironment;

import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 返回字段aop (可以将id 转为中文名 ,keyField 转中文， 支持占位符)
 *
 * @author acer01
 */
@Aspect
public class ReturnFieldDispatchAop {
    private final static Logger log = LoggerFactory.getLogger(ReturnFieldDispatchAop.class);
    private static final Class SPRING_INDEXED_ANNOTATION;

    static {
        Class springIndexedAnnotation;
        try {
            springIndexedAnnotation = Class.forName("org.springframework.stereotype.Indexed");
        } catch (ClassNotFoundException e) {
            springIndexedAnnotation = null;
        }
        SPRING_INDEXED_ANNOTATION = springIndexedAnnotation;
    }

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
    private final Function<String, BiConsumer<JoinPoint, List<CField>>> biConsumerFunction;
    private Function<Runnable, Future> taskExecutor;
    private ConfigurableEnvironment configurableEnvironment;
    private Predicate<Class> skipFieldClassPredicate = type -> SPRING_INDEXED_ANNOTATION != null && AnnotationUtils.findAnnotation(type, SPRING_INDEXED_ANNOTATION) != null;
    private long batchAggregationMilliseconds = 10;
    private boolean batchAggregation;
    private int batchAggregationMinConcurrentCount = 1;

    public ReturnFieldDispatchAop(Map<String, ? extends BiConsumer<JoinPoint, List<CField>>> map) {
        this.biConsumerFunction = map::get;
    }

    public ReturnFieldDispatchAop(Function<String, BiConsumer<JoinPoint, List<CField>>> biConsumerFunction) {
        this.biConsumerFunction = biConsumerFunction;
    }

    public String getMyAnnotationConsumerName(Class<? extends Annotation> myAnnotationClass) {
        return myAnnotationClass.getSimpleName();
    }

    public void setConfigurableEnvironment(ConfigurableEnvironment configurableEnvironment) {
        this.configurableEnvironment = configurableEnvironment;
    }

    public void setTaskExecutor(Function<Runnable, Future> taskExecutor) {
        this.taskExecutor = taskExecutor;
    }

    public void addBeanPackagePaths(String paths) {
        getMyProjectPackagePaths().add(Arrays.asList(paths.split("[./]")));
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
        } catch (InvocationTargetException | IllegalAccessException | InterruptedException | ExecutionException e) {
            sneakyThrows(e);
        } finally {
            after();
        }
    }

    public <T> T autowiredFieldValue(T result) {
        before();
        try {
            returningAfter(null, result);
        } catch (InvocationTargetException | IllegalAccessException | InterruptedException | ExecutionException e) {
            sneakyThrows(e);
        } finally {
            after();
        }
        return result;
    }

    protected Object objectId(Object object) {
        return System.identityHashCode(object);
    }

    @Before(value = "@annotation(com.github.fieldintercept.annotation.ReturnFieldAop)")
    protected void before() {
        if (concurrentThreadMap.computeIfAbsent(Thread.currentThread(), e -> new AtomicInteger()).getAndIncrement() == 0) {
            concurrentThreadCounter.increment();
        }
    }

    @After(value = "@annotation(com.github.fieldintercept.annotation.ReturnFieldAop)")
    protected void after() {
        Thread currentThread = Thread.currentThread();
        if (concurrentThreadMap.get(currentThread).decrementAndGet() == 0) {
            concurrentThreadCounter.decrement();
            concurrentThreadMap.remove(currentThread);
        }
    }

    @AfterReturning(value = "@annotation(com.github.fieldintercept.annotation.ReturnFieldAop)",
            returning = "result")
    protected void returningAfter(JoinPoint joinPoint, Object result) throws InvocationTargetException, IllegalAccessException, ExecutionException, InterruptedException {
        if (log.isTraceEnabled()) {
            log.trace("afterReturning into. joinPoint={}, result={}", joinPoint, result);
        }
        if (isNeedPending(joinPoint, result)) {
            addPendingList(result);
            if (!(result instanceof FieldCompletableFuture)) {
                pending();
            }
        } else {
            collectAndAutowired(joinPoint, result);
        }
    }

    protected boolean isInSignalThread() {
        return pendingSignalThreadRef.get() == Thread.currentThread();
    }

    protected void collectAndAutowired(JoinPoint joinPoint, Object result) throws ExecutionException, InterruptedException, InvocationTargetException, IllegalAccessException {
        if (result == null) {
            return;
        }

        List<FieldCompletableFuture<?>> completableFutureList = new LinkedList<>();
        Map<String, List<CField>> groupCollectMap = new LinkedHashMap<>();

        Map<String, FieldIntercept> aopFieldInterceptMap = new LinkedHashMap<>();
        Set<Object> visitObjectIdSet = new HashSet<>();
        visitObjectIdSet.add(objectId(result));

        List<CField> allFieldList = new ArrayList<>();
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
            for (FieldIntercept intercept : aopFieldInterceptMap.values()) {
                try {
                    intercept.end(joinPoint, allFieldList, result);
                } catch (Exception e) {
                    sneakyThrows(e);
                }
            }
        }
    }

    protected List<CField> autowired(JoinPoint joinPoint, Map<String, List<CField>> groupCollectMap, Map<String, FieldIntercept> aopFieldInterceptMap, int step, Object result) throws ExecutionException, InterruptedException {
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
            FieldIntercept fieldIntercept = aopFieldInterceptMap.get(key);
            BiConsumer<JoinPoint, List<CField>> consumer;
            if (fieldIntercept != null) {
                consumer = fieldIntercept;
            } else {
                consumer = biConsumerFunction.apply(key);
                if (consumer instanceof FieldIntercept) {
                    fieldIntercept = (FieldIntercept) consumer;
                    aopFieldInterceptMap.put(key, fieldIntercept);
                    fieldIntercept.begin(joinPoint, fieldList, result);
                }
            }
            if (consumer == null) {
                throw new IllegalArgumentException("ReturnFieldDispatchAop autowired consumer '" + key + "' not found!");
            }
            callableList.add(new AutowiredRunnable(this, joinPoint, result, step, fieldList, key, consumer));
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

    public static class AutowiredRunnable implements Runnable {
        private final static Logger log = LoggerFactory.getLogger(AutowiredRunnable.class);
        private final ReturnFieldDispatchAop aop;
        private final JoinPoint joinPoint;
        private final Object result;
        private final int step;
        private final List<CField> fieldList;
        private final String consumerName;
        private final BiConsumer<JoinPoint, List<CField>> consumer;

        public AutowiredRunnable(ReturnFieldDispatchAop aop, JoinPoint joinPoint, Object result,
                                 int step, List<CField> fieldList,
                                 String consumerName, BiConsumer<JoinPoint, List<CField>> consumer) {
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

        public ReturnFieldDispatchAop getAop() {
            return aop;
        }

        public JoinPoint getJoinPoint() {
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

        public BiConsumer<JoinPoint, List<CField>> getConsumer() {
            return consumer;
        }

        @Override
        public void run() {
            FieldIntercept fieldIntercept = consumer instanceof FieldIntercept ? (FieldIntercept) consumer : null;
            try {
                if (fieldIntercept != null) {
                    fieldIntercept.stepBegin(step, joinPoint, fieldList, result);
                }
                boolean traceEnabled = log.isTraceEnabled();
                if (traceEnabled) {
                    log.trace("start Consumer ={}, value={}", consumer, fieldList);
                }
                consumer.accept(joinPoint, fieldList);
                if (traceEnabled) {
                    log.trace("end Consumer ={}", consumer);
                }
            } catch (Exception e) {
                if (log.isErrorEnabled()) {
                    log.error("error Consumer ={},message={}", consumer, e.getMessage(), e);
                }
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
                || TemporalAccessor.class.isAssignableFrom(e)
                || e.isEnum());
    }

    protected boolean isEntity(Class type) {
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

        if (bean instanceof Map) {
            for (Object each : ((Map) bean).values()) {
                collectBean(each, groupCollectMap, completableFutureList);
            }
            return;
        }

        boolean isRootEntity = isEntity(rootClass);
        BeanMap beanHandler = null;
        Map<String, PropertyDescriptor> propertyDescriptor = BeanMap.findPropertyDescriptor(rootClass);
        for (PropertyDescriptor descriptor : propertyDescriptor.values()) {
            // 支持getter方法明确表示get返回的结果需要注入
            Method readMethod = descriptor.getReadMethod();
            if (isRootEntity && readMethod != null && readMethod.getDeclaredAnnotations().length > 0
                    && AnnotationUtils.findAnnotation(readMethod, ReturnFieldAop.class) != null) {
                Object fieldData = readMethod.invoke(bean);
                collectBean(fieldData, groupCollectMap, completableFutureList);
                continue;
            }

            Field field = BeanMap.getField(descriptor);
            if (field == null) {
                continue;
            }

            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)) {
                continue;
            }

            if (field.getDeclaringClass() == Object.class) {
                continue;
            }

            //路由消费字段
            RouterFieldConsumer routerFieldConsumer = field.getDeclaredAnnotation(RouterFieldConsumer.class);
            if (routerFieldConsumer != null && routerFieldConsumer.routerField().length() > 0) {
                if (beanHandler == null) {
                    beanHandler = new BeanMap(bean);
                }
                if (!beanHandler.containsKey(routerFieldConsumer.routerField())) {
                    if (log.isWarnEnabled()) {
                        log.warn("RouterFieldConsumer not found field, class={},routerField={}, data={}", rootClass, routerFieldConsumer.routerField(), bean);
                    }
                }
                Object routerFieldData = beanHandler.get(routerFieldConsumer.routerField());
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
                    groupCollectMap.computeIfAbsent(choseFieldConsumer.value(), e -> new ArrayList<>())
                            .add(new CField(choseFieldConsumer.value(), beanHandler, field, choseFieldConsumer));
                }
            }

            //普通消费字段
            FieldConsumer fieldConsumer = field.getDeclaredAnnotation(FieldConsumer.class);
            if (fieldConsumer != null) {
                if (beanHandler == null) {
                    beanHandler = new BeanMap(bean);
                }
                CField cField = new CField(fieldConsumer.value(), beanHandler, field, fieldConsumer);
//                    if(!cField.existValue()){
                groupCollectMap.computeIfAbsent(fieldConsumer.value(), e -> new ArrayList<>())
                        .add(cField);
                continue;
//                    }
            }

            //枚举消费字段
            EnumFieldConsumer enumFieldConsumer = field.getDeclaredAnnotation(EnumFieldConsumer.class);
            if (enumFieldConsumer != null) {
                if (beanHandler == null) {
                    beanHandler = new BeanMap(bean);
                }
                CField cField = new CField(EnumFieldConsumer.NAME, beanHandler, field, enumFieldConsumer);
//                    if(!cField.existValue()) {
                groupCollectMap.computeIfAbsent(EnumFieldConsumer.NAME, e -> new ArrayList<>())
                        .add(cField);
                continue;
//                    }
            }

            //自定义消费字段
            for (Class<? extends Annotation> myAnnotationClass : annotations) {
                Annotation myAnnotation = field.getDeclaredAnnotation(myAnnotationClass);
                if (myAnnotation != null) {
                    if (beanHandler == null) {
                        beanHandler = new BeanMap(bean);
                    }
                    String name = getMyAnnotationConsumerName(myAnnotationClass);
                    CField cField = new CField(name, beanHandler, field, myAnnotation);
                    groupCollectMap.computeIfAbsent(name, e -> new ArrayList<>())
                            .add(cField);
                }
            }


            boolean isMultiple = isMultiple(field.getType());
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

            boolean isEntity = !isBasicType(field.getType()) && isEntity(field.getType());
            if (isEntity) {
                try {
                    // 防止触发 getter方法, 忽略private, 强行取字段值
                    Object fieldData = getFieldValue(field, bean);
                    if (fieldData == null) {
                        continue;
                    }
                    Class<?> fieldDataClass = fieldData.getClass();
                    if (skipFieldClassPredicate.test(fieldDataClass)) {
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
     * 是否是spring对象
     *
     * @param skipFieldClassPredicate 跳过判断, true=跳过
     */
    public void setSkipFieldClassPredicate(Predicate<Class> skipFieldClassPredicate) {
        this.skipFieldClassPredicate = skipFieldClassPredicate;
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

    protected boolean isNeedPending(JoinPoint joinPoint, Object returnResult) {
        if (!batchAggregation) {
            return false;
        }
        long concurrentThreadCount = concurrentThreadCounter.sum();
        if (concurrentThreadCount <= batchAggregationMinConcurrentCount) {
            return false;
        }
        if (joinPoint == null) {
            return true;
        }
        ReturnFieldAop returnFieldAop = ((MethodSignature) joinPoint.getSignature()).getMethod().getAnnotation(ReturnFieldAop.class);
        return returnFieldAop.batchAggregation();
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
                } catch (ExecutionException | InterruptedException | InvocationTargetException | IllegalAccessException e) {
                    sneakyThrows(e);
                } finally {
                    lock.unlock();
                }
            });
        }
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
                    if (log.isWarnEnabled()) {
                        log.warn("collectAndAutowired Execution error = {}", e, e);
                    }
                } catch (Throwable e) {
                    if (log.isWarnEnabled()) {
                        log.warn("collectAndAutowired Throwable error = {}", e, e);
                    }
                }
            }
        }
    }

    /**
     * 字段拦截器 (可以处理字段注入, 加缓存等)
     *
     * @author hao
     */
    public interface FieldIntercept extends BiConsumer<JoinPoint, List<CField>> {
        @Override
        void accept(JoinPoint joinPoint, List<CField> fieldList);

        default void begin(JoinPoint joinPoint, List<CField> fieldList, Object result) {

        }

        default void stepBegin(int step, JoinPoint joinPoint, List<CField> fieldList, Object result) {

        }

        default void stepEnd(int step, JoinPoint joinPoint, List<CField> fieldList, Object result) {

        }

        default void end(JoinPoint joinPoint, List<CField> allFieldList, Object result) {

        }
    }

    protected <E extends Throwable> void sneakyThrows(Throwable t) throws E {
        throw (E) t;
    }
}
