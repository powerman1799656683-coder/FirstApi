package com.firstapi.backend.repository;

import com.firstapi.backend.model.ApiKeyItem;
import com.firstapi.backend.service.SensitiveDataService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

@Repository
public class MyApiKeysRepository {

    private final JdbcTemplate jdbcTemplate;
    private final SensitiveDataService sensitiveDataService;
    private final RowMapper<ApiKeyItem> rowMapper = (rs, rowNum) -> {
        ApiKeyItem item = new ApiKeyItem();
        item.setId(rs.getLong("id"));
        item.setOwnerId(rs.getLong("owner_id"));
        long groupId = rs.getLong("group_id");
        item.setGroupId(rs.wasNull() ? null : groupId);
        item.setName(rs.getString("name"));
        item.setKey(rs.getString("api_key"));
        item.setCreated(rs.getString("created_label"));
        item.setStatus(rs.getString("status_name"));
        item.setLastUsed(rs.getString("last_used"));
        return item;
    };

    public MyApiKeysRepository(JdbcTemplate jdbcTemplate, SensitiveDataService sensitiveDataService) {
        this.jdbcTemplate = jdbcTemplate;
        this.sensitiveDataService = sensitiveDataService;
    }

    public List<ApiKeyItem> findAllByOwnerId(Long ownerId) {
        return jdbcTemplate.query(
                "select `id`, `owner_id`, `group_id`, `name`, `api_key`, `created_label`, `status_name`, `last_used` from `api_keys` where `owner_id` = ? order by `id` desc",
                rowMapper,
                ownerId
        );
    }

    public ApiKeyItem findByIdAndOwnerId(Long id, Long ownerId) {
        List<ApiKeyItem> items = jdbcTemplate.query(
                "select `id`, `owner_id`, `group_id`, `name`, `api_key`, `created_label`, `status_name`, `last_used` from `api_keys` where `id` = ? and `owner_id` = ?",
                rowMapper,
                id,
                ownerId
        );
        return items.isEmpty() ? null : items.get(0);
    }

    public ApiKeyItem findByPlainTextKey(String plainTextKey) {
        List<ApiKeyItem> items = jdbcTemplate.query(
                "select `id`, `owner_id`, `group_id`, `name`, `api_key`, `created_label`, `status_name`, `last_used` from `api_keys` order by `id` desc",
                rowMapper
        );
        for (ApiKeyItem item : items) {
            try {
                if (plainTextKey.equals(sensitiveDataService.reveal(item.getKey()))) {
                    return item;
                }
            } catch (Exception ignored) {
                // 跳过解密失败的记录
            }
        }
        return null;
    }

    public ApiKeyItem save(ApiKeyItem item) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "insert into `api_keys` (`owner_id`, `group_id`, `name`, `api_key`, `created_label`, `status_name`, `last_used`) values (?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, item.getOwnerId());
            if (item.getGroupId() != null) {
                statement.setLong(2, item.getGroupId());
            } else {
                statement.setNull(2, java.sql.Types.BIGINT);
            }
            statement.setString(3, item.getName());
            statement.setString(4, item.getKey());
            statement.setString(5, item.getCreated());
            statement.setString(6, item.getStatus());
            statement.setString(7, item.getLastUsed());
            return statement;
        }, keyHolder);

        Long key = GeneratedKeySupport.extractId(keyHolder);
        if (key != null) {
            item.setId(key);
        }
        return item;
    }

    public ApiKeyItem update(ApiKeyItem item) {
        jdbcTemplate.update(
                "update `api_keys` set `group_id` = ?, `name` = ?, `api_key` = ?, `created_label` = ?, `status_name` = ?, `last_used` = ? where `id` = ? and `owner_id` = ?",
                item.getGroupId(),
                item.getName(),
                item.getKey(),
                item.getCreated(),
                item.getStatus(),
                item.getLastUsed(),
                item.getId(),
                item.getOwnerId()
        );
        return item;
    }

    public void touchLastUsed(Long id, Long ownerId, String lastUsed) {
        jdbcTemplate.update(
                "update `api_keys` set `last_used` = ? where `id` = ? and `owner_id` = ?",
                lastUsed,
                id,
                ownerId
        );
    }

    public void deleteByIdAndOwnerId(Long id, Long ownerId) {
        jdbcTemplate.update("delete from `api_keys` where `id` = ? and `owner_id` = ?", id, ownerId);
    }

    public int countAll() {
        Integer count = jdbcTemplate.queryForObject("select count(*) from `api_keys`", Integer.class);
        return count != null ? count : 0;
    }

    public int countByOwnerId(Long ownerId) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from `api_keys` where `owner_id` = ?", Integer.class, ownerId);
        return count != null ? count : 0;
    }
}
