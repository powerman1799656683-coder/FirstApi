package com.firstapi.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.firstapi.backend.common.SimpleStore;

public class SubscriptionItem implements SimpleStore.Identifiable {
    private Long id;
    private String user;
    private Long uid;
    private String group;
    private String usage;
    private Double progress;
    private String expiry;
    private String status;

    public SubscriptionItem() {}

    public SubscriptionItem(Long id, String user, Long uid, String group, String usage, Double progress, String expiry, String status) {
        this.id = id;
        this.user = user;
        this.uid = uid;
        this.group = group;
        this.usage = usage;
        this.progress = progress;
        this.expiry = expiry;
        this.status = status;
    }

    @Override public Long getId() { return id; }
    @Override public void setId(Long id) { this.id = id; }
    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }
    public Long getUid() { return uid; }
    public void setUid(Long uid) { this.uid = uid; }
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Request {
        private String user;
        private Long uid;
        private String group;
        private String usage;
        private Double progress;
        private String expiry;
        private String status;
        private String quota;

        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }
        public Long getUid() { return uid; }
        public void setUid(Long uid) { this.uid = uid; }
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
    }
}
