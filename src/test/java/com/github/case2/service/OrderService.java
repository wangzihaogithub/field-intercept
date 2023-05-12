package com.github.case2.service;

import com.github.fieldintercept.annotation.ReturnFieldAop;
import com.github.fieldintercept.util.FieldCompletableFuture;
import com.github.case2.dto.OrderSelectListResp;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class OrderService {

    @ReturnFieldAop(batchAggregation = true)
    public CompletableFuture<List<OrderSelectListResp>> selectList(String name) {
        List<OrderSelectListResp> list = new ArrayList<>();
        list.add(new OrderSelectListResp("1,2", 1L, "1,2.3", 3L));
        list.add(new OrderSelectListResp("2", 2L, "2", 2L));
        list.add(new OrderSelectListResp("3", 3L, "3", 1L));
        return new FieldCompletableFuture<>(list);
    }
}
