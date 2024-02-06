package com.github.fieldintercept;

import com.github.fieldintercept.entity.Const;
import com.github.fieldintercept.entity.Department;
import com.github.securityfilter.util.AccessUserUtil;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainTest {
    private transient Integer f = 1;

    public static void main(String[] args) {
        ExecutorService executorService = Executors.newFixedThreadPool(10);

        ReturnFieldDispatchAop<?> dispatchAop = ReturnFieldDispatchAop.newInstance(Const.BEAN_FACTORY);
        dispatchAop.setBatchAggregation(ReturnFieldDispatchAop.BatchAggregationEnum.auto);
        dispatchAop.setBatchAggregationMilliseconds(3000);
        dispatchAop.setBatchAggregationMinConcurrentCount(1);
        dispatchAop.setTaskExecutor(executorService::submit);
        dispatchAop.setTaskDecorate(AccessUserUtil::runnable);
        for (int i = 0; i < 5; i++) {
            int finalI = i;
            new Thread() {
                @Override
                public void run() {
                    AccessUserUtil.setCurrentThreadAccessUser(finalI);

                    while (true) {
                        Department department = new Department();
                        dispatchAop.autowiredFieldValue(department);
                        System.out.println(Thread.currentThread() + "department = " + department);
                    }
                }
            }.start();
        }
        for (int i = 0; i < 3; i++) {
            int finalI = i;
            new Thread() {
                @Override
                public void run() {
                    AccessUserUtil.setCurrentThreadAccessUser(finalI);

                    while (true) {
                        Department department = new Department();
                        dispatchAop.autowiredFieldValue(department);
                        System.out.println(Thread.currentThread() + "departmen222t = " + department);
                    }
                }
            }.start();
        }
        while (true) {
            AccessUserUtil.setCurrentThreadAccessUser("true");
            Department department = new Department();
            dispatchAop.autowiredFieldValue(department);
            System.out.println(Thread.currentThread() + "111department = " + department);
        }

//        System.out.println("department = " + department);
    }
}
