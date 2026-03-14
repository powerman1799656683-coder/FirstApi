package com.firstapi.backend.controller;

import com.firstapi.backend.common.ApiResponse;
import com.firstapi.backend.common.PageResponse;
import com.firstapi.backend.model.ApiKeyItem;
import com.firstapi.backend.service.MyApiKeysService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user/api-keys")
public class MyApiKeysController {

    private final MyApiKeysService service;

    public MyApiKeysController(MyApiKeysService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResponse<ApiKeyItem>> list(@RequestParam(value = "keyword", required = false) String keyword) {
        return ApiResponse.ok(service.list(keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<ApiKeyItem> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    public ApiResponse<ApiKeyItem> create(@RequestBody ApiKeyItem.Request request) {
        return ApiResponse.ok("created", service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ApiKeyItem> update(@PathVariable Long id, @RequestBody ApiKeyItem.Request request) {
        return ApiResponse.ok("updated", service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok("deleted", true);
    }

    @PostMapping("/{id}/rotate")
    public ApiResponse<ApiKeyItem> rotate(@PathVariable Long id) {
        return ApiResponse.ok("rotated", service.rotateKey(id));
    }
}
