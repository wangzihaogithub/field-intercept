package com.github.fieldintercept.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

public class PlatformDependentUtil {
    public static final boolean EXIST_APACHE_DUBBO;
    public static final boolean EXIST_SPRING;
    public static final boolean EXIST_SPRING_WEB;
    public static final Class<? extends Annotation> SPRING_INDEXED_ANNOTATION;
    private static final Method METHOD_GET_LOGGER;
    private static final Method METHOD_LOGGER_ERROR;
    private static final Method METHOD_LOGGER_TRACE;
    private static final Method METHOD_LOGGER_WARN;
    private static final Method METHOD_ASPECTJ_JOIN_POINT_GET_SIGNATURE;
    private static final Method METHOD_ASPECTJ_METHOD_SIGNATURE_GET_METHOD;

    static {
        boolean existApacheDubbo;
        try {
            Class.forName("org.apache.dubbo.rpc.AsyncContext");
            existApacheDubbo = true;
        } catch (Throwable e) {
            existApacheDubbo = false;
        }
        EXIST_APACHE_DUBBO = existApacheDubbo;

        boolean existSpringWeb;
        try {
            Class.forName("org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice");
            Class.forName("org.springframework.web.bind.annotation.ControllerAdvice");
            Class.forName("org.springframework.web.context.request.RequestContextHolder");
            existSpringWeb = true;
        } catch (Throwable e) {
            existSpringWeb = false;
        }
        EXIST_SPRING_WEB = existSpringWeb;

        Class springIndexedAnnotation;
        try {
            springIndexedAnnotation = Class.forName("org.springframework.stereotype.Indexed");
        } catch (Throwable e) {
            springIndexedAnnotation = null;
        }
        SPRING_INDEXED_ANNOTATION = springIndexedAnnotation;

        boolean existSpringBoot;
        try {
            Class.forName("org.springframework.core.env.ConfigurableEnvironment");
            existSpringBoot = true;
        } catch (Throwable e) {
            existSpringBoot = false;
        }
        EXIST_SPRING = existSpringBoot;

        Method loggerFactoryGetLogger;
        Method loggerError;
        Method loggerTrace;
        Method loggerWarn;
        try {
            loggerFactoryGetLogger = Class.forName("org.slf4j.LoggerFactory").getDeclaredMethod("getLogger", Class.class);
            loggerError = Class.forName("org.slf4j.Logger").getDeclaredMethod("error", String.class, Object[].class);
            loggerTrace = Class.forName("org.slf4j.Logger").getDeclaredMethod("trace", String.class, Object[].class);
            loggerWarn = Class.forName("org.slf4j.Logger").getDeclaredMethod("warn", String.class, Object[].class);
        } catch (Throwable e) {
            loggerFactoryGetLogger = null;
            loggerError = null;
            loggerTrace = null;
            loggerWarn = null;
        }
        METHOD_GET_LOGGER = loggerFactoryGetLogger;
        METHOD_LOGGER_ERROR = loggerError;
        METHOD_LOGGER_TRACE = loggerTrace;
        METHOD_LOGGER_WARN = loggerWarn;

        Method aspectjJoinPointGetSignature;
        Method aspectjMethodSignatureGetMethod;
        try {
            aspectjJoinPointGetSignature = Class.forName("org.aspectj.lang.JoinPoint").getDeclaredMethod("getSignature");
            aspectjMethodSignatureGetMethod = Class.forName("org.aspectj.lang.reflect.MethodSignature").getDeclaredMethod("getMethod");
        } catch (Throwable e) {
            aspectjJoinPointGetSignature = null;
            aspectjMethodSignatureGetMethod = null;
        }
        METHOD_ASPECTJ_JOIN_POINT_GET_SIGNATURE = aspectjJoinPointGetSignature;
        METHOD_ASPECTJ_METHOD_SIGNATURE_GET_METHOD = aspectjMethodSignatureGetMethod;
    }

    public static Method aspectjMethodSignatureGetMethod(Object methodSignature) {
        if (methodSignature != null && METHOD_ASPECTJ_JOIN_POINT_GET_SIGNATURE != null && METHOD_ASPECTJ_JOIN_POINT_GET_SIGNATURE.getDeclaringClass().isAssignableFrom(methodSignature.getClass())) {
            try {
                Object signature = METHOD_ASPECTJ_JOIN_POINT_GET_SIGNATURE.invoke(methodSignature);
                if (METHOD_ASPECTJ_METHOD_SIGNATURE_GET_METHOD != null && signature != null && METHOD_ASPECTJ_METHOD_SIGNATURE_GET_METHOD.getDeclaringClass().isAssignableFrom(signature.getClass())) {
                    Object method = METHOD_ASPECTJ_METHOD_SIGNATURE_GET_METHOD.invoke(signature);
                    if (method instanceof Method) {
                        return (Method) method;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public static boolean logError(Class<?> clazz, String format, Object... args) {
        if (METHOD_LOGGER_ERROR != null) {
            try {
                Object logger = METHOD_GET_LOGGER.invoke(null, clazz);
                METHOD_LOGGER_ERROR.invoke(logger, format, args);
                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    public static boolean logTrace(Class<?> clazz, String format, Object... args) {
        if (METHOD_LOGGER_TRACE != null) {
            try {
                Object logger = METHOD_GET_LOGGER.invoke(null, clazz);
                METHOD_LOGGER_TRACE.invoke(logger, format, args);
                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    public static boolean logWarn(Class<?> clazz, String format, Object... args) {
        if (METHOD_LOGGER_WARN != null) {
            try {
                Object logger = METHOD_GET_LOGGER.invoke(null, clazz);
                METHOD_LOGGER_WARN.invoke(logger, format, args);
                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    public static boolean isProxyDubboProviderMethod(Object joinPoint) {
        return joinPoint != null
                && PlatformDependentUtil.EXIST_APACHE_DUBBO
                && ApacheDubboUtil.isProxyDubboProviderMethod(PlatformDependentUtil.aspectjMethodSignatureGetMethod(joinPoint));
    }

    public static <E extends Throwable> void sneakyThrows(Throwable t) throws E {
        throw (E) t;
    }

}
