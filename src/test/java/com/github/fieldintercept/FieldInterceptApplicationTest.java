package com.github.fieldintercept;

import com.github.fieldintercept.annotation.EnableFieldIntercept;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@EnableFieldIntercept(beanBasePackages = {"com.ig"})
@SpringBootApplication
public class FieldInterceptApplicationTest {

    public static void main(String[] args) {
        SpringApplication.run(FieldInterceptApplicationTest.class, args);
    }

}
