package com.firstapi.backend.controller;

import com.firstapi.backend.common.ApiResponse;
import com.firstapi.backend.common.PageResponse;
import com.firstapi.backend.model.AccountItem;
import com.firstapi.backend.service.AccountOAuthService;
import com.firstapi.backend.service.AccountService;
import com.firstapi.backend.service.OAuthTokenRefreshService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/accounts")
public class AccountController {

    private final AccountService service;
    private final AccountOAuthService oauthService;
    private final OAuthTokenRefreshService oauthTokenRefreshService;

    public AccountController(AccountService service, AccountOAuthService oauthService,
                             OAuthTokenRefreshService oauthTokenRefreshService) {
        this.service = service;
        this.oauthService = oauthService;
        this.oauthTokenRefreshService = oauthTokenRefreshService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AccountItem>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "platform", required = false) String platform,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "authMethod", required = false) String authMethod,
            @RequestParam(value = "scheduleEnabled", required = false) String scheduleEnabled,
            @RequestParam(value = "groupId", required = false) Long groupId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sortBy", defaultValue = "priorityValue") String sortBy,
            @RequestParam(value = "sortOrder", defaultValue = "asc") String sortOrder) {
        return ApiResponse.ok(service.list(keyword, platform, status, authMethod, scheduleEnabled, groupId, page, size, sortBy, sortOrder));
    }

    @GetMapping("/{id}")
    public ApiResponse<AccountItem> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    public ApiResponse<AccountItem> create(@RequestBody AccountItem.Request req) {
        return ApiResponse.ok("\u521b\u5efa\u6210\u529f", service.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<AccountItem> update(@PathVariable Long id, @RequestBody AccountItem.Request req) {
        return ApiResponse.ok("\u66f4\u65b0\u6210\u529f", service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok("\u5220\u9664\u6210\u529f", true);
    }

    @PostMapping("/{id}/test")
    public ApiResponse<AccountItem> test(@PathVariable Long id) {
        return ApiResponse.ok("\u6d4b\u8bd5\u5b8c\u6210", service.test(id));
    }

    @PostMapping("/{id}/quota/recover")
    public ApiResponse<AccountItem> recoverQuota(@PathVariable Long id) {
        return ApiResponse.ok("恢复成功", service.recoverQuota(id));
    }

    @PostMapping("/{id}/refresh-oauth")
    public ApiResponse<Map<String, Object>> refreshOAuth(@PathVariable Long id) {
        boolean success = oauthTokenRefreshService.tryRefreshNow(id);
        AccountItem updated = service.get(id);
        return ApiResponse.ok(success ? "OAuth 刷新成功" : "OAuth 刷新失败",
                Map.of("success", success, "oauthTokenExpiresAt",
                        updated.getOauthTokenExpiresAt() != null ? updated.getOauthTokenExpiresAt() : ""));
    }

    // OAuth endpoints
    @PostMapping("/oauth/start")
    public ApiResponse<Map<String, Object>> oauthStart(@RequestBody Map<String, String> req) {
        return ApiResponse.ok(oauthService.start(req));
    }

    @PostMapping("/oauth/exchange")
    public ApiResponse<Map<String, Object>> oauthExchange(@RequestBody Map<String, String> req) {
        return ApiResponse.ok(oauthService.exchange(req));
    }

    // Batch operations
    @PostMapping("/batch/toggle-schedule")
    public ApiResponse<Map<String, Object>> batchToggleSchedule(@RequestBody Map<String, Object> req) {
        @SuppressWarnings("unchecked")
        List<Number> ids = (List<Number>) req.get("ids");
        Boolean enable = (Boolean) req.get("enable");
        if (ids == null || ids.isEmpty()) {
            return ApiResponse.ok(Map.of("affected", 0));
        }
        List<Long> longIds = ids.stream().map(Number::longValue).collect(java.util.stream.Collectors.toList());
        int affected = service.batchToggleSchedule(longIds, enable != null && enable);
        return ApiResponse.ok("\u6279\u91cf\u64cd\u4f5c\u5b8c\u6210", Map.of("affected", affected));
    }

    @PostMapping("/batch/delete")
    public ApiResponse<Map<String, Object>> batchDelete(@RequestBody Map<String, Object> req) {
        @SuppressWarnings("unchecked")
        List<Number> ids = (List<Number>) req.get("ids");
        if (ids == null || ids.isEmpty()) {
            return ApiResponse.ok(Map.of("affected", 0));
        }
        List<Long> longIds = ids.stream().map(Number::longValue).collect(java.util.stream.Collectors.toList());
        int affected = service.batchDelete(longIds);
        return ApiResponse.ok("\u6279\u91cf\u5220\u9664\u5b8c\u6210", Map.of("affected", affected));
    }

    @PostMapping("/batch/test")
    public ApiResponse<List<AccountItem>> batchTest(@RequestBody Map<String, Object> req) {
        @SuppressWarnings("unchecked")
        List<Number> ids = (List<Number>) req.get("ids");
        if (ids == null || ids.isEmpty()) {
            return ApiResponse.ok(List.of());
        }
        List<Long> longIds = ids.stream().map(Number::longValue).collect(java.util.stream.Collectors.toList());
        return ApiResponse.ok("\u6279\u91cf\u6d4b\u8bd5\u5b8c\u6210", service.batchTest(longIds));
    }
}
