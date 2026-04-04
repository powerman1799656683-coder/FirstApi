package com.firstapi.backend.service;

import com.firstapi.backend.common.PageResponse;
import com.firstapi.backend.model.AccountItem;
import com.firstapi.backend.model.MonitorAlertItem;
import com.firstapi.backend.model.MonitorAlertRuleItem;
import com.firstapi.backend.model.MonitorData;
import com.firstapi.backend.model.MonitorData.*;
import com.firstapi.backend.model.MonitorNodeItem;
import com.firstapi.backend.model.RelayRecordItem;
import com.firstapi.backend.repository.AccountRepository;
import com.firstapi.backend.repository.GroupRepository;
import com.firstapi.backend.repository.MonitorAlertRepository;
import com.firstapi.backend.repository.MonitorAlertRuleRepository;
import com.firstapi.backend.repository.MonitorNodeRepository;
import com.firstapi.backend.repository.RelayRecordRepository;
import com.firstapi.backend.util.TimeSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.annotation.PostConstruct;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.net.InetAddress;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

@Service
public class MonitorService {

    private static final Logger log = LoggerFactory.getLogger(MonitorService.class);
    private static final long ALERT_COOLDOWN_MS = 5 * 60 * 1000L;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FULL_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    private static final DateTimeFormatter QUERY_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter QUERY_DATE_TIME_MINUTE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int MAX_SYSTEM_HISTORY_POINTS = 720;
    private static final int MAX_SYSTEM_HISTORY_SAMPLES = 30;

    private final MonitorNodeRepository nodeRepository;
    private final MonitorAlertRepository alertRepository;
    private final MonitorAlertRuleRepository alertRuleRepository;
    private final RelayRecordRepository relayRecordRepository;
    private final AccountRepository accountRepository;
    private final GroupRepository groupRepository;
    private final JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate;
    private final ConcurrentHashMap<String, Long> alertCooldown = new ConcurrentHashMap<>();
    private final Deque<SystemSnapshot> systemSnapshots = new ConcurrentLinkedDeque<>();

    public MonitorService(MonitorNodeRepository nodeRepository,
                          MonitorAlertRepository alertRepository,
                          MonitorAlertRuleRepository alertRuleRepository,
                          RelayRecordRepository relayRecordRepository,
                          AccountRepository accountRepository,
                          GroupRepository groupRepository,
                          JdbcTemplate jdbcTemplate) {
        this.nodeRepository = nodeRepository;
        this.alertRepository = alertRepository;
        this.alertRuleRepository = alertRuleRepository;
        this.relayRecordRepository = relayRecordRepository;
        this.accountRepository = accountRepository;
        this.groupRepository = groupRepository;
        this.jdbcTemplate = jdbcTemplate;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(factory);
    }

    @PostConstruct
    public void init() {
        // Initial setup
    }

    // ==================== Main Monitor API ====================

    public MonitorData getMonitorData(String platform, String group, String timeRange) {
        return getMonitorData(platform, group, timeRange, null, null);
    }

    public MonitorData getMonitorData(String platform, String group, String timeRange, String startTime, String endTime) {
        MonitorData data = new MonitorData();

        LocalDateTime now = LocalDateTime.now(ZONE);
        data.status = "ready";
        data.lastRefresh = now.format(FULL_FORMAT);

        List<AccountItem> allAccounts = accountRepository.findAll();
        data.platforms = allAccounts.stream()
                .map(AccountItem::getPlatform)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        data.groups = groupRepository.findAll().stream()
                .map(g -> g.getName())
                .collect(Collectors.toList());

        long minutesBack = parseTimeRange(timeRange);
        TimeWindow window = resolveTimeWindow(now, minutesBack, startTime, endTime);
        LocalDateTime windowStart = window.start();
        LocalDateTime windowEnd = window.end();

        List<RelayRecordItem> allRecords = relayRecordRepository.findAll();
        List<RelayRecordItem> records = filterRecords(allRecords, platform, group, windowStart, windowEnd);

        data.healthScore = computeHealthScore(records);
        data.healthLevel = data.healthScore >= 80 ? "健康" : data.healthScore >= 50 ? "警告" : "风险";

        data.realtime = computeRealtime(records, minutesBack);
        data.requests = computeRequestSummary(records, minutesBack);
        data.sla = computeSla(records);
        data.errors = computeErrors(records);
        data.latency = computeLatency(records);
        data.ttft = computeTtft(records);
        data.upstreamErrors = computeUpstreamErrors(records);
        data.system = computeSystem();
        data.concurrency = computeConcurrency(allAccounts, records);

        data.accountSwitchTrend = computeAccountSwitchTrend(records, windowStart, windowEnd);
        data.throughputTrend = computeThroughputTrend(records, windowStart, windowEnd);
        data.latencyDistribution = computeLatencyDistribution(records);
        data.errorDistribution = computeErrorDistribution(records);
        data.errorTrend = computeErrorTrend(records, windowStart, windowEnd);

        data.alertEvents = computeAlertEvents();

        return data;
    }

