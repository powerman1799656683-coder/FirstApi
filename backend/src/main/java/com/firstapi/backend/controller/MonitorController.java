package com.firstapi.backend.controller;

import com.firstapi.backend.common.ApiResponse;
import com.firstapi.backend.common.PageResponse;
import com.firstapi.backend.model.MonitorAlertRuleItem;
import com.firstapi.backend.model.MonitorData;
import com.firstapi.backend.model.MonitorNodeItem;
import com.firstapi.backend.service.MonitorService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/monitor")
public class MonitorController {

    private final MonitorService monitorService;

    public MonitorController(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @GetMapping
    public ApiResponse<MonitorData> getMonitor(
            @RequestParam(value = "platform", required = false) String platform,
            @RequestParam(value = "group", required = false) String group,
            @RequestParam(value = "timeRange", required = false, defaultValue = "1h") String timeRange,
            @RequestParam(value = "startTime", required = false) String startTime,
            @RequestParam(value = "endTime", required = false) String endTime) {
        return ApiResponse.ok(monitorService.getMonitorData(platform, group, timeRange, startTime, endTime));
    }

    @GetMapping("/accounts")
    public ApiResponse<Map<String, Object>> getAccountMonitor(
            @RequestParam(value = "timeRange", required = false, defaultValue = "1h") String timeRange) {
        return ApiResponse.ok(monitorService.getAccountMonitorData(timeRange));
    }

    @GetMapping("/system")
    public ApiResponse<MonitorData> getSystemMonitor(
            @RequestParam(value = "timeRange", required = false, defaultValue = "1h") String timeRange,
            @RequestParam(value = "startTime", required = false) String startTime,
            @RequestParam(value = "endTime", required = false) String endTime) {
        return ApiResponse.ok(monitorService.getSystemMonitorData(timeRange, startTime, endTime));
    }

    @GetMapping("/nodes")
    public ApiResponse<PageResponse<MonitorNodeItem>> listNodes(
            @RequestParam(value = "keyword", required = false) String keyword) {
        return ApiResponse.ok(monitorService.listNodes(keyword));
    }

    @PostMapping("/nodes")
    public ApiResponse<MonitorNodeItem> createNode(@RequestBody MonitorNodeItem.Request request) {
        return ApiResponse.ok("创建成功", monitorService.createNode(request));
    }

    @PutMapping("/nodes/{id}")
    public ApiResponse<MonitorNodeItem> updateNode(@PathVariable Long id,
                                                    @RequestBody MonitorNodeItem.Request request) {
        return ApiResponse.ok("更新成功", monitorService.updateNode(id, request));
    }

    @DeleteMapping("/nodes/{id}")
    public ApiResponse<Boolean> deleteNode(@PathVariable Long id) {
        monitorService.deleteNode(id);
        return ApiResponse.ok("删除成功", true);
    }

    @PostMapping("/nodes/{id}/check")
    public ApiResponse<MonitorNodeItem> checkNode(@PathVariable Long id) {
        return ApiResponse.ok("检测完成", monitorService.checkNodeNow(id));
    }

    @PostMapping("/nodes/check-all")
    public ApiResponse<List<MonitorNodeItem>> checkAll() {
        return ApiResponse.ok("检测完成", monitorService.checkAllNodesNow());
    }

    // ==================== Alert Rules ====================

    @GetMapping("/alert-rules")
    public ApiResponse<PageResponse<MonitorAlertRuleItem>> listAlertRules() {
        return ApiResponse.ok(monitorService.listAlertRules());
    }

    @PostMapping("/alert-rules")
    public ApiResponse<MonitorAlertRuleItem> createAlertRule(@RequestBody MonitorAlertRuleItem.Request request) {
        return ApiResponse.ok("创建成功", monitorService.createAlertRule(request));
    }

    @PutMapping("/alert-rules/{id}")
    public ApiResponse<MonitorAlertRuleItem> updateAlertRule(@PathVariable Long id,
                                                              @RequestBody MonitorAlertRuleItem.Request request) {
        return ApiResponse.ok("更新成功", monitorService.updateAlertRule(id, request));
    }

    @DeleteMapping("/alert-rules/{id}")
    public ApiResponse<Boolean> deleteAlertRule(@PathVariable Long id) {
        monitorService.deleteAlertRule(id);
        return ApiResponse.ok("删除成功", true);
    }
}
