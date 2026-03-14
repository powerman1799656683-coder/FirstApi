package com.firstapi.backend.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.firstapi.backend.common.SimpleStore;

public class AccountItem implements SimpleStore.Identifiable {
    private Long id;
    private String name;
    private String platform;
    private String type;
    private String usage;
    private String status;
    private Integer errors;
    private String lastCheck;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String credential;

    public AccountItem() {}

    public AccountItem(Long id, String name, String platform, String type, String usage, String status, Integer errors, String lastCheck, String credential) {
        this.id = id;
        this.name = name;
        this.platform = platform;
        this.type = type;
        this.usage = usage;
        this.status = status;
        this.errors = errors;
        this.lastCheck = lastCheck;
        this.credential = credential;
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
    public String getCredential() { return credential; }
    public void setCredential(String credential) { this.credential = credential; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Request {
        private String name;
        private String platform;
        private String type;
        private String usage;
        private String status;
        private Integer errors;
        private String lastCheck;
        @JsonAlias("credentials")
        private String credential;

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
        public String getCredential() { return credential; }
        public void setCredential(String credential) { this.credential = credential; }
    }
}
