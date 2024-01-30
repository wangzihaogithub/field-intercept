package com.github.fieldintercept;

import com.github.fieldintercept.entity.Const;
import com.github.fieldintercept.entity.Department;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainTest {
    public static void main(String[] args) {
        ExecutorService executorService = Executors.newFixedThreadPool(10);

        ReturnFieldDispatchAop<?> dispatchAop = ReturnFieldDispatchAop.newInstance(Const.BEAN_FACTORY);
        dispatchAop.setBatchAggregation(true);
        dispatchAop.setBatchAggregationMilliseconds(3000);
        dispatchAop.setBatchAggregationMinConcurrentCount(1);
        dispatchAop.setTaskExecutor(executorService::submit);

        for (int i = 0; i < 5; i++) {
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
