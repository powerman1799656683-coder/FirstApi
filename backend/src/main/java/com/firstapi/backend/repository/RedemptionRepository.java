package com.firstapi.backend.repository;

import com.firstapi.backend.model.RedemptionItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;

@Repository
public class RedemptionRepository extends JdbcListRepository<RedemptionItem> {

    public RedemptionRepository(JdbcTemplate jdbcTemplate) {
        super(
                jdbcTemplate,
                "redemptions",
                new String[]{"name", "code", "type_name", "value_text", "usage_text", "time_label", "status_name"},
                (rs, rowNum) -> new RedemptionItem(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("code"),
                        rs.getString("type_name"),
                        rs.getString("value_text"),
                        rs.getString("usage_text"),
                        rs.getString("time_label"),
                        rs.getString("status_name")
                )
        );
    }

    @Override
    protected List<RedemptionItem> defaultItems() {
        return Arrays.asList(
                new RedemptionItem(1L, "春季赠送", "SPRING2026", "余额充值", "$50.00", "0 / 1", "2026/03/10 14:20:00", "未使用"),
                new RedemptionItem(2L, "VIP 体验券", "VIPTRIAL", "会员时长", "30 天", "1 / 1", "2026/03/09 11:00:00", "已兑换"),
                new RedemptionItem(3L, "新用户福利", "WELCOME10", "余额充值", "$10.00", "0 / 5", "2026/03/08 09:15:00", "未使用")
        );
    }

    @Override
    protected Object[] toColumnValues(RedemptionItem item) {
        return new Object[]{
                item.getName(),
                item.getCode(),
                item.getType(),
                item.getValue(),
                item.getUsage(),
                item.getTime(),
                item.getStatus()
        };
    }
}