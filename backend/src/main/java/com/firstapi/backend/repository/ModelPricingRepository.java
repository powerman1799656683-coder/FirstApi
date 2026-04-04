package com.firstapi.backend.repository;

import com.firstapi.backend.model.ModelPricingItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

@Repository
public class ModelPricingRepository extends JdbcListRepository<ModelPricingItem> {

    private final JdbcTemplate jdbcTemplate;

    public ModelPricingRepository(JdbcTemplate jdbcTemplate) {
        super(
                jdbcTemplate,
                "model_pricing",
                new String[]{
                        "model_name",
                        "match_type",
                        "provider",
                        "input_price",
                        "output_price",
                        "currency",
                        "enabled",
                        "effective_from",
                        "created_at",
                        "updated_at"
                },
                (rs, rowNum) -> {
                    ModelPricingItem item = new ModelPricingItem();
                    item.setId(rs.getLong("id"));
                    item.setModelName(rs.getString("model_name"));
                    item.setMatchType(rs.getString("match_type"));
                    item.setProvider(rs.getString("provider"));
                    item.setInputPrice(rs.getBigDecimal("input_price"));
                    item.setOutputPrice(rs.getBigDecimal("output_price"));
                    item.setCurrency(rs.getString("currency"));
                    item.setEnabled(rs.getBoolean("enabled"));
                    item.setEffectiveFrom(rs.getObject("effective_from", java.time.LocalDateTime.class));
                    item.setCreatedAt(rs.getObject("created_at", java.time.LocalDateTime.class));
                    item.setUpdatedAt(rs.getObject("updated_at", java.time.LocalDateTime.class));
                    return item;
                }
        );
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    protected List<ModelPricingItem> defaultItems() {
        return Collections.emptyList();
    }

    @Override
    protected Object[] toColumnValues(ModelPricingItem item) {
        return new Object[]{
                item.getModelName(),
                item.getMatchType(),
                item.getProvider(),
                item.getInputPrice(),
                item.getOutputPrice(),
                item.getCurrency(),
                item.getEnabled(),
                item.getEffectiveFrom(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        };
    }

    /**
     * 查询所有启用且已生效的定价规则（effective_from <= now）
     */
    public List<ModelPricingItem> findAllEnabledEffective() {
        String sql = "select `id`, `model_name`, `match_type`, `provider`, `input_price`, `output_price`, `currency`, `enabled`, `effective_from`, `created_at`, `updated_at` " +
                "from `model_pricing` where `enabled` = 1 and `effective_from` <= ?";
        java.time.LocalDateTime nowCst = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai"));
        return jdbcTemplate.query(sql, getRowMapper(), nowCst);
    }

    /**
     * 查询全部（含未生效、已禁用），用于管理员列表页
     */
    public List<ModelPricingItem> findAllForAdmin(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return findAll();
        }
        String sql = "select `id`, `model_name`, `match_type`, `provider`, `input_price`, `output_price`, `currency`, `enabled`, `effective_from`, `created_at`, `updated_at` " +
                "from `model_pricing` where `model_name` like ? order by `id` desc";
        return jdbcTemplate.query(sql, getRowMapper(), "%" + keyword.trim() + "%");
    }

    /**
     * 查询是否已存在启用的 default 兜底规则（排除指定 id）
     */
    public boolean existsEnabledDefault(Long excludeId) {
        String sql = "select count(*) from `model_pricing` where `match_type` = 'default' and `enabled` = 1"
                + (excludeId != null ? " and `id` != ?" : "");
        Integer count = excludeId != null
                ? jdbcTemplate.queryForObject(sql, Integer.class, excludeId)
                : jdbcTemplate.queryForObject(sql, Integer.class);
        return count != null && count > 0;
    }
}
