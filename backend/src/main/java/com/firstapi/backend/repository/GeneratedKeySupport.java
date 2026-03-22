package com.firstapi.backend.repository;

import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.support.KeyHolder;

import java.util.Map;

final class GeneratedKeySupport {

    private GeneratedKeySupport() {
    }

    static Long extractId(KeyHolder keyHolder) {
        Map<String, Object> keys = keyHolder.getKeys();
        if (keys != null && !keys.isEmpty()) {
            Object id = keys.get("id");
            if (!(id instanceof Number)) {
                id = keys.get("ID");
            }
            if (!(id instanceof Number)) {
                for (Object value : keys.values()) {
                    if (value instanceof Number) {
                        id = value;
                        break;
                    }
                }
            }
            if (id instanceof Number) {
                return ((Number) id).longValue();
            }
        }

        try {
            Number key = keyHolder.getKey();
            return key != null ? key.longValue() : null;
        } catch (InvalidDataAccessApiUsageException ex) {
            return null;
        }
    }
}
