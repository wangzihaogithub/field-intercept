package com.github.fieldintercept.util;

import com.github.fieldintercept.ReturnFieldDispatchAop;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.HandlerMethod;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Objects;

public class SpringWebUtil {

    public static final String ATTR_NAME_PENDING_PROXY = "fieldintercept.pending.isControllerProxyMethod";
    public static final String ATTR_NAME_PENDING = "fieldintercept.pending";

    public static boolean isProxySpringWebControllerMethod(Method proxyMethod) {
        HttpServletRequest request = getCurrentRequest();
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
        if (request0 instanceof HttpServletRequest) {
            HttpServletRequest request = (HttpServletRequest) request0;
            Object attribute = request.getAttribute(ATTR_NAME_PENDING);
            if (attribute instanceof ReturnFieldDispatchAop.Pending) {
                request.removeAttribute(ATTR_NAME_PENDING);
                return (ReturnFieldDispatchAop.Pending<JOIN_POINT>) attribute;
            }
        }
        HttpServletRequest request = getCurrentRequest();
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
        HttpServletRequest request = getCurrentRequest();
        if (request != null) {
            Object attribute = request.getAttribute(ATTR_NAME_PENDING);
            if (attribute instanceof ReturnFieldDispatchAop.Pending) {
                return (ReturnFieldDispatchAop.Pending<JOIN_POINT>) attribute;
            }
        }
        return null;
    }

    public static <JOIN_POINT> boolean startAsync(ReturnFieldDispatchAop.Pending<JOIN_POINT> pending) {
        HttpServletRequest request = getCurrentRequest();
        if (request != null) {
            request.setAttribute(ATTR_NAME_PENDING, pending);
            return true;
        } else {
            return false;
        }
    }

    private static HttpServletRequest getCurrentRequest() {
        HttpServletRequest request;
        try {
            RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
            if (requestAttributes instanceof ServletRequestAttributes) {
                request = ((ServletRequestAttributes) requestAttributes).getRequest();
            } else {
                request = null;
            }
        } catch (Exception e) {
            request = null;
        }

        //验证请求
        try {
            if (request != null) {
                request.getMethod();
            }
        } catch (Exception e) {
            request = null;
        }
        return request;
    }
}
