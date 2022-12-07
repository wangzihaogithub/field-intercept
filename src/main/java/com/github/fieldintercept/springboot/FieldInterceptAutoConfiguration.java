package com.github.fieldintercept.springboot;

import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.core.type.AnnotationMetadata;

/**
 * 配置类
 *
 * @author hao 2021年12月12日19:00:05
 */
public class FieldInterceptAutoConfiguration implements DeferredImportSelector {

    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        return new String[]{"com.github.fieldintercept.springboot.FieldInterceptBeanDefinitionRegistrar"};
    }
}
