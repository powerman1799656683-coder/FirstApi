package com.firstapi.backend.service;

import com.firstapi.backend.common.CurrentSessionHolder;
import com.firstapi.backend.model.MyRecordsData;
import com.firstapi.backend.model.MyRecordsData.RecordItem;
import com.firstapi.backend.model.MyRecordsData.StatCard;
import com.firstapi.backend.model.RelayRecordItem;
import com.firstapi.backend.repository.RelayRecordRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class MyRecordsService {

    private final RelayRecordRepository relayRecordRepository;

    public MyRecordsService(RelayRecordRepository relayRecordRepository) {
        this.relayRecordRepository = relayRecordRepository;
    }

    public MyRecordsData getRecords(String keyword) {
        Long ownerId = CurrentSessionHolder.require().getId();

        BigDecimal totalCost = relayRecordRepository.sumCostByOwner(ownerId);
        long totalTokens = relayRecordRepository.sumTotalTokensByOwner(ownerId);
        double avgLatency = relayRecordRepository.avgLatencyMsByOwner(ownerId);

        MyRecordsData data = new MyRecordsData();
        data.stats = List.of(
                new StatCard("总消费", formatCost(totalCost), "累计花费", "zap", "#00f2ff"),
                new StatCard("总令牌", formatTokens(totalTokens), "累计消耗", "cpu", "#3b82f6"),
                new StatCard("平均响应", formatLatency(avgLatency), "系统稳定", "clock", "#10b981")
        );

        List<RelayRecordItem> rawRecords = relayRecordRepository.findByOwnerId(ownerId, keyword);
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
            String task = r.getProvider() != null ? r.getProvider() : "对话补全";
            records.add(new RecordItem(r.getId(), time, r.getModel(), task, tokens, cost, status));
        }
        data.records = records;
        return data;
    }

    private String formatCost(BigDecimal cost) {
        if (cost == null || cost.compareTo(BigDecimal.ZERO) == 0) return "¥0.00";
        return "¥" + cost.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
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
