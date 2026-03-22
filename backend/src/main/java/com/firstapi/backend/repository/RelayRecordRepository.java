package com.firstapi.backend.repository;

import com.firstapi.backend.model.RelayRecordItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

@Repository
public class RelayRecordRepository extends JdbcListRepository<RelayRecordItem> {

    private final JdbcTemplate jdbcTemplate;

    public RelayRecordRepository(JdbcTemplate jdbcTemplate) {
        super(
                jdbcTemplate,
                "relay_records",
                new String[]{
                        "owner_id",
                        "api_key_id",
                        "provider_name",
                        "account_id",
                        "model_name",
                        "request_id",
                        "success",
                        "status_code",
                        "error_text",
                        "latency_ms",
                        "prompt_tokens",
                        "completion_tokens",
                        "total_tokens",
                        "input_price",
                        "output_price",
                        "pricing_currency",
                        "group_ratio",
                        "pricing_rule_id",
                        "pricing_rule_name",
                        "pricing_status",
                        "pricing_found",
                        "cost",
                        "usage_json",
                        "created_at",
                        "created_at_ts"
                },
                (rs, rowNum) -> {
                    RelayRecordItem item = new RelayRecordItem();
                    item.setId(rs.getLong("id"));
                    item.setOwnerId(rs.getLong("owner_id"));
                    item.setApiKeyId(rs.getLong("api_key_id"));
                    item.setProvider(rs.getString("provider_name"));
                    item.setAccountId(rs.getLong("account_id"));
                    item.setModel(rs.getString("model_name"));
                    item.setRequestId(rs.getString("request_id"));
                    item.setSuccess(rs.getBoolean("success"));
                    item.setStatusCode(rs.getInt("status_code"));
                    item.setErrorText(rs.getString("error_text"));
                    item.setLatencyMs(rs.getLong("latency_ms"));
                    item.setPromptTokens((Integer) rs.getObject("prompt_tokens"));
                    item.setCompletionTokens((Integer) rs.getObject("completion_tokens"));
                    item.setTotalTokens((Integer) rs.getObject("total_tokens"));
                    item.setInputPrice(rs.getBigDecimal("input_price"));
                    item.setOutputPrice(rs.getBigDecimal("output_price"));
                    item.setPricingCurrency(rs.getString("pricing_currency"));
                    item.setGroupRatio(rs.getBigDecimal("group_ratio"));
                    item.setPricingRuleId((Long) rs.getObject("pricing_rule_id"));
                    item.setPricingRuleName(rs.getString("pricing_rule_name"));
                    item.setPricingStatus(rs.getString("pricing_status"));
                    item.setPricingFound(readNullableBoolean(rs.getObject("pricing_found")));
                    item.setCost(rs.getBigDecimal("cost"));
                    item.setUsageJson(rs.getString("usage_json"));
                    item.setCreatedAt(rs.getString("created_at"));
                    item.setCreatedAtTs(rs.getObject("created_at_ts", java.time.LocalDateTime.class));
                    return item;
                }
        );
        this.jdbcTemplate = jdbcTemplate;
    }

