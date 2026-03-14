package com.firstapi.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.firstapi.backend.common.SimpleStore;

public class ApiKeyItem implements SimpleStore.Identifiable {
    private Long id;
    @JsonIgnore
    private Long ownerId;
    private String name;
    @JsonIgnore
    private String key;
    private String keyPreview;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String plainTextKey;
    private String created;
    private String status;
    private String lastUsed;

    public ApiKeyItem() {}

    public ApiKeyItem(Long id, String name, String key, String created, String status, String lastUsed) {
        this.id = id;
        this.name = name;
        this.key = key;
        this.created = created;
        this.status = status;
        this.lastUsed = lastUsed;
    }

    @Override public Long getId() { return id; }
    @Override public void setId(Long id) { this.id = id; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getKeyPreview() { return keyPreview; }
    public void setKeyPreview(String keyPreview) { this.keyPreview = keyPreview; }
    public String getPlainTextKey() { return plainTextKey; }
    public void setPlainTextKey(String plainTextKey) { this.plainTextKey = plainTextKey; }
    public String getCreated() { return created; }
    public void setCreated(String created) { this.created = created; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getLastUsed() { return lastUsed; }
    public void setLastUsed(String lastUsed) { this.lastUsed = lastUsed; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Request {
        private String name;
        private String status;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
