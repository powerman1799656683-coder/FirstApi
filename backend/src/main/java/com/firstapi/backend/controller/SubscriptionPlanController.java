package com.firstapi.backend.controller;

import com.firstapi.backend.common.ApiResponse;
import com.firstapi.backend.common.PageResponse;
import com.firstapi.backend.model.SubscriptionPlanItem;
import com.firstapi.backend.service.SubscriptionPlanService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/subscription-plans")
public class SubscriptionPlanController {

    private final SubscriptionPlanService service;

    public SubscriptionPlanController(SubscriptionPlanService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResponse<SubscriptionPlanItem>> list(@RequestParam(value = "keyword", required = false) String keyword) {
        return ApiResponse.ok(service.list(keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<SubscriptionPlanItem> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    public ApiResponse<SubscriptionPlanItem> create(@RequestBody SubscriptionPlanItem.Request req) {
        return ApiResponse.ok("创建成功", service.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<SubscriptionPlanItem> update(@PathVariable Long id, @RequestBody SubscriptionPlanItem.Request req) {
        return ApiResponse.ok("更新成功", service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok("删除成功", true);
    }
}
