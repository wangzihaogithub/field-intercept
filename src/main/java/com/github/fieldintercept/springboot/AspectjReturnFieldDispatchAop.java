package com.github.fieldintercept.springboot;

import com.github.fieldintercept.ReturnFieldDispatchAop;
import com.github.fieldintercept.annotation.ReturnFieldAop;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;

@Aspect
public class AspectjReturnFieldDispatchAop extends ReturnFieldDispatchAop<JoinPoint> {

    @Before(value = "@annotation(com.github.fieldintercept.annotation.ReturnFieldAop)")
    protected void aopBefore() {
        before();
    }

    @After(value = "@annotation(com.github.fieldintercept.annotation.ReturnFieldAop)")
    protected void aopAfter() {
        after();
    }

    @AfterReturning(value = "@annotation(com.github.fieldintercept.annotation.ReturnFieldAop)", returning = "result")
    protected void aopReturningAfter(JoinPoint joinPoint, Object result) {
        returningAfter(joinPoint, result);
    }

    @Override
    protected ReturnFieldAop getAnnotationReturnFieldAop(JoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        return findDeclaredAnnotation(method, returnFieldAopCache);
    }

}