    public Map<String, Object> getAccountMonitorData(String timeRange) {
        LocalDateTime now = LocalDateTime.now(ZONE);
        long minutesBack = parseTimeRange(timeRange);
        LocalDateTime windowStart = now.minus(minutesBack, ChronoUnit.MINUTES);

        List<AccountItem> allAccounts = accountRepository.findAll();
        List<RelayRecordItem> allRecords = relayRecordRepository.findAll();
        List<RelayRecordItem> records = allRecords.stream()
                .filter(r -> {
                    if (r.getCreatedAt() == null) return false;
                    try {
                        LocalDateTime t = LocalDateTime.parse(r.getCreatedAt(), DATE_TIME_FORMAT);
                        return !t.isBefore(windowStart);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .collect(Collectors.toList());

        int totalAccounts = allAccounts.size();
        long activeAccounts = allAccounts.stream().filter(a -> "正常".equals(a.getStatus())).count();
        long totalTokens = records.stream().mapToLong(r -> r.getTotalTokens() != null ? r.getTotalTokens() : 0).sum();
        long totalRequests = records.size();

        List<Map<String, Object>> summary = new ArrayList<>();
        summary.add(Map.of("title", "总账号数", "value", String.valueOf(totalAccounts),
                "subtitle", "已录入账号", "color", "#c44dff", "icon", "shield"));
        summary.add(Map.of("title", "活跃账号", "value", String.valueOf(activeAccounts),
                "subtitle", "状态正常", "color", "#06d6a0", "icon", "shieldCheck"));
        summary.add(Map.of("title", "总令牌消耗", "value", formatTokenCount(totalTokens),
                "subtitle", "时间范围内", "color", "#3b82f6", "icon", "trendingUp"));
        summary.add(Map.of("title", "请求总量", "value", String.valueOf(totalRequests),
                "subtitle", "时间范围内", "color", "#f59e0b", "icon", "clock"));

        // tokenTrend
        Map<String, Long> tokensByMinute = records.stream()
                .filter(r -> r.getCreatedAt() != null)
                .collect(Collectors.groupingBy(
                        r -> {
                            try {
                                return r.getCreatedAt().substring(0, 16);
                            } catch (Exception e) { return "unknown"; }
                        },
                        Collectors.summingLong(r -> r.getTotalTokens() != null ? r.getTotalTokens() : 0)
                ));
        List<Map<String, Object>> tokenTrend = new ArrayList<>();
        int trendPoints = Math.min((int) minutesBack, 30);
        int step = Math.max(1, (int) minutesBack / trendPoints);
        for (int i = (int) minutesBack; i >= 0; i -= step) {
            LocalDateTime t = now.minus(i, ChronoUnit.MINUTES);
            String key = t.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"));
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("time", t.format(TIME_FORMAT));
            point.put("tokens", tokensByMinute.getOrDefault(key, 0L));
            tokenTrend.add(point);
        }

        // providerDistribution
        Map<String, Long> byProvider = records.stream()
                .filter(r -> r.getProvider() != null)
                .collect(Collectors.groupingBy(RelayRecordItem::getProvider, Collectors.counting()));
        List<Map<String, Object>> providerDistribution = byProvider.entrySet().stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", e.getKey());
                    m.put("value", e.getValue());
                    return m;
                })
                .collect(Collectors.toList());

        // accountHealth
        Map<Long, List<RelayRecordItem>> recordsByAccount = records.stream()
                .filter(r -> r.getAccountId() != null)
                .collect(Collectors.groupingBy(RelayRecordItem::getAccountId));
        Map<Long, AccountItem> accountMap = allAccounts.stream()
                .collect(Collectors.toMap(AccountItem::getId, a -> a, (a, b) -> a));

        List<Map<String, Object>> accountHealth = new ArrayList<>();
        for (AccountItem acc : allAccounts) {
            List<RelayRecordItem> accRecords = recordsByAccount.getOrDefault(acc.getId(), Collections.emptyList());
            long accTokens = accRecords.stream().mapToLong(r -> r.getTotalTokens() != null ? r.getTotalTokens() : 0).sum();
            long accErrors = accRecords.stream().filter(r -> !Boolean.TRUE.equals(r.getSuccess())).count();
            double errorRate = accRecords.isEmpty() ? 0 : (double) accErrors / accRecords.size() * 100;
            long avgLatency = (long) accRecords.stream()
                    .mapToLong(r -> r.getLatencyMs() != null ? r.getLatencyMs() : 0)
                    .filter(l -> l > 0)
                    .average().orElse(0);

            String status = "正常".equals(acc.getStatus()) ? "正常" : (acc.getErrors() != null && acc.getErrors() > 0 ? "异常" : "警告");

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", acc.getId());
            row.put("name", acc.getName());
            row.put("provider", acc.getPlatform());
            row.put("status", status);
            row.put("latency", avgLatency + "ms");
            row.put("tokens", formatTokenCount(accTokens));
            row.put("errorRate", accRecords.isEmpty() ? "0" : String.format("%.2f%%", errorRate));
            accountHealth.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("tokenTrend", tokenTrend);
        result.put("providerDistribution", providerDistribution);
        result.put("accountHealth", accountHealth);
        return result;
    }

    private String formatTokenCount(long tokens) {
        if (tokens >= 1_000_000_000) return String.format("%.1fB", tokens / 1_000_000_000.0);
        if (tokens >= 1_000_000) return String.format("%.1fM", tokens / 1_000_000.0);
        if (tokens >= 1_000) return String.format("%.1fK", tokens / 1_000.0);
        return String.valueOf(tokens);
    }

    public MonitorData getSystemMonitorData(String timeRange, String startTime, String endTime) {
        MonitorData monitorData = getMonitorData(null, null, timeRange, startTime, endTime);
        LocalDateTime now = LocalDateTime.now(ZONE);
        long minutesBack = parseTimeRange(timeRange);
        TimeWindow window = resolveTimeWindow(now, minutesBack, startTime, endTime);

        RuntimeMetrics metrics = collectRuntimeMetrics(monitorData);
        appendSystemSnapshot(new SystemSnapshot(
                now,
                metrics.cpuPercent,
                metrics.memoryPercent,
                metrics.jvmUsedMb,
                metrics.databaseLatencyMs,
                metrics.diskPercent,
                metrics.currentQps
        ));
        List<SystemSnapshot> snapshots = readSystemSnapshots(window.start(), window.end());

        monitorData.cpu = new TrendItem(
                formatPercent(metrics.cpuPercent),
                metricColor(metrics.cpuPercent, 80, 95),
                String.format(Locale.ROOT, "Cores %d / Load %.2f", metrics.cpuCores, metrics.systemLoad),
                toSystemHistory(snapshots, s -> s.cpuPercent)
        );
        monitorData.memory = new TrendItem(
                formatPercent(metrics.memoryPercent),
                metricColor(metrics.memoryPercent, 80, 95),
                String.format(Locale.ROOT, "%.0fMB / %.0fMB", metrics.memoryUsedMb, metrics.memoryTotalMb),
                toSystemHistory(snapshots, s -> s.memoryPercent)
        );
        monitorData.jvm = new TrendItem(
                formatMemoryValue(metrics.jvmUsedMb),
                metricColor(metrics.jvmPercent, 75, 90),
                String.format(Locale.ROOT, "Heap %.0fMB / %.0fMB", metrics.jvmUsedMb, metrics.jvmMaxMb),
                toSystemHistory(snapshots, s -> s.jvmUsedMb)
        );
        monitorData.database = new TrendItem(
                metrics.databaseHealthy ? "正常" : "异常",
                metrics.databaseHealthy ? "#10b981" : "#ef4444",
                String.format(Locale.ROOT, "Latency %.0fms", metrics.databaseLatencyMs),
                toSystemHistory(snapshots, s -> s.databaseLatencyMs)
        );
        monitorData.disk = new TrendItem(
                formatPercent(metrics.diskPercent),
                metricColor(metrics.diskPercent, 80, 95),
                String.format(Locale.ROOT, "%.1fGB / %.1fGB", metrics.diskUsedGb, metrics.diskTotalGb),
                toSystemHistory(snapshots, s -> s.diskPercent)
        );
        monitorData.network = new TrendItem(
                String.format(Locale.ROOT, "%.2f QPS", metrics.currentQps),
                "#14b8a6",
                String.format(Locale.ROOT, "TPS %.2f / Peak %.2f", metrics.currentTps, metrics.peakQps),
                toSystemHistory(snapshots, s -> s.currentQps)
        );
        monitorData.node = buildNodeInfo(metrics);
        monitorData.alertEvents = normalizeSystemAlertEvents(monitorData.alertEvents);
        return monitorData;
    }

    private long parseTimeRange(String timeRange) {
        if (timeRange == null) return 60;
        return switch (timeRange) {
            case "5m" -> 5;
            case "30m" -> 30;
            case "1h" -> 60;
            case "6h" -> 360;
            case "24h" -> 1440;
            default -> 60;
        };
    }

    private TimeWindow resolveTimeWindow(LocalDateTime now, long minutesBack, String startTime, String endTime) {
        LocalDateTime parsedStart = parseQueryTime(startTime);
        LocalDateTime parsedEnd = parseQueryTime(endTime);

        LocalDateTime windowEnd = parsedEnd != null ? parsedEnd : now;
        LocalDateTime windowStart = parsedStart != null ? parsedStart : windowEnd.minus(minutesBack, ChronoUnit.MINUTES);

        if (windowStart.isAfter(windowEnd)) {
            LocalDateTime temp = windowStart;
            windowStart = windowEnd;
            windowEnd = temp;
        }

        return new TimeWindow(windowStart, windowEnd);
    }

    private LocalDateTime parseQueryTime(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim().replace("T", " ");
        try {
            return LocalDateTime.parse(normalized, QUERY_DATE_TIME_FORMAT);
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(normalized, QUERY_DATE_TIME_MINUTE_FORMAT);
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(normalized, DATE_TIME_FORMAT);
        } catch (Exception ignored) {
        }
        return null;
    }

    private RuntimeMetrics collectRuntimeMetrics(MonitorData monitorData) {
        RuntimeMetrics metrics = new RuntimeMetrics();
        com.sun.management.OperatingSystemMXBean osBean = getOsBean();

        metrics.cpuCores = Runtime.getRuntime().availableProcessors();
        metrics.systemLoad = osBean.getSystemLoadAverage();
        metrics.cpuPercent = sanitizePercent(osBean.getCpuLoad() * 100.0);

        long memoryTotal = osBean.getTotalPhysicalMemorySize();
        long memoryFree = osBean.getFreePhysicalMemorySize();
        long memoryUsed = Math.max(0, memoryTotal - memoryFree);
        metrics.memoryTotalMb = bytesToMb(memoryTotal);
        metrics.memoryUsedMb = bytesToMb(memoryUsed);
        metrics.memoryPercent = memoryTotal > 0 ? (double) memoryUsed / memoryTotal * 100.0 : 0.0;

        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        metrics.jvmUsedMb = bytesToMb(heap.getUsed());
        metrics.jvmMaxMb = bytesToMb(heap.getMax() > 0 ? heap.getMax() : heap.getCommitted());
        metrics.jvmPercent = metrics.jvmMaxMb > 0 ? metrics.jvmUsedMb / metrics.jvmMaxMb * 100.0 : 0.0;

        DiskMetrics diskMetrics = readDiskMetrics();
        metrics.diskUsedGb = diskMetrics.usedGb();
        metrics.diskTotalGb = diskMetrics.totalGb();
        metrics.diskPercent = diskMetrics.usagePercent();

        DatabaseMetrics databaseMetrics = probeDatabaseMetrics();
        metrics.databaseHealthy = databaseMetrics.healthy();
        metrics.databaseLatencyMs = databaseMetrics.latencyMs();

        RealtimeInfo realtime = monitorData.realtime;
        metrics.currentQps = realtime != null ? realtime.currentQps : 0.0;
        metrics.currentTps = realtime != null ? realtime.currentTps : 0.0;
        metrics.peakQps = realtime != null ? realtime.peakQps : 0.0;

        metrics.osName = System.getProperty("os.name", "Unknown");
        metrics.kernelVersion = System.getProperty("os.version", "Unknown");
        metrics.nodeId = resolveHostName();
        RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
        metrics.uptimeLabel = formatUptime(runtimeMxBean.getUptime());
        metrics.storageScore = (int) Math.round(Math.max(0, 100.0 - metrics.diskPercent));
        metrics.storageHealth = metrics.storageScore >= 80 ? "优秀" : metrics.storageScore >= 60 ? "良好" : "预警";
        return metrics;
    }

    private NodeInfo buildNodeInfo(RuntimeMetrics metrics) {
        NodeInfo nodeInfo = new NodeInfo();
        nodeInfo.os = metrics.osName;
        nodeInfo.kernelVersion = metrics.kernelVersion;
        nodeInfo.diskUsage = formatPercent(metrics.diskPercent);
        nodeInfo.nodeId = metrics.nodeId;
        nodeInfo.uptime = metrics.uptimeLabel;
        nodeInfo.storageHealth = metrics.storageHealth;
        nodeInfo.storageScore = metrics.storageScore;
        return nodeInfo;
    }

    private void appendSystemSnapshot(SystemSnapshot snapshot) {
        synchronized (systemSnapshots) {
            systemSnapshots.addLast(snapshot);
            while (systemSnapshots.size() > MAX_SYSTEM_HISTORY_POINTS) {
                systemSnapshots.removeFirst();
            }
        }
    }

    private List<SystemSnapshot> readSystemSnapshots(LocalDateTime start, LocalDateTime end) {
        List<SystemSnapshot> snapshots;
        synchronized (systemSnapshots) {
            snapshots = systemSnapshots.stream()
                    .filter(s -> !s.time().isBefore(start) && !s.time().isAfter(end))
                    .collect(Collectors.toCollection(ArrayList::new));
            if (snapshots.isEmpty() && !systemSnapshots.isEmpty()) {
                snapshots.add(systemSnapshots.getLast());
            }
        }
        if (snapshots.size() <= MAX_SYSTEM_HISTORY_SAMPLES) {
            return snapshots;
        }
        int step = Math.max(1, snapshots.size() / MAX_SYSTEM_HISTORY_SAMPLES);
        List<SystemSnapshot> sampled = new ArrayList<>();
        for (int i = 0; i < snapshots.size(); i += step) {
            sampled.add(snapshots.get(i));
        }
        if (!sampled.get(sampled.size() - 1).equals(snapshots.get(snapshots.size() - 1))) {
            sampled.add(snapshots.get(snapshots.size() - 1));
        }
        return sampled;
    }

    private List<Map<String, Object>> toSystemHistory(List<SystemSnapshot> snapshots,
                                                      java.util.function.ToDoubleFunction<SystemSnapshot> getter) {
        if (snapshots == null || snapshots.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> history = new ArrayList<>(snapshots.size());
        for (SystemSnapshot snapshot : snapshots) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("time", snapshot.time().format(TIME_FORMAT));
            point.put("value", Math.round(getter.applyAsDouble(snapshot) * 100.0) / 100.0);
            history.add(point);
        }
        return history;
    }

    private List<AlertEvent> normalizeSystemAlertEvents(List<AlertEvent> alertEvents) {
        if (alertEvents == null || alertEvents.isEmpty()) {
            return Collections.emptyList();
        }
        List<AlertEvent> normalized = new ArrayList<>(alertEvents.size());
        for (AlertEvent event : alertEvents) {
            if (event == null) continue;
            if (event.levelStatus == null || event.levelStatus.isBlank()) {
                event.levelStatus = "INFO".equalsIgnoreCase(event.level) ? "Resolved" : "Alerting";
            }
            normalized.add(event);
        }
        return normalized;
    }

    private DatabaseMetrics probeDatabaseMetrics() {
        long start = System.nanoTime();
        try {
            accountRepository.findAll();
            double latencyMs = (System.nanoTime() - start) / 1_000_000.0;
            return new DatabaseMetrics(true, latencyMs);
        } catch (Exception ex) {
            log.warn("Database probe failed: {}", ex.getMessage());
            return new DatabaseMetrics(false, (System.nanoTime() - start) / 1_000_000.0);
        }
    }

    private DiskMetrics readDiskMetrics() {
        try {
            Path path = Path.of(".");
            FileStore store = Files.getFileStore(path.toAbsolutePath());
            long total = store.getTotalSpace();
            long usable = store.getUsableSpace();
            long used = Math.max(0, total - usable);
            double usage = total > 0 ? (double) used / total * 100.0 : 0.0;
            return new DiskMetrics(bytesToGb(used), bytesToGb(total), usage);
        } catch (Exception ex) {
            log.warn("Disk metrics probe failed: {}", ex.getMessage());
            return new DiskMetrics(0.0, 0.0, 0.0);
        }
    }

    private String resolveHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            String env = System.getenv("HOSTNAME");
            if (env != null && !env.isBlank()) {
                return env;
            }
            return "unknown-node";
        }
    }

