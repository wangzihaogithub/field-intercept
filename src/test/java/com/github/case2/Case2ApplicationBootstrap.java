package com.github.case2;

import com.github.fieldintercept.annotation.EnableFieldIntercept;
import com.github.case2.annotation.EnumDBFieldConsumer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * -Xmx556m -Xms556m -XX:+UseG1GC
 * 试验阶段
 */
@EnableFieldIntercept(
        batchAggregationMinConcurrentCount = 1,
        batchAggregationMilliseconds = 10L,
        parallelQueryMaxThreads = 100,
        batchAggregation = true,
        beanBasePackages = "com.github.case2",
        myAnnotations = {EnumDBFieldConsumer.class}
)
@SpringBootApplication
public class Case2ApplicationBootstrap {

    public static void main(String[] args) {
        SpringApplication.run(Case2ApplicationBootstrap.class, args);
    }

}
