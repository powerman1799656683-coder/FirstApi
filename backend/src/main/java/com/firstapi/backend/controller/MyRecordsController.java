package com.firstapi.backend.controller;

import com.firstapi.backend.common.ApiResponse;
import com.firstapi.backend.model.MyRecordsData;
import com.firstapi.backend.service.MyRecordsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user/records")
public class MyRecordsController {

    private final MyRecordsService service;

    public MyRecordsController(MyRecordsService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<MyRecordsData> get(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.getRecords(keyword));
    }
}
