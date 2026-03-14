package com.firstapi.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.firstapi.backend.common.SimpleStore;

public class GroupItem implements SimpleStore.Identifiable {
    private Long id;
    private String name;
    private String billingType;
    private String userCount;
    private String status;
    private String priority;
    private String rate;

    public GroupItem() {}

    public GroupItem(Long id, String name, String billingType, String userCount, String status, String priority, String rate) {
        this.id = id;
        this.name = name;
        this.billingType = billingType;
        this.userCount = userCount;
        this.status = status;
        this.priority = priority;
        this.rate = rate;
    }

    @Override public Long getId() { return id; }
    @Override public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBillingType() { return billingType; }
    public void setBillingType(String billingType) { this.billingType = billingType; }
    public String getUserCount() { return userCount; }
    public void setUserCount(String userCount) { this.userCount = userCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getRate() { return rate; }
    public void setRate(String rate) { this.rate = rate; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Request {
        private String name;
        private String billingType;
        private String userCount;
        private String status;
        private String priority;
        private String rate;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getBillingType() { return billingType; }
        public void setBillingType(String billingType) { this.billingType = billingType; }
        public String getUserCount() { return userCount; }
        public void setUserCount(String userCount) { this.userCount = userCount; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
        public String getRate() { return rate; }
        public void setRate(String rate) { this.rate = rate; }
    }
}
