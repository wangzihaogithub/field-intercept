package com.github.fieldintercept.springboot;

import com.github.fieldintercept.*;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.config.spring.ServiceBean;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.cluster.LoadBalance;
import org.apache.dubbo.rpc.cluster.loadbalance.ShortestResponseLoadBalance;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.type.AnnotationMetadata;

import java.beans.Introspector;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class RpcDubboBeanDefinitionRegistrar extends FieldInterceptBeanDefinitionRegistrar {
    public static final String BEAN_NAME_DUBBO_SERVICE_CONFIG = "RpcDubboBeanDefinitionRegistrar$DubboServiceConfig";
    public static final String LOCAL_ID = UUID.randomUUID().toString().replace("-", "");
    public static final String NAME_LOCAL_ID = "flocalid";

    public interface Api {
        Map<Object, Object> selectNameMapByKeys(String beanName, Collection<Object> keys);

        Map<Object, Object> selectValueMapByKeys(String beanName, Collection<Object> keys);
    }

    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry definitionRegistry) {
        super.registerBeanDefinitions(metadata, definitionRegistry);

        definitionRegistry.registerBeanDefinition(BEAN_NAME_DUBBO_SERVICE_CONFIG,
                BeanDefinitionBuilder.genericBeanDefinition(DubboServiceConfig.class,
                        () -> new DubboServiceConfig(definitionRegistry, beanFactory, getProperties())).getBeanDefinition());
    }

    @Override
    protected <JOIN_POINT> Function<String, BiConsumer<JOIN_POINT, List<CField>>> consumerFactory() {
        Function<String, BiConsumer<JOIN_POINT, List<CField>>> parent = super.consumerFactory();
        return new DubboReferenceFactory<>(parent, this::getProperties);
    }

    private static void export(ServiceConfig<Api> serviceConfig) {
        serviceConfig.export();
    }

    private static ServiceConfig<Api> addService(Api api, FieldinterceptProperties.Dubbo dubbo) {
        ServiceConfig<Api> config = buildService(api, dubbo);
        ApplicationModel.getConfigManager().addService(config);
        return config;
    }

    private static ReferenceConfig<Api> addReference(FieldinterceptProperties.Dubbo dubbo) {
        ReferenceConfig<Api> reference = buildReference(dubbo);
        ApplicationModel.getConfigManager().addReference(reference);
        return reference;
    }

    private static ServiceConfig<Api> buildService(Api api, FieldinterceptProperties.Dubbo dubbo) {
        ServiceConfig<Api> config = new ServiceConfig<>();
        config.setRef(api);
        config.setInterface(Api.class);
        config.setScope("remote");
        if (dubbo.getRegistry() != null) {
            config.setRegistryIds(String.join(",", dubbo.getRegistry()));
        }
        if (dubbo.getTimeout() != null) {
            config.setTimeout(dubbo.getTimeout());
        }
        if (dubbo.getGroup() != null) {
            config.setGroup(dubbo.getGroup());
        }
        if (dubbo.getVersion() != null) {
            config.setVersion(dubbo.getVersion());
        }
        if (dubbo.getFilter() != null) {
            config.setFilter(String.join(",", dubbo.getFilter()));
        }
        Map<String, String> userParameters = dubbo.getParameters();
        Map<String, String> parameters = new LinkedHashMap<>();
        if (userParameters != null && !userParameters.isEmpty()) {
            parameters.putAll(userParameters);
        }
        parameters.put(NAME_LOCAL_ID, LOCAL_ID);
        config.setParameters(parameters);

        if (dubbo.getRetries() != null) {
            config.setRetries(dubbo.getRetries());
        }
        if (dubbo.getConnections() != null) {
            config.setConnections(dubbo.getConnections());
        }
        config.setId(id("service", dubbo.getVersion()));
        return config;
    }

    private static ReferenceConfig<Api> buildReference(FieldinterceptProperties.Dubbo dubbo) {
        // reference
        ReferenceConfig<Api> reference = new ReferenceConfig<>();
        reference.setScope("remote");
        if (dubbo.getTimeout() != null) {
            reference.setTimeout(dubbo.getTimeout());
        }
        if (dubbo.getGroup() != null) {
            reference.setGroup(dubbo.getGroup());
        }
        if (dubbo.getRegistry() != null) {
            reference.setRegistryIds(String.join(",", dubbo.getRegistry()));
        }
        if (dubbo.getVersion() != null) {
            reference.setVersion(dubbo.getVersion());
        }
        if (dubbo.getFilter() != null) {
            reference.setFilter(String.join(",", dubbo.getFilter()));
        }
        Map<String, String> parameters = dubbo.getParameters();
        if (parameters != null && !parameters.isEmpty()) {
            reference.setParameters(parameters);
        }
        if (dubbo.getRetries() != null) {
            reference.setRetries(dubbo.getRetries());
        }
        if (dubbo.getConnections() != null) {
            reference.setConnections(dubbo.getConnections());
        }
        reference.setInterface(Api.class);
        reference.setId(id("reference", dubbo.getVersion()));

        String ignoreLocalFieldIntercept = "ignoreLocalFieldIntercept";
        reference.setLoadbalance(ignoreLocalFieldIntercept);
        ExtensionLoader.getExtensionLoader(LoadBalance.class).addExtension(ignoreLocalFieldIntercept, IgnoreLocalFieldInterceptLoadBalance.class);
        return reference;
    }

    private static String id(String name, String version) {
        String id;
        if (version == null || version.isEmpty()) {
            id = name;
        } else {
            id = name + "v" + version;
        }
        return "FieldIntercept#" + id;
    }

    protected static class DubboServiceConfig {
        private final BeanDefinitionRegistry registry;
        private final ListableBeanFactory beanFactory;
        private final FieldinterceptProperties properties;
        private final ApiImpl api = new ApiImpl();
        private ServiceConfig<Api> serviceConfig;

        public DubboServiceConfig(BeanDefinitionRegistry registry, ListableBeanFactory beanFactory, FieldinterceptProperties properties) {
            this.registry = registry;
            this.beanFactory = beanFactory;
            this.properties = properties;
        }

        @Bean
        public CommandLineRunner exportDubboOnStartup() {
            return args -> {
                if (serviceConfig != null) {
                    export(serviceConfig);
                }
            };
        }

        @Bean
        public FieldInterceptBeanPostProcessor compositeFieldInterceptBeanPostProcessor() {
            return new FieldInterceptBeanPostProcessor();
        }

        private static class InterceptVO {
            final ReturnFieldDispatchAop.SelectMethodHolder intercept;
            final ServiceBean<ReturnFieldDispatchAop.SelectMethodHolder> serviceBean;

            private InterceptVO(ReturnFieldDispatchAop.SelectMethodHolder intercept, ServiceBean<ReturnFieldDispatchAop.SelectMethodHolder> serviceBean) {
                this.intercept = intercept;
                this.serviceBean = serviceBean;
            }
        }

        private class FieldInterceptBeanPostProcessor implements BeanPostProcessor {

            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
                InterceptVO interceptVO = getInterceptVO(bean);
                if (interceptVO != null) {
                    if (serviceConfig == null) {
                        serviceConfig = addService(api, properties.getDubbo());
                        registry.registerBeanDefinition("fieldInterceptServiceConfig",
                                BeanDefinitionBuilder.genericBeanDefinition(ServiceConfig.class, () -> serviceConfig).getBeanDefinition());
                    }
                    String interceptBeanName = getFieldInterceptBeanName(interceptVO.intercept.getClass());
                    api.put(interceptBeanName, interceptVO.intercept);
                }
                return bean;
            }

            private InterceptVO getInterceptVO(Object bean) {
                if (bean instanceof ServiceBean && ((ServiceBean<?>) bean).getRef() instanceof ReturnFieldDispatchAop.SelectMethodHolder) {
                    return new InterceptVO((ReturnFieldDispatchAop.SelectMethodHolder) ((ServiceBean<?>) bean).getRef(), (ServiceBean<ReturnFieldDispatchAop.SelectMethodHolder>) bean);
                } else if (bean instanceof ReturnFieldDispatchAop.SelectMethodHolder) {
                    return new InterceptVO((ReturnFieldDispatchAop.SelectMethodHolder) bean, null);
                } else {
                    return null;
                }
            }

            private String getFieldInterceptBeanName(Class<?> type) {
                String[] names = beanFactory.getBeanNamesForType(type);
                if (names.length > 0) {
                    return names[0];
                } else {
                    return Introspector.decapitalize(type.getSimpleName());
                }
            }
        }
    }

    private static class ApiImpl implements Api {
        private final Map<String, ReturnFieldDispatchAop.SelectMethodHolder> interceptMap = new ConcurrentHashMap<>();
        private final Map<String, Service> serviceMap = new ConcurrentHashMap<>();

        private Service getService(String beanName) {
            return serviceMap.computeIfAbsent(beanName, e -> {
                ReturnFieldDispatchAop.SelectMethodHolder intercept = interceptMap.get(beanName);
                if (intercept == null) {
                    return new Service(e, null);
                } else {
                    return new Service(e, intercept);
                }
            });
        }

        private ReturnFieldDispatchAop.SelectMethodHolder put(String beanName, ReturnFieldDispatchAop.SelectMethodHolder intercept) {
            return interceptMap.put(beanName, intercept);
        }

        @Override
        public Map<Object, Object> selectNameMapByKeys(String beanName, Collection<Object> keys) {
            Service service = getService(beanName);
            if (service == null) {
                return Collections.emptyMap();
            } else {
                return service.selectNameMapByKeys(keys);
            }
        }

        @Override
        public Map<Object, Object> selectValueMapByKeys(String beanName, Collection<Object> keys) {
            Service service = getService(beanName);
            if (service == null) {
                return Collections.emptyMap();
            } else {
                return service.selectValueMapByKeys(keys);
            }
        }

        @Override
        public String toString() {
            return "DubboProviderGenericService" + serviceMap;
        }

        private static class Service {
            private final String beanName;
            private final ReturnFieldDispatchAop.SelectMethodHolder intercept;
            private final AtomicBoolean bindMethodFlag = new AtomicBoolean();
            private volatile Function<Collection, Map<Object, Object>> selectNameMapByKeys;
            private volatile Function<Collection, Map<Object, Object>> selectValueMapByKeys;

            private Service(String beanName, ReturnFieldDispatchAop.SelectMethodHolder intercept) {
                this.beanName = beanName;
                this.intercept = intercept;
            }

            private static Function<Collection, Map<Object, Object>> bindMethod(KeyNameFieldIntercept intercept) {
                Function<Collection, Map<Object, Object>> method = intercept.getSelectNameMapByKeys();
                if (method != null) {
                    return method;
                } else {
                    return arg0 -> intercept.selectNameMapByKeys(arg0);
                }
            }

            private static Function<Collection, Map<Object, Object>> bindMethod(KeyValueFieldIntercept intercept) {
                Function<Collection, Map<Object, Object>> method = intercept.getSelectValueMapByKeys();
                if (method != null) {
                    return method;
                } else {
                    return arg0 -> intercept.selectValueMapByKeys(arg0);
                }
            }

            private void bindMethod(Object intercept) {
                if (intercept instanceof CompositeFieldIntercept) {
                    CompositeFieldIntercept compositeFieldIntercept = (CompositeFieldIntercept) intercept;
                    selectNameMapByKeys = bindMethod(compositeFieldIntercept.keyNameFieldIntercept());
                    selectValueMapByKeys = bindMethod(compositeFieldIntercept.keyValueFieldIntercept());
                } else if (intercept instanceof KeyNameFieldIntercept) {
                    selectNameMapByKeys = bindMethod((KeyNameFieldIntercept) intercept);
                } else if (intercept instanceof KeyValueFieldIntercept) {
                    selectValueMapByKeys = bindMethod((KeyValueFieldIntercept) intercept);
                }
            }

            public Map<Object, Object> selectNameMapByKeys(Collection<Object> keys) {
                if (bindMethodFlag.compareAndSet(false, true)) {
                    bindMethod(intercept);
                }
                if (selectNameMapByKeys == null) {
                    return Collections.emptyMap();
                } else {
                    return selectNameMapByKeys.apply(keys);
                }
            }

            public Map<Object, Object> selectValueMapByKeys(Collection<Object> keys) {
                if (bindMethodFlag.compareAndSet(false, true)) {
                    bindMethod(intercept);
                }
                if (selectValueMapByKeys == null) {
                    return Collections.emptyMap();
                } else {
                    return selectValueMapByKeys.apply(keys);
                }
            }

            @Override
            public String toString() {
                return "DubboProviderGenericService{" +
                        beanName +
                        '}';
            }
        }
    }

    private static class DubboReferenceFactory<JOIN_POINT> implements Function<String, BiConsumer<JOIN_POINT, List<CField>>> {
        private final Function<String, BiConsumer<JOIN_POINT, List<CField>>> parent;
        private final Map<String, BiConsumer<JOIN_POINT, List<CField>>> beanMap = new ConcurrentHashMap<>();
        private final Supplier<FieldinterceptProperties> properties;
        private volatile ReferenceConfig<Api> reference;

        private DubboReferenceFactory(Function<String, BiConsumer<JOIN_POINT, List<CField>>> parent, Supplier<FieldinterceptProperties> properties) {
            this.parent = parent;
            this.properties = properties;
        }

        @Override
        public BiConsumer<JOIN_POINT, List<CField>> apply(String name) {
            BiConsumer<JOIN_POINT, List<CField>> result = beanMap.get(name);
            if (result == null) {
                try {
                    result = parent.apply(name);
                } catch (NoSuchBeanDefinitionException ignored) {

                }
            }
            if (result == null) {
                if (reference == null) {
                    synchronized (this) {
                        if (reference == null) {
                            reference = addReference(properties.get().getDubbo());
                        }
                    }
                }
                result = new DubboCompositeFieldIntercept<>(name, reference);
                beanMap.put(name, result);
            }
            return result;
        }

        private static class DubboCompositeFieldIntercept<JOIN_POINT> implements CompositeFieldIntercept<Object, Object, JOIN_POINT> {
            private final String beanName;
            private final ReferenceConfig<Api> reference;
            private final KeyNameFieldIntercept<Object, JOIN_POINT> keyNameFieldIntercept = new KeyNameFieldIntercept<>(Object.class, this::selectNameMapByKeys, 0);
            private final KeyValueFieldIntercept<Object, Object, JOIN_POINT> keyValueFieldIntercept = new KeyValueFieldIntercept<>(Object.class, Object.class, this::selectValueMapByKeys, 0);

            private DubboCompositeFieldIntercept(String beanName, ReferenceConfig<Api> reference) {
                this.beanName = beanName;
                this.reference = reference;
            }

            public Map<Object, Object> selectNameMapByKeys(Collection<Object> keys) {
                Api api = reference.get();
                return api.selectNameMapByKeys(beanName, keys);
            }

            public Map<Object, Object> selectValueMapByKeys(Collection<Object> keys) {
                Api api = reference.get();
                return api.selectValueMapByKeys(beanName, keys);
            }

            @Override
            public KeyNameFieldIntercept<Object, JOIN_POINT> keyNameFieldIntercept() {
                return keyNameFieldIntercept;
            }

            @Override
            public KeyValueFieldIntercept<Object, Object, JOIN_POINT> keyValueFieldIntercept() {
                return keyValueFieldIntercept;
            }

            @Override
            public String toString() {
                return "DubboCompositeFieldIntercept{" +
                        beanName +
                        '}';
            }
        }

        @Override
        public String toString() {
            return "DubboReferenceFactory" + beanMap;
        }
    }

    public static class IgnoreLocalFieldInterceptLoadBalance implements LoadBalance {
        private final ShortestResponseLoadBalance loadBalance = new ShortestResponseLoadBalance();

        private static boolean isEmpty(String str) {
            return str == null || str.isEmpty();
        }

        private static boolean isInJvm(URL url) {
            return Objects.equals(url.getParameter(NAME_LOCAL_ID), LOCAL_ID);
        }

        @Override
        public <T> Invoker<T> select(List<Invoker<T>> invokers, URL url, Invocation invocation) throws RpcException {
            if (invokers == null || invokers.isEmpty()) {
                return null;
            }

            String localApplication = url.getParameter("application");
            List<Invoker<T>> selectList = new ArrayList<>(invokers.size());
            if (isEmpty(localApplication)) {
                for (Invoker<T> invoker : invokers) {
                    if (!isInJvm(invoker.getUrl())) {
                        selectList.add(invoker);
                    }
                }
            } else {
                for (Invoker<T> invoker : invokers) {
                    URL invokerUrl = invoker.getUrl();
                    if (isInJvm(invokerUrl)) {
                        continue;
                    }
                    String remoteApplication = invokerUrl.getParameter("remote.application");
                    if (!Objects.equals(remoteApplication, localApplication)) {
                        selectList.add(invoker);
                    }
                }
            }
            int size = selectList.size();
            if (size == 0) {
                return loadBalance.select(invokers, url, invocation);
            } else if (size == 1) {
                return selectList.get(0);
            } else {
                return loadBalance.select(selectList, url, invocation);
            }
        }
    }

}
