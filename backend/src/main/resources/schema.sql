create table if not exists `users` (
    `id` bigint not null auto_increment,
    `email` varchar(255) not null,
    `username` varchar(255) not null,
    `balance` varchar(64) not null,
    `group_name` varchar(128) not null,
    `role_name` varchar(128) not null,
    `status_name` varchar(128) not null,
    `time_label` varchar(64) not null,
    `login_ip` varchar(64) not null default '',
    `login_location` varchar(255) not null default '',
    `updated_at` timestamp not null default current_timestamp on update current_timestamp,
    primary key (`id`)
) engine=InnoDB default charset=utf8mb4;

-- 为已有 users 表添加 login_ip / login_location 列
set @col_ip = (select count(*) from information_schema.columns where table_schema = database() and table_name = 'users' and column_name = 'login_ip');
set @sql_ip = if(@col_ip = 0, 'alter table `users` add column `login_ip` varchar(64) not null default '''' after `time_label`', 'select 1');
prepare stmt_ip from @sql_ip;
execute stmt_ip;
deallocate prepare stmt_ip;

set @col_loc = (select count(*) from information_schema.columns where table_schema = database() and table_name = 'users' and column_name = 'login_location');
set @sql_loc = if(@col_loc = 0, 'alter table `users` add column `login_location` varchar(255) not null default '''' after `login_ip`', 'select 1');
prepare stmt_loc from @sql_loc;
execute stmt_loc;
deallocate prepare stmt_loc;

create table if not exists `auth_users` (
    `id` bigint not null auto_increment,
    `username` varchar(64) not null,
    `email` varchar(255) not null,
    `display_name` varchar(255) not null,
    `password_hash` varchar(255) not null,
    `role_name` varchar(32) not null,
    `enabled` tinyint(1) not null default 1,
    `last_login` varchar(64) null,
    `updated_at` timestamp not null default current_timestamp on update current_timestamp,
    primary key (`id`),
    unique key `uk_auth_users_username` (`username`)
) engine=InnoDB default charset=utf8mb4;

create table if not exists `groups` (
    `id` bigint not null auto_increment,
    `name` varchar(255) not null,
    `billing_type` varchar(128) not null,
    `user_count` varchar(64) not null,
    `status_name` varchar(128) not null,
    `priority_value` varchar(64) not null,
    `rate_value` varchar(64) not null,
    `updated_at` timestamp not null default current_timestamp on update current_timestamp,
    primary key (`id`)
) engine=InnoDB default charset=utf8mb4;

create table if not exists `subscriptions` (
    `id` bigint not null auto_increment,
    `user_name` varchar(255) not null,
    `uid_value` bigint not null,
    `group_name` varchar(255) not null,
    `usage_text` varchar(255) not null,
    `progress_value` double null,
    `expiry_label` varchar(64) not null,
    `status_name` varchar(128) not null,
    `daily_limit` decimal(20,2) null comment '每日配额上限（元），null 表示无限制',
    `updated_at` timestamp not null default current_timestamp on update current_timestamp,
    primary key (`id`)
) engine=InnoDB default charset=utf8mb4;

