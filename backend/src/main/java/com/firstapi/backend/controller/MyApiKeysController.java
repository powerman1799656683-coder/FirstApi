package com.firstapi.backend.controller;

import com.firstapi.backend.common.ApiResponse;
import com.firstapi.backend.common.PageResponse;
import com.firstapi.backend.model.ApiKeyItem;
import com.firstapi.backend.model.GroupItem;
import com.firstapi.backend.repository.GroupRepository;
import com.firstapi.backend.service.MyApiKeysService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/user/api-keys")
public class MyApiKeysController {

    private final MyApiKeysService service;
    private final GroupRepository groupRepository;

    public MyApiKeysController(MyApiKeysService service, GroupRepository groupRepository) {
        this.service = service;
        this.groupRepository = groupRepository;
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
        return ApiResponse.ok("创建成功", service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ApiKeyItem> update(@PathVariable Long id, @RequestBody ApiKeyItem.Request request) {
        return ApiResponse.ok("更新成功", service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok("删除成功", true);
    }

    @PostMapping("/{id}/rotate")
    public ApiResponse<ApiKeyItem> rotate(@PathVariable Long id) {
        return ApiResponse.ok("轮换成功", service.rotateKey(id));
    }

    @GetMapping("/{id}/reveal")
    public ApiResponse<ApiKeyItem> reveal(@PathVariable Long id) {
        return ApiResponse.ok(service.revealKey(id));
    }

    public record GroupOption(Long id, String name, String platform) {}

    @GetMapping("/groups")
    public ApiResponse<List<GroupOption>> listGroups() {
        List<GroupOption> options = groupRepository.findAll().stream()
                .filter(g -> "正常".equals(g.getStatus()))
                .map(g -> new GroupOption(g.getId(), g.getName(), g.getPlatform()))
                .collect(Collectors.toList());
        return ApiResponse.ok(options);
    }
}
