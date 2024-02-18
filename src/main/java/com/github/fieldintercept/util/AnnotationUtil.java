package com.github.fieldintercept.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;

public class AnnotationUtil {

    public static Object getValue(Annotation annotation) {
        return getValue(annotation, "value");
    }

    public static Object getValue(Annotation annotation, String attributeName) {
        Method declaredMethod;
        try {
            declaredMethod = annotation.annotationType().getDeclaredMethod(attributeName);
        } catch (NoSuchMethodException e) {
            return null;
        }
        if (declaredMethod.getReturnType() == void.class) {
            return null;
        }
        try {
            return declaredMethod.invoke(annotation);
        } catch (IllegalAccessException | InvocationTargetException e) {
            return null;
        }
    }

    public interface AnnotationProxy {

    }

    private static <T extends Annotation> T cast(Annotation annotation, Class<T> type) {
        if (type.isAssignableFrom(annotation.annotationType())) {
            return (T) annotation;
        } else {
            Object proxy = Proxy.newProxyInstance(annotation.annotationType().getClassLoader(), new Class[]{type, AnnotationProxy.class}, new GetAnnotationInvocationHandler<>(annotation, type));
            return (T) proxy;
        }
    }

    public static <T extends Annotation> T findExtendsAnnotation(Annotation[] fieldAnnotations, Collection<Class<? extends Annotation>> finds, Class<T> type, Map<Class<?>, Boolean> cacheMap) {
        if (fieldAnnotations != null && fieldAnnotations.length > 0) {
            if (cacheMap == null) {
                cacheMap = new HashMap<>();
            }
            for (Annotation annotation : fieldAnnotations) {
                boolean existAnnotation = isExistAnnotation(annotation.annotationType(), finds, cacheMap);
                if (existAnnotation) {
                    return cast(annotation, type);
                }
            }
        }
        return null;
    }

    public static <T extends Annotation> T findDeclaredAnnotation(AnnotatedElement element, Collection<Class<? extends Annotation>> finds, Map<Class<?>, Boolean> cacheMap) {
        Annotation[] fieldAnnotations = element.getDeclaredAnnotations();
        if (fieldAnnotations != null && fieldAnnotations.length > 0) {
            if (cacheMap == null) {
                cacheMap = new HashMap<>();
            }
            for (Annotation annotation : fieldAnnotations) {
                boolean existAnnotation = isExistAnnotation(annotation.annotationType(), finds, cacheMap);
                if (existAnnotation) {
                    return (T) annotation;
                }
            }
        }
        return null;
    }

    private static Boolean isExistAnnotation0(Class clazz, Collection<Class<? extends Annotation>> finds, Map<Class<?>, Boolean> cacheMap) {
        Annotation annotation;
        Boolean exist = cacheMap.get(clazz);
        if (finds.contains(clazz)) {
            exist = Boolean.TRUE;
        } else if (exist == null) {
            exist = Boolean.FALSE;
            cacheMap.put(clazz, exist);
            Queue<Annotation> queue = new LinkedList<>(Arrays.asList(clazz.getDeclaredAnnotations()));
            while ((annotation = queue.poll()) != null) {
                Class<? extends Annotation> annotationType = annotation.annotationType();
                if (annotationType == clazz) {
                    continue;
                }
                if (finds.contains(annotationType)) {
                    exist = Boolean.TRUE;
                    break;
                }
                if (isExistAnnotation0(annotationType, finds, cacheMap)) {
                    exist = Boolean.TRUE;
                    break;
                }
            }
        }
        cacheMap.put(clazz, exist);
        return exist;
    }

    public static boolean isExistAnnotation(Class clazz, Collection<Class<? extends Annotation>> finds, Map<Class<?>, Boolean> cacheMap) {
        Boolean existAnnotation = cacheMap.get(clazz);
        if (existAnnotation == null) {
            Map<Class<?>, Boolean> tempCacheMap = new HashMap<>(5);
            existAnnotation = isExistAnnotation0(clazz, finds, tempCacheMap);
            cacheMap.putAll(tempCacheMap);
        }
        return existAnnotation;
    }

