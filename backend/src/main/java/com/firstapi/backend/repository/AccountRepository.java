package com.firstapi.backend.repository;

import com.firstapi.backend.model.AccountItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;

@Repository
public class AccountRepository extends JdbcListRepository<AccountItem> {

    public AccountRepository(JdbcTemplate jdbcTemplate) {
        super(
                jdbcTemplate,
                "accounts",
                new String[]{
                        "name", "platform", "type_name", "usage_text", "status_name",
                        "error_count", "last_check", "base_url", "credential",
                        "notes", "account_type", "auth_method",
                        "temp_disabled",
                        "quota_exhausted", "quota_next_retry_at", "quota_fail_count", "quota_last_reason", "quota_updated_at",
                        "priority_value",
                        "expiry_time", "auto_suspend_expiry",
                        "proxy_id",
                        "concurrency",
                        "billing_rate",
                        "models", "tiers", "balance", "weight",
                        "intercept_warmup_request",
                        "window5h_cost_control_enabled", "window5h_cost_limit_usd",
                        "session_count_control_enabled", "session_count_limit",
                        "tls_fingerprint_mode",
                        "session_id_masquerade_enabled", "session_id_masquerade_ttl_minutes"
                },
                (rs, rowNum) -> {
                    AccountItem item = new AccountItem(
                            rs.getLong("id"),
                            rs.getString("name"),
                            rs.getString("platform"),
                            rs.getString("type_name"),
                            rs.getString("usage_text"),
                            rs.getString("status_name"),
                            rs.getInt("error_count"),
                            rs.getString("last_check"),
                            rs.getString("base_url"),
                            rs.getString("credential"),
                            rs.getString("notes"),
                            rs.getString("account_type"),
                            rs.getString("auth_method"),
                            rs.getBoolean("temp_disabled"),
                            rs.getInt("priority_value"),
                            rs.getString("expiry_time"),
                            rs.getBoolean("auto_suspend_expiry"),
                            rs.getObject("proxy_id", Long.class),
                            rs.getInt("concurrency"),
                            rs.getBigDecimal("billing_rate"),
                            rs.getString("models"),
                            rs.getString("tiers"),
                            rs.getBigDecimal("balance"),
                            rs.getInt("weight"),
                            rs.getBoolean("intercept_warmup_request"),
                            rs.getBoolean("window5h_cost_control_enabled"),
                            rs.getBigDecimal("window5h_cost_limit_usd"),
                            rs.getBoolean("session_count_control_enabled"),
                            rs.getObject("session_count_limit", Integer.class),
                            rs.getString("tls_fingerprint_mode"),
                            rs.getBoolean("session_id_masquerade_enabled"),
                            rs.getInt("session_id_masquerade_ttl_minutes")
                    );
                    item.setQuotaExhausted(rs.getBoolean("quota_exhausted"));
                    item.setQuotaNextRetryAt(rs.getString("quota_next_retry_at"));
                    item.setQuotaFailCount(rs.getInt("quota_fail_count"));
                    item.setQuotaLastReason(rs.getString("quota_last_reason"));
                    item.setQuotaUpdatedAt(rs.getString("quota_updated_at"));
                    return item;
                }
        );
    }

    public List<AccountItem> findByQuotaExhausted(boolean exhausted) {
        return getJdbcTemplate().query(
                "select `id`, `name`, `platform`, `type_name`, `usage_text`, `status_name`, " +
                "`error_count`, `last_check`, `base_url`, `credential`, `notes`, `account_type`, `auth_method`, " +
                "`temp_disabled`, `quota_exhausted`, `quota_next_retry_at`, `quota_fail_count`, `quota_last_reason`, `quota_updated_at`, " +
                "`priority_value`, `expiry_time`, `auto_suspend_expiry`, `proxy_id`, `concurrency`, `billing_rate`, " +
                "`models`, `tiers`, `balance`, `weight`, `intercept_warmup_request`, " +
                "`window5h_cost_control_enabled`, `window5h_cost_limit_usd`, " +
                "`session_count_control_enabled`, `session_count_limit`, " +
                "`tls_fingerprint_mode`, `session_id_masquerade_enabled`, `session_id_masquerade_ttl_minutes` " +
                "from `accounts` where `quota_exhausted` = ?",
                getRowMapper(),
                exhausted ? 1 : 0
        );
    }

    @Override
    protected List<AccountItem> defaultItems() {
        return Arrays.asList();
    }

    @Override
    protected Object[] toColumnValues(AccountItem item) {
        return new Object[]{
                item.getName(),
                item.getPlatform(),
                item.getType(),
                item.getUsage(),
                item.getStatus(),
                item.getErrors(),
                item.getLastCheck(),
                item.getBaseUrl(),
                item.getCredential(),
                item.getNotes(),
                item.getAccountType(),
                item.getAuthMethod(),
                item.isTempDisabled() ? 1 : 0,
                item.isQuotaExhausted() ? 1 : 0,
                item.getQuotaNextRetryAt(),
                item.getQuotaFailCount(),
                item.getQuotaLastReason(),
                item.getQuotaUpdatedAt(),
                item.getPriorityValue(),
                item.getExpiryTime(),
                item.isAutoSuspendExpiry() ? 1 : 0,
                item.getProxyId(),
                item.getConcurrency(),
                item.getBillingRate(),
                item.getModels(),
                item.getTiers(),
                item.getBalance(),
                item.getWeight(),
                item.isInterceptWarmupRequest() ? 1 : 0,
                item.isWindow5hCostControlEnabled() ? 1 : 0,
                item.getWindow5hCostLimitUsd(),
                item.isSessionCountControlEnabled() ? 1 : 0,
                item.getSessionCountLimit(),
                item.getTlsFingerprintMode(),
                item.isSessionIdMasqueradeEnabled() ? 1 : 0,
                item.getSessionIdMasqueradeTtlMinutes()
        };
    }
}
