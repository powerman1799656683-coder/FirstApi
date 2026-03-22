package com.firstapi.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.firstapi.backend.common.SimpleStore;

public class MonitorNodeItem implements SimpleStore.Identifiable {
    private Long id;
    private String nodeName;
    private String checkUrl;
    private String locationName;
    private String statusName;
    private String latencyValue;
    private String uptimeValue;
    private int totalChecks;
    private int successChecks;
    private String lastCheckLabel;

    public MonitorNodeItem() {}

    public MonitorNodeItem(Long id, String nodeName, String checkUrl, String locationName,
                           String statusName, String latencyValue, String uptimeValue,
                           int totalChecks, int successChecks, String lastCheckLabel) {
        this.id = id;
        this.nodeName = nodeName;
        this.checkUrl = checkUrl;
        this.locationName = locationName;
        this.statusName = statusName;
        this.latencyValue = latencyValue;
        this.uptimeValue = uptimeValue;
        this.totalChecks = totalChecks;
        this.successChecks = successChecks;
        this.lastCheckLabel = lastCheckLabel;
    }

    @Override public Long getId() { return id; }
    @Override public void setId(Long id) { this.id = id; }

    public String getNodeName() { return nodeName; }
    public void setNodeName(String nodeName) { this.nodeName = nodeName; }
    public String getCheckUrl() { return checkUrl; }
    public void setCheckUrl(String checkUrl) { this.checkUrl = checkUrl; }
    public String getLocationName() { return locationName; }
    public void setLocationName(String locationName) { this.locationName = locationName; }
    public String getStatusName() { return statusName; }
    public void setStatusName(String statusName) { this.statusName = statusName; }
    public String getLatencyValue() { return latencyValue; }
    public void setLatencyValue(String latencyValue) { this.latencyValue = latencyValue; }
    public String getUptimeValue() { return uptimeValue; }
    public void setUptimeValue(String uptimeValue) { this.uptimeValue = uptimeValue; }
    public int getTotalChecks() { return totalChecks; }
    public void setTotalChecks(int totalChecks) { this.totalChecks = totalChecks; }
    public int getSuccessChecks() { return successChecks; }
    public void setSuccessChecks(int successChecks) { this.successChecks = successChecks; }
    public String getLastCheckLabel() { return lastCheckLabel; }
    public void setLastCheckLabel(String lastCheckLabel) { this.lastCheckLabel = lastCheckLabel; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Request {
        private String name;
        private String url;
        private String location;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
    }
}
