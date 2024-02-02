package com.github.case1;

// import com.github.fieldintercept.annotation.EnableFieldIntercept;
import com.github.case1.annotation.EnumDBFieldConsumer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * -Xmx556m -Xms556m -XX:+UseG1GC
 */
//@EnableFieldIntercept(
//        beanBasePackages = "com.github.case1",
//        myAnnotations = {EnumDBFieldConsumer.class}
//)
@SpringBootApplication
public class Case1ApplicationBootstrap {

    public static void main(String[] args) {
        SpringApplication.run(Case1ApplicationBootstrap.class, args);
    }

}
