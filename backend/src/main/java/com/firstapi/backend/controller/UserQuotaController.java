package com.firstapi.backend.controller;

import com.firstapi.backend.common.ApiResponse;
import com.firstapi.backend.common.CurrentSessionHolder;
import com.firstapi.backend.model.AuthenticatedUser;
import com.firstapi.backend.model.SubscriptionItem;
import com.firstapi.backend.service.DailyQuotaService;
import com.firstapi.backend.service.SubscriptionService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user/quota")
public class UserQuotaController {

    private final SubscriptionService subscriptionService;
    private final DailyQuotaService dailyQuotaService;

    public UserQuotaController(SubscriptionService subscriptionService,
                               DailyQuotaService dailyQuotaService) {
        this.subscriptionService = subscriptionService;
        this.dailyQuotaService = dailyQuotaService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> getQuotaSummary() {
        AuthenticatedUser user = CurrentSessionHolder.require();
        List<Map<String, Object>> result = new ArrayList<>();
        for (SubscriptionItem sub : subscriptionService.getActiveSubscriptions(user.getId())) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", sub.getId());
            item.put("group", sub.getGroup());
            item.put("usage", sub.getUsage());
            item.put("progress", sub.getProgress());
            item.put("expiry", sub.getExpiry());
            item.put("status", sub.getStatus());
            result.add(item);
        }
        return ApiResponse.ok(result);
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> getFullSummary() {
        AuthenticatedUser user = CurrentSessionHolder.require();

        // 订阅数据（按 uid 匹配，todayUsed 按各订阅分组单独计算）
        List<Map<String, Object>> subscriptions = new ArrayList<>();
        for (SubscriptionItem sub : subscriptionService.getActiveSubscriptions(user.getId())) {
            // 按该订阅对应分组查当日实际消耗
            java.math.BigDecimal todayUsed = dailyQuotaService.getTodayUsageByGroup(user.getId(), sub.getGroupId());
            Map<String, Object> item = new HashMap<>();
            item.put("id", sub.getId());
            item.put("group", sub.getGroup());
            item.put("usage", sub.getUsage());
            item.put("progress", sub.getProgress());
            item.put("expiry", sub.getExpiry());
            item.put("status", sub.getStatus());
            item.put("dailyLimit", sub.getDailyLimit());
            item.put("todayUsed", todayUsed.setScale(10, java.math.RoundingMode.HALF_UP).toPlainString());
            subscriptions.add(item);
        }

        // 每日配额数据
        List<Map<String, Object>> dailyQuotas = dailyQuotaService.listUserQuotaSummary(user.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("subscriptions", subscriptions);
        result.put("dailyQuotas", dailyQuotas);
        return ApiResponse.ok(result);
    }

    @PutMapping("/daily-limit/{subscriptionId}")
    public ApiResponse<String> updateDailyLimit(
            @PathVariable Long subscriptionId,
            @RequestBody Map<String, Object> request) {
        AuthenticatedUser user = CurrentSessionHolder.require();

        // 获取新的每日限额
        Object dailyLimitObj = request.get("dailyLimit");
        BigDecimal newDailyLimit = null;

        if (dailyLimitObj != null) {
            try {
                newDailyLimit = new BigDecimal(dailyLimitObj.toString());
                if (newDailyLimit.compareTo(BigDecimal.ZERO) <= 0) {
                    return ApiResponse.fail("每日配额必须大于0", null);
                }
                if (newDailyLimit.compareTo(new BigDecimal("10000")) > 0) {
                    return ApiResponse.fail("每日配额不能超过1万元", null);
                }
            } catch (NumberFormatException e) {
                return ApiResponse.fail("无效的每日配额数值", null);
            }
        }

        // 验证用户是否有权限修改此订阅
        List<SubscriptionItem> userSubs = subscriptionService.getActiveSubscriptions(user.getId());
        boolean hasPermission = userSubs.stream().anyMatch(sub -> sub.getId().equals(subscriptionId));
        if (!hasPermission) {
            return ApiResponse.fail("无权限修改此订阅的每日配额", null);
        }

        // 更新每日配额
        boolean success = subscriptionService.updateDailyLimit(subscriptionId, newDailyLimit);
        if (success) {
            return ApiResponse.ok("每日配额更新成功");
        } else {
            return ApiResponse.fail("更新失败，请稍后重试", null);
        }
    }

    @GetMapping("/usage-history")
    public ApiResponse<Map<String, Object>> getUsageHistory(
            @RequestParam(defaultValue = "7") int days) {
        AuthenticatedUser user = CurrentSessionHolder.require();

        if (days <= 0 || days > 30) {
            days = 7; // 默认7天，最多30天
        }

        Map<String, Object> result = dailyQuotaService.getUserUsageHistory(user.getId(), days);
        return ApiResponse.ok(result);
    }
}