    private static Boolean readNullableBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value instanceof String text) {
            String normalized = text.trim();
            if (normalized.isEmpty()) {
                return null;
            }
            return !"0".equals(normalized) && !"false".equalsIgnoreCase(normalized);
        }
        throw new IllegalArgumentException("Unsupported pricing_found value type: " + value.getClass().getName());
    }

    @Override
    protected List<RelayRecordItem> defaultItems() {
        return Collections.emptyList();
    }

    @Override
    protected Object[] toColumnValues(RelayRecordItem item) {
        return new Object[]{
                item.getOwnerId(),
                item.getApiKeyId(),
                item.getProvider(),
                item.getAccountId(),
                item.getModel(),
                item.getRequestId(),
                item.getSuccess(),
                item.getStatusCode(),
                item.getErrorText(),
                item.getLatencyMs(),
                item.getPromptTokens(),
                item.getCompletionTokens(),
                item.getTotalTokens(),
                item.getInputPrice(),
                item.getOutputPrice(),
                item.getPricingCurrency(),
                item.getGroupRatio(),
                item.getPricingRuleId(),
                item.getPricingRuleName(),
                item.getPricingStatus(),
                item.getPricingFound(),
                item.getCost(),
                item.getUsageJson(),
                item.getCreatedAt(),
                item.getCreatedAtTs()
        };
    }

    // -------- 聚合查询 --------

    public BigDecimal sumCost() {
        BigDecimal result = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(cost), 0) FROM relay_records", BigDecimal.class);
        return result != null ? result : BigDecimal.ZERO;
    }

    public long sumTotalTokens() {
        Long result = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(total_tokens), 0) FROM relay_records", Long.class);
        return result != null ? result : 0L;
    }

    public long countDistinctApiKeys() {
        Long result = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT api_key_id) FROM relay_records", Long.class);
        return result != null ? result : 0L;
    }

    public double avgLatencyMs() {
        Double result = jdbcTemplate.queryForObject(
                "SELECT COALESCE(AVG(latency_ms), 0) FROM relay_records", Double.class);
        return result != null ? result : 0.0;
    }

    public List<ModelStat> groupByModel() {
        String sql = "SELECT model_name, COUNT(*) AS call_count, " +
                "COALESCE(SUM(total_tokens), 0) AS total_tokens, " +
                "COALESCE(SUM(cost), 0) AS total_cost " +
                "FROM relay_records GROUP BY model_name ORDER BY call_count DESC";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new ModelStat(
                rs.getString("model_name"),
                rs.getLong("call_count"),
                rs.getLong("total_tokens"),
                rs.getBigDecimal("total_cost")
        ));
    }

    public List<DayStat> groupByDate(int days) {
        String sql = "SELECT DATE(created_at_ts) AS stat_date, COUNT(*) AS call_count, " +
                "COALESCE(SUM(total_tokens), 0) AS total_tokens, " +
                "COALESCE(SUM(cost), 0) AS total_cost " +
                "FROM relay_records WHERE created_at_ts >= DATE_SUB(NOW(), INTERVAL ? DAY) " +
                "GROUP BY DATE(created_at_ts) ORDER BY stat_date ASC";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new DayStat(
                rs.getString("stat_date"),
                rs.getLong("call_count"),
                rs.getLong("total_tokens"),
                rs.getBigDecimal("total_cost")
        ), days);
    }

    public long countByPricingStatus(String status) {
        Long result = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM relay_records WHERE pricing_status = ?", Long.class, status);
        return result != null ? result : 0L;
    }

    public BigDecimal sumCostByOwner(Long ownerId) {
        BigDecimal result = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(cost), 0) FROM relay_records WHERE owner_id = ?", BigDecimal.class, ownerId);
        return result != null ? result : BigDecimal.ZERO;
    }

    public long sumTotalTokensByOwner(Long ownerId) {
        Long result = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(total_tokens), 0) FROM relay_records WHERE owner_id = ?", Long.class, ownerId);
        return result != null ? result : 0L;
    }

    public double avgLatencyMsByOwner(Long ownerId) {
        Double result = jdbcTemplate.queryForObject(
                "SELECT COALESCE(AVG(latency_ms), 0) FROM relay_records WHERE owner_id = ?", Double.class, ownerId);
        return result != null ? result : 0.0;
    }

    public List<RelayRecordItem> findByOwnerId(Long ownerId, String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return jdbcTemplate.query(
                    "SELECT `id`, `owner_id`, `api_key_id`, `provider_name`, `account_id`, `model_name`, `request_id`, " +
                    "`success`, `status_code`, `error_text`, `latency_ms`, `prompt_tokens`, `completion_tokens`, `total_tokens`, " +
                    "`input_price`, `output_price`, `pricing_currency`, `group_ratio`, `pricing_rule_id`, `pricing_rule_name`, " +
                    "`pricing_status`, `pricing_found`, `cost`, `usage_json`, `created_at`, `created_at_ts` " +
                    "FROM relay_records WHERE owner_id = ? ORDER BY id DESC LIMIT 200",
                    getRowMapper(), ownerId);
        }
        String like = "%" + keyword.trim() + "%";
        return jdbcTemplate.query(
                "SELECT `id`, `owner_id`, `api_key_id`, `provider_name`, `account_id`, `model_name`, `request_id`, " +
                "`success`, `status_code`, `error_text`, `latency_ms`, `prompt_tokens`, `completion_tokens`, `total_tokens`, " +
                "`input_price`, `output_price`, `pricing_currency`, `group_ratio`, `pricing_rule_id`, `pricing_rule_name`, " +
                "`pricing_status`, `pricing_found`, `cost`, `usage_json`, `created_at`, `created_at_ts` " +
                "FROM relay_records WHERE owner_id = ? AND model_name LIKE ? ORDER BY id DESC LIMIT 200",
                getRowMapper(), ownerId, like);
    }

    public List<RelayRecordItem> findAll(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return jdbcTemplate.query(
                    "SELECT `id`, `owner_id`, `api_key_id`, `provider_name`, `account_id`, `model_name`, `request_id`, " +
                    "`success`, `status_code`, `error_text`, `latency_ms`, `prompt_tokens`, `completion_tokens`, `total_tokens`, " +
                    "`input_price`, `output_price`, `pricing_currency`, `group_ratio`, `pricing_rule_id`, `pricing_rule_name`, " +
                    "`pricing_status`, `pricing_found`, `cost`, `usage_json`, `created_at`, `created_at_ts` " +
                    "FROM relay_records ORDER BY id DESC LIMIT 500",
                    getRowMapper());
        }
        String like = "%" + keyword.trim() + "%";
        return jdbcTemplate.query(
                "SELECT `id`, `owner_id`, `api_key_id`, `provider_name`, `account_id`, `model_name`, `request_id`, " +
                "`success`, `status_code`, `error_text`, `latency_ms`, `prompt_tokens`, `completion_tokens`, `total_tokens`, " +
                "`input_price`, `output_price`, `pricing_currency`, `group_ratio`, `pricing_rule_id`, `pricing_rule_name`, " +
                "`pricing_status`, `pricing_found`, `cost`, `usage_json`, `created_at`, `created_at_ts` " +
                "FROM relay_records WHERE model_name LIKE ? ORDER BY id DESC LIMIT 500",
                getRowMapper(), like);
    }

    public List<String> distinctModels() {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT model_name FROM relay_records WHERE model_name IS NOT NULL ORDER BY model_name", String.class);
    }

    public record ModelStat(String modelName, long callCount, long totalTokens, BigDecimal totalCost) {}
    public record DayStat(String date, long callCount, long totalTokens, BigDecimal totalCost) {}
}
