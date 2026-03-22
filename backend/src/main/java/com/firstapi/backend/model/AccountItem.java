package com.firstapi.backend.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.firstapi.backend.common.SimpleStore;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class AccountItem implements SimpleStore.Identifiable {
    private Long id;
    private String name;
    private String platform;
    private String type;
    private String usage;
    private String status;
    private Integer errors;
    private String lastCheck;
    private String baseUrl;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String credential;
    private String notes;
    private String accountType;
    private String authMethod;
    private boolean tempDisabled;
    private boolean quotaExhausted;
    private String quotaNextRetryAt;
    private int quotaFailCount;
    private String quotaLastReason;
    private String quotaUpdatedAt;
    private int priorityValue = 1;
    private String expiryTime;
    private boolean autoSuspendExpiry = true;
    private Long proxyId;
    private int concurrency = 10;
    private BigDecimal billingRate = BigDecimal.ONE;
    private String models;
    private String tiers;
    private BigDecimal balance = BigDecimal.ZERO;
    private int weight = 1;
    private boolean interceptWarmupRequest;
    private boolean window5hCostControlEnabled;
    private BigDecimal window5hCostLimitUsd;
    private boolean sessionCountControlEnabled;
    private Integer sessionCountLimit;
    private String tlsFingerprintMode = "NONE";
    private boolean sessionIdMasqueradeEnabled;
    private int sessionIdMasqueradeTtlMinutes = 15;
    private String credentialMask;
    private String effectiveStatus;
    private Boolean scheduleEnabled;
    private Integer capacityUsed;
    private Integer capacityLimit;
    private Long todayRequests;
    private Long todayTokens;
    private String todayAccountCost;
    private String todayUserCost;
    private List<String> groups = new ArrayList<>();
    private List<Long> groupIds = new ArrayList<>();
    private List<UsageWindow> usageWindows = new ArrayList<>();
    private String recentUsedText;
    private String recentUsedAt;
    private Boolean expired;
    private String expiryLabel;

    public AccountItem() {}

    public AccountItem(Long id, String name, String platform, String type, String usage,
                       String status, Integer errors, String lastCheck, String baseUrl, String credential,
                       String notes, String accountType, String authMethod,
                       boolean tempDisabled,
                       int priorityValue,
                       String expiryTime, boolean autoSuspendExpiry,
                       Long proxyId,
                       int concurrency,
                       BigDecimal billingRate,
                       String models, String tiers, BigDecimal balance, int weight,
                       boolean interceptWarmupRequest,
                       boolean window5hCostControlEnabled, BigDecimal window5hCostLimitUsd,
                       boolean sessionCountControlEnabled, Integer sessionCountLimit,
                       String tlsFingerprintMode,
                       boolean sessionIdMasqueradeEnabled, int sessionIdMasqueradeTtlMinutes) {
        this.id = id;
        this.name = name;
        this.platform = platform;
        this.type = type;
        this.usage = usage;
        this.status = status;
        this.errors = errors;
        this.lastCheck = lastCheck;
        this.baseUrl = baseUrl;
        this.credential = credential;
        this.notes = notes;
        this.accountType = accountType;
        this.authMethod = authMethod;
        this.tempDisabled = tempDisabled;
        this.priorityValue = priorityValue;
        this.expiryTime = expiryTime;
        this.autoSuspendExpiry = autoSuspendExpiry;
        this.proxyId = proxyId;
        this.concurrency = concurrency;
        this.billingRate = billingRate;
        this.models = models;
        this.tiers = tiers;
        this.balance = balance != null ? balance : BigDecimal.ZERO;
        this.weight = weight;
        this.interceptWarmupRequest = interceptWarmupRequest;
        this.window5hCostControlEnabled = window5hCostControlEnabled;
        this.window5hCostLimitUsd = window5hCostLimitUsd;
        this.sessionCountControlEnabled = sessionCountControlEnabled;
        this.sessionCountLimit = sessionCountLimit;
        this.tlsFingerprintMode = tlsFingerprintMode != null ? tlsFingerprintMode : "NONE";
        this.sessionIdMasqueradeEnabled = sessionIdMasqueradeEnabled;
        this.sessionIdMasqueradeTtlMinutes = sessionIdMasqueradeTtlMinutes;
    }

    @Override public Long getId() { return id; }
    @Override public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getUsage() { return usage; }
    public void setUsage(String usage) { this.usage = usage; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getErrors() { return errors; }
    public void setErrors(Integer errors) { this.errors = errors; }
    public String getLastCheck() { return lastCheck; }
    public void setLastCheck(String lastCheck) { this.lastCheck = lastCheck; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getCredential() { return credential; }
    public void setCredential(String credential) { this.credential = credential; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getAccountType() { return accountType; }
    public void setAccountType(String accountType) { this.accountType = accountType; }
    public String getAuthMethod() { return authMethod; }
    public void setAuthMethod(String authMethod) { this.authMethod = authMethod; }
    public boolean isTempDisabled() { return tempDisabled; }
    public void setTempDisabled(boolean tempDisabled) { this.tempDisabled = tempDisabled; }
    public boolean isQuotaExhausted() { return quotaExhausted; }
    public void setQuotaExhausted(boolean quotaExhausted) { this.quotaExhausted = quotaExhausted; }
    public String getQuotaNextRetryAt() { return quotaNextRetryAt; }
    public void setQuotaNextRetryAt(String quotaNextRetryAt) { this.quotaNextRetryAt = quotaNextRetryAt; }
    public int getQuotaFailCount() { return quotaFailCount; }
    public void setQuotaFailCount(int quotaFailCount) { this.quotaFailCount = quotaFailCount; }
    public String getQuotaLastReason() { return quotaLastReason; }
    public void setQuotaLastReason(String quotaLastReason) { this.quotaLastReason = quotaLastReason; }
    public String getQuotaUpdatedAt() { return quotaUpdatedAt; }
    public void setQuotaUpdatedAt(String quotaUpdatedAt) { this.quotaUpdatedAt = quotaUpdatedAt; }
    public int getPriorityValue() { return priorityValue; }
    public void setPriorityValue(int priorityValue) { this.priorityValue = priorityValue; }
    public String getExpiryTime() { return expiryTime; }
    public void setExpiryTime(String expiryTime) { this.expiryTime = expiryTime; }
    public boolean isAutoSuspendExpiry() { return autoSuspendExpiry; }
    public void setAutoSuspendExpiry(boolean autoSuspendExpiry) { this.autoSuspendExpiry = autoSuspendExpiry; }
    public Long getProxyId() { return proxyId; }
    public void setProxyId(Long proxyId) { this.proxyId = proxyId; }
    public int getConcurrency() { return concurrency; }
    public void setConcurrency(int concurrency) { this.concurrency = concurrency; }
    public BigDecimal getBillingRate() { return billingRate; }
    public void setBillingRate(BigDecimal billingRate) { this.billingRate = billingRate; }
    public String getModels() { return models; }
    public void setModels(String models) { this.models = models; }
    public String getTiers() { return tiers; }
    public void setTiers(String tiers) { this.tiers = tiers; }
    public List<String> getTierList() {
        if (tiers == null || tiers.trim().isEmpty()) return List.of();
        List<String> result = new ArrayList<>();
        for (String t : tiers.split("[,，]")) {
            String trimmed = t.trim().toLowerCase(java.util.Locale.ROOT);
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result;
    }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public int getWeight() { return weight; }
    public void setWeight(int weight) { this.weight = weight; }
    public boolean isInterceptWarmupRequest() { return interceptWarmupRequest; }
    public void setInterceptWarmupRequest(boolean interceptWarmupRequest) { this.interceptWarmupRequest = interceptWarmupRequest; }
    public boolean isWindow5hCostControlEnabled() { return window5hCostControlEnabled; }
    public void setWindow5hCostControlEnabled(boolean window5hCostControlEnabled) { this.window5hCostControlEnabled = window5hCostControlEnabled; }
    public BigDecimal getWindow5hCostLimitUsd() { return window5hCostLimitUsd; }
    public void setWindow5hCostLimitUsd(BigDecimal window5hCostLimitUsd) { this.window5hCostLimitUsd = window5hCostLimitUsd; }
    public boolean isSessionCountControlEnabled() { return sessionCountControlEnabled; }
    public void setSessionCountControlEnabled(boolean sessionCountControlEnabled) { this.sessionCountControlEnabled = sessionCountControlEnabled; }
    public Integer getSessionCountLimit() { return sessionCountLimit; }
    public void setSessionCountLimit(Integer sessionCountLimit) { this.sessionCountLimit = sessionCountLimit; }
    public String getTlsFingerprintMode() { return tlsFingerprintMode; }
    public void setTlsFingerprintMode(String tlsFingerprintMode) { this.tlsFingerprintMode = tlsFingerprintMode; }
    public boolean isSessionIdMasqueradeEnabled() { return sessionIdMasqueradeEnabled; }
    public void setSessionIdMasqueradeEnabled(boolean sessionIdMasqueradeEnabled) { this.sessionIdMasqueradeEnabled = sessionIdMasqueradeEnabled; }
    public int getSessionIdMasqueradeTtlMinutes() { return sessionIdMasqueradeTtlMinutes; }
    public void setSessionIdMasqueradeTtlMinutes(int sessionIdMasqueradeTtlMinutes) { this.sessionIdMasqueradeTtlMinutes = sessionIdMasqueradeTtlMinutes; }
    public String getCredentialMask() { return credentialMask; }
    public void setCredentialMask(String credentialMask) { this.credentialMask = credentialMask; }
    public String getEffectiveStatus() { return effectiveStatus; }
    public void setEffectiveStatus(String effectiveStatus) { this.effectiveStatus = effectiveStatus; }
    public Boolean getScheduleEnabled() { return scheduleEnabled; }
    public void setScheduleEnabled(Boolean scheduleEnabled) { this.scheduleEnabled = scheduleEnabled; }
    public Integer getCapacityUsed() { return capacityUsed; }
    public void setCapacityUsed(Integer capacityUsed) { this.capacityUsed = capacityUsed; }
    public Integer getCapacityLimit() { return capacityLimit; }
    public void setCapacityLimit(Integer capacityLimit) { this.capacityLimit = capacityLimit; }
    public Long getTodayRequests() { return todayRequests; }
    public void setTodayRequests(Long todayRequests) { this.todayRequests = todayRequests; }
    public Long getTodayTokens() { return todayTokens; }
    public void setTodayTokens(Long todayTokens) { this.todayTokens = todayTokens; }
    public String getTodayAccountCost() { return todayAccountCost; }
    public void setTodayAccountCost(String todayAccountCost) { this.todayAccountCost = todayAccountCost; }
    public String getTodayUserCost() { return todayUserCost; }
    public void setTodayUserCost(String todayUserCost) { this.todayUserCost = todayUserCost; }
    public List<String> getGroups() { return groups; }
    public void setGroups(List<String> groups) { this.groups = groups; }
    public List<Long> getGroupIds() { return groupIds; }
    public void setGroupIds(List<Long> groupIds) {
        this.groupIds = groupIds == null ? new ArrayList<>() : groupIds;
    }
    public List<UsageWindow> getUsageWindows() { return usageWindows; }
    public void setUsageWindows(List<UsageWindow> usageWindows) { this.usageWindows = usageWindows; }
    public String getRecentUsedText() { return recentUsedText; }
    public void setRecentUsedText(String recentUsedText) { this.recentUsedText = recentUsedText; }
    public String getRecentUsedAt() { return recentUsedAt; }
    public void setRecentUsedAt(String recentUsedAt) { this.recentUsedAt = recentUsedAt; }
    public Boolean getExpired() { return expired; }
    public void setExpired(Boolean expired) { this.expired = expired; }
    public String getExpiryLabel() { return expiryLabel; }
    public void setExpiryLabel(String expiryLabel) { this.expiryLabel = expiryLabel; }

    public static class UsageWindow {
        private String key;
        private String label;
        private long used;
        private long limit;
        private int percentage;
        private String detail;
        private String tone;

        public UsageWindow() {}

        public UsageWindow(String key, String label, long used, long limit, int percentage, String detail, String tone) {
            this.key = key;
            this.label = label;
            this.used = used;
            this.limit = limit;
            this.percentage = percentage;
            this.detail = detail;
            this.tone = tone;
        }

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public long getUsed() { return used; }
        public void setUsed(long used) { this.used = used; }
        public long getLimit() { return limit; }
        public void setLimit(long limit) { this.limit = limit; }
        public int getPercentage() { return percentage; }
        public void setPercentage(int percentage) { this.percentage = percentage; }
        public String getDetail() { return detail; }
        public void setDetail(String detail) { this.detail = detail; }
        public String getTone() { return tone; }
        public void setTone(String tone) { this.tone = tone; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Request {
        private String name;
        private String platform;
        private String type;
        private String usage;
        private String status;
        private Integer errors;
        private String lastCheck;
        @JsonAlias("base_url")
        private String baseUrl;
        @JsonAlias("credentials")
        private String credential;
        private String notes;
        private String accountType;
        private String authMethod;
        private Boolean tempDisabled;
        private Boolean quotaExhausted;
        private String quotaNextRetryAt;
        private Integer quotaFailCount;
        private String quotaLastReason;
        private String quotaUpdatedAt;
        private Integer priorityValue;
        private String expiryTime;
        private Boolean autoSuspendExpiry;
        @JsonAlias("proxy_id")
        private Long proxyId;
        private Integer concurrency;
        @JsonAlias("billing_rate")
        private BigDecimal billingRate;
        private String models;
        private String tiers;
        private BigDecimal balance;
        private Integer weight;
        private String credentialRef;
        private List<Long> groupIds;
        private Boolean interceptWarmupRequest;
        private Boolean window5hCostControlEnabled;
        private BigDecimal window5hCostLimitUsd;
        private Boolean sessionCountControlEnabled;
        private Integer sessionCountLimit;
        private String tlsFingerprintMode;
        private Boolean sessionIdMasqueradeEnabled;
        private Integer sessionIdMasqueradeTtlMinutes;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getPlatform() { return platform; }
        public void setPlatform(String platform) { this.platform = platform; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getUsage() { return usage; }
        public void setUsage(String usage) { this.usage = usage; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Integer getErrors() { return errors; }
        public void setErrors(Integer errors) { this.errors = errors; }
        public String getLastCheck() { return lastCheck; }
        public void setLastCheck(String lastCheck) { this.lastCheck = lastCheck; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getCredential() { return credential; }
        public void setCredential(String credential) { this.credential = credential; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
        public String getAccountType() { return accountType; }
        public void setAccountType(String accountType) { this.accountType = accountType; }
        public String getAuthMethod() { return authMethod; }
        public void setAuthMethod(String authMethod) { this.authMethod = authMethod; }
        public Boolean getTempDisabled() { return tempDisabled; }
        public void setTempDisabled(Boolean tempDisabled) { this.tempDisabled = tempDisabled; }
        public Boolean getQuotaExhausted() { return quotaExhausted; }
        public void setQuotaExhausted(Boolean quotaExhausted) { this.quotaExhausted = quotaExhausted; }
        public String getQuotaNextRetryAt() { return quotaNextRetryAt; }
        public void setQuotaNextRetryAt(String quotaNextRetryAt) { this.quotaNextRetryAt = quotaNextRetryAt; }
        public Integer getQuotaFailCount() { return quotaFailCount; }
        public void setQuotaFailCount(Integer quotaFailCount) { this.quotaFailCount = quotaFailCount; }
        public String getQuotaLastReason() { return quotaLastReason; }
        public void setQuotaLastReason(String quotaLastReason) { this.quotaLastReason = quotaLastReason; }
        public String getQuotaUpdatedAt() { return quotaUpdatedAt; }
        public void setQuotaUpdatedAt(String quotaUpdatedAt) { this.quotaUpdatedAt = quotaUpdatedAt; }
        public Integer getPriorityValue() { return priorityValue; }
        public void setPriorityValue(Integer priorityValue) { this.priorityValue = priorityValue; }
        public String getExpiryTime() { return expiryTime; }
        public void setExpiryTime(String expiryTime) { this.expiryTime = expiryTime; }
        public Boolean getAutoSuspendExpiry() { return autoSuspendExpiry; }
        public void setAutoSuspendExpiry(Boolean autoSuspendExpiry) { this.autoSuspendExpiry = autoSuspendExpiry; }
        public Long getProxyId() { return proxyId; }
        public void setProxyId(Long proxyId) { this.proxyId = proxyId; }
        public Integer getConcurrency() { return concurrency; }
        public void setConcurrency(Integer concurrency) { this.concurrency = concurrency; }
        public BigDecimal getBillingRate() { return billingRate; }
        public void setBillingRate(BigDecimal billingRate) { this.billingRate = billingRate; }
        public String getModels() { return models; }
        public void setModels(String models) { this.models = models; }
        public String getTiers() { return tiers; }
        public void setTiers(String tiers) { this.tiers = tiers; }
        public BigDecimal getBalance() { return balance; }
        public void setBalance(BigDecimal balance) { this.balance = balance; }
        public Integer getWeight() { return weight; }
        public void setWeight(Integer weight) { this.weight = weight; }
        public String getCredentialRef() { return credentialRef; }
        public void setCredentialRef(String credentialRef) { this.credentialRef = credentialRef; }
        public List<Long> getGroupIds() { return groupIds; }
        public void setGroupIds(List<Long> groupIds) { this.groupIds = groupIds; }
        public Boolean getInterceptWarmupRequest() { return interceptWarmupRequest; }
        public void setInterceptWarmupRequest(Boolean interceptWarmupRequest) { this.interceptWarmupRequest = interceptWarmupRequest; }
        public Boolean getWindow5hCostControlEnabled() { return window5hCostControlEnabled; }
        public void setWindow5hCostControlEnabled(Boolean window5hCostControlEnabled) { this.window5hCostControlEnabled = window5hCostControlEnabled; }
        public BigDecimal getWindow5hCostLimitUsd() { return window5hCostLimitUsd; }
        public void setWindow5hCostLimitUsd(BigDecimal window5hCostLimitUsd) { this.window5hCostLimitUsd = window5hCostLimitUsd; }
        public Boolean getSessionCountControlEnabled() { return sessionCountControlEnabled; }
        public void setSessionCountControlEnabled(Boolean sessionCountControlEnabled) { this.sessionCountControlEnabled = sessionCountControlEnabled; }
        public Integer getSessionCountLimit() { return sessionCountLimit; }
        public void setSessionCountLimit(Integer sessionCountLimit) { this.sessionCountLimit = sessionCountLimit; }
        public String getTlsFingerprintMode() { return tlsFingerprintMode; }
        public void setTlsFingerprintMode(String tlsFingerprintMode) { this.tlsFingerprintMode = tlsFingerprintMode; }
        public Boolean getSessionIdMasqueradeEnabled() { return sessionIdMasqueradeEnabled; }
        public void setSessionIdMasqueradeEnabled(Boolean sessionIdMasqueradeEnabled) { this.sessionIdMasqueradeEnabled = sessionIdMasqueradeEnabled; }
        public Integer getSessionIdMasqueradeTtlMinutes() { return sessionIdMasqueradeTtlMinutes; }
        public void setSessionIdMasqueradeTtlMinutes(Integer sessionIdMasqueradeTtlMinutes) { this.sessionIdMasqueradeTtlMinutes = sessionIdMasqueradeTtlMinutes; }
    }
}
