package com.firstapi.backend.model;

import com.firstapi.backend.common.SimpleStore;

public class MonitorAlertItem implements SimpleStore.Identifiable {
    private Long id;
    private String timeLabel;
    private String levelName;
    private String eventText;
    private String statusName;
    private String ownerName;
    private String statusColor;

    public MonitorAlertItem() {}

    public MonitorAlertItem(Long id, String timeLabel, String levelName, String eventText,
                            String statusName, String ownerName, String statusColor) {
        this.id = id;
        this.timeLabel = timeLabel;
        this.levelName = levelName;
        this.eventText = eventText;
        this.statusName = statusName;
        this.ownerName = ownerName;
        this.statusColor = statusColor;
    }

    @Override public Long getId() { return id; }
    @Override public void setId(Long id) { this.id = id; }

    public String getTimeLabel() { return timeLabel; }
    public void setTimeLabel(String timeLabel) { this.timeLabel = timeLabel; }
    public String getLevelName() { return levelName; }
    public void setLevelName(String levelName) { this.levelName = levelName; }
    public String getEventText() { return eventText; }
    public void setEventText(String eventText) { this.eventText = eventText; }
    public String getStatusName() { return statusName; }
    public void setStatusName(String statusName) { this.statusName = statusName; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public String getStatusColor() { return statusColor; }
    public void setStatusColor(String statusColor) { this.statusColor = statusColor; }
}
