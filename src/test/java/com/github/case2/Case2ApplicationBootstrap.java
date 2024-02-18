package com.github.case2;

// import com.github.fieldintercept.annotation.EnableFieldIntercept;
import com.github.fieldintercept.Print;
import com.github.fieldintercept.ReturnFieldDispatchAop;
import com.github.fieldintercept.annotation.ReturnFieldAop;
import com.github.fieldintercept.springboot.AspectjReturnFieldDispatchAop;
import com.github.securityfilter.WebSecurityAccessFilter;
import com.github.securityfilter.util.AccessUserUtil;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskDecorator;

import javax.servlet.DispatcherType;
import javax.servlet.http.HttpServletRequest;
import java.net.URL;
import java.util.EnumSet;
import java.util.concurrent.Executor;

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

    public static class AccessUser{
        private final String accessToken;

        public AccessUser(String accessToken) {
            this.accessToken = accessToken;
        }
    }

//    @Bean
    public AspectjReturnFieldDispatchAop aop(){
        AspectjReturnFieldDispatchAop aop = new AspectjReturnFieldDispatchAop();

        aop.setTaskExecutor(new Executor() {
            @Override
            public void execute(Runnable command) {
                command.run();
            }
        });
        return aop;
    }

    @Bean
    public FilterRegistrationBean securityAccessFilterRegistration() {
        FilterRegistrationBean<WebSecurityAccessFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new WebSecurityAccessFilter(){
            @Override
            protected AccessUser selectUserId(HttpServletRequest request, String accessToken) {
                return new AccessUser(accessToken);
            }

            @Override
            protected Object selectUser(HttpServletRequest request, Object o, String accessToken) {
                return o;
            }
        });
        // In case you want the filter to apply to specific URL patterns only
        registration.addUrlPatterns("/*");
        registration.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST));
        return registration;
    }

    @Bean
    public TaskDecorator taskDecorator(){
        return AccessUserUtil::runnable;
    }

    public static void main(String[] args) {
        System.getProperties().put("spring.config.location",CONFIG_URL.toString());
        SpringApplication.run(Case2ApplicationBootstrap.class, args);
        Print.scheduledPrint();
    }

}
