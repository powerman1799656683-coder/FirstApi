package com.firstapi.backend.model;

import java.util.List;

public class MySubscriptionData {
    public Plan plan;
    public List<String> features;
    public List<UsageItem> usage;
    public RequestStats requestStats;
    public List<HistoryItem> history;

    public static class Plan {
        public String name;
        public String renewalDate;

        public Plan() {}

        public Plan(String name, String renewalDate) {
            this.name = name;
            this.renewalDate = renewalDate;
        }
    }

    public static class UsageItem {
        public String label;
        public String used;
        public String total;
        public int percent;
        public String gradient;
        public String shadow;

        public UsageItem() {}

        public UsageItem(String label, String used, String total, int percent, String gradient, String shadow) {
            this.label = label;
            this.used = used;
            this.total = total;
            this.percent = percent;
            this.gradient = gradient;
            this.shadow = shadow;
        }
    }

    public static class RequestStats {
        public String todayRequests;
        public String avgResponse;

        public RequestStats() {}

        public RequestStats(String todayRequests, String avgResponse) {
            this.todayRequests = todayRequests;
            this.avgResponse = avgResponse;
        }
    }

    public static class HistoryItem {
        public String date;
        public String action;
        public String amount;
        public String status;

        public HistoryItem() {}

        public HistoryItem(String date, String action, String amount, String status) {
            this.date = date;
            this.action = action;
            this.amount = amount;
            this.status = status;
        }
    }
}
