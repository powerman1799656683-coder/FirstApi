package com.firstapi.backend.controller;

import com.firstapi.backend.common.ApiResponse;
import com.firstapi.backend.common.PageResponse;
import com.firstapi.backend.model.GroupItem;
import com.firstapi.backend.service.GroupService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/groups")
public class GroupController {

    private final GroupService service;

    public GroupController(GroupService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResponse<GroupItem>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "platform", required = false) String platform,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "groupType", required = false) String groupType) {
        return ApiResponse.ok(service.list(keyword, platform, status, groupType));
    }

    @GetMapping("/{id}")
    public ApiResponse<GroupItem> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    public ApiResponse<GroupItem> create(@RequestBody GroupItem.Request req) {
        return ApiResponse.ok("创建成功", service.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<GroupItem> update(@PathVariable Long id, @RequestBody GroupItem.Request req) {
        return ApiResponse.ok("更新成功", service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok("删除成功", true);
    }
}
