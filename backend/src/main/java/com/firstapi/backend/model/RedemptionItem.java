package com.firstapi.backend.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.firstapi.backend.common.SimpleStore;

public class RedemptionItem implements SimpleStore.Identifiable {
    private Long id;
    private String name;
    private String code;
    private String type;
    private String value;
    private String usage;
    private String time;
    private String status;

    public RedemptionItem() {}

    public RedemptionItem(Long id, String name, String code, String type, String value, String usage, String time, String status) {
        this.id = id;
        this.name = name;
        this.code = code;
        this.type = type;
        this.value = value;
        this.usage = usage;
        this.time = time;
        this.status = status;
    }

    @Override public Long getId() { return id; }
    @Override public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public String getUsage() { return usage; }
    public void setUsage(String usage) { this.usage = usage; }
    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Request {
        private String name;
        private String type;
        private String value;
        private Integer quantity;
        @JsonAlias("usageLimit")
        private Integer validCount;
        private String time;
        private String status;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public Integer getValidCount() { return validCount; }
        public void setValidCount(Integer validCount) { this.validCount = validCount; }
        public String getTime() { return time; }
        public void setTime(String time) { this.time = time; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
