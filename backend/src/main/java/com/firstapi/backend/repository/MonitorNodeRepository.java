package com.firstapi.backend.repository;

import com.firstapi.backend.model.MonitorNodeItem;
import com.firstapi.backend.util.TimeSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;

@Repository
public class MonitorNodeRepository extends JdbcListRepository<MonitorNodeItem> {

    public MonitorNodeRepository(JdbcTemplate jdbcTemplate) {
        super(
                jdbcTemplate,
                "monitor_nodes",
                new String[]{"node_name", "check_url", "location_name", "status_name",
                        "latency_value", "uptime_value", "total_checks", "success_checks", "last_check_label"},
                (rs, rowNum) -> new MonitorNodeItem(
                        rs.getLong("id"),
                        rs.getString("node_name"),
                        rs.getString("check_url"),
                        rs.getString("location_name"),
                        rs.getString("status_name"),
                        rs.getString("latency_value"),
                        rs.getString("uptime_value"),
                        rs.getInt("total_checks"),
                        rs.getInt("success_checks"),
                        rs.getString("last_check_label")
                )
        );
    }

    @Override
    protected List<MonitorNodeItem> defaultItems() {
        String now = TimeSupport.nowDateTime();
        return Arrays.asList(
                new MonitorNodeItem(null, "香港 AWS-1", "https://www.baidu.com", "香港",
                        "未检测", "0ms", "0.00%", 0, 0, now),
                new MonitorNodeItem(null, "美国 Google-2", "https://www.google.com", "美国",
                        "未检测", "0ms", "0.00%", 0, 0, now),
                new MonitorNodeItem(null, "日本 Linode-3", "https://www.yahoo.co.jp", "日本",
                        "未检测", "0ms", "0.00%", 0, 0, now),
                new MonitorNodeItem(null, "新加坡 Oracle-4", "https://www.example.com", "新加坡",
                        "未检测", "0ms", "0.00%", 0, 0, now)
        );
    }

    @Override
    protected Object[] toColumnValues(MonitorNodeItem item) {
        return new Object[]{
                item.getNodeName(),
                item.getCheckUrl(),
                item.getLocationName(),
                item.getStatusName(),
                item.getLatencyValue(),
                item.getUptimeValue(),
                item.getTotalChecks(),
                item.getSuccessChecks(),
                item.getLastCheckLabel()
        };
    }
}
