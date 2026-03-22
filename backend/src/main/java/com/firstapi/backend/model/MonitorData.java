package com.firstapi.backend.model;

import java.util.List;
import java.util.Map;

public class MonitorData {
    public String status;
    public String lastRefresh;

    public List<String> platforms;
    public List<String> groups;

    public int healthScore;
    public String healthLevel;

    public RealtimeInfo realtime;
    public RequestSummary requests;
    public SlaInfo sla;
    public ErrorInfo errors;
    public LatencyInfo latency;
    public LatencyInfo ttft;
    public UpstreamErrorInfo upstreamErrors;
    public SystemInfo system;
    public TrendItem cpu;
    public TrendItem memory;
    public TrendItem jvm;
    public TrendItem database;
    public TrendItem disk;
    public TrendItem network;
    public NodeInfo node;

    public List<ConcurrencyPlatform> concurrency;

    public List<Map<String, Object>> accountSwitchTrend;
    public List<Map<String, Object>> throughputTrend;
    public List<Map<String, Object>> latencyDistribution;
    public Map<String, Object> errorDistribution;
    public List<Map<String, Object>> errorTrend;

    public List<AlertEvent> alertEvents;

    public static class RealtimeInfo {
        public double currentQps;
        public double currentTps;
        public double peakQps;
        public double peakTps;
        public double avgQps;
        public double avgTps;
        public List<Map<String, Object>> sparkline;

        public RealtimeInfo() {}
    }

    public static class RequestSummary {
        public long count;
        public long tokens;
        public double avgOps;
        public double avgTps;

        public RequestSummary() {}

        public RequestSummary(long count, long tokens, double avgOps, double avgTps) {
            this.count = count;
            this.tokens = tokens;
            this.avgOps = avgOps;
            this.avgTps = avgTps;
        }
    }

    public static class SlaInfo {
        public double percentage;
        public int anomalyCount;

        public SlaInfo() {}

        public SlaInfo(double percentage, int anomalyCount) {
            this.percentage = percentage;
            this.anomalyCount = anomalyCount;
        }
    }

    public static class ErrorInfo {
        public double percentage;
        public int count;
        public int businessLimitCount;

        public ErrorInfo() {}

        public ErrorInfo(double percentage, int count, int businessLimitCount) {
            this.percentage = percentage;
            this.count = count;
            this.businessLimitCount = businessLimitCount;
        }
    }

    public static class LatencyInfo {
        public long p99;
        public long p95;
        public long p90;
        public long p50;
        public long avg;
        public long max;

        public LatencyInfo() {}

        public LatencyInfo(long p99, long p95, long p90, long p50, long avg, long max) {
            this.p99 = p99;
            this.p95 = p95;
            this.p90 = p90;
            this.p50 = p50;
            this.avg = avg;
            this.max = max;
        }
    }

    public static class UpstreamErrorInfo {
        public double percentage;
        public int countExcluding429529;
        public int count429529;

        public UpstreamErrorInfo() {}

        public UpstreamErrorInfo(double percentage, int countExcluding429529, int count429529) {
            this.percentage = percentage;
            this.countExcluding429529 = countExcluding429529;
            this.count429529 = count429529;
        }
    }

    public static class SystemInfo {
        public ResourceItem cpu;
        public ResourceItem memory;
        public ResourceItem database;
        public ResourceItem redis;
        public ResourceItem threads;
        public ResourceItem backgroundTasks;

        public SystemInfo() {}
    }

    public static class ResourceItem {
        public String value;
        public String color;
        public String detail;

        public ResourceItem() {}

        public ResourceItem(String value, String color, String detail) {
            this.value = value;
            this.color = color;
            this.detail = detail;
        }
    }

    public static class TrendItem {
        public String value;
        public String color;
        public String detail;
        public List<Map<String, Object>> history;

        public TrendItem() {}

        public TrendItem(String value, String color, String detail, List<Map<String, Object>> history) {
            this.value = value;
            this.color = color;
            this.detail = detail;
            this.history = history;
        }
    }

    public static class NodeInfo {
        public String os;
        public String kernelVersion;
        public String diskUsage;
        public String nodeId;
        public String uptime;
        public String storageHealth;
        public int storageScore;

        public NodeInfo() {}
    }

    public static class ConcurrencyPlatform {
        public String name;
        public int current;
        public int max;
        public double percentage;
        public int accountsCurrent;
        public int accountsTotal;
        public double accountsPercentage;
        public List<String> cooldownAccounts;

        public ConcurrencyPlatform() {}
    }

    public static class AlertEvent {
        public String time;
        public String level;
        public String levelStatus;
        public String platform;
        public String ruleId;
        public String title;
        public String description;
        public String duration;
        public String dimension;
        public String emailSent;

        public AlertEvent() {}
    }
}
