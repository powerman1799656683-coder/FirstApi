package com.firstapi.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.security")
public class AppSecurityProperties {
    private String dataSecret = "local-dev-data-secret-change-me";

    public String getDataSecret() {
        return dataSecret;
    }

    public void setDataSecret(String dataSecret) {
        this.dataSecret = dataSecret;
    }
}
