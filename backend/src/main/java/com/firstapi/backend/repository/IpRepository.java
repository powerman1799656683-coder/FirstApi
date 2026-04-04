package com.firstapi.backend.repository;

import com.firstapi.backend.model.IpItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class IpRepository extends JdbcListRepository<IpItem> {

    public IpRepository(JdbcTemplate jdbcTemplate) {
        super(
                jdbcTemplate,
                "ips",
                new String[]{"name", "protocol", "address", "location", "accounts_count", "latency", "status_name"},
                (rs, rowNum) -> new IpItem(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("protocol"),
                        rs.getString("address"),
                        rs.getString("location"),
                        rs.getString("accounts_count"),
                        rs.getString("latency"),
                        rs.getString("status_name")
                )
        );
    }

    @Override
    protected List<IpItem> defaultItems() {
        return List.of();
    }

    @Override
    protected Object[] toColumnValues(IpItem item) {
        return new Object[]{
                item.getName(),
                item.getProtocol(),
                item.getAddress(),
                item.getLocation(),
                item.getAccounts(),
                item.getLatency(),
                item.getStatus()
        };
    }
}