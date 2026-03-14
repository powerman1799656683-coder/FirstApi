package com.firstapi.backend.controller;

import com.firstapi.backend.common.ApiResponse;
import com.firstapi.backend.common.PageResponse;
import com.firstapi.backend.model.PromoItem;
import com.firstapi.backend.service.PromoService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/promos")
public class PromosController {

    private final PromoService service;

    public PromosController(PromoService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResponse<PromoItem>> list(@RequestParam(value = "keyword", required = false) String keyword) {
        return ApiResponse.ok(service.list(keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<PromoItem> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    public ApiResponse<PromoItem> create(@RequestBody PromoItem.Request request) {
        return ApiResponse.ok("created", service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<PromoItem> update(@PathVariable Long id, @RequestBody PromoItem.Request request) {
        return ApiResponse.ok("updated", service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok("deleted", true);
    }
}
