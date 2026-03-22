-- 修复点: H2兼容的测试Schema（去除MySQL特有语法: engine=InnoDB, on update current_timestamp, 动态SQL）
-- 测试覆盖点: 为集成测试提供与生产一致的表结构

create table if not exists `users` (
    `id` bigint not null auto_increment,
    `email` varchar(255) not null,
    `username` varchar(255) not null,
    `balance` varchar(64) not null,
    `group_name` varchar(128) not null,
    `role_name` varchar(128) not null,
    `status_name` varchar(128) not null,
    `time_label` varchar(64) not null,
    `updated_at` timestamp not null default current_timestamp,
    primary key (`id`)
);

create table if not exists `auth_users` (
    `id` bigint not null auto_increment,
    `username` varchar(64) not null,
    `email` varchar(255) not null,
    `display_name` varchar(255) not null,
    `password_hash` varchar(255) not null,
    `role_name` varchar(32) not null,
    `enabled` tinyint not null default 1,
    `last_login` varchar(64) null,
    `updated_at` timestamp not null default current_timestamp,
    primary key (`id`),
    unique (`username`)
);

create table if not exists `groups` (
    `id` bigint not null auto_increment,
    `name` varchar(255) not null,
    `description` text null,
    `platform` varchar(64) not null default 'OpenAI',
    `billing_type` varchar(128) not null,
    `billing_amount` varchar(64) null,
    `rate_value` varchar(64) not null default '1',
    `group_type` varchar(64) not null default 'public',
    `account_count` varchar(64) not null default '0',
    `status_name` varchar(128) not null,
    `claude_code_limit` tinyint not null default 0,
    `fallback_group` varchar(255) null,
    `model_routing` tinyint not null default 0,
    `updated_at` timestamp not null default current_timestamp,
    primary key (`id`)
);

create table if not exists `subscriptions` (
    `id` bigint not null auto_increment,
    `user_name` varchar(255) not null,
    `uid_value` bigint not null,
    `group_name` varchar(255) not null,
    `usage_text` varchar(255) not null,
    `progress_value` double null,
    `expiry_label` varchar(64) not null,
    `status_name` varchar(128) not null,
    `updated_at` timestamp not null default current_timestamp,
    primary key (`id`)
);

create table if not exists `accounts` (
    `id` bigint not null auto_increment,
    `name` varchar(255) not null,
    `platform` varchar(128) not null,
    `type_name` varchar(128) not null,
    `usage_text` varchar(128) not null,
    `status_name` varchar(128) not null,
    `error_count` int not null,
    `last_check` varchar(64) not null,
    `base_url` varchar(512) null,
    `credential` text null,
    `notes` text null,
    `account_type` varchar(64) null,
    `auth_method` varchar(32) null,
    `temp_disabled` tinyint(1) not null default 0,
    `priority_value` int not null default 1,
    `expiry_time` varchar(64) null,
    `auto_suspend_expiry` tinyint(1) not null default 1,
    `proxy_id` bigint null,
    `concurrency` int not null default 10,
    `billing_rate` decimal(10,4) not null default 1.0000,
    `updated_at` timestamp not null default current_timestamp,
    primary key (`id`)
);

create table if not exists `announcements` (
    `id` bigint not null auto_increment,
    `title` varchar(255) not null,
    `content` text null,
    `type_name` varchar(128) not null,
    `status_name` varchar(128) not null,
    `target_scope` varchar(128) not null,
    `time_label` varchar(64) not null,
    `updated_at` timestamp not null default current_timestamp,
    primary key (`id`)
);

create table if not exists `ips` (
    `id` bigint not null auto_increment,
    `name` varchar(255) not null,
    `protocol` varchar(64) not null,
    `address` varchar(255) not null,
    `location` varchar(128) not null,
    `accounts_count` varchar(64) not null,
    `latency` varchar(64) not null,
    `status_name` varchar(128) not null,
    `updated_at` timestamp not null default current_timestamp,
    primary key (`id`)
);

create table if not exists `redemptions` (
    `id` bigint not null auto_increment,
    `name` varchar(255) not null,
    `code` varchar(64) not null,
    `type_name` varchar(128) not null,
    `value_text` varchar(128) not null,
    `usage_text` varchar(64) not null,
    `time_label` varchar(64) not null,
    `status_name` varchar(128) not null,
    `updated_at` timestamp not null default current_timestamp,
    primary key (`id`),
    unique (`code`)
);

create table if not exists `promos` (
    `id` bigint not null auto_increment,
    `code` varchar(64) not null,
    `type_name` varchar(128) not null,
    `value_text` varchar(128) not null,
    `usage_text` varchar(64) not null,
    `expiry_label` varchar(64) not null,
    `status_name` varchar(128) not null,
    `updated_at` timestamp not null default current_timestamp,
    primary key (`id`),
    unique (`code`)
);

create table if not exists `api_keys` (
    `id` bigint not null auto_increment,
    `owner_id` bigint not null default 1,
    `name` varchar(255) not null,
    `group_name` varchar(255) not null default '默认组',
    `api_key` varchar(255) not null,
    `created_label` varchar(64) not null,
    `status_name` varchar(128) not null,
    `last_used` varchar(64) not null,
    `updated_at` timestamp not null default current_timestamp,
    primary key (`id`),
    unique (`api_key`)
);

