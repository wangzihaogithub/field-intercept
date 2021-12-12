package com.github.fieldintercept;

import com.github.fieldintercept.annotation.EnumFieldConsumer;
import com.github.fieldintercept.annotation.FieldConsumer;
import com.github.fieldintercept.annotation.ReturnFieldAop;
import com.github.fieldintercept.annotation.RouterFieldConsumer;
import com.github.fieldintercept.util.BeanMap;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.stereotype.Indexed;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.beans.PropertyDescriptor;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
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
    /**
     * 实体类包名一样, 就认为是业务实体类
     */
    private final Set<List<String>> myProjectPackagePaths = new LinkedHashSet<>();
    private Function<String, BiConsumer<JoinPoint, List<CField>>> biConsumerFunction;
    private Function<Runnable, Future> taskExecutor;
    private ConfigurableEnvironment configurableEnvironment;
    private Predicate<Class> skipFieldClassPredicate = type-> AnnotationUtils.findAnnotation(type, Indexed.class) != null;

    public ReturnFieldDispatchAop(Map<String, ? extends BiConsumer<JoinPoint, List<CField>>> map) {
        this(map::get);
    }

    public ReturnFieldDispatchAop(Function<String, BiConsumer<JoinPoint, List<CField>>> biConsumerFunction) {
        this.biConsumerFunction = biConsumerFunction;
    }

    @Autowired
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

    public void autowiredFieldValue(Object... result) {
        try {
            returningAfter(null, result);
        } catch (InvocationTargetException | IllegalAccessException | InterruptedException | ExecutionException ignored) {
        }
    }

    public <T> T autowiredFieldValue(T result) {
        try {
            returningAfter(null, result);
        } catch (InvocationTargetException | IllegalAccessException | InterruptedException | ExecutionException ignored) {
        }
        return result;
    }

    protected Object objectId(Object object) {
        return System.identityHashCode(object);
    }

    @AfterReturning(value = "@annotation(com.github.fieldintercept.annotation.ReturnFieldAop)",
            returning = "result")
    protected void returningAfter(JoinPoint joinPoint, Object result) throws InvocationTargetException, IllegalAccessException, ExecutionException, InterruptedException {
        log.trace("afterReturning into. joinPoint={}, result={}", joinPoint, result);
        MultiValueMap<String, CField> groupCollectMap = new LinkedMultiValueMap<>();

        Map<String, FieldIntercept> aopFieldInterceptMap = new LinkedHashMap<>();
        Set<Object> visitObjectIdSet = new HashSet<>();
        visitObjectIdSet.add(objectId(result));

        List<CField> allFieldList = new ArrayList<>();
        int step = 1;
        Object next = result;
        try {
            while (true) {
                //收集返回值中的所有实体类
                collectBean(next, groupCollectMap);
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
            aopFieldInterceptMap.values().forEach(o -> {
                try {
                    o.end(joinPoint, allFieldList, result);
                } catch (Exception e) {
                    log.warn("aopFieldIntercept end error = {}, intercept = {}", e.toString(), o, e);
                }
            });
        }
    }

    protected List<CField> autowired(JoinPoint joinPoint, MultiValueMap<String, CField> groupCollectMap, Map<String, FieldIntercept> aopFieldInterceptMap, int step, Object result) throws ExecutionException, InterruptedException {
        //  通知实现
        List<Runnable> callableList = new ArrayList<>();
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

            FieldIntercept finalFieldIntercept = fieldIntercept;
            callableList.add(() -> {
                try {
                    if (finalFieldIntercept != null) {
                        finalFieldIntercept.stepBegin(step, joinPoint, fieldList, result);
                    }
                    log.trace("start Consumer ={}, value={}", consumer, fieldList);
                    consumer.accept(joinPoint, fieldList);
                    log.trace("end Consumer ={}", consumer);
                } catch (Exception e) {
                    log.error("error Consumer ={},message={}", consumer, e.getMessage(), e);
                } finally {
                    if (finalFieldIntercept != null) {
                        finalFieldIntercept.stepEnd(step, joinPoint, fieldList, result);
                    }
                }
            });
        }

        // 执行
        try {
            Function<Runnable, Future> taskExecutor = this.taskExecutor;
            if (taskExecutor != null) {
                List<Future> futureList = new ArrayList<>();
                for (Runnable runnable : callableList) {
                    futureList.add(taskExecutor.apply(runnable));
                }
                for (Future future : futureList) {
                    future.get();
                }
            } else {
                for (Runnable callable : callableList) {
                    callable.run();
                }
            }
        } finally {
            for (List<CField> cFieldList : groupCollectMap.values()) {
                for (CField cField : cFieldList) {
                    if (cField.isSetValue()) {
                        continue;
                    }
                    //解析占位符
                    String value = cField.resolvePlaceholders(configurableEnvironment, cField.getBeanHandler());
                    if (value == null) {
                        continue;
                    }
                    cField.setValue(value);
                }
            }
            callableList.clear();
        }
        return allFieldList;
    }

    protected boolean isMultiple(Class type) {
        if (Iterable.class.isAssignableFrom(type)) {
            return true;
        }
        if (Map.class.isAssignableFrom(type)) {
            return true;
        }
        if (type.isArray()) {
            return true;
        }
        return false;
    }

    protected boolean isEntity(Class type) {
        Package typePackage = type.getPackage();
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
    }

    /**
     * 收集数据中的所有实体类
     *
     * @param bean            数据
     * @param groupCollectMap 分组收集器
     */
    protected void collectBean(Object bean,
                               MultiValueMap<String, CField> groupCollectMap) throws InvocationTargetException, IllegalAccessException {
        if (bean == null || bean instanceof Class) {
            return;
        }
        Class<?> rootClass = bean.getClass();
        if (rootClass.isPrimitive()
                || rootClass == String.class
                || Number.class.isAssignableFrom(rootClass)
                || Date.class.isAssignableFrom(rootClass)
                || rootClass.isEnum()) {
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

        if (bean instanceof Map) {
            for (Object each : ((Map) bean).values()) {
                collectBean(each, groupCollectMap);
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
                collectBean(fieldData, groupCollectMap);
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
                    log.warn("RouterFieldConsumer not found field, class={},routerField={}, data={}", rootClass, routerFieldConsumer.routerField(), bean);
                }
                Object routerFieldData = beanHandler.get(routerFieldConsumer.routerField());
                String routerFieldDataStr = routerFieldData == null ? null : routerFieldData.toString();
                for (FieldConsumer fieldConsumer : routerFieldConsumer.value()) {
                    String type = fieldConsumer.type();
                    if (Objects.equals(routerFieldDataStr, type)) {
                        groupCollectMap.add(fieldConsumer.value(), new CField(fieldConsumer.value(), beanHandler, field, fieldConsumer));
                        break;
                    }
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
                groupCollectMap.add(fieldConsumer.value(), cField);
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
                groupCollectMap.add(EnumFieldConsumer.NAME, cField);
                continue;
//                    }
            }

            boolean isMultiple = isMultiple(field.getType());
            if (isMultiple) {
                try {
                    // 防止触发 getter方法, 忽略private, 强行取字段值
                    Object fieldData = getFieldValue(field, bean);
                    collectBean(fieldData, groupCollectMap);
                    continue;
                } catch (Exception ig) {
                    //skip
                }
            }

            boolean isEntity = isEntity(field.getType());
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
                    collectBean(fieldData, groupCollectMap);
                } catch (Exception ig) {
                    //skip
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
        if (target == null) {
            return null;
        }
        boolean accessible = field.isAccessible();
        try {
            field.setAccessible(true);
            return field.get(target);
        } finally {
            field.setAccessible(accessible);
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

}
