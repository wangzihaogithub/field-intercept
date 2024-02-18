package com.github;

import com.github.fieldintercept.ReturnFieldDispatchAop;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

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
            protected void aopReturningAfter(Object o, Object result) {

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
