package com.firstapi.backend.repository;

import com.firstapi.backend.model.AuthUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

@Repository
public class AuthUserRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<AuthUser> rowMapper = (rs, rowNum) -> {
        AuthUser user = new AuthUser();
        user.setId(rs.getLong("id"));
        user.setUsername(rs.getString("username"));
        user.setEmail(rs.getString("email"));
        user.setDisplayName(rs.getString("display_name"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setRole(rs.getString("role_name"));
        user.setEnabled(rs.getBoolean("enabled"));
        user.setLastLogin(rs.getString("last_login"));
        return user;
    };

    public AuthUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AuthUser findByUsername(String username) {
        List<AuthUser> users = jdbcTemplate.query(
                "select `id`, `username`, `email`, `display_name`, `password_hash`, `role_name`, `enabled`, `last_login` from `auth_users` where `username` = ?",
                rowMapper,
                username
        );
        return users.isEmpty() ? null : users.get(0);
    }

    public AuthUser findById(Long id) {
        List<AuthUser> users = jdbcTemplate.query(
                "select `id`, `username`, `email`, `display_name`, `password_hash`, `role_name`, `enabled`, `last_login` from `auth_users` where `id` = ?",
                rowMapper,
                id
        );
        return users.isEmpty() ? null : users.get(0);
    }

    public AuthUser save(AuthUser user) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "insert into `auth_users` (`username`, `email`, `display_name`, `password_hash`, `role_name`, `enabled`, `last_login`) values (?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, user.getUsername());
            statement.setString(2, user.getEmail());
            statement.setString(3, user.getDisplayName());
            statement.setString(4, user.getPasswordHash());
            statement.setString(5, user.getRole());
            statement.setBoolean(6, user.isEnabled());
            statement.setString(7, user.getLastLogin());
            return statement;
        }, keyHolder);

        Long key = GeneratedKeySupport.extractId(keyHolder);
        if (key != null) {
            user.setId(key);
        }
        return user;
    }

    public void updatePasswordHash(Long id, String passwordHash) {
        jdbcTemplate.update("update `auth_users` set `password_hash` = ? where `id` = ?", passwordHash, id);
    }

    public void updateDisplayName(Long id, String displayName) {
        jdbcTemplate.update("update `auth_users` set `display_name` = ? where `id` = ?", displayName, id);
    }

    public void updateLastLogin(Long id, String lastLogin) {
        jdbcTemplate.update("update `auth_users` set `last_login` = ? where `id` = ?", lastLogin, id);
    }

    public void updateByUsername(String username, String email, String displayName, String role) {
        jdbcTemplate.update("update `auth_users` set `email` = ?, `display_name` = ?, `role_name` = ? where `username` = ?",
                email, displayName, role, username);
    }

    public void deleteByUsername(String username) {
        jdbcTemplate.update("delete from `auth_users` where `username` = ?", username);
    }
}
