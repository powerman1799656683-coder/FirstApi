package com.firstapi.backend.service;

import com.firstapi.backend.model.MonitorData;
import com.firstapi.backend.model.MonitorData.LoadPoint;
import com.firstapi.backend.model.MonitorData.MonitorAlert;
import com.firstapi.backend.model.MonitorData.MonitorStat;
import com.firstapi.backend.model.MonitorData.NodeStatus;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
public class MonitorService {

    public MonitorData getMonitorData() {
        MonitorData data = new MonitorData();
        data.stats = Arrays.asList(
                new MonitorStat("CPU 负载", "12.8%", "正常运行", "cpu", "rgba(0, 242, 255, 0.1)", "#00f2ff"),
                new MonitorStat("内存占用", "4.2 GB", "总量: 16 GB", "database", "rgba(59, 130, 246, 0.1)", "#3b82f6"),
                new MonitorStat("磁盘剩余", "756 GB", "总量: 1 TB", "harddrive", "rgba(16, 185, 129, 0.1)", "#10b981"),
                new MonitorStat("网络吞吐", "12.5 MB/s", "延迟: 15ms", "network", "rgba(245, 158, 11, 0.1)", "#f59e0b")
        );
        data.chartData = Arrays.asList(
                new LoadPoint("00:00", 18, 32, 90),
                new LoadPoint("04:00", 15, 28, 84),
                new LoadPoint("08:00", 24, 39, 110),
                new LoadPoint("12:00", 31, 42, 132),
                new LoadPoint("16:00", 28, 38, 120),
                new LoadPoint("20:00", 35, 46, 140)
        );
        data.nodes = Arrays.asList(
                new NodeStatus("香港 AWS-1", "在线", "99.99%", "25ms"),
                new NodeStatus("美国 Google-2", "在线", "99.95%", "145ms"),
                new NodeStatus("日本 Linode-3", "在线", "99.98%", "45ms"),
                new NodeStatus("新加坡 Oracle-4", "预警", "98.50%", "12ms")
        );
        data.alerts = Arrays.asList(
                new MonitorAlert("19:22:15", "CRITICAL", "磁盘空间不足 95% - /var/log", "处理中", "运维 A", "#f59e0b"),
                new MonitorAlert("18:45:10", "INFO", "新节点 HKG-2 加入集群", "已恢复", "系统自动", "#10b981")
        );
        return data;
    }
}
