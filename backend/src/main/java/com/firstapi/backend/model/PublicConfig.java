package com.firstapi.backend.model;

public class PublicConfig {
    public String siteName;
    public String siteAnnouncement;
    public Boolean registrationOpen;

    public PublicConfig(String siteName, String siteAnnouncement, Boolean registrationOpen) {
        this.siteName = siteName;
        this.siteAnnouncement = siteAnnouncement;
        this.registrationOpen = registrationOpen;
    }
}
