package com.github.fieldintercept.util;

import com.github.fieldintercept.ReturnFieldDispatchAop;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class SpringWebUtil {

    public static final String ATTR_NAME_PENDING = "fieldintercept.pending";

    public static boolean isProxySpringWebControllerMethod(Method proxyMethod) {
        RequestWrapper request = RequestWrapper.getCurrentRequest();
        if (request == null) {
            return false;
        }
        HandlerMethod handlerMethod = (HandlerMethod) request.getAttribute("org.springframework.web.servlet.HandlerMapping.bestMatchingHandler");
        if (handlerMethod != null) {
            Method method = handlerMethod.getMethod();
            return equalsControllerProxyMethod(proxyMethod, method);
        } else {
            return false;
        }
    }

    public static boolean equalsControllerProxyMethod(Method lastProxyMethod, Method controllerProxyMethod) {
        if (lastProxyMethod == null || controllerProxyMethod == null) {
            return false;
        }
        if (lastProxyMethod == controllerProxyMethod) {
            return true;
        }
        String proxy = lastProxyMethod.getName();
        String controller = controllerProxyMethod.getName();
        if (!Objects.equals(proxy, controller)) {
            return false;
        }
        int lastProxyMethodParameterCount = lastProxyMethod.getParameterCount();
        if (lastProxyMethodParameterCount != controllerProxyMethod.getParameterCount()) {
            return false;
        }
        if (lastProxyMethodParameterCount == 0) {
            return true;
        }
        return equals(controllerProxyMethod.getParameterTypes(), lastProxyMethod.getParameterTypes());
    }

    private static boolean equals(Class<?>[] controllerParameterTypes, Class<?>[] proxyParameterTypes) {
        for (int i = 0, len = controllerParameterTypes.length; i < len; i++) {
            Class<?> controller = controllerParameterTypes[i];
            Class<?> proxy = proxyParameterTypes[i];
            if (controller != proxy && !controller.isAssignableFrom(proxy) && !proxy.isAssignableFrom(controller)) {
                return false;
            }
        }
        return true;
    }

    public static <JOIN_POINT> ReturnFieldDispatchAop.Pending<JOIN_POINT> removeAsync(Object request0) {
        if (RequestWrapper.isServletRequest(request0)) {
            RequestWrapper request = new RequestWrapper(request0);
            Object attribute = request.getAttribute(ATTR_NAME_PENDING);
            if (attribute instanceof ReturnFieldDispatchAop.Pending) {
                request.removeAttribute(ATTR_NAME_PENDING);
                return (ReturnFieldDispatchAop.Pending<JOIN_POINT>) attribute;
            }
        }
        RequestWrapper request = RequestWrapper.getCurrentRequest();
        if (request != null) {
            Object attribute = request.getAttribute(ATTR_NAME_PENDING);
            if (attribute instanceof ReturnFieldDispatchAop.Pending) {
                request.removeAttribute(ATTR_NAME_PENDING);
                return (ReturnFieldDispatchAop.Pending<JOIN_POINT>) attribute;
            }
        }
        return null;
    }

    public static <JOIN_POINT> ReturnFieldDispatchAop.Pending<JOIN_POINT> getAsync() {
        RequestWrapper request = RequestWrapper.getCurrentRequest();
        if (request != null) {
            Object attribute = request.getAttribute(ATTR_NAME_PENDING);
            if (attribute instanceof ReturnFieldDispatchAop.Pending) {
                return (ReturnFieldDispatchAop.Pending<JOIN_POINT>) attribute;
            }
        }
        return null;
    }

    public static <JOIN_POINT> boolean startAsync(ReturnFieldDispatchAop.Pending<JOIN_POINT> pending) {
        if (pending.isDone()) {
            return false;
        }
        RequestWrapper request = RequestWrapper.getCurrentRequest();
        if (request != null) {
            request.setAttribute(ATTR_NAME_PENDING, pending);
            return true;
        } else {
            return false;
        }
    }

    public static class RequestWrapper {
        static boolean isServletRequest(Object request) {
            boolean result = false;
            if (request != null) {
                Class<?> clazz = request.getClass();
                // 动态检测
                if (REQUEST_JAKARTA != null) {
                    // 尝试检查是否实现了 jakarta.servlet.http.HttpServletRequest
                    result = REQUEST_JAKARTA.isAssignableFrom(clazz);
                }
                if (!result && REQUEST_JAVAX != null) {
                    // 尝试检查是否实现了 javax.servlet.http.HttpServletRequest
                    result = REQUEST_JAVAX.isAssignableFrom(clazz);
                }
            }
            return result;
        }

        static RequestWrapper getCurrentRequest() {
            Object requestObj;
            try {
                Object servletRequestAttributes = RequestContextHolder.getRequestAttributes();
                if (servletRequestAttributes == null) {
                    return null;
                }
                requestObj = GET_REQUEST_METHOD.invoke(servletRequestAttributes);
            } catch (Exception e) {
                return null;
            }
            if (requestObj == null) {
                return null;
            }
            RequestWrapper requestWrapper = new RequestWrapper(requestObj);
            try {
                requestWrapper.getAttribute("");
                return requestWrapper;
            } catch (Throwable e) {
                // 验证请求是否垮线程了
                return null;
            }
        }


        private final Object request;
        private final Map<String, Method> methodCache = new ConcurrentHashMap<>();

        RequestWrapper(Object request) {
            this.request = request;
        }

        private Method getMethod(String methodName, Class<?>... parameterTypes) {
            return methodCache.computeIfAbsent(methodName, k -> {
                try {
                    return request.getClass().getMethod(methodName, parameterTypes);
                } catch (NoSuchMethodException e) {
                    throw new RuntimeException("Method not found: " + methodName, e);
                }
            });
        }

        public Object getAttribute(String name) {
            try {
                Method method = getMethod("getAttribute", String.class);
                return method.invoke(request, name);
            } catch (Exception e) {
                throw new RuntimeException("Failed to invoke getAttribute", e);
            }
        }

        public void setAttribute(String name, Object value) {
            try {
                Method method = getMethod("setAttribute", String.class, Object.class);
                method.invoke(request, name, value);
            } catch (Exception e) {
                throw new RuntimeException("Failed to invoke setAttribute", e);
            }
        }

        public void removeAttribute(String name) {
            try {
                Method method = getMethod("removeAttribute", String.class);
                method.invoke(request, name);
            } catch (Exception e) {
                throw new RuntimeException("Failed to invoke removeAttribute", e);
            }
        }
    }

    private static final boolean EXIST_JAKARTA;
    private static final boolean EXIST_JAVAX;
    private static final Class<?> REQUEST_JAKARTA;
    private static final Class<?> REQUEST_JAVAX;
    private static final Method GET_REQUEST_METHOD;

    static {
        boolean existJakarta;
        boolean existJavax;
        Class<?> requestJakarta = null;
        Class<?> requestJavax = null;
        try {
            requestJakarta = Class.forName("jakarta.servlet.http.HttpServletRequest");
            requestJakarta.getDeclaredMethod("getHeader", String.class);
            existJakarta = true;
        } catch (Throwable ignored) {
            existJakarta = false;
        }
        try {
            requestJavax = Class.forName("javax.servlet.http.HttpServletRequest");
            requestJavax.getDeclaredMethod("getHeader", String.class);
            existJavax = true;
        } catch (Throwable ignored) {
            existJavax = false;
        }

        Method getRequestMethod = null;
        try {
            Class<?> requestClass = Class.forName("org.springframework.web.context.request.ServletRequestAttributes");
            getRequestMethod = requestClass.getDeclaredMethod("getRequest");
        } catch (Throwable ignored) {
        }
        GET_REQUEST_METHOD = getRequestMethod;
        EXIST_JAKARTA = existJakarta;
        EXIST_JAVAX = existJavax;
        REQUEST_JAKARTA = requestJakarta;
        REQUEST_JAVAX = requestJavax;
    }
}
