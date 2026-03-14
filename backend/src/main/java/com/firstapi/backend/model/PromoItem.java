package com.firstapi.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.firstapi.backend.common.SimpleStore;

public class PromoItem implements SimpleStore.Identifiable {
    private Long id;
    private String code;
    private String type;
    private String value;
    private String usage;
    private String expiry;
    private String status;

    public PromoItem() {}

    public PromoItem(Long id, String code, String type, String value, String usage, String expiry, String status) {
        this.id = id;
        this.code = code;
        this.type = type;
        this.value = value;
        this.usage = usage;
        this.expiry = expiry;
        this.status = status;
    }

    @Override public Long getId() { return id; }
    @Override public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public String getUsage() { return usage; }
    public void setUsage(String usage) { this.usage = usage; }
    public String getExpiry() { return expiry; }
    public void setExpiry(String expiry) { this.expiry = expiry; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Request {
        private String code;
        private String type;
        private String value;
        private String usage;
        private String expiry;
        private String status;

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public String getUsage() { return usage; }
        public void setUsage(String usage) { this.usage = usage; }
        public String getExpiry() { return expiry; }
        public void setExpiry(String expiry) { this.expiry = expiry; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
