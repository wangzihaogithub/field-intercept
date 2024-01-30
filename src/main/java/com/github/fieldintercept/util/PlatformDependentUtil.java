package com.github.fieldintercept.util;

import java.lang.reflect.Method;

public class PlatformDependentUtil {
    public static final boolean EXIST_SPRING;
    private static final Method METHOD_GET_LOGGER;
    private static final Method METHOD_LOGGER_ERROR;
    private static final Method METHOD_LOGGER_TRACE;
    private static final Method METHOD_LOGGER_WARN;

    static {
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

    public static <E extends Throwable> void sneakyThrows(Throwable t) throws E {
        throw (E) t;
    }

}
