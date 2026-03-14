package com.firstapi.backend.model;

import java.util.List;

public class MonitorData {
    public List<MonitorStat> stats;
    public List<LoadPoint> chartData;
    public List<NodeStatus> nodes;
    public List<MonitorAlert> alerts;

    public static class MonitorStat {
        public String title;
        public String value;
        public String footer;
        public String icon;
        public String iconBg;
        public String iconColor;

        public MonitorStat(String title, String value, String footer, String icon, String iconBg, String iconColor) {
            this.title = title;
            this.value = value;
            this.footer = footer;
            this.icon = icon;
            this.iconBg = iconBg;
            this.iconColor = iconColor;
        }
    }

    public static class LoadPoint {
        public String name;
        public int cpu;
        public int mem;
        public int net;

        public LoadPoint(String name, int cpu, int mem, int net) {
            this.name = name;
            this.cpu = cpu;
            this.mem = mem;
            this.net = net;
        }
    }

    public static class NodeStatus {
        public String name;
        public String status;
        public String uptime;
        public String latency;

        public NodeStatus(String name, String status, String uptime, String latency) {
            this.name = name;
            this.status = status;
            this.uptime = uptime;
            this.latency = latency;
        }
    }

    public static class MonitorAlert {
        public String time;
        public String level;
        public String event;
        public String status;
        public String owner;
        public String statusColor;

        public MonitorAlert(String time, String level, String event, String status, String owner, String statusColor) {
            this.time = time;
            this.level = level;
            this.event = event;
            this.status = status;
            this.owner = owner;
            this.statusColor = statusColor;
        }
    }
}
