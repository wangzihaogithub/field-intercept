package com.github.case2.controller;

import com.github.case2.dto.FolderListResp;
import com.github.case2.service.FolderService;
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
     * [nio-8080-exec-4] c.g.w.case2.dao.FolderMapper$Mock        : findByIds([7]) end 73/ms
     * [nio-8080-exec-4] c.g.w.case2.dao.FolderMapper$Mock        : findByIds([6]) end 68/ms
     * [nio-8080-exec-4] c.g.w.case2.dao.FolderMapper$Mock        : findByIds([5]) end 95/ms
     * [nio-8080-exec-4] c.g.w.case2.dao.FolderMapper$Mock        : findByIds([4]) end 98/ms
     *
     * http://localhost:8080/folder/selectList?id=1,2,3,4,5,6,7
     * [nio-8080-exec-5] c.g.w.case2.dao.FolderMapper$Mock        : findByIds([1, 2, 3, 4, 5, 6, 7]) end 65/ms
     * [nio-8080-exec-5] c.g.w.case2.dao.FolderMapper$Mock        : findByIds([1, 4, 5, 6]) end 103/ms
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