-- MySQL 8+ 兼容：先检查列是否存在再添加
set @col_exists = (select count(*) from information_schema.columns where table_schema = database() and table_name = 'subscriptions' and column_name = 'daily_limit');
set @sql = if(@col_exists = 0, 'alter table `subscriptions` add column `daily_limit` decimal(20,2) null comment ''每日配额上限（元），null 表示无限制'' after `status_name`', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @col_exists = (select count(*) from information_schema.columns where table_schema = database() and table_name = 'subscriptions' and column_name = 'group_id');
set @sql = if(@col_exists = 0, 'alter table `subscriptions` add column `group_id` bigint null comment ''绑定分组ID（groups.id），null表示不限分组'' after `uid_value`', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

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
    `quota_exhausted` tinyint(1) not null default 0,
    `quota_next_retry_at` timestamp null,
    `quota_fail_count` int not null default 0,
    `quota_last_reason` varchar(128) null,
    `quota_updated_at` timestamp null,
    `priority_value` int not null default 1,
    `expiry_time` varchar(64) null,
    `auto_suspend_expiry` tinyint(1) not null default 1,
    `proxy_id` bigint null,
    `concurrency` int not null default 10,
    `billing_rate` decimal(10,4) not null default 1.0000,
    `models` varchar(1024) null,
    `tiers` varchar(256) null,
    `balance` decimal(12,2) not null default 0.00,
    `weight` int not null default 1,
    `intercept_warmup_request` tinyint(1) not null default 0,
    `window5h_cost_control_enabled` tinyint(1) not null default 0,
    `window5h_cost_limit_usd` decimal(12,2) null,
    `session_count_control_enabled` tinyint(1) not null default 0,
    `session_count_limit` int null,
    `tls_fingerprint_mode` varchar(32) not null default 'NONE',
    `session_id_masquerade_enabled` tinyint(1) not null default 0,
    `session_id_masquerade_ttl_minutes` int not null default 15,
    `updated_at` timestamp not null default current_timestamp on update current_timestamp,
    primary key (`id`)
) engine=InnoDB default charset=utf8mb4;

create table if not exists `announcements` (
    `id` bigint not null auto_increment,
    `title` varchar(255) not null,
    `content` text null,
    `type_name` varchar(128) not null,
    `status_name` varchar(128) not null,
    `target_scope` varchar(128) not null,
    `time_label` varchar(64) not null,
    `updated_at` timestamp not null default current_timestamp on update current_timestamp,
    primary key (`id`)
) engine=InnoDB default charset=utf8mb4;

create table if not exists `ips` (
    `id` bigint not null auto_increment,
    `name` varchar(255) not null,
    `protocol` varchar(64) not null,
    `address` varchar(255) not null,
    `location` varchar(128) not null,
    `accounts_count` varchar(64) not null,
    `latency` varchar(64) not null,
    `status_name` varchar(128) not null,
    `updated_at` timestamp not null default current_timestamp on update current_timestamp,
    primary key (`id`)
) engine=InnoDB default charset=utf8mb4;

create table if not exists `redemptions` (
    `id` bigint not null auto_increment,
    `name` varchar(255) not null,
    `code` varchar(64) not null,
    `type_name` varchar(128) not null,
    `value_text` varchar(128) not null,
    `usage_text` varchar(64) not null,
    `time_label` varchar(64) not null,
    `status_name` varchar(128) not null,
    `updated_at` timestamp not null default current_timestamp on update current_timestamp,
    primary key (`id`),
    unique key `uk_redemptions_code` (`code`)
) engine=InnoDB default charset=utf8mb4;

create table if not exists `promos` (
    `id` bigint not null auto_increment,
    `code` varchar(64) not null,
    `type_name` varchar(128) not null,
    `value_text` varchar(128) not null,
    `usage_text` varchar(64) not null,
    `expiry_label` varchar(64) not null,
    `status_name` varchar(128) not null,
    `updated_at` timestamp not null default current_timestamp on update current_timestamp,
    primary key (`id`),
    unique key `uk_promos_code` (`code`)
) engine=InnoDB default charset=utf8mb4;

create table if not exists `api_keys` (
    `id` bigint not null auto_increment,
    `owner_id` bigint not null default 1,
    `name` varchar(255) not null,
    `api_key` varchar(255) not null,
    `created_label` varchar(64) not null,
    `status_name` varchar(128) not null,
    `last_used` varchar(64) not null,
    `updated_at` timestamp not null default current_timestamp on update current_timestamp,
    primary key (`id`),
    unique key `uk_api_keys_key` (`api_key`)
) engine=InnoDB default charset=utf8mb4;

set @api_keys_owner_id_exists = (
    select count(*)
    from information_schema.columns
    where table_schema = database()
      and table_name = 'api_keys'
      and column_name = 'owner_id'
);
set @api_keys_owner_id_sql = if(
    @api_keys_owner_id_exists = 0,
    'alter table `api_keys` add column `owner_id` bigint not null default 1',
    'select 1'
);
prepare api_keys_owner_id_stmt from @api_keys_owner_id_sql;
execute api_keys_owner_id_stmt;
deallocate prepare api_keys_owner_id_stmt;

create table if not exists `settings` (
    `id` bigint not null,
    `site_name` varchar(255) not null,
    `site_announcement` text null,
    `api_proxy` varchar(255) null,
    `stream_timeout` int null,
    `retry_limit` int null,
    `registration_open` tinyint(1) not null,
    `default_group` varchar(128) null,
    `updated_at` timestamp not null default current_timestamp on update current_timestamp,
    primary key (`id`)
) engine=InnoDB default charset=utf8mb4;

create table if not exists `profiles` (
    `id` bigint not null,
    `username` varchar(255) not null,
    `email` varchar(255) not null,
    `role_name` varchar(128) not null,
    `uid_label` varchar(64) not null,
    `phone` varchar(64) null,
    `bio` text null,
    `verified` tinyint(1) not null,
    `two_factor_enabled` tinyint(1) not null,
    `updated_at` timestamp not null default current_timestamp on update current_timestamp,
    primary key (`id`)
) engine=InnoDB default charset=utf8mb4;

create table if not exists `my_subscription` (
    `id` bigint not null,
    `plan_name` varchar(255) not null,
    `renewal_date` varchar(64) not null,
    `features_json` json not null,
    `usage_json` json not null,
    `request_stats_json` json not null,
    `history_json` json not null,
    `updated_at` timestamp not null default current_timestamp on update current_timestamp,
    primary key (`id`)
) engine=InnoDB default charset=utf8mb4;

create table if not exists `my_redemption` (
    `id` bigint not null,
    `title` varchar(255) not null,
    `description` text null,
    `history_json` json not null,
    `updated_at` timestamp not null default current_timestamp on update current_timestamp,
    primary key (`id`)
) engine=InnoDB default charset=utf8mb4;

create table if not exists `account_oauth_sessions` (
    `id` bigint not null auto_increment,
    `session_id` varchar(64) not null,
    `state_value` varchar(128) not null,
    `platform` varchar(32) not null,
    `account_type` varchar(64) not null,
    `auth_method` varchar(32) not null,
    `code_verifier` varchar(255) null,
    `status_name` varchar(32) not null,
    `encrypted_credential` text null,
    `credential_mask` varchar(64) null,
    `provider_subject` varchar(128) null,
    `error_text` text null,
    `expires_at` datetime not null,
    `exchanged_at` datetime null,
    `consumed_at` datetime null,
    `created_by` bigint not null,
    `encrypted_refresh_token` text null,
    `updated_at` timestamp not null default current_timestamp on update current_timestamp,
    primary key (`id`),
    unique key `uk_account_oauth_sessions_session_id` (`session_id`),
    unique key `uk_account_oauth_sessions_state_value` (`state_value`)
) engine=InnoDB default charset=utf8mb4;

-- OAuth: accounts 表增加 refresh_token 和 token 过期时间
set @col_exists = (select count(*) from information_schema.columns where table_schema = database() and table_name = 'accounts' and column_name = 'encrypted_refresh_token');
set @sql = if(@col_exists = 0, 'alter table `accounts` add column `encrypted_refresh_token` text null', 'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

set @col_exists = (select count(*) from information_schema.columns where table_schema = database() and table_name = 'accounts' and column_name = 'oauth_token_expires_at');
set @sql = if(@col_exists = 0, 'alter table `accounts` add column `oauth_token_expires_at` varchar(64) null', 'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

-- OAuth: account_oauth_sessions 表增加 refresh_token
set @col_exists = (select count(*) from information_schema.columns where table_schema = database() and table_name = 'account_oauth_sessions' and column_name = 'encrypted_refresh_token');
set @sql = if(@col_exists = 0, 'alter table `account_oauth_sessions` add column `encrypted_refresh_token` text null', 'select 1');
prepare stmt from @sql; execute stmt; deallocate prepare stmt;

create table if not exists `daily_quota_usage` (
    `id` bigint not null auto_increment,
    `owner_id` bigint not null comment 'auth_users.id',
    `group_id` bigint not null comment 'groups.id',
    `quota_date` date not null comment '业务日期（Asia/Shanghai）',
    `used_cost` decimal(20,10) not null default 0 comment '当日已消耗成本（¥）',
    `updated_at` timestamp not null default current_timestamp on update current_timestamp,
    primary key (`id`),
    unique key `uk_quota_usage_owner_group_date` (`owner_id`, `group_id`, `quota_date`),
    key `idx_quota_usage_group_date` (`group_id`, `quota_date`)
) engine=InnoDB default charset=utf8mb4;

create table if not exists `relay_records` (
    `id` bigint not null auto_increment,
    `owner_id` bigint not null comment 'auth_users.id',
    `api_key_id` bigint not null comment 'api_keys.id',
    `provider_name` varchar(64) not null comment '上游供应商（openai/claude）',
    `account_id` bigint null comment '使用的账号 accounts.id',
    `model_name` varchar(128) null comment '请求模型名称',
    `request_id` varchar(128) null comment '上游返回的请求 ID',
    `success` tinyint(1) not null default 0 comment '是否成功',
    `status_code` int null comment 'HTTP 状态码',
    `error_text` text null comment '失败时的错误响应',
    `latency_ms` bigint null comment '响应延迟（毫秒）',
    `prompt_tokens` int null comment '输入 token 数',
    `completion_tokens` int null comment '输出 token 数',
    `total_tokens` int null comment '总 token 数',
    `input_price` decimal(20,8) null comment '输入单价（元/百万 token）',
    `output_price` decimal(20,8) null comment '输出单价（元/百万 token）',
    `pricing_currency` varchar(16) null comment '定价货币',
    `group_ratio` decimal(10,4) null comment '分组费率倍数',
    `pricing_rule_id` bigint null comment '匹配的定价规则 ID',
    `pricing_rule_name` varchar(128) null comment '匹配的定价规则名称',
    `pricing_status` varchar(32) null comment '定价状态（MATCHED/NOT_FOUND/USAGE_MISSING）',
    `pricing_found` tinyint(1) null comment '是否找到定价规则',
    `cost` decimal(20,10) null comment '本次调用费用（元）',
    `usage_json` text null comment '用量 JSON 快照',
    `created_at` varchar(64) not null comment '创建时间（格式化字符串）',
    `created_at_ts` datetime null comment '创建时间（用于日期范围查询）',
    primary key (`id`)
) engine=InnoDB default charset=utf8mb4;

-- relay_records 高频查询索引（MySQL 8.0 兼容写法）
set @idx_exists = (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'relay_records' and index_name = 'idx_relay_records_owner_id');
set @idx_sql = if(@idx_exists = 0, 'create index `idx_relay_records_owner_id` on `relay_records` (`owner_id`)', 'select 1');
prepare idx_stmt from @idx_sql; execute idx_stmt; deallocate prepare idx_stmt;

set @idx_exists = (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'relay_records' and index_name = 'idx_relay_records_account_id');
set @idx_sql = if(@idx_exists = 0, 'create index `idx_relay_records_account_id` on `relay_records` (`account_id`)', 'select 1');
prepare idx_stmt from @idx_sql; execute idx_stmt; deallocate prepare idx_stmt;

set @idx_exists = (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'relay_records' and index_name = 'idx_relay_records_model_name');
set @idx_sql = if(@idx_exists = 0, 'create index `idx_relay_records_model_name` on `relay_records` (`model_name`)', 'select 1');
prepare idx_stmt from @idx_sql; execute idx_stmt; deallocate prepare idx_stmt;

set @idx_exists = (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'relay_records' and index_name = 'idx_relay_records_created_at_ts');
set @idx_sql = if(@idx_exists = 0, 'create index `idx_relay_records_created_at_ts` on `relay_records` (`created_at_ts`)', 'select 1');
prepare idx_stmt from @idx_sql; execute idx_stmt; deallocate prepare idx_stmt;

set @idx_exists = (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'relay_records' and index_name = 'idx_relay_records_api_key_id');
set @idx_sql = if(@idx_exists = 0, 'create index `idx_relay_records_api_key_id` on `relay_records` (`api_key_id`)', 'select 1');
prepare idx_stmt from @idx_sql; execute idx_stmt; deallocate prepare idx_stmt;

set @idx_exists = (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'relay_records' and index_name = 'idx_relay_records_pricing_status');
set @idx_sql = if(@idx_exists = 0, 'create index `idx_relay_records_pricing_status` on `relay_records` (`pricing_status`)', 'select 1');
prepare idx_stmt from @idx_sql; execute idx_stmt; deallocate prepare idx_stmt;

set @idx_exists = (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'api_keys' and index_name = 'idx_api_keys_owner_id');
set @idx_sql = if(@idx_exists = 0, 'create index `idx_api_keys_owner_id` on `api_keys` (`owner_id`)', 'select 1');
prepare idx_stmt from @idx_sql; execute idx_stmt; deallocate prepare idx_stmt;

set @idx_exists = (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'auth_users' and index_name = 'idx_auth_users_email');
set @idx_sql = if(@idx_exists = 0, 'create index `idx_auth_users_email` on `auth_users` (`email`)', 'select 1');
prepare idx_stmt from @idx_sql; execute idx_stmt; deallocate prepare idx_stmt;

set @idx_exists = (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'accounts' and index_name = 'idx_accounts_platform_type');
set @idx_sql = if(@idx_exists = 0, 'create index `idx_accounts_platform_type` on `accounts` (`platform`, `account_type`)', 'select 1');
prepare idx_stmt from @idx_sql; execute idx_stmt; deallocate prepare idx_stmt;

set @idx_exists = (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'subscriptions' and index_name = 'idx_subscriptions_uid');
set @idx_sql = if(@idx_exists = 0, 'create index `idx_subscriptions_uid` on `subscriptions` (`uid_value`)', 'select 1');
prepare idx_stmt from @idx_sql; execute idx_stmt; deallocate prepare idx_stmt;

-- ========== 订阅等级设置表 ==========
create table if not exists `subscription_plans` (
    `id` bigint not null auto_increment,
    `name` varchar(128) not null comment '等级名称，如 普通会员、Pro会员、Max会员',
    `monthly_quota` decimal(20,2) not null comment '每月配额上限（元）',
    `daily_limit` decimal(20,2) null comment '每日配额上限（元），null 表示不限制',
    `status` varchar(64) not null default '正常' comment '正常 / 禁用',
    `created_at` timestamp not null default current_timestamp,
    `updated_at` timestamp not null default current_timestamp on update current_timestamp,
    primary key (`id`),
    unique key `uk_plan_name` (`name`)
) engine=InnoDB default charset=utf8mb4;

-- subscriptions 表增加 plan_id 列（关联 subscription_plans.id）
set @col_exists = (select count(*) from information_schema.columns where table_schema = database() and table_name = 'subscriptions' and column_name = 'plan_id');
set @sql = if(@col_exists = 0, 'alter table `subscriptions` add column `plan_id` bigint null comment ''关联订阅等级ID（subscription_plans.id）'' after `group_id`', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;
