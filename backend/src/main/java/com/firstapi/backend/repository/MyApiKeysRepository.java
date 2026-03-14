package com.firstapi.backend.repository;

import com.firstapi.backend.model.ApiKeyItem;
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
    private final RowMapper<ApiKeyItem> rowMapper = (rs, rowNum) -> {
        ApiKeyItem item = new ApiKeyItem();
        item.setId(rs.getLong("id"));
        item.setOwnerId(rs.getLong("owner_id"));
        item.setName(rs.getString("name"));
        item.setKey(rs.getString("api_key"));
        item.setCreated(rs.getString("created_label"));
        item.setStatus(rs.getString("status_name"));
        item.setLastUsed(rs.getString("last_used"));
        return item;
    };

    public MyApiKeysRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ApiKeyItem> findAllByOwnerId(Long ownerId) {
        return jdbcTemplate.query(
                "select `id`, `owner_id`, `name`, `api_key`, `created_label`, `status_name`, `last_used` from `api_keys` where `owner_id` = ? order by `id` desc",
                rowMapper,
                ownerId
        );
    }

    public ApiKeyItem findByIdAndOwnerId(Long id, Long ownerId) {
        List<ApiKeyItem> items = jdbcTemplate.query(
                "select `id`, `owner_id`, `name`, `api_key`, `created_label`, `status_name`, `last_used` from `api_keys` where `id` = ? and `owner_id` = ?",
                rowMapper,
                id,
                ownerId
        );
        return items.isEmpty() ? null : items.get(0);
    }

    public ApiKeyItem save(ApiKeyItem item) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "insert into `api_keys` (`owner_id`, `name`, `api_key`, `created_label`, `status_name`, `last_used`) values (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, item.getOwnerId());
            statement.setString(2, item.getName());
            statement.setString(3, item.getKey());
            statement.setString(4, item.getCreated());
            statement.setString(5, item.getStatus());
            statement.setString(6, item.getLastUsed());
            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key != null) {
            item.setId(key.longValue());
        }
        return item;
    }

    public ApiKeyItem update(ApiKeyItem item) {
        jdbcTemplate.update(
                "update `api_keys` set `name` = ?, `api_key` = ?, `created_label` = ?, `status_name` = ?, `last_used` = ? where `id` = ? and `owner_id` = ?",
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

    public void deleteByIdAndOwnerId(Long id, Long ownerId) {
        jdbcTemplate.update("delete from `api_keys` where `id` = ? and `owner_id` = ?", id, ownerId);
    }
}
