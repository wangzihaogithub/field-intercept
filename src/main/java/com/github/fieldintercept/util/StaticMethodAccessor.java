package com.github.fieldintercept.util;

import java.io.IOException;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class StaticMethodAccessor implements Function<String, Object> {
    private final String classMethodName;
    private final Method method;
    private final Class<?>[] parameterTypes;
    private final String[] parameterNames;
    private static final Map<String, StaticMethodAccessor> CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<Member, String[]>> PARAMETER_NAMES_CACHE = new ConcurrentHashMap<>();

    public StaticMethodAccessor(String classMethodName) {
        Method method = null;
        try {
            String[] split = classMethodName.split("#");
            Class<?> clazz = Class.forName(split[0]);
            for (Method declaredMethod : clazz.getDeclaredMethods()) {
                if (declaredMethod.getName().equals(split[1])) {
                    if (method == null || declaredMethod.getParameterCount() > method.getParameterCount()) {
                        method = declaredMethod;
                        method.setAccessible(true);
                    }
                }
            }
            if (method == null) {
                throw new NoSuchMethodException(classMethodName);
            }
        } catch (Exception e) {
            PlatformDependentUtil.sneakyThrows(e);
        }
        if (!Modifier.isStatic(method.getModifiers())) {
            throw new IllegalArgumentException("must is static method. " + classMethodName);
        }
        Class<?>[] parameterTypes = method.getParameterTypes();
        this.parameterTypes = parameterTypes;
        this.classMethodName = classMethodName;
        this.method = method;
        String[] parameterNames = getParameterNames(method);
        this.parameterNames = parameterNames != null && parameterNames.length == parameterTypes.length ? parameterNames : null;
    }

    private static Map<Member, String[]> readParameterNameMap(Class<?> clazz) {
        try {
            JavaClassFile javaClassFile = new JavaClassFile(clazz);
            Map<Member, String[]> result = new HashMap<>(6);
            for (JavaClassFile.Member member : javaClassFile.getMethods()) {
                try {
                    Member javaMember = member.toJavaMember();
                    String[] parameterNames = member.getParameterNames();
                    result.put(javaMember, parameterNames);
                } catch (Exception ignored) {
                }
            }
            return result;
        } catch (ClassNotFoundException | IOException | IllegalClassFormatException e) {
            return Collections.emptyMap();
        }
    }

    public static String[] getParameterNames(Method method) {
        Class<?> declaringClass = method.getDeclaringClass();
        if (declaringClass.isInterface()) {
            return null;
        }
        Map<Member, String[]> memberMap = PARAMETER_NAMES_CACHE.computeIfAbsent(declaringClass, e -> readParameterNameMap(declaringClass));
        return memberMap.get(method);
    }

    public static StaticMethodAccessor newInstance(String classMethodName) {
        return CACHE.computeIfAbsent(classMethodName, StaticMethodAccessor::new);
    }

    public Object invoke(Object[] args) throws InvocationTargetException, IllegalAccessException {
        return method.invoke(null, args);
    }

    public Class<?>[] getParameterTypes() {
        return parameterTypes;
    }

    public String[] getParameterNames() {
        return parameterNames;
    }

    @Override
    public Object apply(String name) {
        try {
            return method.invoke(null, name);
        } catch (IllegalAccessException | InvocationTargetException e) {
            PlatformDependentUtil.sneakyThrows(e);
            return null;
        }
    }

    @Override
    public String toString() {
        return classMethodName;
    }

}