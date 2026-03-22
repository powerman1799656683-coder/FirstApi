package com.firstapi.backend.repository;

import com.firstapi.backend.common.SimpleStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import jakarta.annotation.PostConstruct;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public abstract class JdbcListRepository<T extends SimpleStore.Identifiable> {

    /** 仅允许字母、数字、下划线的标识符（防止 SQL 注入） */
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,63}$");

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<T> rowMapper;
    private final String selectAllSql;
    private final String selectByIdSql;
    private final String insertSql;
    private final String insertWithIdSql;
    private final String updateSql;
    private final String deleteSql;

    protected JdbcListRepository(JdbcTemplate jdbcTemplate, String tableName, String[] columnNames, RowMapper<T> rowMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.rowMapper = rowMapper;

        validateIdentifier(tableName, "表名");
        for (String col : columnNames) {
            validateIdentifier(col, "列名");
        }

        String tableRef = "`" + tableName + "`";
        String columns = joinColumns(columnNames);
        String placeholders = placeholders(columnNames.length);
        String assignments = assignments(columnNames);

        this.selectAllSql = "select `id`, " + columns + " from " + tableRef + " order by `id` desc";
        this.selectByIdSql = "select `id`, " + columns + " from " + tableRef + " where `id` = ?";
        this.insertSql = "insert into " + tableRef + " (" + columns + ") values (" + placeholders + ")";
        this.insertWithIdSql = "insert into " + tableRef + " (`id`, " + columns + ") values (? , " + placeholders + ")";
        this.updateSql = "update " + tableRef + " set " + assignments + " where `id` = ?";
        this.deleteSql = "delete from " + tableRef + " where `id` = ?";
    }

    @PostConstruct
    public void init() {
        if (findAll().isEmpty()) {
            for (T item : defaultItems()) {
                save(item);
            }
        }
    }

    public synchronized List<T> findAll() {
        return new ArrayList<T>(jdbcTemplate.query(selectAllSql, rowMapper));
    }

    public synchronized T findById(Long id) {
        List<T> items = jdbcTemplate.query(selectByIdSql, rowMapper, id);
        return items.isEmpty() ? null : items.get(0);
    }

    public synchronized T save(T item) {
        final Object[] values = toColumnValues(item);
        if (item.getId() != null) {
            jdbcTemplate.update(insertWithIdSql, prepend(item.getId(), values));
            return item;
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
            bind(statement, values);
            return statement;
        }, keyHolder);

        Long key = GeneratedKeySupport.extractId(keyHolder);
        if (key != null) {
            item.setId(key);
        }
        return item;
    }

    public synchronized T update(Long id, T item) {
        item.setId(id);
        jdbcTemplate.update(updateSql, append(toColumnValues(item), id));
        return item;
    }

    public synchronized void deleteById(Long id) {
        jdbcTemplate.update(deleteSql, id);
    }

    protected JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }

    protected RowMapper<T> getRowMapper() {
        return rowMapper;
    }

    protected abstract List<T> defaultItems();

    protected abstract Object[] toColumnValues(T item);

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

    private String joinColumns(String[] columnNames) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < columnNames.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append("`").append(columnNames[i]).append("`");
        }
        return builder.toString();
    }

    private String placeholders(int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append("?");
        }
        return builder.toString();
    }

    private String assignments(String[] columnNames) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < columnNames.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append("`").append(columnNames[i]).append("` = ?");
        }
        return builder.toString();
    }

    private static void validateIdentifier(String name, String label) {
        if (name == null || !SAFE_IDENTIFIER.matcher(name).matches()) {
            throw new IllegalArgumentException("不安全的" + label + ": " + name);
        }
    }
}
