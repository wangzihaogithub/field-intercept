package com.github.fieldintercept.springboot;

import com.github.fieldintercept.ReturnFieldDispatchAop;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

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

}