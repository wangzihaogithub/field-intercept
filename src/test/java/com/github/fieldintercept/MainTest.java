package com.github.fieldintercept;

import com.github.fieldintercept.entity.Const;
import com.github.fieldintercept.entity.Department;
import com.github.fieldintercept.entity.User;
import com.github.fieldintercept.util.FieldCompletableFuture;
import com.github.securityfilter.util.AccessUserUtil;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public class MainTest {
    private transient Integer f = 1;

    private static void test(ReturnFieldDispatchAop<?> dispatchAop) {
        Department department = new Department();
        dispatchAop.autowiredFieldValue(new FieldCompletableFuture<>(department).handle(new BiFunction<Department, Throwable, Object>() {
            @Override
            public Object apply(Department department, Throwable throwable) {
                return new User(1, "用户1", 0, 2, "1,2");
            }
        })).whenComplete(new BiConsumer<Object, Throwable>() {
            @Override
            public void accept(Object o, Throwable throwable) {
                System.out.println("o = " + o);
            }
        });
    }

    private static void test2(ReturnFieldDispatchAop<?> dispatchAop) {

        for (int i = 0; i < 5; i++) {
            int finalI = i;
            new Thread() {
                @Override
                public void run() {
                    AccessUserUtil.setCurrentThreadAccessUser(finalI);

                    while (true) {
                        Department department = new Department();
                        dispatchAop.autowiredFieldValue(new FieldCompletableFuture<>(department));
                        System.out.println(Thread.currentThread() + "department = " + department);
//                        try {
//                            Thread.sleep(1000);
//                        } catch (InterruptedException e) {
//                            e.printStackTrace();
//                        }
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
                        dispatchAop.autowiredFieldValue(new FieldCompletableFuture<>(department));
                        System.out.println(Thread.currentThread() + "departmen222t = " + department);
                    }
                }
            }.start();
        }
        while (true) {
            AccessUserUtil.setCurrentThreadAccessUser("true");
            AccessUserUtil.runnable(new Runnable() {
                @Override
                public void run() {

                    Department department = new Department();
                    dispatchAop.autowiredFieldValue(new FieldCompletableFuture<>(department).handle(new BiFunction<Department, Throwable, Object>() {
                        @Override
                        public Object apply(Department department, Throwable throwable) {
                            return new User(1, "用户1", 0, 2, "1,2");
                        }
                    })).whenComplete(new BiConsumer<Object, Throwable>() {
                        @Override
                        public void accept(Object o, Throwable throwable) {
                            System.out.println("o = " + o);
                        }
                    });
                    System.out.println(Thread.currentThread() + "111department = " + department);
                }
            }).run();
        }
    }

    public static void main(String[] args) {
        ExecutorService executorService = Executors.newFixedThreadPool(10);

        ReturnFieldDispatchAop<?> dispatchAop = ReturnFieldDispatchAop.newInstance(Const.BEAN_FACTORY);
        dispatchAop.setBatchAggregation(ReturnFieldDispatchAop.BatchAggregationEnum.auto);
        dispatchAop.setBatchAggregationPollMilliseconds(3000);
        dispatchAop.setBatchAggregationPollMaxSize(5);
        dispatchAop.setTaskExecutor(executorService);
        dispatchAop.setTaskDecorate(AccessUserUtil::runnable);

        test2(dispatchAop);

//        System.out.println("department = " + department);
    }
}
