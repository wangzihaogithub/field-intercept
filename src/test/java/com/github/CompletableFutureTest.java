package com.github;

import com.github.fieldintercept.ReturnFieldDispatchAop;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class CompletableFutureTest {
    public static void main(String[] args) {
        ReturnFieldDispatchAop f = ReturnFieldDispatchAop.newInstance(new HashMap<>());
        ReturnFieldDispatchAop f1 = new ReturnFieldDispatchAop() {
            @Override
            protected void aopBefore() {

            }

            @Override
            protected void aopAfter() {

            }

            @Override
            protected void aopReturningAfter(Object o, Object result) throws InvocationTargetException, IllegalAccessException, ExecutionException, InterruptedException {

            }
        };


        CompletableFuture future = new CompletableFuture();

        future.whenComplete(((o, o2) -> {
            System.out.println("whenComplete = " + o);

        })).thenAccept(o -> {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("thenAccept = " + o);
        });
        new Thread() {
            @Override
            public void run() {
                System.out.println("future = " + future);

                future.complete(1);
            }
        }.start();

    }
}
