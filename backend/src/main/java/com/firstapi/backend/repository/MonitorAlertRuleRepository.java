package com.firstapi.backend.repository;

import com.firstapi.backend.model.MonitorAlertRuleItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MonitorAlertRuleRepository extends JdbcListRepository<MonitorAlertRuleItem> {

    public MonitorAlertRuleRepository(JdbcTemplate jdbcTemplate) {
        super(
                jdbcTemplate,
                "monitor_alert_rules",
                new String[]{"rule_name", "metric_key", "operator", "threshold_value",
                        "level_name", "enabled", "description"},
                (rs, rowNum) -> new MonitorAlertRuleItem(
                        rs.getLong("id"),
                        rs.getString("rule_name"),
                        rs.getString("metric_key"),
                        rs.getString("operator"),
                        rs.getDouble("threshold_value"),
                        rs.getString("level_name"),
                        rs.getBoolean("enabled"),
                        rs.getString("description")
                )
        );
    }

    @Override
    protected List<MonitorAlertRuleItem> defaultItems() {
        return List.of(
                new MonitorAlertRuleItem(null, "错误率过高", "errorRate", ">", 5.0, "WARNING", true, "请求错误率超过5%时告警"),
                new MonitorAlertRuleItem(null, "错误率严重", "errorRate", ">", 10.0, "CRITICAL", true, "请求错误率超过10%时严重告警"),
                new MonitorAlertRuleItem(null, "P99延迟过高", "latencyP99", ">", 10000.0, "WARNING", true, "P99延迟超过10秒时告警"),
                new MonitorAlertRuleItem(null, "SLA低于阈值", "slaPercentage", "<", 99.0, "WARNING", true, "SLA低于99%时告警")
        );
    }

    @Override
    protected Object[] toColumnValues(MonitorAlertRuleItem item) {
        return new Object[]{
                item.getRuleName(),
                item.getMetricKey(),
                item.getOperator(),
                item.getThresholdValue(),
                item.getLevelName(),
                item.getEnabled(),
                item.getDescription()
        };
    }
}
