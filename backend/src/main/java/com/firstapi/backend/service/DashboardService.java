package com.firstapi.backend.service;

import com.firstapi.backend.model.DashboardData;
import com.firstapi.backend.model.DashboardData.AlertRow;
import com.firstapi.backend.model.DashboardData.ModelShare;
import com.firstapi.backend.model.DashboardData.StatCard;
import com.firstapi.backend.model.DashboardData.TrendPoint;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
public class DashboardService {

    public DashboardData getDashboard() {
        DashboardData data = new DashboardData();
        data.stats = Arrays.asList(
                new StatCard("API 密钥", "38", "+12% 较上周", "key", "#00f2ff"),
                new StatCard("活跃账号", "6", "系统运行正常", "shield", "#3b82f6"),
                new StatCard("今日请求", "2,596", "并发峰值: 120", "activity", "#10b981"),
                new StatCard("注册用户", "22", "今日新增: 2", "users", "#8b5cf6"),
                new StatCard("今日流量", "185M", "Tokens 消耗", "box", "#f59e0b"),
                new StatCard("累计消耗", "5.21B", "Tokens 总计", "database", "#ef4444"),
                new StatCard("响应性能", "420ms", "平均延迟", "zap", "#00f2ff"),
                new StatCard("系统在线", "99.9%", "SLA 指标", "clock", "#10b981")
        );
        data.modelDistribution = Arrays.asList(
                new ModelShare("GPT-4o", 45, "#00f2ff"),
                new ModelShare("Claude 3.5", 30, "#3b82f6"),
                new ModelShare("Gemini 1.5", 15, "#10b981"),
                new ModelShare("Llama 3", 10, "#8b5cf6")
        );
        data.trends = Arrays.asList(
                new TrendPoint("00:00", 120, 45),
                new TrendPoint("04:00", 80, 30),
                new TrendPoint("08:00", 250, 90),
                new TrendPoint("12:00", 450, 180),
                new TrendPoint("16:00", 380, 140),
                new TrendPoint("20:00", 520, 210),
                new TrendPoint("23:59", 400, 160)
        );
        data.alerts = Arrays.asList(
                new AlertRow("19:00:22", "US-EAST-1", "CRITICAL", "接口响应超时，延迟超过 5000ms"),
                new AlertRow("18:45:10", "HKG-2", "WARNING", "系统负载超过 85%，资源紧张")
        );
        return data;
    }
}
