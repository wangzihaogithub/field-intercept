package com.github.fieldintercept.util;

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StaticMethodAccessor {
    private static final Map<String, StaticMethodAccessor> CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<Member, JavaClassFile.Parameter[]>> PARAMETER_NAMES_CACHE = new ConcurrentHashMap<>();
    private final String classMethodName;
    private final Method method;
    private final Class<?>[] parameterTypes;
    private final JavaClassFile.Parameter[] parameters;

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
        JavaClassFile.Parameter[] parameterNames = getParameterNames(method);
        this.parameters = parameterNames != null && parameterNames.length == parameterTypes.length ? parameterNames : null;
    }

    private static Map<Member, JavaClassFile.Parameter[]> readParameterNameMap(Class<?> clazz) {
        try {
            JavaClassFile javaClassFile = new JavaClassFile(clazz);
            Map<Member, JavaClassFile.Parameter[]> result = new HashMap<>(6);
            for (JavaClassFile.Member member : javaClassFile.getMethods()) {
                try {
                    Member javaMember = member.toJavaMember();
                    JavaClassFile.Parameter[] parameters = member.getParameters();
                    result.put(javaMember, parameters);
                } catch (Exception ignored) {
                }
            }
            return result;
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    public static JavaClassFile.Parameter[] getParameterNames(Method method) {
        Class<?> declaringClass = method.getDeclaringClass();
        if (declaringClass.isInterface()) {
            return null;
        }
        Map<Member, JavaClassFile.Parameter[]> memberMap = PARAMETER_NAMES_CACHE.computeIfAbsent(declaringClass, e -> readParameterNameMap(declaringClass));
        return memberMap.get(method);
    }

    public static StaticMethodAccessor newInstance(String classMethodName) {
        return CACHE.computeIfAbsent(classMethodName, StaticMethodAccessor::new);
    }

    public Method getMethod() {
        return method;
    }

    public Class<?>[] getParameterTypes() {
        return parameterTypes;
    }

    public JavaClassFile.Parameter[] getParameters() {
        return parameters;
    }

    @Override
    public String toString() {
        return classMethodName;
    }

}