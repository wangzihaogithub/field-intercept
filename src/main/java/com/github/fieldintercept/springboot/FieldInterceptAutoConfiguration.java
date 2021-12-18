package com.github.fieldintercept.springboot;

import com.github.fieldintercept.EnumFieldIntercept;
import com.github.fieldintercept.ReturnFieldDispatchAop;
import com.github.fieldintercept.annotation.EnableFieldIntercept;
import com.github.fieldintercept.annotation.EnumFieldConsumer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.type.AnnotationMetadata;

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

    @Bean("returnFieldDispatchAop")
    public ReturnFieldDispatchAop returnFieldDispatchAop(ApplicationContext context, ConfigurableEnvironment environment) {
        ExecutorService taskExecutor = taskExecutor();

        ReturnFieldDispatchAop dispatchAop = new ReturnFieldDispatchAop(s -> context.getBean(s, BiConsumer.class));
        dispatchAop.setSkipFieldClassPredicate(type -> context.getBeanNamesForType(type, true, false).length > 0);
        dispatchAop.setTaskExecutor(taskExecutor::submit);
        dispatchAop.setConfigurableEnvironment(environment);
        for (String beanBasePackage : beanBasePackages) {
            dispatchAop.addBeanPackagePaths(beanBasePackage);
        }
        return dispatchAop;
    }

    @Bean(EnumFieldConsumer.NAME)
    public EnumFieldIntercept enumFieldIntercept() {
        return new EnumFieldIntercept();
    }

    private ExecutorService taskExecutor() {
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
    }

}
