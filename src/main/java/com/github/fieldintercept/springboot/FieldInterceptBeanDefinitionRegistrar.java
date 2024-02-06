package com.github.fieldintercept.springboot;

import com.github.fieldintercept.*;
import com.github.fieldintercept.annotation.EnumDBFieldConsumer;
import com.github.fieldintercept.annotation.EnumFieldConsumer;
import com.github.fieldintercept.annotation.FieldConsumer;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.type.AnnotationMetadata;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class FieldInterceptBeanDefinitionRegistrar implements ImportBeanDefinitionRegistrar, BeanFactoryAware, EnvironmentAware {
    public static final String BEAN_NAME_RETURN_FIELD_DISPATCH_AOP = "returnFieldDispatchAop";
    public static final String BEAN_NAME_RETURN_FIELD_DISPATCH_AOP_BEAN_POST_PROCESSOR = "returnFieldDispatchAopBeanPostProcessor";

    public static final String BEAN_NAME_USED_KEY_INTERCEPT = FieldConsumer.NAME_USED_KEY;
    public static final String BEAN_NAME_ENUM_FIELD_INTERCEPT = EnumFieldConsumer.NAME;
    public static final String BEAN_NAME_ENUM_DB_FIELD_INTERCEPT = EnumDBFieldConsumer.NAME;

    protected boolean enabled = true;
    protected String[] beanBasePackages = {};
    protected boolean parallelQuery;
    protected int parallelQueryMaxThreads;
    protected long batchAggregationMilliseconds;
    protected int batchAggregationMinConcurrentCount;
    protected FieldinterceptProperties.BatchAggregationEnum batchAggregation;
    protected Class<? extends Annotation>[] myAnnotations = new Class[0];
    protected Class<? extends ReturnFieldDispatchAop> aopClass;
    protected ListableBeanFactory beanFactory;
    protected Environment environment;
    protected BeanDefinitionRegistry definitionRegistry;
    private final AtomicBoolean initPropertiesFlag = new AtomicBoolean();
    private Supplier<FieldinterceptProperties> propertiesSupplier;

    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry definitionRegistry) {
        if (beanFactory == null && definitionRegistry instanceof ListableBeanFactory) {
            beanFactory = (ListableBeanFactory) definitionRegistry;
        }
        this.definitionRegistry = definitionRegistry;
        Objects.requireNonNull(beanFactory);

        // 1.EnumFieldIntercept.class (if not exist)
        if (!beanFactory.containsBeanDefinition(BEAN_NAME_ENUM_FIELD_INTERCEPT)) {
            registerBeanDefinitionsEnumFieldIntercept();
        }
        // 2.ReturnKeyFieldIntercept.class (if not exist)
        if (!beanFactory.containsBeanDefinition(BEAN_NAME_USED_KEY_INTERCEPT)) {
            registerBeanDefinitionsReturnKeyFieldIntercept();
        }
        // 3.ReturnFieldDispatchAopBeanPostProcessor.class (if not exist)
        if (beanFactory.getBeanNamesForType(ReturnFieldDispatchAopBeanPostProcessor.class).length == 0) {
            registerBeanDefinitionsReturnFieldDispatchAopBeanPostProcessor();
        }
        // 4.ReturnFieldDispatchAop.class (if not exist)
        if (beanFactory.getBeanNamesForType(ReturnFieldDispatchAop.class).length == 0) {
            registerBeanDefinitionsReturnFieldDispatchAop();
        }
    }

    public FieldinterceptProperties getProperties() {
        if (propertiesSupplier == null) {
            return null;
        }
        initProperties();
        return propertiesSupplier.get();
    }

    private void initProperties() {
        if (initPropertiesFlag.compareAndSet(false, true)) {
            setMetadata(propertiesSupplier.get());
        }
    }

    protected <JOIN_POINT> void config(ReturnFieldDispatchAop<JOIN_POINT> aop) {
        initProperties();
        aop.setConsumerFactory(consumerFactory());
        aop.setBatchAggregation(ReturnFieldDispatchAop.BatchAggregationEnum.valueOf(batchAggregation.name()));
        aop.setBatchAggregationMilliseconds(batchAggregationMilliseconds);
        aop.setBatchAggregationMinConcurrentCount(batchAggregationMinConcurrentCount);
        // 注册判断是否是bean
        for (String beanBasePackage : beanBasePackages) {
            aop.addBeanPackagePaths(beanBasePackage);
        }
        // 注册自定义注解
        for (Class<? extends Annotation> myAnnotation : myAnnotations) {
            aop.getAnnotations().add(myAnnotation);
        }
        // 注册数据库枚举查询别名
        if (registryAlias(EnumDBFieldIntercept.class, BEAN_NAME_ENUM_DB_FIELD_INTERCEPT)) {
            aop.getAnnotations().add(EnumDBFieldConsumer.class);
        }
        if (aop.getConfigurableEnvironment() == null) {
            aop.setConfigurableEnvironment(configurableEnvironment());
        }
        if (aop.getSkipFieldClassPredicate() == ReturnFieldDispatchAop.DEFAULT_SKIP_FIELD_CLASS_PREDICATE) {
            aop.setSkipFieldClassPredicate(this::isSkipFieldClass);
        }

        TaskDecorator decorator = taskDecorator();
        if (decorator != null && aop.getTaskDecorate() != null) {
            aop.setTaskDecorate(decorator::decorate);
        }
        if (aop.getTaskExecutor() == null) {
            aop.setTaskExecutor(taskExecutorFunction());
        }
        if (!enabled) {
            aop.setEnabled((j, r) -> false);
        }
    }

    protected <JOIN_POINT> Function<String, BiConsumer<JOIN_POINT, List<CField>>> consumerFactory() {
        return new SpringConsumerFactory<>(beanFactory);
    }

    protected ConfigurableEnvironment configurableEnvironment() {
        if (environment instanceof ConfigurableEnvironment) {
            return (ConfigurableEnvironment) environment;
        } else {
            try {
                return beanFactory.getBean(ConfigurableEnvironment.class);
            } catch (Exception e) {
                return null;
            }
        }
    }

    protected Function<Runnable, Future> taskExecutorFunction() {
        if (parallelQuery) {
            ExecutorService taskExecutor = taskExecutor();
            return taskExecutor::submit;
        } else {
            return null;
        }
    }

    protected boolean isSkipFieldClass(Class<?> type) {
        return beanFactory.getBeanNamesForType(type, true, false).length > 0;
    }

    public void registerBeanDefinitionsReturnFieldDispatchAopBeanPostProcessor() {
        BeanDefinitionBuilder builder = BeanDefinitionBuilder
                .genericBeanDefinition(ReturnFieldDispatchAopBeanPostProcessor.class, () -> new ReturnFieldDispatchAopBeanPostProcessor(this::config));
        definitionRegistry.registerBeanDefinition(BEAN_NAME_RETURN_FIELD_DISPATCH_AOP_BEAN_POST_PROCESSOR, builder.getBeanDefinition());
    }

    public void registerBeanDefinitionsReturnFieldDispatchAop() {
        Class aopClass = environment.getProperty(FieldinterceptProperties.PREFIX + ".aopClass", Class.class, AspectjReturnFieldDispatchAop.class);

        BeanDefinitionBuilder builder = BeanDefinitionBuilder
                .genericBeanDefinition(aopClass, () -> {
                    this.initProperties();
                    return BeanUtils.instantiateClass(this.aopClass);
                });
        definitionRegistry.registerBeanDefinition(BEAN_NAME_RETURN_FIELD_DISPATCH_AOP, builder.getBeanDefinition());
    }

    public void registerBeanDefinitionsReturnKeyFieldIntercept() {
        BeanDefinitionBuilder builder = BeanDefinitionBuilder
                .genericBeanDefinition(UsedKeyFieldIntercept.class, UsedKeyFieldIntercept::new);
        definitionRegistry.registerBeanDefinition(BEAN_NAME_USED_KEY_INTERCEPT, builder.getBeanDefinition());
    }

    public void registerBeanDefinitionsEnumFieldIntercept() {
        BeanDefinitionBuilder builder = BeanDefinitionBuilder
                .genericBeanDefinition(EnumFieldIntercept.class, EnumFieldIntercept::new);
        definitionRegistry.registerBeanDefinition(BEAN_NAME_ENUM_FIELD_INTERCEPT, builder.getBeanDefinition());
    }

    protected TaskDecorator taskDecorator() {
        Map<String, TaskDecorator> decoratorMap = beanFactory.getBeansOfType(TaskDecorator.class);
        switch (decoratorMap.size()) {
            case 0: {
                return null;
            }
            case 1: {
                return decoratorMap.values().iterator().next();
            }
            default: {
                // need use @Primary
                return beanFactory.getBean(TaskDecorator.class);
            }
        }
    }

    protected ExecutorService taskExecutor() {
        return new ThreadPoolExecutor(0, parallelQueryMaxThreads,
                60L, TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                new ThreadFactory() {
                    private final ThreadGroup group = Thread.currentThread().getThreadGroup();
                    private final AtomicInteger threadNumber = new AtomicInteger(1);

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread thread = new Thread(group, r,
                                "FieldIntercept-" + threadNumber.getAndIncrement());
                        thread.setDaemon(true);
                        return thread;
                    }
                }, new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /**
     * 注册别名
     *
     * @param beanType bean类型
     * @param alias    起的新别名
     * @return 是否注册别名成功。true=成功
     */
    protected boolean registryAlias(Class beanType, String alias) {
        List<String> beanNames = Arrays.asList(beanFactory.getBeanNamesForType(beanType));
        if (beanNames.isEmpty() || beanNames.contains(alias) || definitionRegistry.containsBeanDefinition(alias)) {
            return false;
        } else {
            definitionRegistry.registerAlias(beanNames.get(beanNames.size() - 1), alias);
            return true;
        }
    }

    public void setMetadata(FieldinterceptProperties properties) {
        this.beanBasePackages = properties.getBeanBasePackages();
        this.parallelQuery = properties.isParallelQuery();
        this.parallelQueryMaxThreads = properties.getParallelQueryMaxThreads();
        this.myAnnotations = properties.getMyAnnotations();
        this.batchAggregationMilliseconds = properties.getBatchAggregationMilliseconds();
        this.batchAggregationMinConcurrentCount = properties.getBatchAggregationMinConcurrentCount();
        this.batchAggregation = properties.getBatchAggregation();
        this.aopClass = properties.getAopClass();
        this.enabled = properties.isEnabled();
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory instanceof ListableBeanFactory ? (ListableBeanFactory) beanFactory : null;
        this.propertiesSupplier = () -> beanFactory.getBean(FieldinterceptProperties.class);
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    /*
     * 返回key名称一致
     */
    private static class UsedKeyFieldIntercept extends KeyNameFieldIntercept<Object, Object> {
        @Override
        public Map<Object, String> selectNameMapByKeys(Collection<Object> keys) {
            return keys.stream()
                    .collect(Collectors.toMap(e -> e, e -> e == null ? "" : e.toString()));
        }
    }

    private static class SpringConsumerFactory<JOIN_POINT> implements Function<String, BiConsumer<JOIN_POINT, List<CField>>> {
        private final BeanFactory beanFactory;

        private SpringConsumerFactory(BeanFactory beanFactory) {
            this.beanFactory = beanFactory;
        }

        @Override
        public BiConsumer<JOIN_POINT, List<CField>> apply(String name) {
            return beanFactory.getBean(name, BiConsumer.class);
        }
    }

    public static class ReturnFieldDispatchAopBeanPostProcessor implements BeanPostProcessor {
        protected final Consumer<ReturnFieldDispatchAop<?>> consumer;

        ReturnFieldDispatchAopBeanPostProcessor(Consumer<ReturnFieldDispatchAop<?>> consumer) {
            this.consumer = consumer;
        }

        @Override
        public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
            if (bean instanceof ReturnFieldDispatchAop) {
                consumer.accept((ReturnFieldDispatchAop<?>) bean);
            }
            return bean;
        }
    }
}
