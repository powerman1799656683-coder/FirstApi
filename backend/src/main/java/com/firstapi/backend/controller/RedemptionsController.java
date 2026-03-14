package com.firstapi.backend.controller;

import com.firstapi.backend.common.ApiResponse;
import com.firstapi.backend.common.PageResponse;
import com.firstapi.backend.model.RedemptionItem;
import com.firstapi.backend.service.RedemptionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/redemptions")
public class RedemptionsController {

    private final RedemptionService service;

    public RedemptionsController(RedemptionService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResponse<RedemptionItem>> list(@RequestParam(value = "keyword", required = false) String keyword) {
        return ApiResponse.ok(service.list(keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<RedemptionItem> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    public ApiResponse<List<RedemptionItem>> create(@RequestBody RedemptionItem.Request request) {
        return ApiResponse.ok("created", service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<RedemptionItem> update(@PathVariable Long id, @RequestBody RedemptionItem.Request request) {
        return ApiResponse.ok("updated", service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok("deleted", true);
    }
}
