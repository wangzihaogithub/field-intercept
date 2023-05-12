package com.github.case1.dto;

import com.github.fieldintercept.annotation.FieldConsumer;
import com.github.case1.enumer.Providers;

import java.util.LinkedList;
import java.util.List;

public class FolderParent {
    private String name;
    private Long parentId;

    @FieldConsumer(value = Providers.FOLDER, keyField = "parentId")
    private FolderParent parent;

    public String getNamePath() {
        List<String> list = new LinkedList<>();
        for (FolderParent curr = this; curr != null; curr = curr.parent) {
            list.add(0, curr.name);
        }
        return String.join("/", list);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

}