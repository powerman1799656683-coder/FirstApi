package com.firstapi.backend.repository;

import com.firstapi.backend.model.GroupItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;

@Repository
public class GroupRepository extends JdbcListRepository<GroupItem> {

    public GroupRepository(JdbcTemplate jdbcTemplate) {
        super(
                jdbcTemplate,
                "groups",
                new String[]{"name", "billing_type", "user_count", "status_name", "priority_value", "rate_value"},
                (rs, rowNum) -> new GroupItem(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("billing_type"),
                        rs.getString("user_count"),
                        rs.getString("status_name"),
                        rs.getString("priority_value"),
                        rs.getString("rate_value")
                )
        );
    }

    @Override
    protected List<GroupItem> defaultItems() {
        return Arrays.asList(
                new GroupItem(1L, "Default", "按量计费", "245", "激活", "10", "1.0x"),
                new GroupItem(2L, "VIP", "包月", "118", "激活", "20", "0.8x"),
                new GroupItem(3L, "Enterprise", "专属", "18", "激活", "30", "0.6x")
        );
    }

    @Override
    protected Object[] toColumnValues(GroupItem item) {
        return new Object[]{
                item.getName(),
                item.getBillingType(),
                item.getUserCount(),
                item.getStatus(),
                item.getPriority(),
                item.getRate()
        };
    }
}