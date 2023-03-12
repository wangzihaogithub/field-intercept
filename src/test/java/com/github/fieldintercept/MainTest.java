package com.github.fieldintercept;

import com.github.fieldintercept.entity.Const;
import com.github.fieldintercept.entity.Department;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainTest {
    public static void main(String[] args) {
        ExecutorService executorService = Executors.newFixedThreadPool(10);

        ReturnFieldDispatchAop dispatchAop = new ReturnFieldDispatchAop(Const.BEAN_FACTORY);
        dispatchAop.setBatchAggregation(true);
        dispatchAop.setBatchAggregationTimeMs(1000);
        dispatchAop.setTaskExecutor(executorService::submit);

        for (int i = 0; i < 100; i++) {
            new Thread(){
                @Override
                public void run() {
                    while (true) {
                        Department department = new Department();
                        dispatchAop.autowiredFieldValue(department);
                        System.out.println("department = " + department);
                    }
                }
            }.start();
        }
        while (true) {
            Department department = new Department();
            dispatchAop.autowiredFieldValue(department);
            System.out.println("111department = " + department);
        }

//        System.out.println("department = " + department);
    }
}
