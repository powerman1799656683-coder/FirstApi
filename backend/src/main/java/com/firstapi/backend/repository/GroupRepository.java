package com.firstapi.backend.repository;

import com.firstapi.backend.model.GroupItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;

@Repository
public class GroupRepository extends JdbcListRepository<GroupItem> {

    private static final String[] COLUMNS_WITH_LEGACY_USER_COUNT = new String[]{
            "name", "description", "platform", "account_type", "billing_type", "billing_amount",
            "rate_value", "group_type", "account_count", "user_count", "status_name",
            "claude_code_limit", "fallback_group", "model_routing"
    };

    private static final String INSERT_SQL_WITH_LEGACY_USER_COUNT =
            "insert into `groups` (`name`, `description`, `platform`, `account_type`, `billing_type`, `billing_amount`, " +
                    "`rate_value`, `group_type`, `account_count`, `user_count`, `status_name`, `claude_code_limit`, " +
                    "`fallback_group`, `model_routing`) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String INSERT_WITH_ID_SQL_WITH_LEGACY_USER_COUNT =
            "insert into `groups` (`id`, `name`, `description`, `platform`, `account_type`, `billing_type`, `billing_amount`, " +
                    "`rate_value`, `group_type`, `account_count`, `user_count`, `status_name`, `claude_code_limit`, " +
                    "`fallback_group`, `model_routing`) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String UPDATE_SQL_WITH_LEGACY_USER_COUNT =
            "update `groups` set `name` = ?, `description` = ?, `platform` = ?, `account_type` = ?, `billing_type` = ?, " +
                    "`billing_amount` = ?, `rate_value` = ?, `group_type` = ?, `account_count` = ?, `user_count` = ?, " +
                    "`status_name` = ?, `claude_code_limit` = ?, `fallback_group` = ?, `model_routing` = ? where `id` = ?";

    private final JdbcTemplate jdbcTemplate;
    private volatile Boolean hasLegacyUserCount;

    public GroupRepository(JdbcTemplate jdbcTemplate) {
        super(
                jdbcTemplate,
                "groups",
                new String[]{"name", "description", "platform", "account_type", "billing_type", "billing_amount",
                             "rate_value", "group_type", "account_count", "status_name",
                             "claude_code_limit", "fallback_group", "model_routing"},
                (rs, rowNum) -> {
                    GroupItem item = new GroupItem(
                            rs.getLong("id"),
                            rs.getString("name"),
                            rs.getString("description"),
                            rs.getString("platform"),
                            rs.getString("billing_type"),
                            rs.getString("billing_amount"),
                            rs.getString("rate_value"),
                            rs.getString("group_type"),
                            rs.getString("account_count"),
                            rs.getString("status_name"),
                            rs.getBoolean("claude_code_limit"),
                            rs.getString("fallback_group"),
                            rs.getBoolean("model_routing")
                    );
                    item.setAccountType(rs.getString("account_type"));
                    return item;
                }
        );
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    protected List<GroupItem> defaultItems() {
        List<GroupItem> defaults = Arrays.asList(
                new GroupItem(1L, "claude max20", null, "Anthropic", "标准（余额）", null, "1", "公开", "2个账号", "正常", false, null, false),
                new GroupItem(2L, "codex", null, "OpenAI", "标准（余额）", null, "1", "公开", "4个账号", "正常", false, null, false),
                new GroupItem(3L, "gemini", null, "Gemini", "标准（余额）", null, "1", "公开", "0个账号", "正常", false, null, false)
        );
        defaults.get(0).setAccountType("Claude Code");
        defaults.get(1).setAccountType("ChatGPT Plus");
        defaults.get(2).setAccountType("Gemini Advanced");
        return defaults;
    }

    @Override
    protected Object[] toColumnValues(GroupItem item) {
        return new Object[]{
                item.getName(),
                item.getDescription(),
                item.getPlatform(),
                item.getAccountType(),
                item.getBillingType(),
                item.getBillingAmount(),
                item.getRate(),
                item.getGroupType(),
                item.getAccountCount(),
                item.getStatus(),
                item.isClaudeCodeLimit() ? 1 : 0,
                item.getFallbackGroup(),
                item.isModelRouting() ? 1 : 0
        };
    }

    @Override
    public synchronized GroupItem save(GroupItem item) {
        if (!hasLegacyUserCountColumn()) {
            return super.save(item);
        }

        final Object[] values = toLegacyColumnValues(item);
        if (item.getId() != null) {
            jdbcTemplate.update(INSERT_WITH_ID_SQL_WITH_LEGACY_USER_COUNT, prepend(item.getId(), values));
            return item;
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(INSERT_SQL_WITH_LEGACY_USER_COUNT, Statement.RETURN_GENERATED_KEYS);
            bind(statement, values);
            return statement;
        }, keyHolder);

        Long key = GeneratedKeySupport.extractId(keyHolder);
        if (key != null) {
            item.setId(key);
        }
        return item;
    }

    @Override
    public synchronized GroupItem update(Long id, GroupItem item) {
        if (!hasLegacyUserCountColumn()) {
            return super.update(id, item);
        }

        item.setId(id);
        jdbcTemplate.update(UPDATE_SQL_WITH_LEGACY_USER_COUNT, append(toLegacyColumnValues(item), id));
        return item;
    }

    private boolean hasLegacyUserCountColumn() {
        Boolean cached = hasLegacyUserCount;
        if (cached != null) {
            return cached;
        }
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "select count(*) from information_schema.columns where lower(table_name) = 'groups' and lower(column_name) = 'user_count'",
                    Integer.class
            );
            boolean exists = count != null && count > 0;
            hasLegacyUserCount = exists;
            return exists;
        } catch (Exception ignored) {
            hasLegacyUserCount = false;
            return false;
        }
    }

    private Object[] toLegacyColumnValues(GroupItem item) {
        Object[] base = toColumnValues(item);
        Object[] values = new Object[COLUMNS_WITH_LEGACY_USER_COUNT.length];
        System.arraycopy(base, 0, values, 0, 8);
        values[8] = base[8];
        values[9] = base[8];
        System.arraycopy(base, 9, values, 10, 4);
        return values;
    }

    private void bind(PreparedStatement statement, Object[] values) throws java.sql.SQLException {
        for (int i = 0; i < values.length; i++) {
            statement.setObject(i + 1, values[i]);
        }
    }

    private Object[] prepend(Object first, Object[] rest) {
        Object[] values = new Object[rest.length + 1];
        values[0] = first;
        System.arraycopy(rest, 0, values, 1, rest.length);
        return values;
    }

    private Object[] append(Object[] values, Object last) {
        Object[] result = new Object[values.length + 1];
        System.arraycopy(values, 0, result, 0, values.length);
        result[values.length] = last;
        return result;
    }
}
