package com.github.case1.controller;

import com.github.case1.dto.FolderListResp;
import com.github.case1.service.FolderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RequestMapping("/folder")
@RestController
public class FolderController {
    @Autowired
    private FolderService folderService;

    /**
     * <pre>
     * http://localhost:8080/folder/selectList?id=7
     * [nio-8080-exec-5] c.g.w.case1.dao.FolderMapper$Mock        : findByIds([7]) end 99/ms
     * [nio-8080-exec-5] c.g.w.case1.dao.FolderMapper$Mock        : findByIds([6]) end 53/ms
     * [nio-8080-exec-5] c.g.w.case1.dao.FolderMapper$Mock        : findByIds([5]) end 86/ms
     * [nio-8080-exec-5] c.g.w.case1.dao.FolderMapper$Mock        : findByIds([4]) end 77/ms
     *
     * http://localhost:8080/folder/selectList?id=1,2,3,4,5,6,7
     * [nio-8080-exec-8] c.g.w.case1.dao.FolderMapper$Mock        : findByIds([1, 2, 3, 4, 5, 6, 7]) end 77/ms
     * [nio-8080-exec-8] c.g.w.case1.dao.FolderMapper$Mock        : findByIds([1, 4, 5, 6]) end 79/ms
     * </pre>
     *
     * @param id
     * @return
     */
    @RequestMapping("/selectList")
    public List<FolderListResp> selectList(Long[] id) {
        if (id == null) {
            return Collections.emptyList();
        } else {
            return folderService.selectList(Arrays.asList(id));
        }
    }
}
