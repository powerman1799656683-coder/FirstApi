package com.firstapi.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.firstapi.backend.common.SimpleStore;

public class UserItem implements SimpleStore.Identifiable {
    private Long id;
    private String email;
    private String username;
    private String balance;
    private String group;
    private String role;
    private String status;
    private String time;

    public UserItem() {}

    public UserItem(Long id, String email, String username, String balance, String group, String role, String status, String time) {
        this.id = id;
        this.email = email;
        this.username = username;
        this.balance = balance;
        this.group = group;
        this.role = role;
        this.status = status;
        this.time = time;
    }

    @Override public Long getId() { return id; }
    @Override public void setId(Long id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getBalance() { return balance; }
    public void setBalance(String balance) { this.balance = balance; }
    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Request {
        private String email;
        private String username;
        private String balance;
        private String group;
        private String role;
        private String status;
        private String time;
        private String password;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getBalance() { return balance; }
        public void setBalance(String balance) { this.balance = balance; }
        public String getGroup() { return group; }
        public void setGroup(String group) { this.group = group; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getTime() { return time; }
        public void setTime(String time) { this.time = time; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}
