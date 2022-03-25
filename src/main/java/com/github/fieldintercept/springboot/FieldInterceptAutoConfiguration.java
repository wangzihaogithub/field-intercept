package com.github.fieldintercept.springboot;

import com.github.fieldintercept.EnumDBFieldIntercept;
import com.github.fieldintercept.EnumFieldIntercept;
import com.github.fieldintercept.ReturnFieldDispatchAop;
import com.github.fieldintercept.annotation.EnableFieldIntercept;
import com.github.fieldintercept.annotation.EnumDBFieldConsumer;
import com.github.fieldintercept.annotation.EnumFieldConsumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.AliasRegistry;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.type.AnnotationMetadata;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * 配置类
 *
 * @author hao 2021年12月12日19:00:05
 */
public class FieldInterceptAutoConfiguration implements ImportAware {
    private String[] beanBasePackages = {};
    private boolean parallelQuery;
    private Class<? extends Annotation>[] myAnnotations = new Class[0];

    /**
     * 注册别名
     *
     * @param context  spring上下文
     * @param beanType bean类型
     * @param alias    起的新别名
     * @return 是否注册别名成功。true=成功
     */
    public static boolean registryAlias(ApplicationContext context, Class beanType, String alias) {
        if (context instanceof AliasRegistry) {
            AliasRegistry aliasRegistry = ((AliasRegistry) context);
            List<String> beanNames = Arrays.asList(context.getBeanNamesForType(beanType));
            if (beanNames.size() > 0) {
                if (!beanNames.contains(alias)) {
                    aliasRegistry.registerAlias(beanNames.get(beanNames.size() - 1), alias);
                }
                return true;
            }
        }
        return false;
    }

    @Bean("returnFieldDispatchAop")
    public ReturnFieldDispatchAop returnFieldDispatchAop(ApplicationContext context,
                                                         @Autowired(required = false) ConfigurableEnvironment environment) {
        ReturnFieldDispatchAop dispatchAop = new ReturnFieldDispatchAop(s -> context.getBean(s, BiConsumer.class));
        dispatchAop.setConfigurableEnvironment(environment);
        dispatchAop.setSkipFieldClassPredicate(type -> context.getBeanNamesForType(type, true, false).length > 0);
        if (parallelQuery) {
            ExecutorService taskExecutor = taskExecutor();
            dispatchAop.setTaskExecutor(taskExecutor::submit);
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
        if (registryAlias(context, EnumDBFieldIntercept.class, EnumDBFieldConsumer.NAME)) {
            dispatchAop.getAnnotations().add(EnumDBFieldConsumer.class);
        }
        return dispatchAop;
    }

    @Bean(EnumFieldConsumer.NAME)
    @ConditionalOnMissingBean(name = EnumFieldConsumer.NAME)
    public EnumFieldIntercept enumFieldIntercept() {
        return new EnumFieldIntercept();
    }

    public ExecutorService taskExecutor() {
        return new ThreadPoolExecutor(0, Math.max(8, Runtime.getRuntime().availableProcessors() * 4),
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(20),
                new ThreadFactory() {
                    private final ThreadGroup group = Thread.currentThread().getThreadGroup();
                    private final AtomicInteger threadNumber = new AtomicInteger(1);

                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(group, r,
                                "FieldIntercept-" + threadNumber.getAndIncrement());
                    }
                }, new ThreadPoolExecutor.CallerRunsPolicy());
    }

    @Override
    public void setImportMetadata(AnnotationMetadata metadata) {
        AnnotationAttributes attributes = AnnotationAttributes.fromMap(metadata.getAnnotationAttributes(EnableFieldIntercept.class.getName()));
        this.beanBasePackages = attributes.getStringArray("beanBasePackages");
        this.parallelQuery = attributes.getBoolean("parallelQuery");
        this.myAnnotations = (Class<? extends Annotation>[]) attributes.getClassArray("myAnnotations");
    }

}
