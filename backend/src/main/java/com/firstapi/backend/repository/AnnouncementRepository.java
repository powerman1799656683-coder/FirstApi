package com.firstapi.backend.repository;

import com.firstapi.backend.model.AnnouncementItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;

@Repository
public class AnnouncementRepository extends JdbcListRepository<AnnouncementItem> {

    public AnnouncementRepository(JdbcTemplate jdbcTemplate) {
        super(
                jdbcTemplate,
                "announcements",
                new String[]{"title", "content", "type_name", "status_name", "target_scope", "time_label"},
                (rs, rowNum) -> new AnnouncementItem(
                        rs.getLong("id"),
                        rs.getString("title"),
                        rs.getString("content"),
                        rs.getString("type_name"),
                        rs.getString("status_name"),
                        rs.getString("target_scope"),
                        rs.getString("time_label")
                )
        );
    }

    @Override
    protected List<AnnouncementItem> defaultItems() {
        return Arrays.asList(
                new AnnouncementItem(1L, "系统升级通知", "今晚 23:00 至 23:30 进行例行升级。", "更新", "发布中", "所有用户", "2026/03/13 20:00:00"),
                new AnnouncementItem(2L, "VIP 分组调整", "VIP 用户将获得更高优先级。", "通知", "发布中", "VIP", "2026/03/12 10:00:00"),
                new AnnouncementItem(3L, "内测问卷", "欢迎填写内测反馈问卷。", "活动", "草稿", "所有用户", "2026/03/10 09:30:00")
        );
    }

    @Override
    protected Object[] toColumnValues(AnnouncementItem item) {
        return new Object[]{
                item.getTitle(),
                item.getContent(),
                item.getType(),
                item.getStatus(),
                item.getTarget(),
                item.getTime()
        };
    }
}