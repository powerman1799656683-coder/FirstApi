package com.firstapi.backend.controller;

import com.firstapi.backend.common.ApiResponse;
import com.firstapi.backend.common.PageResponse;
import com.firstapi.backend.model.AnnouncementItem;
import com.firstapi.backend.service.AnnouncementService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/announcements")
public class AnnouncementController {

    private final AnnouncementService service;

    public AnnouncementController(AnnouncementService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResponse<AnnouncementItem>> list(@RequestParam(value = "keyword", required = false) String keyword) {
        return ApiResponse.ok(service.list(keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<AnnouncementItem> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    public ApiResponse<AnnouncementItem> create(@RequestBody AnnouncementItem.Request req) {
        return ApiResponse.ok("创建成功", service.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<AnnouncementItem> update(@PathVariable Long id, @RequestBody AnnouncementItem.Request req) {
        return ApiResponse.ok("更新成功", service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok("删除成功", true);
    }
}
