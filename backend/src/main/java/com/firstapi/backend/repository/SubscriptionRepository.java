package com.firstapi.backend.repository;

import com.firstapi.backend.model.SubscriptionItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

@Repository
public class SubscriptionRepository extends JdbcListRepository<SubscriptionItem> {

    public SubscriptionRepository(JdbcTemplate jdbcTemplate) {
        super(
                jdbcTemplate,
                "subscriptions",
                new String[]{"user_name", "uid_value", "group_name", "usage_text", "progress_value", "expiry_label", "status_name"},
                (rs, rowNum) -> {
                    Double progress = rs.getObject("progress_value") == null ? null : rs.getDouble("progress_value");
                    return new SubscriptionItem(
                            rs.getLong("id"),
                            rs.getString("user_name"),
                            rs.getLong("uid_value"),
                            rs.getString("group_name"),
                            rs.getString("usage_text"),
                            progress,
                            rs.getString("expiry_label"),
                            rs.getString("status_name")
                    );
                }
        );
    }

    @Override
    protected List<SubscriptionItem> defaultItems() {
        return Collections.emptyList();
    }

    @Override
    protected Object[] toColumnValues(SubscriptionItem item) {
        return new Object[]{
                item.getUser(),
                item.getUid(),
                item.getGroup(),
                item.getUsage(),
                item.getProgress(),
                item.getExpiry(),
                item.getStatus()
        };
    }
}