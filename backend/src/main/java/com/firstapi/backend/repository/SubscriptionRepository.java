package com.firstapi.backend.repository;

import com.firstapi.backend.model.SubscriptionItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
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
        return Arrays.asList(
                new SubscriptionItem(1L, "check123@gmail.com", 22L, "Claude Max20", "$0.00 / Unlimited", 0.0, "2026/12/31", "正常"),
                new SubscriptionItem(2L, "atawubop75@gmail.com", 21L, "Claude Pro", "$12.50 / $100", 12.5, "2026/06/30", "正常"),
                new SubscriptionItem(3L, "wenchy@gmail.com", 18L, "Enterprise Gold", "$100.00 / $100", 100.0, "2026/03/15", "已满额")
        );
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