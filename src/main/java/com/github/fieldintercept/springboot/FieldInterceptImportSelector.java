package com.github.fieldintercept.springboot;

import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;

/**
 * 配置类
 *
 * @author hao 2021年12月12日19:00:05
 */
public class FieldInterceptImportSelector implements DeferredImportSelector, EnvironmentAware {
    private Environment environment;

    @Override
    public String[] selectImports(AnnotationMetadata metadata) {
        FieldinterceptProperties.RpcEnum rpcEnum = environment.getProperty(FieldinterceptProperties.PREFIX + ".rpc", FieldinterceptProperties.RpcEnum.class, FieldinterceptProperties.RpcEnum.disabled);
        switch (rpcEnum) {
            default:
            case disabled: {
                return new String[]{"com.github.fieldintercept.springboot.FieldInterceptBeanDefinitionRegistrar"};
            }
            case dubbo: {
                return new String[]{"com.github.fieldintercept.springboot.RpcDubboBeanDefinitionRegistrar"};
            }
        }
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

}
