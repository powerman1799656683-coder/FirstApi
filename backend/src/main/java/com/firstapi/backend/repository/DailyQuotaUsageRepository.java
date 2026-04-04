package com.firstapi.backend.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Repository
public class DailyQuotaUsageRepository {

    private final JdbcTemplate jdbcTemplate;

    public DailyQuotaUsageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 确保当日记录存在（INSERT IGNORE）。
     */
    public void ensureRow(Long ownerId, Long groupId, LocalDate date) {
        jdbcTemplate.update(
                "insert ignore into daily_quota_usage (owner_id, group_id, quota_date, used_cost) values (?, ?, ?, 0)",
                ownerId, groupId, Date.valueOf(date));
    }

    /**
     * 查询当日已用额度。
     */
    public BigDecimal getUsedCost(Long ownerId, Long groupId, LocalDate date) {
        ensureRow(ownerId, groupId, date);
        BigDecimal result = jdbcTemplate.queryForObject(
                "select used_cost from daily_quota_usage where owner_id = ? and group_id = ? and quota_date = ?",
                BigDecimal.class, ownerId, groupId, Date.valueOf(date));
        return result != null ? result : BigDecimal.ZERO;
    }

    /**
     * 累加成本到当日记录。
     */
    public void addCost(Long ownerId, Long groupId, LocalDate date, BigDecimal cost) {
        ensureRow(ownerId, groupId, date);
        jdbcTemplate.update(
                "update daily_quota_usage set used_cost = used_cost + ? where owner_id = ? and group_id = ? and quota_date = ?",
                cost, ownerId, groupId, Date.valueOf(date));
    }

    /**
     * 查询指定用户在指定日期所有分组的用量记录。
     * 返回 List of Map，每个 Map 包含 group_id 和 used_cost。
     */
    public List<Map<String, Object>> findByOwnerAndDate(Long ownerId, LocalDate date) {
        return jdbcTemplate.queryForList(
                "select group_id, used_cost from daily_quota_usage where owner_id = ? and quota_date = ?",
                ownerId, Date.valueOf(date));
    }

    /**
     * 查询指定用户在指定日期的总消费（所有分组汇总）。
     */
    public BigDecimal getTotalUsedCost(Long ownerId, LocalDate date) {
        BigDecimal result = jdbcTemplate.queryForObject(
                "select coalesce(sum(used_cost), 0) from daily_quota_usage where owner_id = ? and quota_date = ?",
                BigDecimal.class, ownerId, Date.valueOf(date));
        return result != null ? result : BigDecimal.ZERO;
    }

    /**
     * 累加成本到当日记录（使用 group_id=0 表示用户级别汇总）。
     */
    public void addCostByOwner(Long ownerId, LocalDate date, BigDecimal cost) {
        ensureRow(ownerId, 0L, date);
        jdbcTemplate.update(
                "update daily_quota_usage set used_cost = used_cost + ? where owner_id = ? and group_id = 0 and quota_date = ?",
                cost, ownerId, Date.valueOf(date));
    }
}
