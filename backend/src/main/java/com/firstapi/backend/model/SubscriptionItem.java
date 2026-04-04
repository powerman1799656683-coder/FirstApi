package com.firstapi.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.firstapi.backend.common.SimpleStore;

public class SubscriptionItem implements SimpleStore.Identifiable {
    private Long id;
    private String user;
    private Long uid;
    private Long groupId;
    private Long planId;
    private String group;
    private String usage;
    private Double progress;
    private String expiry;
    private String status;
    private String dailyLimit;

    public SubscriptionItem() {}

    public SubscriptionItem(Long id, String user, Long uid, String group, String usage, Double progress, String expiry, String status, String dailyLimit) {
        this.id = id;
        this.user = user;
        this.uid = uid;
        this.group = group;
        this.usage = usage;
        this.progress = progress;
        this.expiry = expiry;
        this.status = status;
        this.dailyLimit = dailyLimit;
    }

    @Override public Long getId() { return id; }
    @Override public void setId(Long id) { this.id = id; }
    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }
    public Long getUid() { return uid; }
    public void setUid(Long uid) { this.uid = uid; }
    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }
    public String getUsage() { return usage; }
    public void setUsage(String usage) { this.usage = usage; }
    public Double getProgress() { return progress; }
    public void setProgress(Double progress) { this.progress = progress; }
    public String getExpiry() { return expiry; }
    public void setExpiry(String expiry) { this.expiry = expiry; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDailyLimit() { return dailyLimit; }
    public void setDailyLimit(String dailyLimit) { this.dailyLimit = dailyLimit; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Request {
        private String user;
        private Long uid;
        private Long groupId;
        private Long planId;
        private String group;
        private String usage;
        private Double progress;
        private String expiry;
        private String status;
        private String quota;
        private String dailyLimit;

        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }
        public Long getUid() { return uid; }
        public void setUid(Long uid) { this.uid = uid; }
        public Long getGroupId() { return groupId; }
        public void setGroupId(Long groupId) { this.groupId = groupId; }
        public Long getPlanId() { return planId; }
        public void setPlanId(Long planId) { this.planId = planId; }
        public String getGroup() { return group; }
        public void setGroup(String group) { this.group = group; }
        public String getUsage() { return usage; }
        public void setUsage(String usage) { this.usage = usage; }
        public Double getProgress() { return progress; }
        public void setProgress(Double progress) { this.progress = progress; }
        public String getExpiry() { return expiry; }
        public void setExpiry(String expiry) { this.expiry = expiry; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getQuota() { return quota; }
        public void setQuota(String quota) { this.quota = quota; }
        public String getDailyLimit() { return dailyLimit; }
        public void setDailyLimit(String dailyLimit) { this.dailyLimit = dailyLimit; }
    }
}
