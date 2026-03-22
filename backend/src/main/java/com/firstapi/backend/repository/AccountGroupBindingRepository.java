package com.firstapi.backend.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AccountGroupBindingRepository {

    private final JdbcTemplate jdbcTemplate;

    public AccountGroupBindingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Long> findGroupIdsByAccountId(Long accountId) {
        return jdbcTemplate.queryForList(
                "select `group_id` from `account_group_bindings` where `account_id` = ?",
                Long.class, accountId);
    }

    public List<Long> findAccountIdsByGroupId(Long groupId) {
        return jdbcTemplate.queryForList(
                "select `account_id` from `account_group_bindings` where `group_id` = ?",
                Long.class, groupId);
    }

    public void replaceBindings(Long accountId, List<Long> groupIds) {
        jdbcTemplate.update("delete from `account_group_bindings` where `account_id` = ?", accountId);
        if (groupIds != null && !groupIds.isEmpty()) {
            for (Long groupId : groupIds) {
                jdbcTemplate.update(
                        "insert into `account_group_bindings` (`account_id`, `group_id`) values (?, ?)",
                        accountId, groupId);
            }
        }
    }

    public void deleteByAccountId(Long accountId) {
        jdbcTemplate.update("delete from `account_group_bindings` where `account_id` = ?", accountId);
    }

    public java.util.Map<Long, List<Long>> findAllGroupings() {
        java.util.Map<Long, List<Long>> result = new java.util.HashMap<>();
        jdbcTemplate.query(
                "select `account_id`, `group_id` from `account_group_bindings`",
                (rs) -> {
                    Long accountId = rs.getLong("account_id");
                    Long groupId = rs.getLong("group_id");
                    result.computeIfAbsent(accountId, k -> new java.util.ArrayList<>()).add(groupId);
                });
        return result;
    }
}
