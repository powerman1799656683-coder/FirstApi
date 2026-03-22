package com.firstapi.backend.model;

import com.firstapi.backend.common.SimpleStore;

public class MonitorAlertRuleItem implements SimpleStore.Identifiable {
    private Long id;
    private String ruleName;
    private String metricKey;
    private String operator;
    private Double thresholdValue;
    private String levelName;
    private Boolean enabled;
    private String description;

    public MonitorAlertRuleItem() {}

    public MonitorAlertRuleItem(Long id, String ruleName, String metricKey, String operator,
                                 Double thresholdValue, String levelName, Boolean enabled, String description) {
        this.id = id;
        this.ruleName = ruleName;
        this.metricKey = metricKey;
        this.operator = operator;
        this.thresholdValue = thresholdValue;
        this.levelName = levelName;
        this.enabled = enabled;
        this.description = description;
    }

    @Override public Long getId() { return id; }
    @Override public void setId(Long id) { this.id = id; }

    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public String getMetricKey() { return metricKey; }
    public void setMetricKey(String metricKey) { this.metricKey = metricKey; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public Double getThresholdValue() { return thresholdValue; }
    public void setThresholdValue(Double thresholdValue) { this.thresholdValue = thresholdValue; }
    public String getLevelName() { return levelName; }
    public void setLevelName(String levelName) { this.levelName = levelName; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public record Request(String ruleName, String metricKey, String operator,
                           Double thresholdValue, String levelName, Boolean enabled, String description) {}
}
