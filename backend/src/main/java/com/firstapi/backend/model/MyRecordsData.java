package com.firstapi.backend.model;

import java.util.List;

public class MyRecordsData {
    public List<StatCard> stats;
    public List<RecordItem> records;

    public static class StatCard {
        public String title;
        public String value;
        public String footer;
        public String icon;
        public String iconColor;

        public StatCard(String title, String value, String footer, String icon, String iconColor) {
            this.title = title;
            this.value = value;
            this.footer = footer;
            this.icon = icon;
            this.iconColor = iconColor;
        }
    }

    public static class RecordItem {
        public Long id;
        public String time;
        public String model;
        public String task;
        public String tokens;
        public String cost;
        public String status;

        public RecordItem(Long id, String time, String model, String task, String tokens, String cost, String status) {
            this.id = id;
            this.time = time;
            this.model = model;
            this.task = task;
            this.tokens = tokens;
            this.cost = cost;
            this.status = status;
        }
    }
}
