package com.firstapi.backend.controller;

import com.firstapi.backend.common.ApiResponse;
import com.firstapi.backend.common.PageResponse;
import com.firstapi.backend.model.AccountItem;
import com.firstapi.backend.service.AccountService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/accounts")
public class AccountController {

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResponse<AccountItem>> list(@RequestParam(value = "keyword", required = false) String keyword) {
        return ApiResponse.ok(service.list(keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<AccountItem> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    public ApiResponse<AccountItem> create(@RequestBody AccountItem.Request req) {
        return ApiResponse.ok("created", service.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<AccountItem> update(@PathVariable Long id, @RequestBody AccountItem.Request req) {
        return ApiResponse.ok("updated", service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok("deleted", true);
    }

    @PostMapping("/{id}/test")
    public ApiResponse<AccountItem> test(@PathVariable Long id) {
        return ApiResponse.ok("tested", service.test(id));
    }
}