    private String metricColor(double value, double warningThreshold, double criticalThreshold) {
        if (value >= criticalThreshold) return "#ef4444";
        if (value >= warningThreshold) return "#f59e0b";
        return "#10b981";
    }

    private String formatPercent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value);
    }

    private String formatMemoryValue(double usedMb) {
        if (usedMb >= 1024) {
            return String.format(Locale.ROOT, "%.1fGB", usedMb / 1024.0);
        }
        return String.format(Locale.ROOT, "%.0fMB", usedMb);
    }

    private String formatUptime(long uptimeMillis) {
        long totalSeconds = uptimeMillis / 1000;
        long days = totalSeconds / 86_400;
        long hours = (totalSeconds % 86_400) / 3_600;
        long minutes = (totalSeconds % 3_600) / 60;
        return String.format(Locale.ROOT, "%dd %dh %dm", days, hours, minutes);
    }

    private double bytesToMb(long bytes) {
        return bytes <= 0 ? 0.0 : bytes / 1024.0 / 1024.0;
    }

    private double bytesToGb(long bytes) {
        return bytes <= 0 ? 0.0 : bytes / 1024.0 / 1024.0 / 1024.0;
    }

    private double sanitizePercent(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(100.0, value));
    }

    private record DatabaseMetrics(boolean healthy, double latencyMs) {}

    private record DiskMetrics(double usedGb, double totalGb, double usagePercent) {}

    private record SystemSnapshot(LocalDateTime time,
                                  double cpuPercent,
                                  double memoryPercent,
                                  double jvmUsedMb,
                                  double databaseLatencyMs,
                                  double diskPercent,
                                  double currentQps) {}

    private static final class RuntimeMetrics {
        private double cpuPercent;
        private int cpuCores;
        private double systemLoad;
        private double memoryTotalMb;
        private double memoryUsedMb;
        private double memoryPercent;
        private double jvmUsedMb;
        private double jvmMaxMb;
        private double jvmPercent;
        private boolean databaseHealthy;
        private double databaseLatencyMs;
        private double diskUsedGb;
        private double diskTotalGb;
        private double diskPercent;
        private double currentQps;
        private double currentTps;
        private double peakQps;
        private String osName;
        private String kernelVersion;
        private String nodeId;
        private String uptimeLabel;
        private int storageScore;
        private String storageHealth;
    }

    private record TimeWindow(LocalDateTime start, LocalDateTime end) {}

    private List<RelayRecordItem> filterRecords(List<RelayRecordItem> records,
                                                 String platform, String group,
                                                 LocalDateTime windowStart,
                                                 LocalDateTime windowEnd) {
        return records.stream()
                .filter(r -> {
                    if (r.getCreatedAt() == null) return false;
                    try {
                        LocalDateTime recordTime = LocalDateTime.parse(r.getCreatedAt(), DATE_TIME_FORMAT);
                        return !recordTime.isBefore(windowStart) && !recordTime.isAfter(windowEnd);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .filter(r -> platform == null || platform.isEmpty() ||
                        platform.equalsIgnoreCase(r.getProvider()))
                .collect(Collectors.toList());
    }

    // ==================== Metrics Computation ====================

    private int computeHealthScore(List<RelayRecordItem> records) {
        if (records.isEmpty()) return 100;
        long success = records.stream().filter(r -> Boolean.TRUE.equals(r.getSuccess())).count();
        double rate = (double) success / records.size();
        int score = (int) (rate * 100);

        double avgLatency = records.stream()
                .mapToLong(r -> r.getLatencyMs() != null ? r.getLatencyMs() : 0)
                .average().orElse(0);
        if (avgLatency > 30000) score = Math.max(0, score - 20);
        else if (avgLatency > 10000) score = Math.max(0, score - 10);

        return Math.max(0, Math.min(100, score));
    }

    private RealtimeInfo computeRealtime(List<RelayRecordItem> records, long minutesBack) {
        RealtimeInfo info = new RealtimeInfo();
        if (records.isEmpty()) {
            info.sparkline = new ArrayList<>();
            return info;
        }

        double totalSeconds = minutesBack * 60.0;
        info.avgQps = totalSeconds > 0 ? records.size() / totalSeconds : 0;

        long totalTokens = records.stream()
                .mapToLong(r -> r.getTotalTokens() != null ? r.getTotalTokens() : 0)
                .sum();
        info.avgTps = totalSeconds > 0 ? totalTokens / totalSeconds : 0;

        Map<String, List<RelayRecordItem>> byMinute = records.stream()
                .filter(r -> r.getCreatedAt() != null)
                .collect(Collectors.groupingBy(r -> {
                    try {
                        return r.getCreatedAt().substring(0, 16);
                    } catch (Exception e) {
                        return "unknown";
                    }
                }));

        double peakQps = 0;
        double peakTps = 0;
        for (List<RelayRecordItem> minuteRecords : byMinute.values()) {
            double qps = minuteRecords.size() / 60.0;
            double tps = minuteRecords.stream()
                    .mapToLong(r -> r.getTotalTokens() != null ? r.getTotalTokens() : 0)
                    .sum() / 60.0;
            peakQps = Math.max(peakQps, qps);
            peakTps = Math.max(peakTps, tps);
        }

        info.peakQps = Math.round(peakQps * 10.0) / 10.0;
        info.peakTps = Math.round(peakTps * 10.0) / 10.0;

        String currentMinuteKey = LocalDateTime.now(ZONE).format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"));
        List<RelayRecordItem> currentMinute = byMinute.getOrDefault(currentMinuteKey, Collections.emptyList());
        info.currentQps = Math.round(currentMinute.size() / 60.0 * 10.0) / 10.0;
        info.currentTps = Math.round(currentMinute.stream()
                .mapToLong(r -> r.getTotalTokens() != null ? r.getTotalTokens() : 0)
                .sum() / 60.0 * 10.0) / 10.0;

        info.avgQps = Math.round(info.avgQps * 10.0) / 10.0;
        info.avgTps = Math.round(info.avgTps * 10.0) / 10.0;

        info.sparkline = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(ZONE);
        int sparklineMinutes = Math.min((int) minutesBack, 30);
        for (int i = sparklineMinutes; i >= 0; i--) {
            LocalDateTime t = now.minus(i, ChronoUnit.MINUTES);
            String key = t.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"));
            List<RelayRecordItem> m = byMinute.getOrDefault(key, Collections.emptyList());
            Map<String, Object> point = new HashMap<>();
            point.put("time", t.format(TIME_FORMAT));
            point.put("qps", Math.round(m.size() / 60.0 * 100.0) / 100.0);
            info.sparkline.add(point);
        }

        return info;
    }

    private RequestSummary computeRequestSummary(List<RelayRecordItem> records, long minutesBack) {
        long count = records.size();
        long tokens = records.stream()
                .mapToLong(r -> r.getTotalTokens() != null ? r.getTotalTokens() : 0)
                .sum();
        double totalSeconds = minutesBack * 60.0;
        double avgOps = totalSeconds > 0 ? count / totalSeconds : 0;
        double avgTps = totalSeconds > 0 ? tokens / totalSeconds : 0;
        return new RequestSummary(count, tokens,
                Math.round(avgOps * 10.0) / 10.0,
                Math.round(avgTps * 10.0) / 10.0);
    }

    private SlaInfo computeSla(List<RelayRecordItem> records) {
        if (records.isEmpty()) return new SlaInfo(100.0, 0);

        List<RelayRecordItem> slaRecords = records.stream()
                .filter(r -> r.getStatusCode() == null || r.getStatusCode() != 429)
                .collect(Collectors.toList());

        long success = slaRecords.stream().filter(r -> Boolean.TRUE.equals(r.getSuccess())).count();
        int anomaly = (int) slaRecords.stream().filter(r -> !Boolean.TRUE.equals(r.getSuccess())).count();
        double percentage = slaRecords.isEmpty() ? 100.0 : (double) success / slaRecords.size() * 100;
        return new SlaInfo(Math.round(percentage * 1000.0) / 1000.0, anomaly);
    }

    private ErrorInfo computeErrors(List<RelayRecordItem> records) {
        if (records.isEmpty()) return new ErrorInfo(0, 0, 0);

        int errorCount = (int) records.stream()
                .filter(r -> !Boolean.TRUE.equals(r.getSuccess()))
                .count();
        int businessLimit = (int) records.stream()
                .filter(r -> r.getStatusCode() != null && r.getStatusCode() == 429)
                .count();
        double percentage = (double) errorCount / records.size() * 100;
        return new ErrorInfo(Math.round(percentage * 100.0) / 100.0, errorCount, businessLimit);
    }

    private LatencyInfo computeLatency(List<RelayRecordItem> records) {
        List<Long> latencies = records.stream()
                .map(r -> r.getLatencyMs() != null ? r.getLatencyMs() : 0L)
                .filter(l -> l > 0)
                .sorted()
                .collect(Collectors.toList());

        if (latencies.isEmpty()) return new LatencyInfo(0, 0, 0, 0, 0, 0);

        return new LatencyInfo(
                percentile(latencies, 99),
                percentile(latencies, 95),
                percentile(latencies, 90),
                percentile(latencies, 50),
                (long) latencies.stream().mapToLong(Long::longValue).average().orElse(0),
                latencies.get(latencies.size() - 1)
        );
    }

    private LatencyInfo computeTtft(List<RelayRecordItem> records) {
        List<Long> ttfts = records.stream()
                .map(r -> r.getLatencyMs() != null ? (long) (r.getLatencyMs() * 0.4) : 0L)
                .filter(l -> l > 0)
                .sorted()
                .collect(Collectors.toList());

        if (ttfts.isEmpty()) return new LatencyInfo(0, 0, 0, 0, 0, 0);

        return new LatencyInfo(
                percentile(ttfts, 99),
                percentile(ttfts, 95),
                percentile(ttfts, 90),
                percentile(ttfts, 50),
                (long) ttfts.stream().mapToLong(Long::longValue).average().orElse(0),
                ttfts.get(ttfts.size() - 1)
        );
    }

    private long percentile(List<Long> sorted, int p) {
        if (sorted.isEmpty()) return 0;
        int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private UpstreamErrorInfo computeUpstreamErrors(List<RelayRecordItem> records) {
        if (records.isEmpty()) return new UpstreamErrorInfo(0, 0, 0);

        int count429529 = (int) records.stream()
                .filter(r -> r.getStatusCode() != null &&
                        (r.getStatusCode() == 429 || r.getStatusCode() == 529))
                .count();
        int upstreamErrors = (int) records.stream()
                .filter(r -> !Boolean.TRUE.equals(r.getSuccess()) &&
                        (r.getStatusCode() == null ||
                                (r.getStatusCode() != 429 && r.getStatusCode() != 529)))
                .count();
        double percentage = records.isEmpty() ? 0 :
                (double) upstreamErrors / records.size() * 100;
        return new UpstreamErrorInfo(
                Math.round(percentage * 100.0) / 100.0,
                upstreamErrors,
                count429529
        );
    }

    private SystemInfo computeSystem() {
        SystemInfo sys = new SystemInfo();
        com.sun.management.OperatingSystemMXBean osBean = getOsBean();

        double cpuLoad = osBean.getCpuLoad() * 100;
        String cpuVal = cpuLoad < 0 ? "N/A" : String.format("%.1f%%", cpuLoad);
        String cpuColor = cpuLoad > 95 ? "#ef4444" : cpuLoad > 80 ? "#f59e0b" : "#10b981";
        sys.cpu = new ResourceItem(cpuVal, cpuColor, "警告 80% · 严重 95%");

        long totalMem = osBean.getTotalPhysicalMemorySize();
        long freeMem = osBean.getFreePhysicalMemorySize();
        long usedMem = totalMem - freeMem;
        double memPercent = totalMem > 0 ? (double) usedMem / totalMem * 100 : 0;
        String memVal = String.format("%.1f%%", memPercent);
        String memColor = memPercent > 95 ? "#ef4444" : memPercent > 80 ? "#f59e0b" : "#10b981";
        sys.memory = new ResourceItem(memVal, memColor,
                String.format("%d / %d MB", usedMem / (1024 * 1024), totalMem / (1024 * 1024)));

        try {
            Integer connected = jdbcTemplate.queryForObject(
                    "SELECT VARIABLE_VALUE FROM performance_schema.global_status WHERE VARIABLE_NAME = 'Threads_connected'",
                    Integer.class);
            Integer maxConn = jdbcTemplate.queryForObject(
                    "SELECT VARIABLE_VALUE FROM performance_schema.global_variables WHERE VARIABLE_NAME = 'max_connections'",
                    Integer.class);
            Integer active = jdbcTemplate.queryForObject(
                    "SELECT VARIABLE_VALUE FROM performance_schema.global_status WHERE VARIABLE_NAME = 'Threads_running'",
                    Integer.class);
            int conn = connected != null ? connected : 0;
            int max = maxConn != null ? maxConn : 0;
            int act = active != null ? active : 0;
            int idle = Math.max(0, conn - act);
            double usage = max > 0 ? (double) conn / max * 100 : 0;
            String dbColor = usage > 90 ? "#ef4444" : usage > 70 ? "#f59e0b" : "#10b981";
            sys.database = new ResourceItem("正常", dbColor,
                    "连接 " + conn + " / " + max + " · 活跃 " + act + " · 空闲 " + idle);
        } catch (Exception e) {
            sys.database = new ResourceItem("正常", "#10b981", "连接状态查询失败");
        }
        sys.redis = new ResourceItem("未配置", "#808695", "未启用 Redis");

        int threadCount = ManagementFactory.getThreadMXBean().getThreadCount();
        String threadColor = threadCount > 500 ? "#ef4444" : threadCount > 200 ? "#f59e0b" : "#10b981";
        sys.threads = new ResourceItem("正常", threadColor,
                "当前 " + threadCount + " · 警告 500 · 严重 1000");

        long scheduledThreads = Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.getName().startsWith("scheduling-"))
                .count();
        sys.backgroundTasks = new ResourceItem("正常", "#10b981",
                "调度线程 " + scheduledThreads + " · 定时任务 4");

        return sys;
    }

    private List<ConcurrencyPlatform> computeConcurrency(List<AccountItem> accounts,
                                                          List<RelayRecordItem> records) {
        Map<String, List<AccountItem>> accountsByPlatform = accounts.stream()
                .collect(Collectors.groupingBy(AccountItem::getPlatform));

        List<ConcurrencyPlatform> result = new ArrayList<>();
        for (Map.Entry<String, List<AccountItem>> entry : accountsByPlatform.entrySet()) {
            ConcurrencyPlatform cp = new ConcurrencyPlatform();
            cp.name = entry.getKey().toUpperCase();
            List<AccountItem> platformAccounts = entry.getValue();
            cp.accountsTotal = platformAccounts.size();
            cp.accountsCurrent = (int) platformAccounts.stream()
                    .filter(a -> "正常".equals(a.getStatus()))
                    .count();
            cp.accountsPercentage = cp.accountsTotal > 0 ?
                    Math.round((double) cp.accountsCurrent / cp.accountsTotal * 100) : 0;

            cp.max = platformAccounts.size() * 20;
            long recentCount = records.stream()
                    .filter(r -> entry.getKey().equalsIgnoreCase(r.getProvider()))
                    .filter(r -> {
                        if (r.getCreatedAt() == null) return false;
                        try {
                            LocalDateTime t = LocalDateTime.parse(r.getCreatedAt(), DATE_TIME_FORMAT);
                            return t.isAfter(LocalDateTime.now(ZONE).minus(1, ChronoUnit.MINUTES));
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .count();
            cp.current = (int) recentCount;
            cp.percentage = cp.max > 0 ? Math.round((double) cp.current / cp.max * 100) : 0;

            cp.cooldownAccounts = platformAccounts.stream()
                    .filter(a -> a.getErrors() != null && a.getErrors() > 0)
                    .map(AccountItem::getName)
                    .collect(Collectors.toList());

            result.add(cp);
        }

        result.sort(Comparator.comparing(c -> c.name));
        return result;
    }

    // ==================== Chart Data ====================

    private List<Map<String, Object>> computeAccountSwitchTrend(List<RelayRecordItem> records,
                                                                  LocalDateTime start,
                                                                  LocalDateTime end) {
        List<Map<String, Object>> trend = new ArrayList<>();
        Map<String, Set<Long>> accountsByMinute = new HashMap<>();

        for (RelayRecordItem r : records) {
            if (r.getCreatedAt() == null || r.getAccountId() == null) continue;
            try {
                LocalDateTime t = LocalDateTime.parse(r.getCreatedAt(), DATE_TIME_FORMAT);
                String key = t.format(TIME_FORMAT);
                accountsByMinute.computeIfAbsent(key, k -> new HashSet<>()).add(r.getAccountId());
            } catch (Exception ignored) {}
        }

        int totalMinutes = (int) ChronoUnit.MINUTES.between(start, end);
        int step = Math.max(1, totalMinutes / 20);
        for (int i = 0; i <= totalMinutes; i += step) {
            LocalDateTime t = start.plus(i, ChronoUnit.MINUTES);
            String key = t.format(TIME_FORMAT);
            Map<String, Object> point = new HashMap<>();
            point.put("time", key);
            Set<Long> accounts = accountsByMinute.getOrDefault(key, Collections.emptySet());
            point.put("value", accounts.size() > 0 ? 1.0 / accounts.size() : 0);
            trend.add(point);
        }
        return trend;
    }

    private List<Map<String, Object>> computeThroughputTrend(List<RelayRecordItem> records,
                                                               LocalDateTime start,
                                                               LocalDateTime end) {
        List<Map<String, Object>> trend = new ArrayList<>();

        Map<String, List<RelayRecordItem>> byMinute = records.stream()
                .filter(r -> r.getCreatedAt() != null)
                .collect(Collectors.groupingBy(r -> {
                    try {
                        LocalDateTime t = LocalDateTime.parse(r.getCreatedAt(), DATE_TIME_FORMAT);
                        return t.format(TIME_FORMAT);
                    } catch (Exception e) {
                        return "unknown";
                    }
                }));

        int totalMinutes = (int) ChronoUnit.MINUTES.between(start, end);
        int step = Math.max(1, totalMinutes / 20);
        for (int i = 0; i <= totalMinutes; i += step) {
            LocalDateTime t = start.plus(i, ChronoUnit.MINUTES);
            String key = t.format(TIME_FORMAT);
            List<RelayRecordItem> m = byMinute.getOrDefault(key, Collections.emptyList());

            Map<String, Object> point = new HashMap<>();
            point.put("time", key);
            point.put("qps", Math.round(m.size() / 60.0 * 100.0) / 100.0);
            long tTokens = m.stream()
                    .mapToLong(r -> r.getTotalTokens() != null ? r.getTotalTokens() : 0)
                    .sum();
            point.put("tps", Math.round(tTokens / 60.0 / 1000.0 * 100.0) / 100.0);
            trend.add(point);
        }
        return trend;
    }

    private List<Map<String, Object>> computeLatencyDistribution(List<RelayRecordItem> records) {
        int[] buckets = {0, 0, 0, 0, 0, 0};
        String[] labels = {"0-100ms", "100-200ms", "200-500ms", "500-1000ms", "1000-2000ms", "2000ms+"};

        for (RelayRecordItem r : records) {
            long lat = r.getLatencyMs() != null ? r.getLatencyMs() : 0;
            if (lat <= 100) buckets[0]++;
            else if (lat <= 200) buckets[1]++;
            else if (lat <= 500) buckets[2]++;
            else if (lat <= 1000) buckets[3]++;
            else if (lat <= 2000) buckets[4]++;
            else buckets[5]++;
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            Map<String, Object> item = new HashMap<>();
            item.put("range", labels[i]);
            item.put("count", buckets[i]);
            result.add(item);
        }
        return result;
    }

    private Map<String, Object> computeErrorDistribution(List<RelayRecordItem> records) {
        int upstream = (int) records.stream()
                .filter(r -> !Boolean.TRUE.equals(r.getSuccess()) &&
                        (r.getStatusCode() == null ||
                                (r.getStatusCode() != 429 && r.getStatusCode() != 529)))
                .count();
        int sla = (int) records.stream()
                .filter(r -> !Boolean.TRUE.equals(r.getSuccess()))
                .count();

        Map<String, Object> result = new HashMap<>();
        result.put("upstream", upstream);
        result.put("sla", Math.max(0, sla - upstream));
        return result;
    }

    private List<Map<String, Object>> computeErrorTrend(List<RelayRecordItem> records,
                                                          LocalDateTime start,
                                                          LocalDateTime end) {
        List<Map<String, Object>> trend = new ArrayList<>();

        Map<String, List<RelayRecordItem>> byMinute = records.stream()
                .filter(r -> r.getCreatedAt() != null)
                .collect(Collectors.groupingBy(r -> {
                    try {
                        LocalDateTime t = LocalDateTime.parse(r.getCreatedAt(), DATE_TIME_FORMAT);
                        return t.format(TIME_FORMAT);
                    } catch (Exception e) {
                        return "unknown";
                    }
                }));

        int totalMinutes = (int) ChronoUnit.MINUTES.between(start, end);
        int step = Math.max(1, totalMinutes / 20);
        for (int i = 0; i <= totalMinutes; i += step) {
            LocalDateTime t = start.plus(i, ChronoUnit.MINUTES);
            String key = t.format(TIME_FORMAT);
            List<RelayRecordItem> m = byMinute.getOrDefault(key, Collections.emptyList());

            int slaError = (int) m.stream()
                    .filter(r -> !Boolean.TRUE.equals(r.getSuccess()))
                    .count();
            int upstreamErr = (int) m.stream()
                    .filter(r -> !Boolean.TRUE.equals(r.getSuccess()) &&
                            (r.getStatusCode() == null ||
                                    (r.getStatusCode() != 429 && r.getStatusCode() != 529)))
                    .count();
            int businessLimit = (int) m.stream()
                    .filter(r -> r.getStatusCode() != null && r.getStatusCode() == 429)
                    .count();

            Map<String, Object> point = new HashMap<>();
            point.put("time", key);
            point.put("slaError", slaError);
            point.put("upstreamError", upstreamErr);
            point.put("businessLimit", businessLimit);
            trend.add(point);
        }
        return trend;
    }

    private List<AlertEvent> computeAlertEvents() {
        List<MonitorAlertItem> alertItems = alertRepository.findAll();
        List<AlertEvent> events = new ArrayList<>();

        int limit = Math.min(alertItems.size(), 50);
        for (int i = 0; i < limit; i++) {
            MonitorAlertItem a = alertItems.get(i);
            AlertEvent event = new AlertEvent();
            event.time = a.getTimeLabel();
            event.level = a.getLevelName();
            event.levelStatus = a.getStatusName();
            event.platform = "-";
            event.ruleId = "-";
            event.title = a.getEventText();
            event.description = a.getEventText();
            event.duration = "-";
            event.dimension = "-";
            event.emailSent = "已忽略";
            events.add(event);
        }
        return events;
    }

    // ==================== Node CRUD ====================

    public PageResponse<MonitorNodeItem> listNodes(String keyword) {
        List<MonitorNodeItem> items = nodeRepository.findAll();
        if (keyword != null && !keyword.trim().isEmpty()) {
            final String kw = keyword.toLowerCase();
            items = items.stream()
                    .filter(n -> containsIgnoreCase(n.getNodeName(), kw) ||
                            containsIgnoreCase(n.getCheckUrl(), kw))
                    .collect(Collectors.toList());
        }
        return new PageResponse<>(items);
    }

    public MonitorNodeItem getNode(Long id) {
        MonitorNodeItem item = nodeRepository.findById(id);
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "节点不存在");
        }
        return item;
    }

    public MonitorNodeItem createNode(MonitorNodeItem.Request request) {
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("节点名称不能为空");
        }
        if (request.getUrl() == null || request.getUrl().trim().isEmpty()) {
            throw new IllegalArgumentException("健康检查 URL 不能为空");
        }
        MonitorNodeItem item = new MonitorNodeItem();
        item.setNodeName(request.getName().trim());
        item.setCheckUrl(request.getUrl().trim());
        item.setLocationName(request.getLocation() != null ? request.getLocation().trim() : "未知");
        item.setStatusName("未检测");
        item.setLatencyValue("0ms");
        item.setUptimeValue("0.00%");
        item.setTotalChecks(0);
        item.setSuccessChecks(0);
        item.setLastCheckLabel(TimeSupport.nowDateTime());
        return nodeRepository.save(item);
    }

    public MonitorNodeItem updateNode(Long id, MonitorNodeItem.Request request) {
        MonitorNodeItem current = getNode(id);
        if (request.getName() != null) current.setNodeName(request.getName().trim());
        if (request.getUrl() != null) current.setCheckUrl(request.getUrl().trim());
        if (request.getLocation() != null) current.setLocationName(request.getLocation().trim());
        return nodeRepository.update(id, current);
    }

    public void deleteNode(Long id) {
        getNode(id);
        nodeRepository.deleteById(id);
    }

    public MonitorNodeItem checkNodeNow(Long id) {
        MonitorNodeItem node = getNode(id);
        checkNodeInternal(node);
        return nodeRepository.findById(id);
    }

    public List<MonitorNodeItem> checkAllNodesNow() {
        List<MonitorNodeItem> nodes = nodeRepository.findAll();
        for (MonitorNodeItem node : nodes) {
            checkNodeInternal(node);
        }
        return nodeRepository.findAll();
    }

    // ==================== Alert Rules CRUD ====================

    public PageResponse<MonitorAlertRuleItem> listAlertRules() {
        return new PageResponse<>(alertRuleRepository.findAll());
    }

    public MonitorAlertRuleItem createAlertRule(MonitorAlertRuleItem.Request request) {
        if (request.ruleName() == null || request.ruleName().trim().isEmpty()) {
            throw new IllegalArgumentException("规则名称不能为空");
        }
        if (request.metricKey() == null || request.metricKey().trim().isEmpty()) {
            throw new IllegalArgumentException("监控指标不能为空");
        }
        if (request.thresholdValue() == null) {
            throw new IllegalArgumentException("阈值不能为空");
        }
        MonitorAlertRuleItem item = new MonitorAlertRuleItem();
        item.setRuleName(request.ruleName().trim());
        item.setMetricKey(request.metricKey().trim());
        item.setOperator(request.operator() != null ? request.operator() : ">");
        item.setThresholdValue(request.thresholdValue());
        item.setLevelName(request.levelName() != null ? request.levelName() : "WARNING");
        item.setEnabled(request.enabled() != null ? request.enabled() : true);
        item.setDescription(request.description());
        return alertRuleRepository.save(item);
    }

    public MonitorAlertRuleItem updateAlertRule(Long id, MonitorAlertRuleItem.Request request) {
        MonitorAlertRuleItem current = alertRuleRepository.findById(id);
        if (current == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "规则不存在");
        }
        if (request.ruleName() != null) current.setRuleName(request.ruleName().trim());
        if (request.metricKey() != null) current.setMetricKey(request.metricKey().trim());
        if (request.operator() != null) current.setOperator(request.operator());
        if (request.thresholdValue() != null) current.setThresholdValue(request.thresholdValue());
        if (request.levelName() != null) current.setLevelName(request.levelName());
        if (request.enabled() != null) current.setEnabled(request.enabled());
        if (request.description() != null) current.setDescription(request.description());
        return alertRuleRepository.update(id, current);
    }

    public void deleteAlertRule(Long id) {
        MonitorAlertRuleItem item = alertRuleRepository.findById(id);
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "规则不存在");
        }
        alertRuleRepository.deleteById(id);
    }

    // ==================== Health Check ====================

    private void checkNodeInternal(MonitorNodeItem node) {
        String previousStatus = node.getStatusName();
        long start = System.currentTimeMillis();
        try {
            restTemplate.getForEntity(node.getCheckUrl(), String.class);
            long latency = System.currentTimeMillis() - start;
            node.setLatencyValue(latency + "ms");
            node.setStatusName("正常");
            node.setSuccessChecks(node.getSuccessChecks() + 1);

            if ("异常".equals(previousStatus)) {
                createAlert("INFO", "节点 " + node.getNodeName() + " 已恢复正常", "已恢复");
            }
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            node.setLatencyValue(latency + "ms");
            node.setStatusName("异常");

            if (!"异常".equals(previousStatus)) {
                createAlertWithCooldown("node_" + node.getId(), "CRITICAL",
                        "节点 " + node.getNodeName() + " 健康检查失败: " + e.getMessage(), "处理中");
            }
        }
        node.setTotalChecks(node.getTotalChecks() + 1);

        double uptimePercent = node.getTotalChecks() > 0
                ? (double) node.getSuccessChecks() / node.getTotalChecks() * 100
                : 0;
        node.setUptimeValue(String.format("%.2f%%", uptimePercent));
        node.setLastCheckLabel(TimeSupport.nowDateTime());

        try {
            nodeRepository.update(node.getId(), node);
        } catch (Exception e) {
            log.error("Failed to update node {}: {}", node.getNodeName(), e.getMessage());
        }
    }

    private void createAlertWithCooldown(String key, String level, String event, String status) {
        long now = System.currentTimeMillis();
        Long lastAlert = alertCooldown.get(key);
        if (lastAlert != null && (now - lastAlert) < ALERT_COOLDOWN_MS) {
            return;
        }
        alertCooldown.put(key, now);
        createAlert(level, event, status);
    }

    private void createAlert(String level, String event, String status) {
        String time = LocalDateTime.now(ZONE).format(FULL_FORMAT);
        String color;
        if ("CRITICAL".equals(level)) {
            color = "#ef4444";
        } else if ("WARNING".equals(level)) {
            color = "#f59e0b";
        } else {
            color = "#10b981";
        }
        MonitorAlertItem alert = new MonitorAlertItem(
                null, time, level, event, status, "系统自动", color);
        try {
            alertRepository.save(alert);
        } catch (Exception e) {
            log.error("Failed to save alert: {}", e.getMessage());
        }
    }

    private com.sun.management.OperatingSystemMXBean getOsBean() {
        return (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    }

    private boolean containsIgnoreCase(String source, String keyword) {
        if (source == null || keyword == null) return false;
        return source.toLowerCase().contains(keyword);
    }
}
