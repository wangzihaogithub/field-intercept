package com.github.fieldintercept.util;

import com.github.fieldintercept.ReturnFieldDispatchAop;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

public class SpringWebUtil {

    public static final String ATTR_NAME_PENDING_PROXY = "fieldintercept.pending.isControllerProxyMethod";
    public static final String ATTR_NAME_PENDING = "fieldintercept.pending";

    public static boolean requestAttributeIsControllerProxyMethod() {
        HttpServletRequest request = getCurrentRequest();
        if (request != null) {
            Object attribute = request.getAttribute(ATTR_NAME_PENDING_PROXY);
            if (attribute instanceof Boolean) {
                request.removeAttribute(ATTR_NAME_PENDING_PROXY);
                return (boolean) attribute;
            }
        }
        return false;
    }

    public static boolean setIsControllerProxyMethodRequestAttribute(boolean isControllerProxyMethod) {
        HttpServletRequest request = getCurrentRequest();
        if (request != null) {
            request.setAttribute(ATTR_NAME_PENDING_PROXY, isControllerProxyMethod);
            return true;
        } else {
            return false;
        }
    }

    public static <JOIN_POINT> ReturnFieldDispatchAop.Pending<JOIN_POINT> getPendingRequestAttribute() {
        HttpServletRequest request = getCurrentRequest();
        if (request != null) {
            Object attribute = request.getAttribute(ATTR_NAME_PENDING);
            if (attribute instanceof ReturnFieldDispatchAop.Pending) {
                return (ReturnFieldDispatchAop.Pending<JOIN_POINT>) attribute;
            }
        }
        return null;
    }

    public static <JOIN_POINT> boolean setPendingRequestAttribute(ReturnFieldDispatchAop.Pending<JOIN_POINT> pending) {
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
