package com.github.case1.controller;

import com.github.case1.dto.OrderSelectListResp;
import com.github.case1.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequestMapping("/order")
@RestController
public class OrderController {
    @Autowired
    private OrderService orderService;

    /**
     * <pre>
     * qps 1660 (这种方式mysql，redis连接池会不够用，需要走本地缓存)
     *
     * java8
     *
     * parallelQuery = false,
     *
     * Jmeter-5.5
     * Threads: 1000
     * Ramp-up: 0.5
     *
     * server:
     *   tomcat:
     *     threads:
     *       max: 3010
     *
     * </pre>
     * <pre>
     * http://localhost:8080/order/selectList
     * [nio-8080-exec-1] c.g.w.case1.dao.InvoiceMapper$Mock       : findByIds([1, 2, 3]) end 95/ms
     * [nio-8080-exec-1] c.g.w.case1.dao.InterviewMapper$Mock     : findByIds([1, 2, 3]) end 75/ms
     * [nio-8080-exec-1] c.g.w.case1.dao.UserMapper$Mock          : findByIds([1, 2, 3]) end 73/ms
     * [nio-8080-exec-1] c.g.w.case1.dao.UserMapper$Mock          : findByIds([3, 2, 1]) end 56/ms
     * [nio-8080-exec-1] c.g.w.case1.dao.BizEnumMapper$Mock       : selectEnumGroupKeyValueList([inter_round], [1, 2, 3]) end 80/ms
     * </pre>
     *
     * @return
     */
    @RequestMapping("/selectList")
    public List<OrderSelectListResp> selectList() {
        return orderService.selectList();
    }


}
