package com.github.fieldintercept;

import com.github.fieldintercept.entity.Const;
import com.github.fieldintercept.entity.Department;

public class MainTest {
    public static void main(String[] args) {
        ReturnFieldDispatchAop dispatchAop = new ReturnFieldDispatchAop(Const.BEAN_FACTORY);

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
            System.out.println("department = " + department);
        }

//        System.out.println("department = " + department);
    }
}
