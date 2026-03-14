package com.firstapi.backend.service;

import com.firstapi.backend.model.SettingsData;
import com.firstapi.backend.util.ValidationSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;

@Service
public class SettingsService {

    private final JdbcTemplate jdbcTemplate;

    public SettingsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        if (!exists()) {
            save(defaultSettings());
        }
    }

    public synchronized SettingsData getSettings() {
        List<SettingsData> result = jdbcTemplate.query(
                "select `site_name`, `site_announcement`, `api_proxy`, `stream_timeout`, `retry_limit`, `registration_open`, `default_group` from `settings` where `id` = 1",
                (rs, rowNum) -> {
                    SettingsData data = new SettingsData();
                    data.siteName = rs.getString("site_name");
                    data.siteAnnouncement = rs.getString("site_announcement");
                    data.apiProxy = rs.getString("api_proxy");
                    data.streamTimeout = rs.getObject("stream_timeout") == null ? null : rs.getInt("stream_timeout");
                    data.retryLimit = rs.getObject("retry_limit") == null ? null : rs.getInt("retry_limit");
                    data.registrationOpen = rs.getBoolean("registration_open");
                    data.defaultGroup = rs.getString("default_group");
                    return data;
                }
        );
        return result.isEmpty() ? defaultSettings() : result.get(0);
    }

    public synchronized SettingsData updateSettings(SettingsData.Request request) {
        SettingsData data = getSettings();
        if (request.siteName != null) {
            data.siteName = ValidationSupport.requireNotBlank(request.siteName, "Site name is required");
        }
        if (request.siteAnnouncement != null) {
            data.siteAnnouncement = request.siteAnnouncement;
        }
        if (request.apiProxy != null) {
            data.apiProxy = request.apiProxy;
        }
        if (request.streamTimeout != null) {
            data.streamTimeout = ValidationSupport.requireNonNegative(request.streamTimeout, "Stream timeout must be 0 or greater");
        }
        if (request.retryLimit != null) {
            data.retryLimit = ValidationSupport.requireNonNegative(request.retryLimit, "Retry limit must be 0 or greater");
        }
        if (request.registrationOpen != null) {
            data.registrationOpen = request.registrationOpen;
        }
        if (request.defaultGroup != null) {
            data.defaultGroup = ValidationSupport.requireNotBlank(request.defaultGroup, "Default group is required");
        }
        save(data);
        return data;
    }

    private boolean exists() {
        Integer count = jdbcTemplate.queryForObject("select count(*) from `settings` where `id` = 1", Integer.class);
        return count != null && count > 0;
    }

    private void save(SettingsData data) {
        jdbcTemplate.update(
                "insert into `settings` (`id`, `site_name`, `site_announcement`, `api_proxy`, `stream_timeout`, `retry_limit`, `registration_open`, `default_group`) values (1, ?, ?, ?, ?, ?, ?, ?) on duplicate key update `site_name` = values(`site_name`), `site_announcement` = values(`site_announcement`), `api_proxy` = values(`api_proxy`), `stream_timeout` = values(`stream_timeout`), `retry_limit` = values(`retry_limit`), `registration_open` = values(`registration_open`), `default_group` = values(`default_group`)",
                data.siteName,
                data.siteAnnouncement,
                data.apiProxy,
                data.streamTimeout,
                data.retryLimit,
                data.registrationOpen,
                data.defaultGroup
        );
    }

    private SettingsData defaultSettings() {
        SettingsData data = new SettingsData();
        data.siteName = "YC-API HUB";
        data.siteAnnouncement = "欢迎来到 YC-API Hub。";
        data.apiProxy = "https://proxy.ycapi.com/v1";
        data.streamTimeout = 30000;
        data.retryLimit = 3;
        data.registrationOpen = true;
        data.defaultGroup = "Default";
        return data;
    }
}