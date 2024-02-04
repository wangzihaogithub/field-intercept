package com.github.fieldintercept.springboot;

import com.github.fieldintercept.ReturnFieldDispatchAop;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.lang.annotation.Annotation;
import java.util.Map;

@ConfigurationProperties(prefix = FieldinterceptProperties.PREFIX, ignoreUnknownFields = true)
public class FieldinterceptProperties {
    public static final String PREFIX = "spring.fieldintercept";
    private static final long serialVersionUID = 1L;

    /**
     * 是否开启字段拦截
     */
    private boolean enabled = true;
    /**
     * 集群配置
     */
    @NestedConfigurationProperty
    private final Cluster cluster = new Cluster();

    /**
     * 业务实体类的包路径
     * 用于快速判断是否是业务实体类 ,如果是业务实体类,则会深度遍历访问内部字段
     *
     * @return 包路径. 例如 {"com.ig", "com.xx"}
     */
    private String[] beanBasePackages = {};

    /**
     * 切面对象
     *
     * @return
     */
    private Class<? extends ReturnFieldDispatchAop> aopClass = AspectjReturnFieldDispatchAop.class;

    /**
     * 是否并行查询
     *
     * @return true=用线程池并行,false=在调用者线程上串行
     */
    private boolean parallelQuery = true;

    /**
     * 并行查询线程数量
     * 如果并发超过线程数量，超出的部分会在调用者线程上执行
     *
     * @return 线程数量
     */
    private int parallelQueryMaxThreads = 100;

    /**
     * 是否开启将N毫秒内的多个并发请求攒到一起处理
     *
     * @return true=开启,false=不开启
     */
    private boolean batchAggregation = false;

    /**
     * 攒多个并发请求的等待时间（毫秒）
     *
     * @return 将N毫秒内的所有线程聚合到一起查询
     */
    private long batchAggregationMilliseconds = 10L;

