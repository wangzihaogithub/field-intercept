package com.github.fieldintercept;

import com.github.fieldintercept.entity.Const;
import com.github.fieldintercept.entity.Department;

public class MainTest {
    public static void main(String[] args) {
        ReturnFieldDispatchAop dispatchAop = new ReturnFieldDispatchAop(Const.BEAN_FACTORY);

        Department department = new Department();
        dispatchAop.autowiredFieldValue(department);

        System.out.println("department = " + department);
    }
}
