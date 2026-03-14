package com.firstapi.backend.model;

import java.util.List;

public class DashboardData {
    public List<StatCard> stats;
    public List<ModelShare> modelDistribution;
    public List<TrendPoint> trends;
    public List<AlertRow> alerts;

    public static class StatCard {
        public String title;
        public String value;
        public String sub;
        public String icon;
        public String color;

        public StatCard(String title, String value, String sub, String icon, String color) {
            this.title = title;
            this.value = value;
            this.sub = sub;
            this.icon = icon;
            this.color = color;
        }
    }

    public static class ModelShare {
        public String name;
        public int value;
        public String color;

        public ModelShare(String name, int value, String color) {
            this.name = name;
            this.value = value;
            this.color = color;
        }
    }

    public static class TrendPoint {
        public String name;
        public int tokens;
        public int requests;

        public TrendPoint(String name, int tokens, int requests) {
            this.name = name;
            this.tokens = tokens;
            this.requests = requests;
        }
    }

    public static class AlertRow {
        public String time;
        public String node;
        public String level;
        public String description;

        public AlertRow(String time, String node, String level, String description) {
            this.time = time;
            this.node = node;
            this.level = level;
            this.description = description;
        }
    }
}
