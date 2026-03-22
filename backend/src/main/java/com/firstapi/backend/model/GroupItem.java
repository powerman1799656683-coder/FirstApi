package com.firstapi.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.firstapi.backend.common.SimpleStore;

import java.util.ArrayList;
import java.util.List;

public class GroupItem implements SimpleStore.Identifiable {
    private Long id;
    private String name;
    private String description;
    private String platform;
    private String accountType;
    private String billingType;
    private String billingAmount;
    private String rate;
    private String groupType;
    private String accountCount;
    private String status;
    private boolean claudeCodeLimit;
    private String fallbackGroup;
    private boolean modelRouting;
    private List<Long> fallbackGroupIds = new ArrayList<>();
    private Integer accountTotal;

    public GroupItem() {}

    public GroupItem(Long id, String name, String description, String platform, String billingType,
                     String billingAmount, String rate, String groupType, String accountCount,
                     String status, boolean claudeCodeLimit, String fallbackGroup, boolean modelRouting) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.platform = platform;
        this.billingType = billingType;
        this.billingAmount = billingAmount;
        this.rate = rate;
        this.groupType = groupType;
        this.accountCount = accountCount;
        this.status = status;
        this.claudeCodeLimit = claudeCodeLimit;
        this.fallbackGroup = fallbackGroup;
        this.modelRouting = modelRouting;
    }

    @Override public Long getId() { return id; }
    @Override public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getAccountType() { return accountType; }
    public void setAccountType(String accountType) { this.accountType = accountType; }
    public String getBillingType() { return billingType; }
    public void setBillingType(String billingType) { this.billingType = billingType; }
    public String getBillingAmount() { return billingAmount; }
    public void setBillingAmount(String billingAmount) { this.billingAmount = billingAmount; }
    public String getRate() { return rate; }
    public void setRate(String rate) { this.rate = rate; }
    public String getGroupType() { return groupType; }
    public void setGroupType(String groupType) { this.groupType = groupType; }
    public String getAccountCount() { return accountCount; }
    public void setAccountCount(String accountCount) { this.accountCount = accountCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isClaudeCodeLimit() { return claudeCodeLimit; }
    public void setClaudeCodeLimit(boolean claudeCodeLimit) { this.claudeCodeLimit = claudeCodeLimit; }
    public String getFallbackGroup() { return fallbackGroup; }
    public void setFallbackGroup(String fallbackGroup) { this.fallbackGroup = fallbackGroup; }
    public boolean isModelRouting() { return modelRouting; }
    public void setModelRouting(boolean modelRouting) { this.modelRouting = modelRouting; }
    public List<Long> getFallbackGroupIds() { return fallbackGroupIds; }
    public void setFallbackGroupIds(List<Long> fallbackGroupIds) {
        this.fallbackGroupIds = fallbackGroupIds == null ? new ArrayList<>() : fallbackGroupIds;
    }
    public Integer getAccountTotal() { return accountTotal; }
    public void setAccountTotal(Integer accountTotal) { this.accountTotal = accountTotal; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Request {
        private String name;
        private String description;
        private String platform;
        private String accountType;
        private String billingType;
        private String billingAmount;
        private String rate;
        private String groupType;
        private String accountCount;
        private String status;
        private Boolean claudeCodeLimit;
        private String fallbackGroup;
        private List<Long> fallbackGroupIds;
        private Boolean modelRouting;
        private String copyFromGroup;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getPlatform() { return platform; }
        public void setPlatform(String platform) { this.platform = platform; }
        public String getAccountType() { return accountType; }
        public void setAccountType(String accountType) { this.accountType = accountType; }
        public String getBillingType() { return billingType; }
        public void setBillingType(String billingType) { this.billingType = billingType; }
        public String getBillingAmount() { return billingAmount; }
        public void setBillingAmount(String billingAmount) { this.billingAmount = billingAmount; }
        public String getRate() { return rate; }
        public void setRate(String rate) { this.rate = rate; }
        public String getGroupType() { return groupType; }
        public void setGroupType(String groupType) { this.groupType = groupType; }
        public String getAccountCount() { return accountCount; }
        public void setAccountCount(String accountCount) { this.accountCount = accountCount; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Boolean getClaudeCodeLimit() { return claudeCodeLimit; }
        public void setClaudeCodeLimit(Boolean claudeCodeLimit) { this.claudeCodeLimit = claudeCodeLimit; }
        public String getFallbackGroup() { return fallbackGroup; }
        public void setFallbackGroup(String fallbackGroup) { this.fallbackGroup = fallbackGroup; }
        public List<Long> getFallbackGroupIds() { return fallbackGroupIds; }
        public void setFallbackGroupIds(List<Long> fallbackGroupIds) { this.fallbackGroupIds = fallbackGroupIds; }
        public Boolean getModelRouting() { return modelRouting; }
        public void setModelRouting(Boolean modelRouting) { this.modelRouting = modelRouting; }
        public String getCopyFromGroup() { return copyFromGroup; }
        public void setCopyFromGroup(String copyFromGroup) { this.copyFromGroup = copyFromGroup; }
    }
}
