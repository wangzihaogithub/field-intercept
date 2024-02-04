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
        boolean enabledCluster = environment.getProperty(FieldinterceptProperties.PREFIX + ".cluster.enabled", boolean.class, false);
        if (enabledCluster) {
            FieldinterceptProperties.ClusterRpcEnum clusterEnum = environment.getProperty(FieldinterceptProperties.PREFIX + ".cluster.rpc", FieldinterceptProperties.ClusterRpcEnum.class, FieldinterceptProperties.ClusterRpcEnum.dubbo);
            switch (clusterEnum) {
                default:
                case dubbo: {
                    return new String[]{"com.github.fieldintercept.springboot.DubboBeanDefinitionRegistrar"};
                }
            }
        } else {
            return new String[]{"com.github.fieldintercept.springboot.FieldInterceptBeanDefinitionRegistrar"};
        }
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

}
