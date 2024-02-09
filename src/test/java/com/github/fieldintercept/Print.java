package com.github.fieldintercept;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Print {
    public static void scheduledPrint() {
        Executors.newScheduledThreadPool(1).scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                ReturnFieldDispatchAop<Object> instance = ReturnFieldDispatchAop.getInstance();
                if (!instance.pendingSignalThreadList.isEmpty()) {
                    System.out.println(instance.pendingSignalThreadList);
                }
            }
        }, 1000, 1000, TimeUnit.MILLISECONDS);
    }
}