create table if not exists `relay_records` (
    `id` bigint not null auto_increment,
    `owner_id` bigint not null,
    `api_key_id` bigint not null,
    `provider_name` varchar(32) not null,
    `account_id` bigint not null,
    `model_name` varchar(255) not null,
    `request_id` varchar(128) null,
    `success` tinyint not null,
    `status_code` int not null,
    `error_text` text null,
    `latency_ms` bigint not null,
    `prompt_tokens` int null,
    `completion_tokens` int null,
    `total_tokens` int null,
    `created_at` varchar(64) null,
    `updated_at` timestamp not null default current_timestamp,
    primary key (`id`)
);

create table if not exists `settings` (
    `id` bigint not null,
    `site_name` varchar(255) not null,
    `site_announcement` text null,
    `api_proxy` varchar(255) null,
    `stream_timeout` int null,
    `retry_limit` int null,
    `registration_open` tinyint not null,
    `default_group` varchar(128) null,
    `updated_at` timestamp not null default current_timestamp,
    primary key (`id`)
);

create table if not exists `profiles` (
    `id` bigint not null,
    `username` varchar(255) not null,
    `email` varchar(255) not null,
    `role_name` varchar(128) not null,
    `uid_label` varchar(64) not null,
    `phone` varchar(64) null,
    `bio` text null,
    `verified` tinyint not null,
    `two_factor_enabled` tinyint not null,
    `updated_at` timestamp not null default current_timestamp,
    primary key (`id`)
);

create table if not exists `my_subscription` (
    `id` bigint not null,
    `plan_name` varchar(255) not null,
    `renewal_date` varchar(64) not null,
    `features_json` text not null,
    `usage_json` text not null,
    `request_stats_json` text not null,
    `history_json` text not null,
    `updated_at` timestamp not null default current_timestamp,
    primary key (`id`)
);

create table if not exists `my_redemption` (
    `id` bigint not null,
    `title` varchar(255) not null,
    `description` text null,
    `history_json` text not null,
    `updated_at` timestamp not null default current_timestamp,
    primary key (`id`)
);

create table if not exists `monitor_nodes` (
    `id` bigint not null auto_increment,
    `node_name` varchar(255) not null,
    `check_url` varchar(512) not null,
    `location_name` varchar(128) not null,
    `status_name` varchar(128) not null default '未检测',
    `latency_value` varchar(64) not null default '0ms',
    `uptime_value` varchar(64) not null default '0.00%',
    `total_checks` int not null default 0,
    `success_checks` int not null default 0,
    `last_check_label` varchar(64) not null default '',
    `updated_at` timestamp not null default current_timestamp,
    primary key (`id`)
);

create table if not exists `monitor_alerts` (
    `id` bigint not null auto_increment,
    `time_label` varchar(64) not null,
    `level_name` varchar(32) not null,
    `event_text` varchar(512) not null,
    `status_name` varchar(128) not null,
    `owner_name` varchar(128) not null,
    `status_color` varchar(32) not null,
    `updated_at` timestamp not null default current_timestamp,
    primary key (`id`)
);

create table if not exists `payment_orders` (
    `id` bigint not null auto_increment,
    `order_no` varchar(64) not null,
    `owner_id` bigint not null,
    `owner_name` varchar(255) not null,
    `channel_name` varchar(64) not null,
    `purpose_name` varchar(128) not null,
    `amount_value` varchar(64) not null,
    `status_name` varchar(64) not null,
    `trade_no` varchar(128) null,
    `pay_url` text null,
    `notify_json` text null,
    `created_label` varchar(64) not null,
    `paid_label` varchar(64) null,
    `expire_label` varchar(64) not null,
    `remark_text` varchar(512) null,
    `updated_at` timestamp not null default current_timestamp,
    primary key (`id`),
    unique (`order_no`)
);

create table if not exists `payment_config` (
    `id` bigint not null,
    `wechat_enabled` tinyint not null default 0,
    `wechat_app_id` varchar(255) null,
    `wechat_mch_id` varchar(255) null,
    `wechat_api_key` varchar(512) null,
    `alipay_enabled` tinyint not null default 0,
    `alipay_app_id` varchar(255) null,
    `alipay_private_key` text null,
    `alipay_public_key` text null,
    `alipay_gateway_url` varchar(512) null,
    `notify_base_url` varchar(512) null,
    `order_expire_minutes` int not null default 30,
    `updated_at` timestamp not null default current_timestamp,
    primary key (`id`)
);

create table if not exists `json_store` (
    `store_key` varchar(128) not null,
    `payload` text not null,
    `updated_at` timestamp not null default current_timestamp,
    primary key (`store_key`)
);

merge into `accounts` (`id`, `name`, `platform`, `type_name`, `usage_text`, `status_name`, `error_count`, `last_check`, `base_url`, `credential`, `notes`, `account_type`, `auth_method`, `temp_disabled`, `priority_value`, `expiry_time`, `auto_suspend_expiry`, `proxy_id`, `concurrency`, `billing_rate`)
key(`id`) values
(1, 'relay-openai', 'OpenAI', 'OpenAI API', '¥0.00', 'normal', 0, '2026/03/17 00:00:00', null, 'sk-openai-test', null, 'OpenAI API', 'API Key', 0, 1, null, 1, null, 10, 1.0000),
(2, 'relay-claude', 'Anthropic', 'Claude Code', '¥0.00', 'normal', 0, '2026/03/17 00:00:00', null, 'sk-claude-test', null, 'Claude Code', 'API Key', 0, 2, null, 1, null, 10, 1.0000);
