package com.firstapi.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

public class SettingsData {
    public String siteName;
    public String siteAnnouncement;
    public String apiProxy;
    public Integer streamTimeout;
    public Integer retryLimit;
    public Boolean registrationOpen;
    public String defaultGroup;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Request {
        public String siteName;
        public String siteAnnouncement;
        public String apiProxy;
        public Integer streamTimeout;
        public Integer retryLimit;
        public Boolean registrationOpen;
        public String defaultGroup;
    }
}
