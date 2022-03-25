package com.github.fieldintercept;

import com.github.fieldintercept.annotation.EnableFieldIntercept;
import com.github.fieldintercept.entity.ApplyOrder;
import com.github.fieldintercept.entity.BaseEnumGroupEnum;
import com.github.fieldintercept.entity.EnumDBFieldConsumer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@EnableFieldIntercept(beanBasePackages = {"com.ig"}, myAnnotations = {EnumDBFieldConsumer.class})
@SpringBootApplication
public class FieldInterceptApplicationTest {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(FieldInterceptApplicationTest.class, args);

        ReturnFieldDispatchAop dispatchAop = context.getBean(ReturnFieldDispatchAop.class);
        ApplyOrder order = new ApplyOrder();
        dispatchAop.autowiredFieldValue(order);
        System.out.println("user = " + order);
    }

    @Component
    public static class MyEnumDBFieldIntercept extends EnumDBFieldIntercept {
        @Override
        public String[] getGroups(Annotation annotation) {
            BaseEnumGroupEnum[] value = (BaseEnumGroupEnum[]) AnnotationUtils.getValue(annotation);
            return Stream.of(value).map(BaseEnumGroupEnum::getGroup)
                    .toArray(String[]::new);
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
