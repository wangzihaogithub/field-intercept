package com.github.case2.controller;

import com.github.case2.dto.OrderSelectListResp;
import com.github.case2.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RequestMapping("/order")
@RestController
public class OrderController {
    @Autowired
    private OrderService orderService;

    /**
     * qps 100+ （这种全局阻塞的方式增加了rt响应时间，所以qps大幅降低）
     * <pre>
     *     -Xmx556m -Xms556m -XX:+UseG1GC
     *
     *         batchAggregation = true,
     *
     * server:
     *   tomcat:
     *     threads:
     *       max: 3010
     * </pre>
     * <pre>
     * http://localhost:8080/order/selectList
     * [nio-8080-exec-1] c.g.w.case2.dao.InvoiceMapper$Mock       : findByIds([1, 2, 3]) end 75/ms
     * [ieldIntercept-1] c.g.w.case2.dao.InterviewMapper$Mock     : findByIds([1, 2, 3]) end 72/ms
     * [ieldIntercept-2] c.g.w.case2.dao.UserMapper$Mock          : findByIds([1, 2, 3]) end 78/ms
     * [ieldIntercept-2] c.g.w.case2.dao.UserMapper$Mock          : findByIds([3, 2, 1]) end 79/ms
     * [ieldIntercept-2] c.g.w.case2.dao.BizEnumMapper$Mock       : selectEnumGroupKeyValueList([inter_round], [1, 2, 3]) end 74/ms
     * </pre>
     *
     * @param name
     * @return
     */
    @RequestMapping("/selectList")
    public CompletableFuture<List<OrderSelectListResp>> selectList(String name) {
        return orderService.selectList(name);
    }

}
