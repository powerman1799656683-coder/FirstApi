package com.firstapi.backend.controller;

import com.firstapi.backend.common.ApiResponse;
import com.firstapi.backend.model.PublicConfig;
import com.firstapi.backend.model.SettingsData;
import com.firstapi.backend.service.SettingsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
public class PublicController {

    private final SettingsService settingsService;

    public PublicController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping("/config")
    public ApiResponse<PublicConfig> getConfig() {
        SettingsData settings = settingsService.getSettings();
        return ApiResponse.ok(new PublicConfig(
                settings.siteName,
                settings.siteAnnouncement,
                settings.registrationOpen
        ));
    }
}
