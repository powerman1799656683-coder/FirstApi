package com.firstapi.backend.controller;

import com.firstapi.backend.common.ApiResponse;
import com.firstapi.backend.common.PageResponse;
import com.firstapi.backend.model.IpItem;
import com.firstapi.backend.service.IpService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/ips")
public class IPsController {

    private final IpService service;

    public IPsController(IpService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResponse<IpItem>> list(@RequestParam(value = "keyword", required = false) String keyword) {
        return ApiResponse.ok(service.list(keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<IpItem> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    public ApiResponse<IpItem> create(@RequestBody IpItem.Request request) {
        return ApiResponse.ok("创建成功", service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<IpItem> update(@PathVariable Long id, @RequestBody IpItem.Request request) {
        return ApiResponse.ok("更新成功", service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok("删除成功", true);
    }

    @PostMapping("/{id}/test")
    public ApiResponse<IpItem> test(@PathVariable Long id) {
        return ApiResponse.ok("测试完成", service.testIp(id));
    }

    @PostMapping("/test-all")
    public ApiResponse<List<IpItem>> testAll() {
        return ApiResponse.ok("测试完成", service.testAll());
    }
}
