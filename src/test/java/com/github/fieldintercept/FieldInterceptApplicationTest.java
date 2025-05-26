package com.github.fieldintercept;

// import com.github.fieldintercept.annotation.EnableFieldIntercept;

import com.github.case1.enumer.BizEnumGroupEnum;
import com.github.fieldintercept.annotation.ReturnFieldAop;
import com.github.fieldintercept.entity.ApplyOrder;
import com.github.fieldintercept.entity.BaseEnumGroupEnum;
import com.github.fieldintercept.util.AnnotationUtil;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.TaskDecorator;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

//@EnableFieldIntercept(beanBasePackages = {"com.ig"})
//@EnableFieldIntercept(beanBasePackages = {"com.ig"},
//        myAnnotations = {EnumDBFieldConsumer.class},
//        batchAggregation = true
//)
@SpringBootApplication
public class FieldInterceptApplicationTest {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(FieldInterceptApplicationTest.class, args);

        ApplyOrderService applyOrderService = context.getBean(ApplyOrderService.class);
        ApplyOrder applyOrder = applyOrderService.getById();
        ReturnFieldDispatchAop.staticAutowiredFieldValue(applyOrder);
        System.out.println("applyOrder = " + applyOrder);
    }

    @Configuration
    public static class Config {
        @Bean
        public TaskDecorator taskDecorator1() {
            return new TaskDecorator() {
                @Override
                public Runnable decorate(Runnable runnable) {
                    return runnable;
                }
            };
        }

        @Primary
        @Bean
        public TaskDecorator taskDecorator2() {
            return new TaskDecorator() {
                @Override
                public Runnable decorate(Runnable runnable) {
                    return runnable;
                }
            };
        }
    }

    @Service
    public static class ApplyOrderService {
        @ReturnFieldAop(batchAggregation = true)
        public ApplyOrder getById() {
            return new ApplyOrder();
        }
    }

    @Component
    public static class MyEnumDBFieldIntercept extends EnumDBFieldIntercept<Object> {
        @Override
        public String[] getGroups(CField cField) {
            String[] groups = super.getGroups(cField);
            if (groups == null) {
                BizEnumGroupEnum[] value = (BizEnumGroupEnum[]) AnnotationUtil.getValue(cField.getAnnotation());
                groups = Stream.of(value).map(BizEnumGroupEnum::getGroup)
                        .toArray(String[]::new);
            }
            return groups;
        }

        @Override
        public Map<String, Map<String, Object>> selectEnumGroupKeyValueMap(Set<String> groups, Collection<Object> keys) {
            Map<String, Map<String, Object>> result = new LinkedHashMap<>();

            Map<String, Object> applyMap = new LinkedHashMap<>();
            applyMap.put("1", new Apply("待审批"));
            result.put(BaseEnumGroupEnum.APPLY.getGroup(), applyMap);
            return result;
        }
    }

    public static class Apply {
        private String name;

        public Apply(String name) {
            this.name = name;
        }
    }
}
