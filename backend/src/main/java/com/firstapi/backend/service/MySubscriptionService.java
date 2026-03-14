package com.firstapi.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firstapi.backend.common.CurrentSessionHolder;
import com.firstapi.backend.model.MySubscriptionData;
import com.firstapi.backend.model.MySubscriptionData.HistoryItem;
import com.firstapi.backend.model.MySubscriptionData.Plan;
import com.firstapi.backend.model.MySubscriptionData.RequestStats;
import com.firstapi.backend.model.MySubscriptionData.UsageItem;
import com.firstapi.backend.util.TimeSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class MySubscriptionService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MySubscriptionService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public synchronized MySubscriptionData getSubscription() {
        Long ownerId = CurrentSessionHolder.require().getId();
        List<MySubscriptionData> result = jdbcTemplate.query(
                "select `plan_name`, `renewal_date`, `features_json`, `usage_json`, `request_stats_json`, `history_json` from `my_subscription` where `id` = ?",
                (rs, rowNum) -> {
                    MySubscriptionData data = new MySubscriptionData();
                    data.plan = new Plan(rs.getString("plan_name"), rs.getString("renewal_date"));
                    data.features = readList(rs.getString("features_json"), String.class);
                    data.usage = readList(rs.getString("usage_json"), UsageItem.class);
                    data.requestStats = readObject(rs.getString("request_stats_json"), RequestStats.class);
                    data.history = readList(rs.getString("history_json"), HistoryItem.class);
                    return data;
                },
                ownerId
        );

        if (result.isEmpty()) {
            MySubscriptionData data = defaultData();
            save(ownerId, data);
            return data;
        }
        return result.get(0);
    }

    public synchronized MySubscriptionData renew() {
        Long ownerId = CurrentSessionHolder.require().getId();
        MySubscriptionData data = getSubscription();
        String currentRenewDate = data.plan == null ? null : data.plan.renewalDate;
        String nextRenewDate = TimeSupport.plusMonths(currentRenewDate, 1);
        if (data.plan == null) {
            data.plan = new Plan("Professional", nextRenewDate);
        } else {
            data.plan.renewalDate = nextRenewDate;
        }
        data.history.add(0, new HistoryItem(TimeSupport.nowDateTime().substring(0, 10), "Renew " + data.plan.name + " plan", "$29.00", "Completed"));
        save(ownerId, data);
        return data;
    }

    private void save(Long ownerId, MySubscriptionData data) {
        jdbcTemplate.update(
                "insert into `my_subscription` (`id`, `plan_name`, `renewal_date`, `features_json`, `usage_json`, `request_stats_json`, `history_json`) values (?, ?, ?, cast(? as json), cast(? as json), cast(? as json), cast(? as json)) on duplicate key update `plan_name` = values(`plan_name`), `renewal_date` = values(`renewal_date`), `features_json` = values(`features_json`), `usage_json` = values(`usage_json`), `request_stats_json` = values(`request_stats_json`), `history_json` = values(`history_json`)",
                ownerId,
                data.plan == null ? "Professional" : data.plan.name,
                data.plan == null ? TimeSupport.plusMonths(null, 1) : data.plan.renewalDate,
                writeJson(data.features),
                writeJson(data.usage),
                writeJson(data.requestStats),
                writeJson(data.history)
        );
    }

    private MySubscriptionData defaultData() {
        MySubscriptionData data = new MySubscriptionData();
        data.plan = new Plan("Professional", "2026/04/12");
        data.features = Arrays.asList(
                "Priority access to all new models",
                "Faster response time with a 99.9% SLA",
                "Cross-device key management",
                "No concurrency cap for API access"
        );
        data.usage = Arrays.asList(
                new UsageItem("GPT-4o Tokens", "84,520", "100,000", 84, "linear-gradient(90deg, #3b82f6, #00f2ff)", "0 0 10px rgba(0, 242, 255, 0.2)"),
                new UsageItem("Claude 3.5 Sonnet", "12,800", "50,000", 25, "linear-gradient(90deg, #10b981, #34d399)", "none")
        );
        data.requestStats = new RequestStats("420", "0.4s");
        data.history = new ArrayList<HistoryItem>(Arrays.asList(
                new HistoryItem("2026/03/12", "Renew Professional plan", "$29.00", "Completed"),
                new HistoryItem("2026/02/12", "Renew Professional plan", "$29.00", "Completed"),
                new HistoryItem("2026/01/12", "Started Professional plan", "$29.00", "Completed")
        ));
        return data;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize subscription document", ex);
        }
    }

    private <T> T readObject(String json, Class<T> type) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to deserialize subscription object", ex);
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
            throw new IllegalStateException("Failed to deserialize subscription list", ex);
        }
    }
}
