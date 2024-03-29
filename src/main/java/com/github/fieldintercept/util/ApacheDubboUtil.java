package com.github.fieldintercept.util;

import com.github.fieldintercept.ReturnFieldDispatchAop;
import org.apache.dubbo.rpc.AsyncContext;
import org.apache.dubbo.rpc.RpcContext;

import java.lang.reflect.Method;
import java.util.Objects;

public class ApacheDubboUtil {

    public static boolean isProxyDubboProviderMethod(Method proxyMethod) {
        if (proxyMethod == null) {
            return false;
        }
        RpcContext context = RpcContext.getContext();
        String methodName = context.getMethodName();
        if (!Objects.equals(proxyMethod.getName(), methodName)) {
            return false;
        }
        boolean consumerSide = context.isConsumerSide();
        if (consumerSide) {
            return false;
        }
        Class<?>[] parameterTypes = context.getParameterTypes();
        if (parameterTypes.length != proxyMethod.getParameterCount()) {
            return false;
        }
        if (parameterTypes.length == 0) {
            return true;
        }
        return equals(context.getParameterTypes(), proxyMethod.getParameterTypes());
    }

    public static <JOIN_POINT> boolean startAsync(ReturnFieldDispatchAop.Pending<JOIN_POINT> pending) {
        if (pending.isDone()) {
            return false;
        }
        AsyncContext asyncContext = getAsyncContext();
        pending.whenComplete((value, err) -> asyncContext.write(err != null ? err : value));
        return true;
    }

    private static AsyncContext getAsyncContext() {
        RpcContext context = RpcContext.getContext();
        AsyncContext asyncContext;
        if (context != null && context.isAsyncStarted()) {
            asyncContext = context.getAsyncContext();
        } else {
            asyncContext = RpcContext.startAsync();
        }
        return asyncContext;
    }

    private static boolean equals(Class<?>[] dubboParameterTypes, Class<?>[] proxyParameterTypes) {
        for (int i = 0, len = dubboParameterTypes.length; i < len; i++) {
            Class<?> dubbo = dubboParameterTypes[i];
            Class<?> proxy = proxyParameterTypes[i];
            if (dubbo != proxy && !dubbo.isAssignableFrom(proxy) && !proxy.isAssignableFrom(dubbo)) {
                return false;
            }
        }
        return true;
    }

}
