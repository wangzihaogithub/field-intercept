package com.github;

import com.github.fieldintercept.ReturnFieldDispatchAop;
import com.github.fieldintercept.util.FieldCompletableFuture;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public class CompletableFutureTest {
    public static void main(String[] args) {
        FieldCompletableFuture w = new FieldCompletableFuture("ff");
        FieldCompletableFuture future1 = w.whenComplete(new BiConsumer() {
                    @Override
                    public void accept(Object o, Object o2) {
                        System.out.println("o = " + o);
                    }
                }).whenComplete(new BiConsumer() {
                    @Override
                    public void accept(Object o, Object o2) {
                        System.out.println("o = " + o);
                    }
                }).handle(new BiFunction() {
                    @Override
                    public Object apply(Object o, Object o2) {
                        return "11";
                    }
                }).whenComplete(new BiConsumer() {
                    @Override
                    public void accept(Object o, Object o2) {
                        System.out.println("o = " + o);
                    }
                })
                .handle(new BiFunction() {
                    @Override
                    public Object apply(Object o, Object o2) {
                        return "fsada";
                    }
                })
                .handle(new BiFunction() {
                    @Override
                    public Object apply(Object o, Object o2) {
                        return "999";
                    }
                })
                .whenComplete(new BiConsumer() {
                    @Override
                    public void accept(Object o, Object o2) {
                        System.out.println("o = " + o);
                    }
                });
//        w.complete();
        future1.snapshot(null);
        w.complete("qq");

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
