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
     * 集群配置
     */
    @NestedConfigurationProperty
    private final Cluster cluster = new Cluster();
    /**
     * 聚合策略
     */
    @NestedConfigurationProperty
    private final BatchAggregation batchAggregation = new BatchAggregation();
    /**
     * 线程策略
     */
    @NestedConfigurationProperty
    private final Thread thread = new Thread();
    /**
     * 是否开启字段拦截
     */
    private boolean enabled = true;
    /**
     * 业务实体类的包路径
     * 用于快速判断是否是业务实体类 ,如果是业务实体类,则会深度遍历访问内部字段
     * 包路径. 例如 {"com.ig", "com.xx"}
     */
    private String[] beanBasePackages = {};
    /**
     * 如果超过这个数量，就会阻塞调用方(业务代码)继续生产自动注入任务。阻塞创建AutowiredRunnable，创建不出来就提交不到线程池里
     */
    private int maxRunnableConcurrentCount = Integer.MAX_VALUE;
    /**
     * 自动注入同步调用时的超时时间
     */
    private int blockGetterTimeoutMilliseconds = 10_000;
    /**
     * 切面对象
     */
    private Class<? extends ReturnFieldDispatchAop> aopClass = AspectjReturnFieldDispatchAop.class;
    /**
     * 注册自定义注解
     * 1. 自定义注解可以像使用 FieldConsumer注解一样，拦截字段处理逻辑
     * 2. 自定义注解可以覆盖框架注解
     * 前提
     * 1. spring容器里必须有和注解短类名相同的bean。例： com.ig.MyAnnotation的名字是MyAnnotation。 {@link ReturnFieldDispatchAop#getMyAnnotationConsumerName(Class)}
     * 2. bean需要实现接口处理自定义逻辑 {@link ReturnFieldDispatchAop.FieldIntercept}
     */
    private Class<? extends Annotation>[] myAnnotations = new Class[0];

    public Cluster getCluster() {
        return cluster;
    }

    public BatchAggregation getBatchAggregation() {
        return batchAggregation;
    }

    public Thread getThread() {
        return thread;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

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

    public Class<? extends Annotation>[] getMyAnnotations() {
        return myAnnotations;
    }

    public void setMyAnnotations(Class<? extends Annotation>[] myAnnotations) {
        this.myAnnotations = myAnnotations;
    }

    public int getBlockGetterTimeoutMilliseconds() {
        return blockGetterTimeoutMilliseconds;
    }

    public void setBlockGetterTimeoutMilliseconds(int blockGetterTimeoutMilliseconds) {
        this.blockGetterTimeoutMilliseconds = blockGetterTimeoutMilliseconds;
    }

    public int getMaxRunnableConcurrentCount() {
        return maxRunnableConcurrentCount;
    }

    public void setMaxRunnableConcurrentCount(int maxRunnableConcurrentCount) {
        this.maxRunnableConcurrentCount = maxRunnableConcurrentCount;
    }

    public enum BatchAggregationEnum {
        disabled,
        auto,
        manual
    }

    public enum ClusterRpcEnum {
        dubbo
    }

    public enum ClusterRoleEnum {
        provider,
        consumer,
        all
    }

    public static class Thread {
        /**
         * 是否并行查询 true=用线程池并行,false=在调用者线程上串行
         */
        private boolean enabled = true;
        /**
         * 线程名称前缀
         */
        private String prefix = "FieldIntercept-";
        private int corePoolSize = 0;
        /**
         * 线程数量
         * 如果并发超过线程数量，超出的部分会在调用者线程上执行
         */
        private int maxThreads = 100;

        private long keepAliveTimeSeconds = 60L;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public void setCorePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
        }

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }

        public long getKeepAliveTimeSeconds() {
            return keepAliveTimeSeconds;
        }

        public void setKeepAliveTimeSeconds(long keepAliveTimeSeconds) {
            this.keepAliveTimeSeconds = keepAliveTimeSeconds;
        }

        public int getMaxThreads() {
            return maxThreads;
        }

        public void setMaxThreads(int maxThreads) {
            this.maxThreads = maxThreads;
        }
    }

    public static class BatchAggregation {
        /**
         * 聚合策略
         * 开启将N毫秒内的多个并发请求攒到一起处理
         */
        private BatchAggregationEnum enabled = BatchAggregationEnum.disabled;

        /**
         * 攒多个并发请求的等待时间（毫秒） 将N毫秒内的所有线程聚合到一起查询
         * {pollMilliseconds}毫秒内，有{pollMinSize}个就发车，一趟车最多{pollMaxSize}人，最多同时发{maxSignalConcurrentCount}辆车，等下次发车的排队人数为{pendingQueueCapacity}
         */
        private long pollMilliseconds = 50L;
        /**
         * 最少聚合个数
         * {pollMilliseconds}毫秒内，有{pollMinSize}个就发车，一趟车最多{pollMaxSize}人，最多同时发{maxSignalConcurrentCount}辆车，等下次发车的排队人数为{pendingQueueCapacity}
         */
        private int pollMinSize = 1;
        /**
         * 最大聚合个数
         * {pollMilliseconds}毫秒内，有{pollMinSize}个就发车，一趟车最多{pollMaxSize}人，最多同时发{maxSignalConcurrentCount}辆车，等下次发车的排队人数为{pendingQueueCapacity}
         */
        private int pollMaxSize = 1000;
        /**
         * 控制批量聚合信号（发车）最大并发量，如果超过这个并发量，并且超过了队列长度(pendingQueueCapacity)，则会阻塞调用方(业务代码)继续生产自动注入任务。
         * {pollMilliseconds}毫秒内，有{pollMinSize}个就发车，一趟车最多{pollMaxSize}人，最多同时发{maxSignalConcurrentCount}辆车，等下次发车的排队人数为{pendingQueueCapacity}
         */
        private int maxSignalConcurrentCount = 10000;
        /**
         * 聚合阻塞队列容量
         * {pollMilliseconds}毫秒内，有{pollMinSize}个就发车，一趟车最多{pollMaxSize}人，最多同时发{maxSignalConcurrentCount}辆车，等下次发车的排队人数为{pendingQueueCapacity}
         */
        private int pendingQueueCapacity = 10000;
        /**
         * 并发量小于这个数，直接走同步代码逻辑。
         * 超过这个并发请求的数量后，才开始聚合攒批。 否则立即执行
         * 攒批的并发量最低要求
         */
        private int thresholdMinConcurrentCount = 1;
        /**
         * 打上ReturnFieldAop注解的方法，是否使用非阻塞返回（dubbo-server接口转异步，spring-web-server接口转异步）
         */
        private boolean pendingNonBlock = true;

        public BatchAggregationEnum getEnabled() {
            return enabled;
        }

        public void setEnabled(BatchAggregationEnum enabled) {
            this.enabled = enabled;
        }

        public long getPollMilliseconds() {
            return pollMilliseconds;
        }

        public void setPollMilliseconds(long pollMilliseconds) {
            this.pollMilliseconds = pollMilliseconds;
        }

        public int getPollMinSize() {
            return pollMinSize;
        }

        public void setPollMinSize(int pollMinSize) {
            this.pollMinSize = pollMinSize;
        }

        public int getPollMaxSize() {
            return pollMaxSize;
        }

        public void setPollMaxSize(int pollMaxSize) {
            this.pollMaxSize = pollMaxSize;
        }

        public boolean isPendingNonBlock() {
            return pendingNonBlock;
        }

        public void setPendingNonBlock(boolean pendingNonBlock) {
            this.pendingNonBlock = pendingNonBlock;
        }

        public int getThresholdMinConcurrentCount() {
            return thresholdMinConcurrentCount;
        }

        public void setThresholdMinConcurrentCount(int thresholdMinConcurrentCount) {
            this.thresholdMinConcurrentCount = thresholdMinConcurrentCount;
        }

        public int getPendingQueueCapacity() {
            return pendingQueueCapacity;
        }

        public void setPendingQueueCapacity(int pendingQueueCapacity) {
            this.pendingQueueCapacity = pendingQueueCapacity;
        }

        public int getMaxSignalConcurrentCount() {
            return maxSignalConcurrentCount;
        }

        public void setMaxSignalConcurrentCount(int maxSignalConcurrentCount) {
            this.maxSignalConcurrentCount = maxSignalConcurrentCount;
        }
    }

    public static class Cluster {
        /**
         * Dubbo配置
         */
        @NestedConfigurationProperty
        private final Dubbo dubbo = new Dubbo();
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

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
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
         * Whether to async
         * note that: it is an unreliable asynchronism that ignores return values and does not block threads.
         */
        private Boolean async = true;
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

        public Boolean getAsync() {
            return async;
        }

        public void setAsync(Boolean async) {
            this.async = async;
        }

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
