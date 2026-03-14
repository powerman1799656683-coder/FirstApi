package com.firstapi.backend.controller;

import com.firstapi.backend.common.ApiResponse;
import com.firstapi.backend.model.MyRedemptionData;
import com.firstapi.backend.model.MyRedemptionData.RedeemRequest;
import com.firstapi.backend.service.MyRedemptionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/user/redemption", "/api/user/redemptions"})
public class MyRedemptionController {

    private final MyRedemptionService service;

    public MyRedemptionController(MyRedemptionService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<MyRedemptionData> get() {
        return ApiResponse.ok(service.getRedemptions());
    }

    @PostMapping("/redeem")
    public ApiResponse<MyRedemptionData> redeem(@RequestBody RedeemRequest request) {
        return ApiResponse.ok("redeemed", service.redeem(request));
    }
}