    private static class GetAnnotationInvocationHandler<T> implements InvocationHandler {
        private static final Class<?>[] EMPTY_PARAMETER_TYPES = new Class[0];
        private final Annotation source;
        private final Class<T> type;
        private final Map<String, Object> castCache = new HashMap<>(2);

        private GetAnnotationInvocationHandler(Annotation source, Class<T> type) {
            this.source = source;
            this.type = type;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            int parameterCount = method.getParameterCount();
            if (parameterCount == 0) {
                if ("annotationType".equals(methodName)) {
                    return type;
                }
                if ("toString".equals(methodName)) {
                    return toString();
                }
                if ("hashCode".equals(methodName)) {
                    return hashCode();
                }
            } else if (parameterCount == 1) {
                if ("equals".equals(methodName)) {
                    Object that = args[0];
                    if (that != null && Proxy.isProxyClass(that.getClass())) {
                        InvocationHandler h = Proxy.getInvocationHandler(that);
                        return h == this;
                    } else {
                        return false;
                    }
                }
            }

            String castCacheKey = methodName + "#" + parameterCount;
            Object castCacheResult = castCache.get(castCacheKey);
            if (castCacheResult != null) {
                return castCacheResult;
            }

            Class<?>[] parameterTypes = parameterCount == 0 ? EMPTY_PARAMETER_TYPES : method.getParameterTypes();
            Object result;
            try {
                Method declaredMethod = source.annotationType().getDeclaredMethod(methodName, parameterTypes);
                result = declaredMethod.invoke(source, args);
            } catch (NoSuchMethodException e) {
                try {
                    Method defaultDeclaredMethod = type.getDeclaredMethod(methodName, parameterTypes);
                    result = defaultDeclaredMethod.getDefaultValue();
                } catch (NoSuchMethodException e1) {
                    throw e;
                }
            }

            Object returnResult = result;
            if (result != null) {
                Class<?> resultClass = result.getClass();
                Class<?> returnType = method.getReturnType();
                if (!returnType.isAssignableFrom(resultClass)) {
                    returnResult = castResult(result, resultClass, returnType);
                    castCache.put(castCacheKey, returnResult);
                }
            }
            return returnResult;
        }

        private static Object cast(Object item, Class<?> componentType) {
            if (item instanceof Annotation) {
                return AnnotationUtil.cast((Annotation) item, (Class<? extends Annotation>) componentType);
            } else {
                return TypeUtil.cast(item, componentType);
            }
        }

        private Object castResult(Object result, Class<?> resultClass, Class<?> returnType) {
            if (returnType.isArray()) {
                Class<?> componentType = returnType.getComponentType();
                if (resultClass.isArray()) {
                    int len = Array.getLength(result);
                    Object instance = Array.newInstance(componentType, len);
                    for (int i = 0; i < len; i++) {
                        Object item = Array.get(result, i);
                        Object cast = cast(item, componentType);
                        Array.set(instance, i, cast);
                    }
                    return instance;
                } else {
                    Object instance = Array.newInstance(componentType, 1);
                    Object cast = cast(result, componentType);
                    Array.set(instance, 0, cast);
                    return instance;
                }
            } else if (resultClass.isArray()) {
                int len = Array.getLength(result);
                if (len > 0) {
                    Object resultItem = Array.get(result, 0);
                    return cast(resultItem, returnType);
                } else if (returnType.isPrimitive()) {
                    return TypeUtil.cast(null, returnType);
                } else {
                    return null;
                }
            } else {
                return cast(result, returnType);
            }
        }

        @Override
        public String toString() {
            return "GetAnnotationInvocationHandler{" +
                    "source=" + source +
                    ", type=" + type +
                    '}';
        }
    }


}
