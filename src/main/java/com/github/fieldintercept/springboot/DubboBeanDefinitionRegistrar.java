package com.github.fieldintercept.springboot;

import com.github.fieldintercept.*;
import com.github.fieldintercept.annotation.ServiceOptions;
import com.github.fieldintercept.util.AnnotationUtil;
import com.github.fieldintercept.util.PlatformDependentUtil;
import com.github.fieldintercept.util.SnapshotCompletableFuture;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.apache.dubbo.config.spring.ServiceBean;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.cluster.LoadBalance;
import org.apache.dubbo.rpc.cluster.loadbalance.ShortestResponseLoadBalance;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class DubboBeanDefinitionRegistrar extends FieldInterceptBeanDefinitionRegistrar {
    public static final String LOCAL_ID = UUID.randomUUID().toString().replace("-", "");
    public static final String NAME_LOCAL_ID = "flocalid";

    private static void export(ServiceConfig<Api> serviceConfig) {
        serviceConfig.export();
    }

    private static ServiceConfig<Api> addService(Api api, FieldinterceptProperties.Dubbo dubbo) {
        ServiceConfig<Api> config = buildService(api, dubbo);
        DubboBootstrap.getInstance().service(config);
        return config;
    }

    private static ReferenceConfig<Api> addReference(FieldinterceptProperties.Dubbo dubbo) {
        ReferenceConfig<Api> reference = buildReference(dubbo);
        DubboBootstrap.getInstance().reference(reference);
        return reference;
    }

    private static ServiceConfig<Api> buildService(Api api, FieldinterceptProperties.Dubbo dubbo) {
        ServiceConfig<Api> config = new ServiceConfig<>();
        config.setExport(true);
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
        reference.setCheck(dubbo.isCheck());
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
        if (dubbo.getAsync() != null) {
            reference.setAsync(dubbo.getAsync());
        }

        String ignoreLocalFieldIntercept = "ignoreLocalFieldIntercept";
        reference.setLoadbalance(ignoreLocalFieldIntercept);
        ExtensionLoader.getExtensionLoader(LoadBalance.class).addExtension(ignoreLocalFieldIntercept, DubboServiceConfig.IgnoreLocalFieldInterceptLoadBalance.class);
        return reference;
    }

    private static String id(String name, String version) {
        return "FieldIntercept#" + (version == null || version.isEmpty() ? name : name + "v" + version);
    }

    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry definitionRegistry) {
        super.registerBeanDefinitions(metadata, definitionRegistry);

        FieldinterceptProperties.ClusterRoleEnum roleEnum = environment.getProperty(FieldinterceptProperties.PREFIX + ".cluster.role", FieldinterceptProperties.ClusterRoleEnum.class, FieldinterceptProperties.ClusterRoleEnum.all);
        switch (roleEnum) {
            case provider:
            case all: {
                definitionRegistry.registerBeanDefinition("dubboBeanDefinitionRegistrar$DubboServiceConfig",
                        BeanDefinitionBuilder.genericBeanDefinition(DubboServiceConfig.class,
                                () -> new DubboServiceConfig(definitionRegistry, beanFactory, getProperties())).getBeanDefinition());
                break;
            }
            default: {
                break;
            }
        }
    }

    @Override
    protected <JOIN_POINT> Function<String, BiConsumer<JOIN_POINT, List<CField>>> consumerFactory() {
        Function<String, BiConsumer<JOIN_POINT, List<CField>>> parent = super.consumerFactory();
        FieldinterceptProperties.ClusterRoleEnum roleEnum = environment.getProperty(FieldinterceptProperties.PREFIX + ".cluster.role", FieldinterceptProperties.ClusterRoleEnum.class, FieldinterceptProperties.ClusterRoleEnum.all);
        switch (roleEnum) {
            case consumer:
            case all: {
                return new DubboReferenceFactory<>(parent, getEnumDBFieldInterceptNames(), this::getProperties);
            }
            default: {
                return parent;
            }
        }
    }

    public interface Api {
        @Deprecated
        Map<Object, Object> selectNameMapByKeys(String beanName, Collection<Object> keys);

        @Deprecated
        Map<Object, Object> selectValueMapByKeys(String beanName, Collection<Object> keys);

        Map<Object, Object> selectSerializeNameMapByKeys(String beanName, List<CField.SerializeCField> fields, Collection<Object> keys);

        Map<Object, Object> selectSerializeValueMapByKeys(String beanName, List<CField.SerializeCField> fields, Collection<Object> keys);

        Map<String, Map<String, Object>> selectEnumGroupKeyValueMap(String beanName, List<CField.SerializeCField> fields, Set<String> groups, Collection<Object> keys);
    }

    public static class DubboServiceConfig {
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
                InterceptVO.CACHE_MAP = null;
            };
        }

        @Bean
        public FieldInterceptBeanPostProcessor compositeFieldInterceptBeanPostProcessor() {
            return new FieldInterceptBeanPostProcessor();
        }

        private static class InterceptVO {
            private static Map<Class<?>, Boolean> CACHE_MAP = new ConcurrentHashMap<>();

            private final ReturnFieldDispatchAop.SelectMethodHolder intercept;
            private final ServiceBean<ReturnFieldDispatchAop.SelectMethodHolder> serviceBean;
            private final String beanName;
            private final ServiceOptions options;

            private InterceptVO(ReturnFieldDispatchAop.SelectMethodHolder intercept, ServiceBean<ReturnFieldDispatchAop.SelectMethodHolder> serviceBean, String beanName) {
                this.intercept = intercept;
                this.serviceBean = serviceBean;
                this.beanName = beanName;
                this.options = AnnotationUtil.cast(AnnotationUtil.findExtendsAnnotation(intercept.getClass().getDeclaredAnnotations(), Arrays.asList(ServiceOptions.class, ServiceOptions.Extends.class), CACHE_MAP), ServiceOptions.class);
            }

            private boolean isRpc() {
                return options == null || options.rpc();
            }
        }

        private static class ApiImpl implements Api {
            private final Map<String, ReturnFieldDispatchAop.SelectMethodHolder> interceptMap = new ConcurrentHashMap<>();
            private final Map<String, Service> serviceMap = new ConcurrentHashMap<>();
            private final Map<String, ServiceBean<ReturnFieldDispatchAop.SelectMethodHolder>> interceptServiceBeanMap = new ConcurrentHashMap<>();

            private Service getService(String beanName) {
                return serviceMap.computeIfAbsent(beanName, e -> {
                    ReturnFieldDispatchAop.SelectMethodHolder intercept = interceptMap.get(beanName);
                    if (intercept == null) {
                        intercept = interceptMap.get(ReturnFieldDispatchAop.getBeanName(beanName));
                    }
                    return new Service(e, intercept);
                });
            }

            private ReturnFieldDispatchAop.SelectMethodHolder put(String beanName, ReturnFieldDispatchAop.SelectMethodHolder intercept, ServiceBean<ReturnFieldDispatchAop.SelectMethodHolder> serviceBean) {
                String beanName0 = ReturnFieldDispatchAop.getBeanName(beanName);
                if (!Objects.equals(beanName, beanName0)) {
                    if (serviceBean != null) {
                        interceptServiceBeanMap.put(beanName0, serviceBean);
                    }
                    interceptMap.put(beanName0, intercept);
                }
                if (serviceBean != null) {
                    interceptServiceBeanMap.put(beanName, serviceBean);
                }
                return interceptMap.put(beanName, intercept);
            }

            @Override
            public Map<Object, Object> selectSerializeNameMapByKeys(String beanName, List<CField.SerializeCField> fields, Collection<Object> keys) {
                Service service = getService(beanName);
                if (service == null) {
                    return Collections.emptyMap();
                } else {
                    return service.selectNameMapByKeys(keys, fields);
                }
            }

            @Override
            public Map<Object, Object> selectSerializeValueMapByKeys(String beanName, List<CField.SerializeCField> fields, Collection<Object> keys) {
                Service service = getService(beanName);
                if (service == null) {
                    return Collections.emptyMap();
                } else {
                    return service.selectValueMapByKeys(keys, fields);
                }
            }

            @Override
            public Map<String, Map<String, Object>> selectEnumGroupKeyValueMap(String beanName, List<CField.SerializeCField> fields, Set<String> groups, Collection<Object> keys) {
                Service service = getService(beanName);
                if (service == null) {
                    return Collections.emptyMap();
                } else {
                    return service.selectEnumGroupKeyValueMap(groups, keys, fields);
                }
            }

            @Override
            public Map<Object, Object> selectNameMapByKeys(String beanName, Collection<Object> keys) {
                Service service = getService(beanName);
                if (service == null) {
                    return Collections.emptyMap();
                } else {
                    return service.selectNameMapByKeys(keys, Collections.emptyList());
                }
            }

            @Override
            public Map<Object, Object> selectValueMapByKeys(String beanName, Collection<Object> keys) {
                Service service = getService(beanName);
                if (service == null) {
                    return Collections.emptyMap();
                } else {
                    return service.selectValueMapByKeys(keys, Collections.emptyList());
                }
            }

            @Override
            public String toString() {
                return "DubboBeanDefinitionRegistrar$Api" + serviceMap;
            }

            private static class Service {
                private final String beanName;
                private final ReturnFieldDispatchAop.SelectMethodHolder intercept;
                private final AtomicBoolean bindMethodFlag = new AtomicBoolean();
                private volatile BiFunction<List<CField.SerializeCField>, Collection, Map> selectNameMapByKeys;
                private volatile BiFunction<List<CField.SerializeCField>, Collection, Map> selectValueMapByKeys;
                private volatile EnumDBFieldIntercept enumDBFieldIntercept;

                private Service(String beanName, ReturnFieldDispatchAop.SelectMethodHolder intercept) {
                    this.beanName = beanName;
                    this.intercept = intercept;
                }

                private static BiFunction<List<CField.SerializeCField>, Collection, Map> bindMethod(KeyNameFieldIntercept intercept) {
                    Function<Collection, Map> method = intercept.getSelectNameMapByKeys();
                    return method != null ? (fields, collection) -> method.apply(collection) : intercept::selectSerializeNameMapByKeys;
                }

                private static BiFunction<List<CField.SerializeCField>, Collection, Map> bindMethod(KeyValueFieldIntercept intercept) {
                    Function<Collection, Map> method = intercept.getSelectValueMapByKeys();
                    return method != null ? (fields, collection) -> method.apply(collection) : intercept::selectSerializeValueMapByKeys;
                }

                private void bindMethod(Object intercept) {
                    if (intercept instanceof CompositeFieldIntercept) {
                        CompositeFieldIntercept cast = (CompositeFieldIntercept) intercept;
                        selectNameMapByKeys = bindMethod(cast.keyNameFieldIntercept());
                        selectValueMapByKeys = bindMethod(cast.keyValueFieldIntercept());
                    } else if (intercept instanceof EnumFieldIntercept) {
                        EnumFieldIntercept cast = (EnumFieldIntercept) intercept;
                        selectValueMapByKeys = selectNameMapByKeys = bindMethod(cast);
                    } else if (intercept instanceof EnumDBFieldIntercept) {
                        EnumDBFieldIntercept cast = (EnumDBFieldIntercept) intercept;
                        selectValueMapByKeys = selectNameMapByKeys = bindMethod(cast);
                        enumDBFieldIntercept = cast;
                    } else if (intercept instanceof KeyNameFieldIntercept) {
                        selectNameMapByKeys = bindMethod((KeyNameFieldIntercept) intercept);
                    } else if (intercept instanceof KeyValueFieldIntercept) {
                        selectValueMapByKeys = bindMethod((KeyValueFieldIntercept) intercept);
                    }
                }

                public Map<Object, Object> selectNameMapByKeys(Collection<Object> keys, List<CField.SerializeCField> fields) {
                    if (bindMethodFlag.compareAndSet(false, true)) {
                        bindMethod(intercept);
                    }
                    if (selectNameMapByKeys == null) {
                        return Collections.emptyMap();
                    } else {
                        return selectNameMapByKeys.apply(fields, keys);
                    }
                }

                public Map<Object, Object> selectValueMapByKeys(Collection<Object> keys, List<CField.SerializeCField> fields) {
                    if (bindMethodFlag.compareAndSet(false, true)) {
                        bindMethod(intercept);
                    }
                    if (selectValueMapByKeys == null) {
                        return Collections.emptyMap();
                    } else {
                        return selectValueMapByKeys.apply(fields, keys);
                    }
                }

                public Map<String, Map<String, Object>> selectEnumGroupKeyValueMap(Set<String> groups, Collection<Object> keys, List<CField.SerializeCField> fields) {
                    if (bindMethodFlag.compareAndSet(false, true)) {
                        bindMethod(intercept);
                    }
                    if (enumDBFieldIntercept == null) {
                        return Collections.emptyMap();
                    } else {
                        return enumDBFieldIntercept.selectSerializeEnumGroupKeyValueMap(fields, groups, keys);
                    }
                }

                @Override
                public String toString() {
                    return "DubboService{" + beanName + '}';
                }
            }
        }

        /**
         * 忽略本地调用
         */
        public static class IgnoreLocalFieldInterceptLoadBalance implements LoadBalance {
            private final ShortestResponseLoadBalance loadBalance = new ShortestResponseLoadBalance();

            private static <T> List<Invoker<T>> selectList(List<Invoker<T>> invokers, String localApplication) {
                List<Invoker<T>> list = new ArrayList<>(invokers.size());
                if (isEmpty(localApplication)) {
                    for (Invoker<T> invoker : invokers) {
                        if (!isInJvm(invoker.getUrl())) {
                            list.add(invoker);
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
                            list.add(invoker);
                        }
                    }
                }
                return list;
            }

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
                List<Invoker<T>> selectList = selectList(invokers, url.getParameter("application"));
                int size = selectList.size();
                switch (size) {
                    case 0: {
                        return loadBalance.select(invokers, url, invocation);
                    }
                    case 1: {
                        return selectList.get(0);
                    }
                    default: {
                        return loadBalance.select(selectList, url, invocation);
                    }
                }
            }
        }

        private class FieldInterceptBeanPostProcessor implements BeanPostProcessor {

            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
                InterceptVO interceptVO = getInterceptVO(bean, beanName);
                if (interceptVO != null && interceptVO.isRpc()) {
                    if (serviceConfig == null) {
                        serviceConfig = addService(api, properties.getCluster().getDubbo());
                        registry.registerBeanDefinition("fieldInterceptServiceConfig",
                                BeanDefinitionBuilder.genericBeanDefinition(ServiceConfig.class, () -> serviceConfig).getBeanDefinition());
                    }
                    api.put(interceptVO.beanName, interceptVO.intercept, interceptVO.serviceBean);
                }
                return bean;
            }

            private InterceptVO getInterceptVO(Object bean, String beanName) {
                if (bean instanceof ServiceBean && ((ServiceBean<?>) bean).getRef() instanceof ReturnFieldDispatchAop.SelectMethodHolder) {
                    ServiceBean<ReturnFieldDispatchAop.SelectMethodHolder> serviceBean = (ServiceBean) bean;
                    ReturnFieldDispatchAop.SelectMethodHolder ref = serviceBean.getRef();
                    return new InterceptVO(
                            ref,
                            serviceBean,
                            getFieldInterceptBeanName(ref.getClass()));
                } else if (bean instanceof ReturnFieldDispatchAop.SelectMethodHolder) {
                    return new InterceptVO((ReturnFieldDispatchAop.SelectMethodHolder) bean, null, beanName);
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

    private static class DubboReferenceFactory<JOIN_POINT> implements Function<String, BiConsumer<JOIN_POINT, List<CField>>> {
        private final Function<String, BiConsumer<JOIN_POINT, List<CField>>> parent;
        private final Map<String, BiConsumer<JOIN_POINT, List<CField>>> beanMap = new ConcurrentHashMap<>();
        private final Set<String> enumDBFieldInterceptNames;
        private final Supplier<FieldinterceptProperties> properties;
        private volatile ReferenceConfig<Api> reference;

        private DubboReferenceFactory(Function<String, BiConsumer<JOIN_POINT, List<CField>>> parent, Set<String> enumDBFieldInterceptNames, Supplier<FieldinterceptProperties> properties) {
            this.parent = parent;
            this.enumDBFieldInterceptNames = enumDBFieldInterceptNames;
            this.properties = properties;
        }

        private static List<CField.SerializeCField> convert(List<CField> cFields) {
            List<CField.SerializeCField> list = new ArrayList<>(cFields.size());
            for (CField cField : cFields) {
                list.add(new CField.SerializeCField(cField));
            }
            return list;
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
                FieldinterceptProperties.Dubbo dubbo = properties.get().getCluster().getDubbo();
                if (reference == null) {
                    synchronized (this) {
                        if (reference == null) {
                            reference = addReference(dubbo);
                        }
                    }
                }
                boolean argumentFields = dubbo.isArgumentFields();
                boolean isEnumDBFieldIntercept = enumDBFieldInterceptNames != null && enumDBFieldInterceptNames.contains(name);
                result = isEnumDBFieldIntercept ? new DubboDBEnumFieldIntercept<>(name, reference, argumentFields) : new DubboCompositeFieldIntercept<>(name, reference, argumentFields);
                beanMap.put(name, result);
            }
            return result;
        }

        @Override
        public String toString() {
            return "DubboReferenceFactory" + beanMap;
        }

        private static class DubboDBEnumFieldIntercept<JOIN_POINT> extends EnumDBFieldIntercept<JOIN_POINT> {
            private final String beanName;
            private final ReferenceConfig<Api> reference;
            private final boolean argumentFields;

            private DubboDBEnumFieldIntercept(String beanName, ReferenceConfig<Api> reference, boolean argumentFields) {
                this.beanName = beanName;
                this.reference = reference;
                this.argumentFields = argumentFields;
            }

            @Override
            public Map<String, Map<String, Object>> selectEnumGroupKeyValueMap(List<CField> cFields, Set<String> groups, Collection<Object> keys) {
                Api api = reference.get();
                Map<String, Map<String, Object>> result = api.selectEnumGroupKeyValueMap(beanName, argumentFields ? convert(cFields) : null, groups, keys);
                return convertAsyncIfNeed(result, reference.isAsync(), cFields, this);
            }

            private Map<String, Map<String, Object>> convertAsyncIfNeed(Map<String, Map<String, Object>> result, Boolean async, List<CField> cFields, Object cacheKey) {
                if (Boolean.TRUE.equals(async)) {
                    CompletableFuture<Map<String, Map<String, Object>>> dubboFuture = RpcContext.getContext().getCompletableFuture();
                    SnapshotCompletableFuture<Map<String, Object>> future = ReturnFieldDispatchAop.startAsync(cFields, cacheKey);
                    if (future == null) {
                        try {
                            return dubboFuture.get();
                        } catch (Exception e) {
                            PlatformDependentUtil.sneakyThrows(PlatformDependentUtil.unwrap(e));
                            return null;
                        }
                    } else {
                        dubboFuture.whenComplete((groupMap, throwable) -> future.complete(flatMap(groupMap), throwable));
                        return result;
                    }
                } else {
                    return result;
                }
            }

            @Override
            public String toString() {
                return "DubboDBEnumFieldIntercept{" + beanName + '}';
            }
        }

        private static class DubboCompositeFieldIntercept<JOIN_POINT> implements CompositeFieldIntercept<Object, Object, JOIN_POINT> {
            private final String beanName;
            private final ReferenceConfig<Api> reference;
            private final boolean argumentFields;
            private final KeyNameFieldIntercept<Object, JOIN_POINT> keyNameFieldIntercept = new KeyNameFieldIntercept<Object, JOIN_POINT>(Object.class) {
                @Override
                public Map<Object, ?> selectObjectMapByKeys(List<CField> cFields, Collection<Object> keys) {
                    Api api = reference.get();
                    Map<Object, ?> result = api.selectSerializeNameMapByKeys(beanName, argumentFields ? convert(cFields) : null, keys);
                    return convertAsyncIfNeed(result, reference.isAsync(), cFields, this);
                }

                @Override
                public String toString() {
                    return "DubboKeyNameFieldIntercept{" + beanName + '}';
                }
            };
            private final KeyValueFieldIntercept<Object, Object, JOIN_POINT> keyValueFieldIntercept = new KeyValueFieldIntercept<Object, Object, JOIN_POINT>(Object.class, Object.class) {
                @Override
                public Map<Object, Object> selectValueMapByKeys(List<CField> cFields, Collection<Object> keys) {
                    Api api = reference.get();
                    Map<Object, Object> result = api.selectSerializeValueMapByKeys(beanName, argumentFields ? convert(cFields) : null, keys);
                    return convertAsyncIfNeed(result, reference.isAsync(), cFields, this);
                }

                @Override
                public String toString() {
                    return "DubboKeyValueFieldIntercept{" + beanName + '}';
                }
            };

            private DubboCompositeFieldIntercept(String beanName, ReferenceConfig<Api> reference, boolean argumentFields) {
                this.beanName = beanName;
                this.reference = reference;
                this.argumentFields = argumentFields;
            }

            private static <T> T convertAsyncIfNeed(T result, Boolean async, List<CField> cFields, Object cacheKey) {
                if (Boolean.TRUE.equals(async)) {
                    CompletableFuture<T> dubboFuture = RpcContext.getContext().getCompletableFuture();
                    SnapshotCompletableFuture<T> future = ReturnFieldDispatchAop.startAsync(cFields, cacheKey);
                    if (future == null) {
                        try {
                            return dubboFuture.get();
                        } catch (Exception e) {
                            PlatformDependentUtil.sneakyThrows(PlatformDependentUtil.unwrap(e));
                            return null;
                        }
                    } else {
                        dubboFuture.whenComplete(future::complete);
                        return result;
                    }
                } else {
                    return result;
                }
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
                return "DubboCompositeFieldIntercept{" + beanName + '}';
            }
        }
    }

}
