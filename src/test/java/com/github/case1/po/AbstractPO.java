package com.github.case1.po;

import java.io.Serializable;

public abstract class AbstractPO<ID> implements Serializable {
    public abstract ID getId();
    public abstract void setId(ID id);
}
