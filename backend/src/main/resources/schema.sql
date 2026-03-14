create table if not exists `users` (
    `id` bigint not null auto_increment,
    `email` varchar(255) not null,
    `username` varchar(255) not null,
    `balance` varchar(64) not null,
    `group_name` varchar(128) not null,
    `role_name` varchar(128) not null,
    `status_name` varchar(128) not null,
    `time_label` varchar(64) not null,
    `updated_at` timestamp not null default current_timestamp on update current_timestamp,
    primary key (`id`)
) engine=InnoDB default charset=utf8mb4;

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
    `updated_at` timestamp not null default current_timestamp on update current_timestamp,
    primary key (`id`)
) engine=InnoDB default charset=utf8mb4;

create table if not exists `accounts` (
    `id` bigint not null auto_increment,
    `name` varchar(255) not null,
    `platform` varchar(128) not null,
    `type_name` varchar(128) not null,
    `usage_text` varchar(128) not null,
    `status_name` varchar(128) not null,
    `error_count` int not null,
    `last_check` varchar(64) not null,
    `credential` text null,
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
