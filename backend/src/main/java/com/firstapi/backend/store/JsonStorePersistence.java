package com.firstapi.backend.store;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.Collections;
import java.util.List;

@Component
public class JsonStorePersistence {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JsonStorePersistence(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public boolean exists(String key) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from json_store where store_key = ?",
                Integer.class,
                key
        );
        return count != null && count > 0;
    }

    public <T> List<T> readList(String key, Class<T> itemType) {
        String payload = readPayload(key);
        if (payload == null) {
            return Collections.emptyList();
        }
        JavaType javaType = objectMapper.getTypeFactory().constructCollectionType(List.class, itemType);
        return readValue(payload, javaType);
    }

    public void writeList(String key, List<?> value) {
        writePayload(key, writeValue(value));
    }

    public <T> T readObject(String key, Class<T> type) {
        String payload = readPayload(key);
        if (payload == null) {
            return null;
        }
        return readValue(payload, type);
    }

    public void writeObject(String key, Object value) {
        writePayload(key, writeValue(value));
    }

    public void seedListIfMissing(String key, List<?> value) {
        if (!exists(key)) {
            writeList(key, value);
        }
    }

    public void seedObjectIfMissing(String key, Object value) {
        if (!exists(key)) {
            writeObject(key, value);
        }
    }

    private String readPayload(String key) {
        List<String> results = jdbcTemplate.query(
                "select payload from json_store where store_key = ?",
                (rs, rowNum) -> rs.getString("payload"),
                key
        );
        return results.isEmpty() ? null : results.get(0);
    }

    private void writePayload(String key, String payload) {
        if (exists(key)) {
            jdbcTemplate.update(
                    "update json_store set payload = ?, updated_at = current_timestamp where store_key = ?",
                    payload,
                    key
            );
            return;
        }
        jdbcTemplate.update(
                "insert into json_store(store_key, payload, updated_at) values (?, ?, current_timestamp)",
                key,
                payload
        );
    }

    private String writeValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new IllegalStateException("存储数据序列化失败", ex);
        }
    }

    private <T> T readValue(String payload, Class<T> type) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (JacksonException ex) {
            throw new IllegalStateException("存储数据反序列化失败", ex);
        }
    }

    private <T> T readValue(String payload, JavaType type) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (JacksonException ex) {
            throw new IllegalStateException("存储数据反序列化失败", ex);
        }
    }
}
