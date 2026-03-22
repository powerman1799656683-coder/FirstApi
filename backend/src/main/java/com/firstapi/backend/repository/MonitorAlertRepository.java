package com.firstapi.backend.repository;

import com.firstapi.backend.model.MonitorAlertItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

@Repository
public class MonitorAlertRepository extends JdbcListRepository<MonitorAlertItem> {

    public MonitorAlertRepository(JdbcTemplate jdbcTemplate) {
        super(
                jdbcTemplate,
                "monitor_alerts",
                new String[]{"time_label", "level_name", "event_text", "status_name",
                        "owner_name", "status_color"},
                (rs, rowNum) -> new MonitorAlertItem(
                        rs.getLong("id"),
                        rs.getString("time_label"),
                        rs.getString("level_name"),
                        rs.getString("event_text"),
                        rs.getString("status_name"),
                        rs.getString("owner_name"),
                        rs.getString("status_color")
                )
        );
    }

    @Override
    protected List<MonitorAlertItem> defaultItems() {
        return Collections.emptyList();
    }

    @Override
    protected Object[] toColumnValues(MonitorAlertItem item) {
        return new Object[]{
                item.getTimeLabel(),
                item.getLevelName(),
                item.getEventText(),
                item.getStatusName(),
                item.getOwnerName(),
                item.getStatusColor()
        };
    }
}
