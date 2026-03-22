package com.firstapi.backend.model;

import com.firstapi.backend.common.SimpleStore;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class RelayRecordItem implements SimpleStore.Identifiable {
    private Long id;
    private Long ownerId;
    private Long apiKeyId;
    private String provider;
    private Long accountId;
    private String model;
    private String requestId;
    private Boolean success;
    private Integer statusCode;
    private String errorText;
    private Long latencyMs;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private BigDecimal inputPrice;
    private BigDecimal outputPrice;
    private String pricingCurrency;
    private BigDecimal groupRatio;
    private Long pricingRuleId;
    private String pricingRuleName;
    private String pricingStatus;
    private Boolean pricingFound;
    private BigDecimal cost;
    private String usageJson;
    private String createdAt;
    private LocalDateTime createdAtTs;

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public void setId(Long id) {
        this.id = id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public Long getApiKeyId() {
        return apiKeyId;
    }

    public void setApiKeyId(Long apiKeyId) {
        this.apiKeyId = apiKeyId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
    }

    public String getErrorText() {
        return errorText;
    }

    public void setErrorText(String errorText) {
        this.errorText = errorText;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(Integer promptTokens) {
        this.promptTokens = promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(Integer completionTokens) {
        this.completionTokens = completionTokens;
    }

    public Integer getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(Integer totalTokens) {
        this.totalTokens = totalTokens;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public BigDecimal getInputPrice() {
        return inputPrice;
    }

    public void setInputPrice(BigDecimal inputPrice) {
        this.inputPrice = inputPrice;
    }

    public BigDecimal getOutputPrice() {
        return outputPrice;
    }

    public void setOutputPrice(BigDecimal outputPrice) {
        this.outputPrice = outputPrice;
    }

    public String getPricingCurrency() {
        return pricingCurrency;
    }

    public void setPricingCurrency(String pricingCurrency) {
        this.pricingCurrency = pricingCurrency;
    }

    public BigDecimal getGroupRatio() {
        return groupRatio;
    }

    public void setGroupRatio(BigDecimal groupRatio) {
        this.groupRatio = groupRatio;
    }

    public Long getPricingRuleId() {
        return pricingRuleId;
    }

    public void setPricingRuleId(Long pricingRuleId) {
        this.pricingRuleId = pricingRuleId;
    }

    public String getPricingRuleName() {
        return pricingRuleName;
    }

    public void setPricingRuleName(String pricingRuleName) {
        this.pricingRuleName = pricingRuleName;
    }

    public String getPricingStatus() {
        return pricingStatus;
    }

    public void setPricingStatus(String pricingStatus) {
        this.pricingStatus = pricingStatus;
    }

    public Boolean getPricingFound() {
        return pricingFound;
    }

    public void setPricingFound(Boolean pricingFound) {
        this.pricingFound = pricingFound;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public void setCost(BigDecimal cost) {
        this.cost = cost;
    }

    public String getUsageJson() {
        return usageJson;
    }

    public void setUsageJson(String usageJson) {
        this.usageJson = usageJson;
    }

    public LocalDateTime getCreatedAtTs() {
        return createdAtTs;
    }

    public void setCreatedAtTs(LocalDateTime createdAtTs) {
        this.createdAtTs = createdAtTs;
    }
}
