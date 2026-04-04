package com.firstapi.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.firstapi.backend.common.SimpleStore;

public class SubscriptionPlanItem implements SimpleStore.Identifiable {
    private Long id;
    private String name;
    private String monthlyQuota;
    private String dailyLimit;
    private String status;

    public SubscriptionPlanItem() {}

    public SubscriptionPlanItem(Long id, String name, String monthlyQuota, String dailyLimit, String status) {
        this.id = id;
        this.name = name;
        this.monthlyQuota = monthlyQuota;
        this.dailyLimit = dailyLimit;
        this.status = status;
    }

    @Override public Long getId() { return id; }
    @Override public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getMonthlyQuota() { return monthlyQuota; }
    public void setMonthlyQuota(String monthlyQuota) { this.monthlyQuota = monthlyQuota; }
    public String getDailyLimit() { return dailyLimit; }
    public void setDailyLimit(String dailyLimit) { this.dailyLimit = dailyLimit; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Request {
        private String name;
        private String monthlyQuota;
        private String dailyLimit;
        private String status;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getMonthlyQuota() { return monthlyQuota; }
        public void setMonthlyQuota(String monthlyQuota) { this.monthlyQuota = monthlyQuota; }
        public String getDailyLimit() { return dailyLimit; }
        public void setDailyLimit(String dailyLimit) { this.dailyLimit = dailyLimit; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
