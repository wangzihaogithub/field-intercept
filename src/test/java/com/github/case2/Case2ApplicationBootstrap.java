package com.github.case2;

// import com.github.fieldintercept.annotation.EnableFieldIntercept;
import com.github.case2.annotation.EnumDBFieldConsumer;
import com.github.securityfilter.util.AccessUserUtil;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskDecorator;

import java.net.URL;

/**
 * -Xmx556m -Xms556m -XX:+UseG1GC
 * 试验阶段
 */
//@EnableFieldIntercept(
//        batchAggregationMinConcurrentCount = 1,
//        batchAggregationMilliseconds = 10L,
//        parallelQueryMaxThreads = 100,
//        batchAggregation = true,
//        beanBasePackages = "com.github.case2",
//        myAnnotations = {EnumDBFieldConsumer.class}
//)
@SpringBootApplication
public class Case2ApplicationBootstrap {

    private static final URL CONFIG_URL = Case2ApplicationBootstrap.class.getResource(
            "/case2/application.yaml");

    @Bean
    public TaskDecorator taskDecorator(){
        return AccessUserUtil::runnable;
    }

    public static void main(String[] args) {
        System.getProperties().put("spring.config.location",CONFIG_URL.toString());
        SpringApplication.run(Case2ApplicationBootstrap.class, args);
    }

}
