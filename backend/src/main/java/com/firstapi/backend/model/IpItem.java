package com.firstapi.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.firstapi.backend.common.SimpleStore;

public class IpItem implements SimpleStore.Identifiable {
    private Long id;
    private String name;
    private String protocol;
    private String address;
    private String location;
    private String accounts;
    private String latency;
    private String status;

    public IpItem() {}

    public IpItem(Long id, String name, String protocol, String address, String location, String accounts, String latency, String status) {
        this.id = id;
        this.name = name;
        this.protocol = protocol;
        this.address = address;
        this.location = location;
        this.accounts = accounts;
        this.latency = latency;
        this.status = status;
    }

    @Override public Long getId() { return id; }
    @Override public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { this.protocol = protocol; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getAccounts() { return accounts; }
    public void setAccounts(String accounts) { this.accounts = accounts; }
    public String getLatency() { return latency; }
    public void setLatency(String latency) { this.latency = latency; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Request {
        private String name;
        private String protocol;
        private String address;
        private String location;
        private String accounts;
        private String latency;
        private String status;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getProtocol() { return protocol; }
        public void setProtocol(String protocol) { this.protocol = protocol; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
        public String getAccounts() { return accounts; }
        public void setAccounts(String accounts) { this.accounts = accounts; }
        public String getLatency() { return latency; }
        public void setLatency(String latency) { this.latency = latency; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
