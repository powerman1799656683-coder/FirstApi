package com.firstapi.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.firstapi.backend.common.SimpleStore;

public class AnnouncementItem implements SimpleStore.Identifiable {
    private Long id;
    private String title;
    private String content;
    private String type;
    private String status;
    private String target;
    private String time;

    public AnnouncementItem() {}

    public AnnouncementItem(Long id, String title, String content, String type, String status, String target, String time) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.type = type;
        this.status = status;
        this.target = target;
        this.time = time;
    }

    @Override public Long getId() { return id; }
    @Override public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Request {
        private String title;
        private String content;
        private String type;
        private String status;
        private String target;
        private String time;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getTarget() { return target; }
        public void setTarget(String target) { this.target = target; }
        public String getTime() { return time; }
        public void setTime(String time) { this.time = time; }
    }
}
