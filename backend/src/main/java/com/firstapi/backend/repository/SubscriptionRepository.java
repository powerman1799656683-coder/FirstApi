package com.firstapi.backend.repository;

import com.firstapi.backend.model.SubscriptionItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

@Repository
public class SubscriptionRepository extends JdbcListRepository<SubscriptionItem> {

    private static final String ALL_COLS =
            "`id`, `user_name`, `uid_value`, `group_id`, `plan_id`, `group_name`, `usage_text`, `progress_value`, `expiry_label`, `status_name`, `daily_limit`";

    private static final RowMapper<SubscriptionItem> ROW_MAPPER = (rs, rowNum) -> {
        Double progress = rs.getObject("progress_value") == null ? null : rs.getDouble("progress_value");
        BigDecimal dl = rs.getBigDecimal("daily_limit");
        Long groupId = rs.getObject("group_id") != null ? rs.getLong("group_id") : null;
        Long planId = rs.getObject("plan_id") != null ? rs.getLong("plan_id") : null;
        SubscriptionItem item = new SubscriptionItem(
                rs.getLong("id"),
                rs.getString("user_name"),
                rs.getLong("uid_value"),
                rs.getString("group_name"),
                rs.getString("usage_text"),
                progress,
                rs.getString("expiry_label"),
                rs.getString("status_name"),
                dl != null ? dl.toPlainString() : null
        );
        item.setGroupId(groupId);
        item.setPlanId(planId);
        return item;
    };

    public SubscriptionRepository(JdbcTemplate jdbcTemplate) {
        super(
                jdbcTemplate,
                "subscriptions",
                new String[]{"user_name", "uid_value", "group_id", "plan_id", "group_name", "usage_text", "progress_value", "expiry_label", "status_name", "daily_limit"},
                ROW_MAPPER
        );
    }

    /**
     * 查找指定用户的活跃订阅（状态为"正常"）。
     */
    public SubscriptionItem findActiveByUid(Long uid) {
        List<SubscriptionItem> items = getJdbcTemplate().query(
                "select " + ALL_COLS + " from `subscriptions` where `uid_value` = ? and `status_name` = '正常' order by `id` desc limit 1",
                ROW_MAPPER, uid
        );
        return items.isEmpty() ? null : items.get(0);
    }

    /**
     * 按 uid 查找用户的所有活跃订阅。
     */
    public List<SubscriptionItem> findActiveListByUid(Long uid) {
        return getJdbcTemplate().query(
                "select " + ALL_COLS + " from `subscriptions` where `uid_value` = ? and `status_name` = '正常' order by `id` desc",
                ROW_MAPPER, uid
        );
    }

    @Override
    protected List<SubscriptionItem> defaultItems() {
        return Collections.emptyList();
    }

    /**
     * 按 uid + groupId 查找活跃订阅。
     * 精确匹配 group_id，找不到时兜底查 group_id IS NULL（存量兼容）。
     * groupId 为 null 时直接走 findActiveByUid。
     */
    public SubscriptionItem findActiveByUidAndGroup(Long uid, Long groupId) {
        if (groupId == null) {
            return findActiveByUid(uid);
        }
        List<SubscriptionItem> items = getJdbcTemplate().query(
                "select " + ALL_COLS + " from `subscriptions` " +
                "where `uid_value` = ? and `status_name` = '正常' " +
                "and (`group_id` = ? or `group_id` is null) " +
                "order by `group_id` desc, `id` desc limit 1",
                ROW_MAPPER, uid, groupId
        );
        return items.isEmpty() ? null : items.get(0);
    }

    @Override
    protected Object[] toColumnValues(SubscriptionItem item) {
        String dlStr = item.getDailyLimit();
        java.math.BigDecimal dlDecimal = null;
        if (dlStr != null && !dlStr.isBlank()) {
            try {
                dlDecimal = new java.math.BigDecimal(dlStr.trim());
            } catch (NumberFormatException ignored) {
                // 非法格式视为 null，不写入
            }
        }
        return new Object[]{
                item.getUser(),
                item.getUid(),
                item.getGroupId(),
                item.getPlanId(),
                item.getGroup(),
                item.getUsage(),
                item.getProgress(),
                item.getExpiry(),
                item.getStatus(),
                dlDecimal
        };
    }
}
