package com.firstapi.backend.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import com.firstapi.backend.common.CurrentSessionHolder;
import com.firstapi.backend.model.MySubscriptionData;
import com.firstapi.backend.model.MySubscriptionData.HistoryItem;
import com.firstapi.backend.model.MySubscriptionData.Plan;
import com.firstapi.backend.model.MySubscriptionData.RequestStats;
import com.firstapi.backend.model.MySubscriptionData.UsageItem;
import com.firstapi.backend.util.TimeSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
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
        return getSubscriptionForUser(ownerId);
    }

    private MySubscriptionData getSubscriptionForUser(Long ownerId) {
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
        return doRenew(ownerId);
    }

    public synchronized MySubscriptionData renewForUser(Long userId) {
        return doRenew(userId);
    }

    private MySubscriptionData doRenew(Long ownerId) {
        MySubscriptionData data = getSubscriptionForUser(ownerId);
        if (data.plan == null) {
            throw new IllegalStateException("当前没有有效套餐，无法续费");
        }
        String nextRenewDate = TimeSupport.plusMonths(data.plan.renewalDate, 1);
        data.plan.renewalDate = nextRenewDate;
        data.history.add(0, new HistoryItem(TimeSupport.nowDateTime().substring(0, 10), "续费 " + data.plan.name, "-", "已完成"));
        save(ownerId, data);
        return data;
    }

    private void save(Long ownerId, MySubscriptionData data) {
        jdbcTemplate.update(
                "insert into `my_subscription` (`id`, `plan_name`, `renewal_date`, `features_json`, `usage_json`, `request_stats_json`, `history_json`) values (?, ?, ?, cast(? as json), cast(? as json), cast(? as json), cast(? as json)) on duplicate key update `plan_name` = values(`plan_name`), `renewal_date` = values(`renewal_date`), `features_json` = values(`features_json`), `usage_json` = values(`usage_json`), `request_stats_json` = values(`request_stats_json`), `history_json` = values(`history_json`)",
                ownerId,
                data.plan == null ? "专业版" : data.plan.name,
                data.plan == null ? TimeSupport.plusMonths(null, 1) : data.plan.renewalDate,
                writeJson(data.features),
                writeJson(data.usage),
                writeJson(data.requestStats),
                writeJson(data.history)
        );
    }

    private MySubscriptionData defaultData() {
        MySubscriptionData data = new MySubscriptionData();
        data.plan = null;
        data.features = new ArrayList<>();
        data.usage = new ArrayList<>();
        data.requestStats = null;
        data.history = new ArrayList<>();
        return data;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new IllegalStateException("订阅数据序列化失败", ex);
        }
    }

    private <T> T readObject(String json, Class<T> type) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JacksonException ex) {
            throw new IllegalStateException("订阅对象反序列化失败", ex);
        }
    }

    private <T> List<T> readList(String json, Class<T> itemType) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<T>();
        }
        JavaType type = objectMapper.getTypeFactory().constructCollectionType(List.class, itemType);
        try {
            return new ArrayList<T>(objectMapper.readValue(json, type));
        } catch (JacksonException ex) {
            throw new IllegalStateException("订阅列表反序列化失败", ex);
        }
    }
}
