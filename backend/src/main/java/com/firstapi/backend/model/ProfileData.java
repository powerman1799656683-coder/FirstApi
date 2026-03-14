package com.firstapi.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

public class ProfileData {
    public String username;
    public String email;
    public String role;
    public String uid;
    public String phone;
    public String bio;
    public Boolean verified;
    public Boolean twoFactorEnabled;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UpdateRequest {
        public String username;
        public String phone;
        public String bio;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PasswordRequest {
        public String oldPassword;
        public String newPassword;
    }

    public static class ActionResult {
        public String message;

        public ActionResult() {}

        public ActionResult(String message) {
            this.message = message;
        }
    }
}
