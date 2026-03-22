package com.firstapi.backend.service;

import com.firstapi.backend.model.SettingsData;
import com.firstapi.backend.util.ValidationSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
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

        SettingsData data = result.isEmpty() ? defaultSettings() : result.get(0);
        boolean normalized = normalize(data);
        if (normalized && !result.isEmpty()) {
            save(data);
        }
        return data;
    }

    public synchronized SettingsData updateSettings(SettingsData.Request request) {
        SettingsData data = getSettings();
        if (request.siteName != null) {
            String name = ValidationSupport.requireNotBlank(request.siteName, "站点名称不能为空");
            if (name.contains("<") || name.contains(">")) {
                throw new IllegalArgumentException("站点名称包含非法字符");
            }
            data.siteName = name;
        }
        if (request.siteAnnouncement != null) {
            if (request.siteAnnouncement.contains("<script") || request.siteAnnouncement.contains("<SCRIPT")) {
                throw new IllegalArgumentException("公告内容包含非法脚本标签");
            }
            data.siteAnnouncement = request.siteAnnouncement;
        }
        if (request.apiProxy != null) {
            String proxy = request.apiProxy.trim();
            if (!proxy.isEmpty()) {
                if (proxy.toLowerCase().startsWith("javascript:")) {
                    throw new IllegalArgumentException("代理地址不允许使用 javascript: 协议");
                }
                if (!proxy.startsWith("http://") && !proxy.startsWith("https://")) {
                    throw new IllegalArgumentException("代理地址必须以 http:// 或 https:// 开头");
                }
            }
            data.apiProxy = proxy;
        }
        if (request.streamTimeout != null) {
            data.streamTimeout = ValidationSupport.requireNonNegative(request.streamTimeout, "流式超时必须大于或等于 0");
        }
        if (request.retryLimit != null) {
            data.retryLimit = ValidationSupport.requireNonNegative(request.retryLimit, "重试次数必须大于或等于 0");
        }
        if (request.registrationOpen != null) {
            data.registrationOpen = request.registrationOpen;
        }
        if (request.defaultGroup != null) {
            data.defaultGroup = ValidationSupport.requireNotBlank(request.defaultGroup, "默认分组不能为空");
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
        data.siteName = "FirstApi";
        data.siteAnnouncement = "欢迎使用 FirstApi。";
        data.apiProxy = "https://proxy.firstapi.com/v1";
        data.streamTimeout = 30000;
        data.retryLimit = 3;
        data.registrationOpen = true;
        data.defaultGroup = "默认组";
        return data;
    }

    private boolean normalize(SettingsData data) {
        boolean changed = false;
        if (needsDefaultSiteName(data.siteName)) {
            data.siteName = "FirstApi";
            changed = true;
        }
        if (needsDefaultAnnouncement(data.siteAnnouncement)) {
            data.siteAnnouncement = "欢迎使用 FirstApi。";
            changed = true;
        }
        if (needsDefaultProxy(data.apiProxy)) {
            data.apiProxy = "https://proxy.firstapi.com/v1";
            changed = true;
        }
        if (isBlank(data.defaultGroup) || containsBrokenText(data.defaultGroup)) {
            data.defaultGroup = "默认组";
            changed = true;
        }
        return changed;
    }

    private boolean containsBrokenText(String value) {
        return value != null && value.indexOf('\uFFFD') >= 0;
    }

    private boolean needsDefaultSiteName(String value) {
        return isBlank(value)
                || containsBrokenText(value);
    }

    private boolean needsDefaultAnnouncement(String value) {
        return isBlank(value)
                || containsBrokenText(value);
    }

    private boolean needsDefaultProxy(String value) {
        return isBlank(value)
                || value.trim().matches("(?i)^https://proxy\\.[a-z]{2}api\\.com/v1/?$");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}