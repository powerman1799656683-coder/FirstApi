package com.firstapi.backend.service;

import com.firstapi.backend.model.RecordsData;
import com.firstapi.backend.model.RecordsData.BarPoint;
import com.firstapi.backend.model.RecordsData.PieSlice;
import com.firstapi.backend.model.RecordsData.RecordItem;
import com.firstapi.backend.model.RecordsData.StatCard;
import com.firstapi.backend.model.RelayRecordItem;
import com.firstapi.backend.repository.RelayRecordRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class RecordsService {

    private final RelayRecordRepository relayRecordRepository;

    public RecordsService(RelayRecordRepository relayRecordRepository) {
        this.relayRecordRepository = relayRecordRepository;
    }

    public RecordsData getRecords(String keyword) {
        RecordsData data = new RecordsData();

        // 统计卡片（真实聚合）
        BigDecimal totalCost = relayRecordRepository.sumCost();
        long totalTokens = relayRecordRepository.sumTotalTokens();
        long activeKeys = relayRecordRepository.countDistinctApiKeys();
        double avgLatency = relayRecordRepository.avgLatencyMs();

        data.stats = List.of(
                new StatCard("总调用成本", formatCost(totalCost), "", "zap", "#ef4444"),
                new StatCard("总消耗令牌", formatTokens(totalTokens), "", "activity", "#10b981"),
                new StatCard("活跃 API 密钥", String.valueOf(activeKeys), "", "database", "#3b82f6"),
                new StatCard("平均响应耗时", formatLatency(avgLatency), "", "clock", "#00f2ff")
        );

        // 模型分布饼图
        List<RelayRecordRepository.ModelStat> modelStats = relayRecordRepository.groupByModel();
        long totalCalls = modelStats.stream().mapToLong(RelayRecordRepository.ModelStat::callCount).sum();
        String[] colors = {"#00f2ff", "#3b82f6", "#10b981", "#8b5cf6", "#f59e0b", "#ef4444", "#ec4899"};
        List<PieSlice> pie = new ArrayList<>();
        for (int i = 0; i < modelStats.size(); i++) {
            RelayRecordRepository.ModelStat ms = modelStats.get(i);
            int pct = totalCalls > 0 ? (int) Math.round(ms.callCount() * 100.0 / totalCalls) : 0;
            pie.add(new PieSlice(ms.modelName(), pct, colors[i % colors.length]));
        }
        data.modelPie = pie;

        // 趋势柱状图（最近 7 天）
        List<RelayRecordRepository.DayStat> dayStats = relayRecordRepository.groupByDate(7);
        List<BarPoint> bar = new ArrayList<>();
        for (RelayRecordRepository.DayStat ds : dayStats) {
            bar.add(new BarPoint(ds.date(), (int) Math.min(ds.totalTokens(), Integer.MAX_VALUE)));
        }
        data.bar = bar;

        // 记录列表
        List<RelayRecordItem> rawRecords = relayRecordRepository.findAll(keyword);
        List<RecordItem> records = new ArrayList<>();
        for (RelayRecordItem r : rawRecords) {
            String tokens = r.getTotalTokens() != null ? formatNumber(r.getTotalTokens()) : "-";
            String cost;
            if (r.getCost() != null) {
                cost = "¥" + r.getCost().setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
            } else if ("USAGE_MISSING".equals(r.getPricingStatus())) {
                cost = "usage缺失";
            } else if ("NOT_FOUND".equals(r.getPricingStatus())) {
                cost = "未定价";
            } else {
                cost = "-";
            }
            String status = Boolean.TRUE.equals(r.getSuccess()) ? "成功" : "失败";
            String time = r.getCreatedAt() != null ? r.getCreatedAt() : "-";
            String user = "uid:" + r.getOwnerId();
            String key = "keyId:" + r.getApiKeyId();
            records.add(new RecordItem(r.getId(), time, user, key, r.getModel(), tokens, cost, status));
        }
        data.records = records;
        data.models = relayRecordRepository.distinctModels();
        return data;
    }

    private String formatCost(BigDecimal cost) {
        if (cost == null || cost.compareTo(BigDecimal.ZERO) == 0) return "¥0.00";
        return "¥" + cost.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatTokens(long tokens) {
        if (tokens >= 1_000_000) return String.format("%.1fM", tokens / 1_000_000.0);
        if (tokens >= 1_000) return String.format("%.1fK", tokens / 1_000.0);
        return String.valueOf(tokens);
    }

    private String formatLatency(double ms) {
        if (ms >= 1000) return String.format("%.2fs", ms / 1000.0);
        return String.format("%.0fms", ms);
    }

    private String formatNumber(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format("%,d", n);
        return String.valueOf(n);
    }
}