    /**
     * 超过这个并发请求的数量后，才开始攒批。 否则立即执行
     *
     * @return 攒批的并发量最低要求
     */
    private int batchAggregationMinConcurrentCount = 1;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 注册自定义注解
     * 1. 自定义注解可以像使用 FieldConsumer注解一样，拦截字段处理逻辑
     * 2. 自定义注解可以覆盖框架注解
     * 前提
     * 1. spring容器里必须有和注解短类名相同的bean。例： com.ig.MyAnnotation的名字是MyAnnotation。 {@link ReturnFieldDispatchAop#getMyAnnotationConsumerName(Class)}
     * 2. bean需要实现接口处理自定义逻辑 {@link ReturnFieldDispatchAop.FieldIntercept}
     *
     * @return 需要添加的自定义注解
     */
    private Class<? extends Annotation>[] myAnnotations = new Class[0];

    public String[] getBeanBasePackages() {
        return beanBasePackages;
    }

    public void setBeanBasePackages(String[] beanBasePackages) {
        this.beanBasePackages = beanBasePackages;
    }

    public Class<? extends ReturnFieldDispatchAop> getAopClass() {
        return aopClass;
    }

    public void setAopClass(Class<? extends ReturnFieldDispatchAop> aopClass) {
        this.aopClass = aopClass;
    }

    public boolean isParallelQuery() {
        return parallelQuery;
    }

    public void setParallelQuery(boolean parallelQuery) {
        this.parallelQuery = parallelQuery;
    }

    public int getParallelQueryMaxThreads() {
        return parallelQueryMaxThreads;
    }

    public void setParallelQueryMaxThreads(int parallelQueryMaxThreads) {
        this.parallelQueryMaxThreads = parallelQueryMaxThreads;
    }

    public boolean isBatchAggregation() {
        return batchAggregation;
    }

    public void setBatchAggregation(boolean batchAggregation) {
        this.batchAggregation = batchAggregation;
    }

    public long getBatchAggregationMilliseconds() {
        return batchAggregationMilliseconds;
    }

    public void setBatchAggregationMilliseconds(long batchAggregationMilliseconds) {
        this.batchAggregationMilliseconds = batchAggregationMilliseconds;
    }

    public int getBatchAggregationMinConcurrentCount() {
        return batchAggregationMinConcurrentCount;
    }

    public void setBatchAggregationMinConcurrentCount(int batchAggregationMinConcurrentCount) {
        this.batchAggregationMinConcurrentCount = batchAggregationMinConcurrentCount;
    }

    public Class<? extends Annotation>[] getMyAnnotations() {
        return myAnnotations;
    }

    public void setMyAnnotations(Class<? extends Annotation>[] myAnnotations) {
        this.myAnnotations = myAnnotations;
    }

    public Cluster getCluster() {
        return cluster;
    }

    public enum ClusterRpcEnum {
        dubbo
    }

    public enum ClusterRoleEnum {
        provider,
        consumer,
        all
    }

    public static class Cluster {
        /**
         * 是否开启集群模式
         */
        private boolean enabled = false;
        /**
         * dubbo=使用dubbo远程注册与调用
         */
        private ClusterRpcEnum rpc = ClusterRpcEnum.dubbo;
        /**
         * 服务角色
         * provider=服务端（配置后不会调用远程接口，会提供远程接口）
         * consumer=客户端（配置后不会提供远程服务，会调用远程接口）
         * all=服务端加客户端（会提供远程接口 + 会调用远程接口）
         */
        private ClusterRoleEnum role = ClusterRoleEnum.all;

        /**
         * Dubbo配置
         */
        @NestedConfigurationProperty
        private final Dubbo dubbo = new Dubbo();

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public Dubbo getDubbo() {
            return dubbo;
        }

        public ClusterRpcEnum getRpc() {
            return rpc;
        }

        public void setRpc(ClusterRpcEnum rpc) {
            this.rpc = rpc;
        }

        public ClusterRoleEnum getRole() {
            return role;
        }

        public void setRole(ClusterRoleEnum role) {
            this.role = role;
        }
    }

    public static class Dubbo {

        /**
         * Registry spring bean name
         */
        private String[] registry;

        /**
         * Service version, default value is empty string
         */
        private String version;

        /**
         * Timeout value for service invocation, default value is 0
         */
        private Integer timeout;
        /**
         * Check if service provider exists, if not exists, it will be fast fail
         */
        private boolean check = false;
        /**
         * dubbo filter
         */
        private String[] filter;
        /**
         * Customized parameter key-value pair, for example: {key1, value1, key2, value2}
         */
        private Map<String, String> parameters;

        /**
         * Service invocation retry times (iterget的配置, 关闭重试.默认3次)
         * <p>
         * //     * @see Constants#DEFAULT_RETRIES
         */
        private Integer retries;
        /**
         * Service group, default value is empty string
         */
        private String group;
        /**
         * Maximum connections service provider can accept, default value is 0 - connection is shared
         */
        private Integer connections;

        public boolean isCheck() {
            return check;
        }

        public void setCheck(boolean check) {
            this.check = check;
        }

        public String[] getRegistry() {
            return registry;
        }

        public void setRegistry(String[] registry) {
            this.registry = registry;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public Integer getTimeout() {
            return timeout;
        }

        public void setTimeout(Integer timeout) {
            this.timeout = timeout;
        }

        public String[] getFilter() {
            return filter;
        }

        public void setFilter(String[] filter) {
            this.filter = filter;
        }

        public Map<String, String> getParameters() {
            return parameters;
        }

        public void setParameters(Map<String, String> parameters) {
            this.parameters = parameters;
        }

        public Integer getRetries() {
            return retries;
        }

        public void setRetries(Integer retries) {
            this.retries = retries;
        }

        public String getGroup() {
            return group;
        }

        public void setGroup(String group) {
            this.group = group;
        }

        public Integer getConnections() {
            return connections;
        }

        public void setConnections(Integer connections) {
            this.connections = connections;
        }
    }
}
