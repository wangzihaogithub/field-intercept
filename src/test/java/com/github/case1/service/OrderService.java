package com.github.case1.service;

import com.github.fieldintercept.annotation.ReturnFieldAop;
import com.github.case1.dto.OrderSelectListResp;
import com.github.fieldintercept.util.FieldCompletableFuture;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class OrderService {

    @ReturnFieldAop
    public CompletableFuture<List<OrderSelectListResp>> selectList() {
        List<OrderSelectListResp> list = new ArrayList<>();
        list.add(new OrderSelectListResp("1,2", 1L, "1,2.3", 3L));
        list.add(new OrderSelectListResp("2", 2L, "2", 2L));
        list.add(new OrderSelectListResp("3", 3L, "3", 1L));
        return new FieldCompletableFuture<>(list);
    }
}
