package com.firstapi.backend.repository;

import com.firstapi.backend.model.SubscriptionPlanItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

@Repository
public class SubscriptionPlanRepository extends JdbcListRepository<SubscriptionPlanItem> {

    private static final RowMapper<SubscriptionPlanItem> ROW_MAPPER = (rs, rowNum) -> {
        BigDecimal mq = rs.getBigDecimal("monthly_quota");
        BigDecimal dl = rs.getBigDecimal("daily_limit");
        return new SubscriptionPlanItem(
                rs.getLong("id"),
                rs.getString("name"),
                mq != null ? mq.toPlainString() : "0",
                dl != null ? dl.toPlainString() : null,
                rs.getString("status")
        );
    };

    public SubscriptionPlanRepository(JdbcTemplate jdbcTemplate) {
        super(
                jdbcTemplate,
                "subscription_plans",
                new String[]{"name", "monthly_quota", "daily_limit", "status"},
                ROW_MAPPER
        );
    }

    @Override
    protected List<SubscriptionPlanItem> defaultItems() {
        return Collections.emptyList();
    }

    @Override
    protected Object[] toColumnValues(SubscriptionPlanItem item) {
        BigDecimal mq;
        try {
            mq = item.getMonthlyQuota() != null ? new BigDecimal(item.getMonthlyQuota().trim()) : BigDecimal.ZERO;
        } catch (NumberFormatException e) {
            mq = BigDecimal.ZERO;
        }

        BigDecimal dl = null;
        if (item.getDailyLimit() != null && !item.getDailyLimit().isBlank()) {
            try {
                dl = new BigDecimal(item.getDailyLimit().trim());
            } catch (NumberFormatException ignored) {
            }
        }

        return new Object[]{
                item.getName(),
                mq,
                dl,
                item.getStatus()
        };
    }

    /**
     * 查询所有状态为"正常"的等级。
     */
    public List<SubscriptionPlanItem> findActiveList() {
        return getJdbcTemplate().query(
                "select `id`, `name`, `monthly_quota`, `daily_limit`, `status` from `subscription_plans` where `status` = '正常' order by `id` asc",
                getRowMapper()
        );
    }
}
