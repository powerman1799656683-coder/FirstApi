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
                new String[]{"name", "platform", "type_name", "usage_text", "status_name", "error_count", "last_check", "credential"},
                (rs, rowNum) -> new AccountItem(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("platform"),
                        rs.getString("type_name"),
                        rs.getString("usage_text"),
                        rs.getString("status_name"),
                        rs.getInt("error_count"),
                        rs.getString("last_check"),
                        rs.getString("credential")
                )
        );
    }

    @Override
    protected List<AccountItem> defaultItems() {
        return Arrays.asList(
                new AccountItem(1L, "Official OpenAI", "OpenAI", "API Key", "$128.22", "正常", 0, "2026/03/13 18:30:15", "sk-official-openai"),
                new AccountItem(2L, "Claude Shared Pool", "Anthropic", "Session", "$92.10", "正常", 0, "2026/03/13 17:20:06", "anthropic-session-token"),
                new AccountItem(3L, "Gemini Backup", "Google", "API Key", "$11.44", "异常", 2, "2026/03/13 11:05:44", "gemini-backup-key")
        );
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
                item.getCredential()
        };
    }
}