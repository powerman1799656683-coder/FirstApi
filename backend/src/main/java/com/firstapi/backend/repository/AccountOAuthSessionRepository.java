package com.firstapi.backend.repository;

import com.firstapi.backend.model.AccountOAuthSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

@Repository
public class AccountOAuthSessionRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<AccountOAuthSession> ROW_MAPPER = (rs, rowNum) -> {
        AccountOAuthSession session = new AccountOAuthSession(
                rs.getLong("id"),
                rs.getString("session_id"),
                rs.getString("state_value"),
                rs.getString("platform"),
                rs.getString("account_type"),
                rs.getString("auth_method"),
                rs.getString("code_verifier"),
                rs.getString("status_name"),
                rs.getString("encrypted_credential"),
                rs.getString("credential_mask"),
                rs.getString("provider_subject"),
                rs.getString("error_text"),
                rs.getString("expires_at"),
                rs.getString("exchanged_at"),
                rs.getString("consumed_at"),
                rs.getLong("created_by")
        );
        try { session.setEncryptedRefreshToken(rs.getString("encrypted_refresh_token")); } catch (Exception ignored) {}
        return session;
    };

    public AccountOAuthSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AccountOAuthSession findBySessionId(String sessionId) {
        List<AccountOAuthSession> items = jdbcTemplate.query(
                "select * from `account_oauth_sessions` where `session_id` = ?",
                ROW_MAPPER, sessionId);
        return items.isEmpty() ? null : items.get(0);
    }

    public AccountOAuthSession findByState(String stateValue) {
        List<AccountOAuthSession> items = jdbcTemplate.query(
                "select * from `account_oauth_sessions` where `state_value` = ?",
                ROW_MAPPER, stateValue);
        return items.isEmpty() ? null : items.get(0);
    }

    public AccountOAuthSession save(AccountOAuthSession session) {
        String sql = "insert into `account_oauth_sessions` " +
                "(`session_id`, `state_value`, `platform`, `account_type`, `auth_method`, `code_verifier`, `status_name`, " +
                "`encrypted_credential`, `credential_mask`, `provider_subject`, `error_text`, " +
                "`expires_at`, `exchanged_at`, `consumed_at`, `created_by`, `encrypted_refresh_token`) " +
                "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, session.getSessionId());
            ps.setString(2, session.getStateValue());
            ps.setString(3, session.getPlatform());
            ps.setString(4, session.getAccountType());
            ps.setString(5, session.getAuthMethod());
            ps.setString(6, session.getCodeVerifier());
            ps.setString(7, session.getStatusName());
            ps.setString(8, session.getEncryptedCredential());
            ps.setString(9, session.getCredentialMask());
            ps.setString(10, session.getProviderSubject());
            ps.setString(11, session.getErrorText());
            ps.setString(12, session.getExpiresAt());
            ps.setString(13, session.getExchangedAt());
            ps.setString(14, session.getConsumedAt());
            ps.setObject(15, session.getCreatedBy());
            ps.setString(16, session.getEncryptedRefreshToken());
            return ps;
        }, keyHolder);
        Long key = GeneratedKeySupport.extractId(keyHolder);
        if (key != null) {
            session.setId(key);
        }
        return session;
    }

    public void update(AccountOAuthSession session) {
        jdbcTemplate.update(
                "update `account_oauth_sessions` set `status_name` = ?, `encrypted_credential` = ?, " +
                        "`credential_mask` = ?, `provider_subject` = ?, `error_text` = ?, " +
                        "`exchanged_at` = ?, `consumed_at` = ?, `encrypted_refresh_token` = ? where `id` = ?",
                session.getStatusName(),
                session.getEncryptedCredential(),
                session.getCredentialMask(),
                session.getProviderSubject(),
                session.getErrorText(),
                session.getExchangedAt(),
                session.getConsumedAt(),
                session.getEncryptedRefreshToken(),
                session.getId()
        );
    }

    public void deleteExpired() {
        jdbcTemplate.update(
                "delete from `account_oauth_sessions` where `expires_at` < now() and `status_name` != 'CONSUMED'"
        );
    }
}
