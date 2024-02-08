package com.github.fieldintercept.springboot;

import com.github.fieldintercept.ReturnFieldDispatchAop;
import com.github.fieldintercept.util.BeanMap;
import com.github.fieldintercept.util.PlatformDependentUtil;
import com.github.fieldintercept.util.SpringWebUtil;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class SpringWebMvcRegistrarUtil {

    public static BeanDefinition[] newBeanDefinitions(Supplier<FieldinterceptProperties> propertiesSupplier) {
        return new BeanDefinition[]{
                BeanDefinitionBuilder.genericBeanDefinition(NonBlockResponseBodyAdviceBefore.class, () -> new NonBlockResponseBodyAdviceBefore(propertiesSupplier)).getBeanDefinition(),
                BeanDefinitionBuilder.genericBeanDefinition(NonBlockResponseBodyAdviceAfter.class, () -> new NonBlockResponseBodyAdviceAfter(propertiesSupplier)).getBeanDefinition()
        };
    }

    @Order(Integer.MIN_VALUE)
    @ControllerAdvice
    public static class NonBlockResponseBodyAdviceBefore implements ResponseBodyAdvice<Object> {
        private final Supplier<FieldinterceptProperties> propertiesSupplier;

        public NonBlockResponseBodyAdviceBefore(Supplier<FieldinterceptProperties> propertiesSupplier) {
            this.propertiesSupplier = propertiesSupplier;
        }

        @Override
        public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
            FieldinterceptProperties properties = propertiesSupplier.get();
            return properties.getBatchAggregation().isPendingNonBlock();
        }

        @Override
        public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType, Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
            ReturnFieldDispatchAop.Pending<Object> pending = SpringWebUtil.getPendingRequestAttribute();
            boolean isControllerProxyMethod;
            if (pending != null && !pending.isDone()) {
                Object value = pending.value();
                Method method = PlatformDependentUtil.aspectjMethodSignatureGetMethod(pending.getGroupCollect().getJoinPoint());
                isControllerProxyMethod = value == body || SpringWebUtil.equalsControllerProxyMethod(method, returnType.getMethod()) || value == BeanMap.invokeGetter(returnType, "returnValue");
            } else {
                isControllerProxyMethod = false;
            }
            SpringWebUtil.setIsControllerProxyMethodRequestAttribute(isControllerProxyMethod);
            return body;
        }
    }

    @Order(Integer.MAX_VALUE)
    @ControllerAdvice
    public static class NonBlockResponseBodyAdviceAfter implements ResponseBodyAdvice<Object> {
        private final Supplier<FieldinterceptProperties> propertiesSupplier;

        public NonBlockResponseBodyAdviceAfter(Supplier<FieldinterceptProperties> propertiesSupplier) {
            this.propertiesSupplier = propertiesSupplier;
        }

        @Override
        public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
            FieldinterceptProperties properties = propertiesSupplier.get();
            return properties.getBatchAggregation().isPendingNonBlock();
        }

        @Override
        public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType, Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
            if (SpringWebUtil.requestAttributeIsControllerProxyMethod()) {
                ReturnFieldDispatchAop.Pending<Object> pending = SpringWebUtil.getPendingRequestAttribute();
                if (pending != null && !pending.isDone()) {
                    CompletableFuture<Object> future = new CompletableFuture<>();
                    pending.whenComplete((value, err) -> {
                        if (err != null) {
                            future.completeExceptionally(err);
                        } else {
                            future.complete(body);
                        }
                    });
                    return future;
                }
            }
            return body;
        }
    }

}
