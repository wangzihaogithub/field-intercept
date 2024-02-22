package com.github.fieldintercept;

import com.github.fieldintercept.annotation.FieldConsumer;
import com.github.fieldintercept.entity.Const;
import com.github.fieldintercept.entity.Department;
import com.github.fieldintercept.entity.User;
import com.github.fieldintercept.util.FieldCompletableFuture;
import com.github.securityfilter.util.AccessUserUtil;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

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
    public static class User1{
        private String id;
        @FieldConsumer(value = Const.CUSTOMER_USER, keyField = "id")
        private List<Map> users;
        @FieldConsumer(value = Const.CUSTOMER_USER, keyField = "id",valueField = "${_deptId}")
        private String deptIds;
    }
    public static class Dept{
        private Object id;
        @FieldConsumer(value = Const.CUSTOMER_DEPT, keyField = "id")
        private List<Map> depts;
    }
    public static class Tenant{
        private Object id;
        private Dept dept;
    }
    private static void test3(ReturnFieldDispatchAop<?> dispatchAop){
        User1 user1 = new User1();
        user1.deptIds="1";
        FieldCompletableFuture future = new FieldCompletableFuture<>(user1)
                .thenApply(new Function<User1, Dept>() {
                    @Override
                    public Dept apply(User1 user) {
                        Dept dept = new Dept();
                        dept.id = user.deptIds;
                        if (1 == 1) {
//                            throw new RuntimeException(ErrorMessage.TIANRUN_POLL_ERROR);
                        }
                        return dept;
                    }
                })
                .thenApply(new Function<Dept, Tenant>() {
                    @Override
                    public Tenant apply(Dept dept) {
                        Object accessUser = AccessUserUtil.getAccessUser();
                        Object accessUser1 = AccessUserUtil.getAccessUser();
                        Tenant tenant = new Tenant();
                        tenant.dept = dept;
                        return tenant;
                    }
                })
                .thenApply(new Function<Tenant, Object>() {
                    @Override
                    public Object apply(Tenant dept) {
                        Object accessUser = AccessUserUtil.getAccessUser();
                        Object accessUser1 = AccessUserUtil.getAccessUser();
                        Tenant tenant = new Tenant();
                        return tenant;
                    }
                });
        future.completeExceptionally(new Throwable());

        dispatchAop.autowiredFieldValue(future);
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

        test3(dispatchAop);

//        System.out.println("department = " + department);
    }
}
