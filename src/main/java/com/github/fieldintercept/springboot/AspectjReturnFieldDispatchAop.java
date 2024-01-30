package com.github.fieldintercept.springboot;

import com.github.fieldintercept.ReturnFieldDispatchAop;
import com.github.fieldintercept.annotation.ReturnFieldAop;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutionException;

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
    protected void aopReturningAfter(JoinPoint joinPoint, Object result) throws InvocationTargetException, IllegalAccessException, ExecutionException, InterruptedException {
        returningAfter(joinPoint, result);
    }

    @Override
    protected boolean isNeedPending(JoinPoint joinPoint, Object returnResult) {
        boolean needPending = super.isNeedPending(joinPoint, returnResult);
        if (!needPending) {
            return false;
        }
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        ReturnFieldAop returnFieldAop = findDeclaredAnnotation(method, returnFieldAopCache);
        return returnFieldAop != null && returnFieldAop.batchAggregation();
    }
}