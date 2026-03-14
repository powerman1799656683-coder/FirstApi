package com.firstapi.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firstapi.backend.common.CurrentSessionHolder;
import com.firstapi.backend.model.MyRedemptionData;
import com.firstapi.backend.model.MyRedemptionData.HistoryItem;
import com.firstapi.backend.model.MyRedemptionData.RedeemRequest;
import com.firstapi.backend.util.TimeSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class MyRedemptionService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MyRedemptionService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public synchronized MyRedemptionData getRedemptions() {
        Long ownerId = CurrentSessionHolder.require().getId();
        List<MyRedemptionData> result = jdbcTemplate.query(
                "select `title`, `description`, `history_json` from `my_redemption` where `id` = ?",
                (rs, rowNum) -> {
                    MyRedemptionData data = new MyRedemptionData();
                    data.title = rs.getString("title");
                    data.description = rs.getString("description");
                    data.history = readList(rs.getString("history_json"), HistoryItem.class);
                    return data;
                },
                ownerId
        );

        if (result.isEmpty()) {
            MyRedemptionData data = defaultData();
            save(ownerId, data);
            return data;
        }
        return result.get(0);
    }

    public synchronized MyRedemptionData redeem(RedeemRequest request) {
        Long ownerId = CurrentSessionHolder.require().getId();
        MyRedemptionData data = getRedemptions();
        if (request.code != null && !request.code.trim().isEmpty()) {
            String code = request.code.toUpperCase();
            String type = guessType(code);
            String value = guessValue(code);
            data.history.add(0, new HistoryItem(code, type, value, TimeSupport.nowDateTime(), "Applied"));
            save(ownerId, data);
        }
        return data;
    }

    private void save(Long ownerId, MyRedemptionData data) {
        jdbcTemplate.update(
                "insert into `my_redemption` (`id`, `title`, `description`, `history_json`) values (?, ?, ?, cast(? as json)) on duplicate key update `title` = values(`title`), `description` = values(`description`), `history_json` = values(`history_json`)",
                ownerId,
                data.title,
                data.description,
                writeJson(data.history)
        );
    }

    private MyRedemptionData defaultData() {
        MyRedemptionData data = new MyRedemptionData();
        data.title = "Redeem Gift Codes";
        data.description = "Apply a gift code to add balance or subscription time instantly.";
        data.history = new ArrayList<HistoryItem>(Arrays.asList(
                new HistoryItem("SPRING_SALE_2026", "Balance", "$50.00", "2026/03/10 14:20:00", "Applied"),
                new HistoryItem("BETA_TESTER_VIP", "Membership", "30 days", "2026/02/15 10:11:00", "Applied"),
                new HistoryItem("WELCOME_BONUS", "Starter Credit", "$5.00", "2026/01/10 09:45:00", "Applied")
        ));
        return data;
    }

    private String guessType(String code) {
        return code.contains("VIP") ? "Membership" : "Balance";
    }

    private String guessValue(String code) {
        return code.contains("VIP") ? "30 days" : "$10.00";
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize redemption history", ex);
        }
    }

    private <T> List<T> readList(String json, Class<T> itemType) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<T>();
        }
        JavaType type = objectMapper.getTypeFactory().constructCollectionType(List.class, itemType);
        try {
            return new ArrayList<T>(objectMapper.readValue(json, type));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to deserialize redemption history", ex);
        }
    }
}
