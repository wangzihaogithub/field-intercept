package com.github.fieldintercept.springboot;

import com.github.fieldintercept.ReturnFieldDispatchAop;
import com.github.fieldintercept.util.BeanMap;
import com.github.fieldintercept.util.PlatformDependentUtil;
import com.github.fieldintercept.util.SpringWebUtil;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.Order;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.method.support.AsyncHandlerMethodReturnValueHandler;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class SpringWebMvcRegistrarUtil {

    public static BeanDefinition[] newBeanDefinitions(Supplier<FieldinterceptProperties> propertiesSupplier) {
        return new BeanDefinition[]{
                BeanDefinitionBuilder.genericBeanDefinition(NonBlockHandlerMethodReturnValueHandlerPostProcessor.class, () -> new NonBlockHandlerMethodReturnValueHandlerPostProcessor(propertiesSupplier)).getBeanDefinition()
        };
    }

    private static class NonBlockHandlerMethodReturnValueHandlerPostProcessor implements BeanPostProcessor {
        private final Supplier<FieldinterceptProperties> propertiesSupplier;

        private NonBlockHandlerMethodReturnValueHandlerPostProcessor(Supplier<FieldinterceptProperties> propertiesSupplier) {
            this.propertiesSupplier = propertiesSupplier;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
            if (bean instanceof RequestMappingHandlerAdapter) {
                RequestMappingHandlerAdapter requestMappingHandler = (RequestMappingHandlerAdapter) bean;

                List<HandlerMethodReturnValueHandler> newHandlers = new ArrayList<>();
                newHandlers.add(new NonBlockHandlerMethodReturnValueHandler(propertiesSupplier));
                List<HandlerMethodReturnValueHandler> oldHandlers = requestMappingHandler.getReturnValueHandlers();
                if (oldHandlers != null) {
                    newHandlers.addAll(oldHandlers);
                }
                requestMappingHandler.setReturnValueHandlers(newHandlers);
            }
            return bean;
        }
    }

    @Order(Integer.MIN_VALUE + 10)
    public static class NonBlockHandlerMethodReturnValueHandler implements AsyncHandlerMethodReturnValueHandler {
        private final Supplier<FieldinterceptProperties> propertiesSupplier;

        public NonBlockHandlerMethodReturnValueHandler(Supplier<FieldinterceptProperties> propertiesSupplier) {
            this.propertiesSupplier = propertiesSupplier;
        }

        @Override
        public boolean isAsyncReturnValue(Object returnValue, MethodParameter returnType) {
            return isAsyncReturnValue0(returnValue, returnType);
        }

        @Override
        public boolean supportsReturnType(MethodParameter returnType) {
            return isAsyncReturnValue0(null, returnType);
        }

        private boolean isAsyncReturnValue0(Object returnValue, MethodParameter returnType) {
            FieldinterceptProperties properties = propertiesSupplier.get();
            if (properties.getBatchAggregation().isPendingNonBlock()) {
                ReturnFieldDispatchAop.Pending<Object> pending = SpringWebUtil.getPendingRequestAttribute();
                if (pending != null && !pending.isDoneAndSnapshot()) {
                    Object value = pending.value();
                    Method method = PlatformDependentUtil.aspectjMethodSignatureGetMethod(pending.getGroupCollect().getJoinPoint());
                    return returnValue == value || SpringWebUtil.equalsControllerProxyMethod(method, returnType.getMethod()) || value == BeanMap.invokeGetter(returnType, "returnValue");
                }
            }
            return false;
        }

        @Override
        public void handleReturnValue(Object returnValue, MethodParameter returnType, ModelAndViewContainer mavContainer, NativeWebRequest webRequest) throws Exception {
            ReturnFieldDispatchAop.Pending<Object> pending = SpringWebUtil.removePendingRequestAttribute(webRequest.getNativeRequest());
            Objects.requireNonNull(pending);

            DeferredResult<Object> result = new DeferredResult<>();
            PlatformDependentUtil.ThreadSnapshot threadSnapshot = new PlatformDependentUtil.ThreadSnapshot(ReturnFieldDispatchAop.getInstance().getTaskDecorate());
            if (returnValue instanceof DeferredResult) {
                ((DeferredResult<?>) returnValue).onCompletion(new Runnable() {
                    @Override
                    public void run() {
                        pending.whenComplete((unused, err1) -> {
                            threadSnapshot.replay(() -> {
                                if (err1 != null) {
                                    result.setErrorResult(err1);
                                } else {
                                    result.setResult(((DeferredResult<?>) returnValue).getResult());
                                }
                            });
                        });
                    }
                });
                ((DeferredResult<?>) returnValue).onError(new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) {
                        pending.whenComplete((unused, err1) -> {
                            threadSnapshot.replay(() -> {
                                result.setErrorResult(throwable);
                            });
                        });
                    }
                });
            } else if (returnValue instanceof CompletionStage) {
                ((CompletionStage<?>) returnValue).whenComplete((body, err) -> {
                    pending.whenComplete((unused, err1) -> {
                        threadSnapshot.replay(() -> {
                            if (err != null) {
                                if (err instanceof CompletionException && err.getCause() != null) {
                                    result.setErrorResult(err.getCause());
                                } else {
                                    result.setErrorResult(err);
                                }
                            } else if (err1 != null) {
                                result.setErrorResult(err1);
                            } else {
                                result.setResult(body);
                            }
                        });
                    });
                });
            } else if (returnValue instanceof ListenableFuture) {
                ((ListenableFuture<?>) returnValue).addCallback(new ListenableFutureCallback<Object>() {
                    @Override
                    public void onSuccess(Object body) {
                        pending.whenComplete((unused, err1) -> {
                            threadSnapshot.replay(() -> {
                                if (err1 != null) {
                                    result.setErrorResult(err1);
                                } else {
                                    result.setResult(body);
                                }
                            });
                        });
                    }

                    @Override
                    public void onFailure(Throwable ex) {
                        pending.whenComplete((unused, err1) -> {
                            threadSnapshot.replay(() -> {
                                result.setErrorResult(ex);
                            });
                        });
                    }
                });
            } else {
                pending.whenComplete((value, err) -> {
                    threadSnapshot.replay(() -> {
                        if (err != null) {
                            result.setErrorResult(err);
                        } else {
                            result.setResult(returnValue);
                        }
                    });
                });
            }
            WebAsyncUtils.getAsyncManager(webRequest).startDeferredResultProcessing(result, mavContainer);
        }
    }

}
