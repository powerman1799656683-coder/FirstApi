package com.firstapi.backend.repository;

import com.firstapi.backend.model.PromoItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;

@Repository
public class PromoRepository extends JdbcListRepository<PromoItem> {

    public PromoRepository(JdbcTemplate jdbcTemplate) {
        super(
                jdbcTemplate,
                "promos",
                new String[]{"code", "type_name", "value_text", "usage_text", "expiry_label", "status_name"},
                (rs, rowNum) -> new PromoItem(
                        rs.getLong("id"),
                        rs.getString("code"),
                        rs.getString("type_name"),
                        rs.getString("value_text"),
                        rs.getString("usage_text"),
                        rs.getString("expiry_label"),
                        rs.getString("status_name")
                )
        );
    }

    @Override
    protected List<PromoItem> defaultItems() {
        return Arrays.asList(
                new PromoItem(1L, "WELCOME5", "注册奖励", "$5.00", "12 / 100", "2026/12/31", "进行中"),
                new PromoItem(2L, "MARCHVIP", "续费折扣", "$9.90", "6 / 50", "2026/03/31", "进行中")
        );
    }

    @Override
    protected Object[] toColumnValues(PromoItem item) {
        return new Object[]{
                item.getCode(),
                item.getType(),
                item.getValue(),
                item.getUsage(),
                item.getExpiry(),
                item.getStatus()
        };
    }
}