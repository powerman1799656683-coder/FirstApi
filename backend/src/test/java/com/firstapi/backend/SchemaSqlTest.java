package com.firstapi.backend;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaSqlTest {

    @Test
    void schemaCreatesAccountOauthSessionsBeforeApplyingOauthMigrations() throws IOException {
        String schema = readSchema();
        String createTable = "create table if not exists `account_oauth_sessions`";
        String addRefreshToken = "alter table `account_oauth_sessions` add column `encrypted_refresh_token` text null";

        assertThat(schema).contains(createTable);
        assertThat(schema.indexOf(createTable)).isLessThan(schema.indexOf(addRefreshToken));
    }

    @Test
    void schemaDefinesAccountsColumnsRequiredByRepository() throws IOException {
        String schema = readSchema();

        assertThat(schema).contains(
                "`base_url` varchar(512) null",
                "`notes` text null",
                "`quota_exhausted` tinyint(1) not null default 0",
                "`quota_next_retry_at` timestamp null",
                "`quota_fail_count` int not null default 0",
                "`quota_last_reason` varchar(128) null",
                "`quota_updated_at` timestamp null",
                "`models` varchar(1024) null",
                "`tiers` varchar(256) null",
                "`balance` decimal(12,2) not null default 0.00",
                "`weight` int not null default 1",
                "`intercept_warmup_request` tinyint(1) not null default 0",
                "`window5h_cost_control_enabled` tinyint(1) not null default 0",
                "`window5h_cost_limit_usd` decimal(12,2) null",
                "`session_count_control_enabled` tinyint(1) not null default 0",
                "`session_count_limit` int null",
                "`tls_fingerprint_mode` varchar(32) not null default 'NONE'",
                "`session_id_masquerade_enabled` tinyint(1) not null default 0",
                "`session_id_masquerade_ttl_minutes` int not null default 15",
                "`encrypted_refresh_token` text null",
                "`oauth_token_expires_at` varchar(64) null"
        );
    }

    @Test
    void schemaDefinesCreateStatementsForRepositoryTables() throws IOException {
        String schema = readSchema();

        assertThat(schema).contains(
                "create table if not exists `relay_records`",
                "create table if not exists `model_pricing`",
                "create table if not exists `monitor_nodes`",
                "create table if not exists `monitor_alerts`",
                "create table if not exists `monitor_alert_rules`",
                "create table if not exists `payment_orders`",
                "create table if not exists `account_group_bindings`",
                "create table if not exists `payment_config`",
                "create table if not exists `json_store`"
        );
    }

    private String readSchema() throws IOException {
        Path schemaPath = Path.of("src", "main", "resources", "schema.sql");
        assertThat(schemaPath).exists();
        return Files.readString(schemaPath, StandardCharsets.UTF_8);
    }
}
