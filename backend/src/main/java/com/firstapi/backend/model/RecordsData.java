package com.firstapi.backend.model;

import java.util.List;

public class RecordsData {
    public List<StatCard> stats;
    public List<PieSlice> modelPie;
    public List<BarPoint> bar;
    public List<RecordItem> records;
    public List<String> models;

    public static class StatCard {
        public String title;
        public String value;
        public String trend;
        public String icon;
        public String color;

        public StatCard(String title, String value, String trend, String icon, String color) {
            this.title = title;
            this.value = value;
            this.trend = trend;
            this.icon = icon;
            this.color = color;
        }
    }

    public static class PieSlice {
        public String name;
        public int value;
        public String color;

        public PieSlice(String name, int value, String color) {
            this.name = name;
            this.value = value;
            this.color = color;
        }
    }

    public static class BarPoint {
        public String name;
        public int tokens;

        public BarPoint(String name, int tokens) {
            this.name = name;
            this.tokens = tokens;
        }
    }

    public static class RecordItem {
        public Long id;
        public String time;
        public String user;
        public String key;
        public String model;
        public String tokens;
        public String cost;
        public String status;

        public RecordItem(Long id, String time, String user, String key, String model, String tokens, String cost, String status) {
            this.id = id;
            this.time = time;
            this.user = user;
            this.key = key;
            this.model = model;
            this.tokens = tokens;
            this.cost = cost;
            this.status = status;
        }
    }
}
