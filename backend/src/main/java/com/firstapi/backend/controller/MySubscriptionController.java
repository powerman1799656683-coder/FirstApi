package com.firstapi.backend.controller;

import com.firstapi.backend.common.ApiResponse;
import com.firstapi.backend.model.MySubscriptionData;
import com.firstapi.backend.service.MySubscriptionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user/subscription")
public class MySubscriptionController {

    private final MySubscriptionService service;

    public MySubscriptionController(MySubscriptionService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<MySubscriptionData> get() {
        return ApiResponse.ok(service.getSubscription());
    }

    @PostMapping("/renew")
    public ApiResponse<MySubscriptionData> renew() {
        return ApiResponse.ok("续费成功", service.renew());
    }
}
