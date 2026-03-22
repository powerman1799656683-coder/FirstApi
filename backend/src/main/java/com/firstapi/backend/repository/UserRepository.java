package com.firstapi.backend.repository;

import com.firstapi.backend.model.UserItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collections;
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
        return Collections.emptyList();
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