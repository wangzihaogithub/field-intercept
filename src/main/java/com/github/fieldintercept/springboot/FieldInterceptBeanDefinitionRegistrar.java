package com.github.fieldintercept.springboot;

import com.github.fieldintercept.EnumDBFieldIntercept;
import com.github.fieldintercept.EnumFieldIntercept;
import com.github.fieldintercept.ReturnFieldDispatchAop;
import com.github.fieldintercept.annotation.EnableFieldIntercept;
import com.github.fieldintercept.annotation.EnumDBFieldConsumer;
import com.github.fieldintercept.annotation.EnumFieldConsumer;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.type.AnnotationMetadata;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public class FieldInterceptBeanDefinitionRegistrar implements ImportBeanDefinitionRegistrar, BeanFactoryAware, EnvironmentAware {
    public static final String BEAN_NAME_RETURN_FIELD_DISPATCH_AOP = "returnFieldDispatchAop";
    public static final String BEAN_NAME_ENUM_FIELD_INTERCEPT = EnumFieldConsumer.NAME;
    public static final String BEAN_NAME_ENUM_DB_FIELD_INTERCEPT = EnumDBFieldConsumer.NAME;

    private String[] beanBasePackages = {};
    private boolean parallelQuery;
    private int parallelQueryMaxThreads;
    private long batchAggregationMilliseconds;
    private int batchAggregationMinConcurrentCount;
    private boolean batchAggregation;
    private Class<? extends Annotation>[] myAnnotations = new Class[0];
    private ListableBeanFactory beanFactory;
    private Environment environment;
    private BeanDefinitionRegistry definitionRegistry;

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry definitionRegistry) {
        if (beanFactory == null && definitionRegistry instanceof ListableBeanFactory) {
            beanFactory = (ListableBeanFactory) definitionRegistry;
        }
        this.definitionRegistry = definitionRegistry;
        Objects.requireNonNull(beanFactory);
        setImportMetadata(importingClassMetadata);

        // 1.EnumFieldIntercept.class (if not exist)
        if (!beanFactory.containsBeanDefinition(BEAN_NAME_ENUM_FIELD_INTERCEPT)) {
            registerBeanDefinitionsEnumFieldIntercept();
        }
        // 2.ReturnFieldDispatchAop.class (if not exist)
        if (beanFactory.getBeanNamesForType(ReturnFieldDispatchAop.class).length == 0) {
            registerBeanDefinitionsReturnFieldDispatchAop();
        }
    }

    public void registerBeanDefinitionsReturnFieldDispatchAop() {
        BeanDefinitionBuilder builder = BeanDefinitionBuilder
                .genericBeanDefinition(ReturnFieldDispatchAop.class, () -> {
                    ConfigurableEnvironment configurableEnvironment;
                    if (environment instanceof ConfigurableEnvironment) {
                        configurableEnvironment = (ConfigurableEnvironment) environment;
                    } else {
                        try {
                            configurableEnvironment = beanFactory.getBean(ConfigurableEnvironment.class);
                        } catch (Exception e) {
                            configurableEnvironment = null;
                        }
                    }

                    ReturnFieldDispatchAop dispatchAop = new ReturnFieldDispatchAop(s -> beanFactory.getBean(s, BiConsumer.class));
                    dispatchAop.setConfigurableEnvironment(configurableEnvironment);
                    dispatchAop.setSkipFieldClassPredicate(type -> beanFactory.getBeanNamesForType(type, true, false).length > 0);
                    dispatchAop.setBatchAggregation(batchAggregation);
                    dispatchAop.setBatchAggregationMilliseconds(batchAggregationMilliseconds);
                    dispatchAop.setBatchAggregationMinConcurrentCount(batchAggregationMinConcurrentCount);
                    if (parallelQuery) {
                        TaskDecorator decorator = taskDecorator();
                        ExecutorService taskExecutor = taskExecutor();
                        if (decorator != null) {
                            dispatchAop.setTaskExecutor(e -> taskExecutor.submit(decorator.decorate(e)));
                        } else {
                            dispatchAop.setTaskExecutor(taskExecutor::submit);
                        }
                    } else {
                        dispatchAop.setTaskExecutor(null);
                    }
                    // 注册判断是否是bean
                    for (String beanBasePackage : beanBasePackages) {
                        dispatchAop.addBeanPackagePaths(beanBasePackage);
                    }
                    // 注册自定义注解
                    for (Class<? extends Annotation> myAnnotation : myAnnotations) {
                        dispatchAop.getAnnotations().add(myAnnotation);
                    }
                    // 注册数据库枚举查询别名
                    if (registryAlias(EnumDBFieldIntercept.class, BEAN_NAME_ENUM_DB_FIELD_INTERCEPT)) {
                        dispatchAop.getAnnotations().add(EnumDBFieldConsumer.class);
                    }
                    return dispatchAop;
                });
        definitionRegistry.registerBeanDefinition(BEAN_NAME_RETURN_FIELD_DISPATCH_AOP, builder.getBeanDefinition());
    }

    private TaskDecorator taskDecorator() {
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

    public void registerBeanDefinitionsEnumFieldIntercept() {
        BeanDefinitionBuilder builder = BeanDefinitionBuilder
                .genericBeanDefinition(EnumFieldIntercept.class, EnumFieldIntercept::new);
        definitionRegistry.registerBeanDefinition(BEAN_NAME_ENUM_FIELD_INTERCEPT, builder.getBeanDefinition());
    }

    public ExecutorService taskExecutor() {
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
    public boolean registryAlias(Class beanType, String alias) {
        List<String> beanNames = Arrays.asList(beanFactory.getBeanNamesForType(beanType));
        if (beanNames.isEmpty() || beanNames.contains(alias) || definitionRegistry.containsBeanDefinition(alias)) {
            return false;
        } else {
            definitionRegistry.registerAlias(beanNames.get(beanNames.size() - 1), alias);
            return true;
        }
    }

    public void setImportMetadata(AnnotationMetadata metadata) {
        AnnotationAttributes attributes = AnnotationAttributes.fromMap(metadata.getAnnotationAttributes(EnableFieldIntercept.class.getName()));
        this.beanBasePackages = attributes.getStringArray("beanBasePackages");
        this.parallelQuery = attributes.getBoolean("parallelQuery");
        this.parallelQueryMaxThreads = attributes.getNumber("parallelQueryMaxThreads").intValue();
        this.myAnnotations = (Class<? extends Annotation>[]) attributes.getClassArray("myAnnotations");
        this.batchAggregationMilliseconds = attributes.getNumber("batchAggregationMilliseconds").longValue();
        this.batchAggregationMinConcurrentCount = attributes.getNumber("batchAggregationMinConcurrentCount").intValue();
        this.batchAggregation = attributes.getBoolean("batchAggregation");
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory instanceof ListableBeanFactory ? (ListableBeanFactory) beanFactory : null;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }
}
