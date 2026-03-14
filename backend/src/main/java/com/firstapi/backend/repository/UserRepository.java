package com.firstapi.backend.repository;

import com.firstapi.backend.model.UserItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;

@Repository
public class UserRepository extends JdbcListRepository<UserItem> {

    public UserRepository(JdbcTemplate jdbcTemplate) {
        super(
                jdbcTemplate,
                "users",
                new String[]{"email", "username", "balance", "group_name", "role_name", "status_name", "time_label"},
                (rs, rowNum) -> new UserItem(
                        rs.getLong("id"),
                        rs.getString("email"),
                        rs.getString("username"),
                        rs.getString("balance"),
                        rs.getString("group_name"),
                        rs.getString("role_name"),
                        rs.getString("status_name"),
                        rs.getString("time_label")
                )
        );
    }

    @Override
    protected List<UserItem> defaultItems() {
        return Arrays.asList(
                new UserItem(22L, "check123@gmail.com", "check123", "$988.69", "Default", "用户", "正常", "2026/03/12"),
                new UserItem(21L, "atawubop75@gmail.com", "atawubop75", "$0.00", "VIP", "用户", "正常", "2026/03/11")
        );
    }

    @Override
    protected Object[] toColumnValues(UserItem item) {
        return new Object[]{
                item.getEmail(),
                item.getUsername(),
                item.getBalance(),
                item.getGroup(),
                item.getRole(),
                item.getStatus(),
                item.getTime()
        };
    }
}