package com.firstapi.backend.controller;

import com.firstapi.backend.common.ApiResponse;
import com.firstapi.backend.common.PageResponse;
import com.firstapi.backend.model.ApiKeyItem;
import com.firstapi.backend.model.UserItem;
import com.firstapi.backend.service.MyApiKeysService;
import com.firstapi.backend.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
public class UserController {

    private final UserService service;
    private final MyApiKeysService apiKeysService;

    public UserController(UserService service, MyApiKeysService apiKeysService) {
        this.service = service;
        this.apiKeysService = apiKeysService;
    }

    @GetMapping
    public ApiResponse<PageResponse<UserItem>> list(@RequestParam(value = "keyword", required = false) String keyword) {
        return ApiResponse.ok(service.list(keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserItem> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    public ApiResponse<UserItem> create(@RequestBody UserItem.Request req) {
        return ApiResponse.ok("创建成功", service.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserItem> update(@PathVariable Long id, @RequestBody UserItem.Request req) {
        return ApiResponse.ok("更新成功", service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok("删除成功", true);
    }

    @GetMapping("/{id}/api-keys")
    public ApiResponse<PageResponse<ApiKeyItem>> listApiKeys(@PathVariable Long id) {
        return ApiResponse.ok(apiKeysService.listByUserId(id));
    }

    @PostMapping("/{id}/topup")
    public ApiResponse<UserItem> topup(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object raw = body.get("amount");
        if (!(raw instanceof Number)) {
            throw new IllegalArgumentException("金额参数无效");
        }
        double amount = ((Number) raw).doubleValue();
        if (amount <= 0 || !Double.isFinite(amount)) {
            throw new IllegalArgumentException("金额必须为正数");
        }
        return ApiResponse.ok("充值成功", service.adjustBalance(id, amount));
    }

    @PostMapping("/{id}/refund")
    public ApiResponse<UserItem> refund(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object raw = body.get("amount");
        if (!(raw instanceof Number)) {
            throw new IllegalArgumentException("金额参数无效");
        }
        double amount = ((Number) raw).doubleValue();
        if (amount <= 0 || !Double.isFinite(amount)) {
            throw new IllegalArgumentException("金额必须为正数");
        }
        return ApiResponse.ok("退款成功", service.adjustBalance(id, -amount));
    }
}
