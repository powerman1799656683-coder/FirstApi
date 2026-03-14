package com.firstapi.backend.repository;

import com.firstapi.backend.model.IpItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
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
        return Arrays.asList(
                new IpItem(1L, "Tokyo-1", "SOCKS5", "10.0.0.1:9000", "日本", "18", "42ms", "正常"),
                new IpItem(2L, "Singapore-2", "HTTP", "10.0.0.2:9001", "新加坡", "12", "58ms", "正常"),
                new IpItem(3L, "LosAngeles-1", "SOCKS5", "10.0.0.3:9002", "美国", "5", "132ms", "告警")
        );
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