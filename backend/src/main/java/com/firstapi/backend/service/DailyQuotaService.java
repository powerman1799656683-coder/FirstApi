package com.firstapi.backend.service;

import com.firstapi.backend.model.SubscriptionItem;
import com.firstapi.backend.repository.DailyQuotaUsageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DailyQuotaService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DailyQuotaService.class);
    private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final DailyQuotaUsageRepository usageRepository;
    private final SubscriptionService subscriptionService;
    private final JdbcTemplate jdbcTemplate;

    public DailyQuotaService(DailyQuotaUsageRepository usageRepository,
                             SubscriptionService subscriptionService,
                             JdbcTemplate jdbcTemplate) {
        this.usageRepository = usageRepository;
        this.subscriptionService = subscriptionService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 查询指定用户在指定分组当日的实际消耗（从 relay_records 关联 api_keys 计算）。
     * groupId 为 null 时返回该用户当日所有分组汇总。
     */
    public BigDecimal getTodayUsageByGroup(Long ownerId, Long groupId) {
        LocalDate today = LocalDate.now(ZONE_SHANGHAI);
        if (groupId == null) {
            return usageRepository.getTotalUsedCost(ownerId, today);
        }
        BigDecimal result = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(rr.cost), 0) FROM relay_records rr " +
                "JOIN api_keys ak ON ak.id = rr.api_key_id " +
                "WHERE rr.owner_id = ? AND ak.group_id = ? AND DATE(rr.created_at_ts) = ?",
                BigDecimal.class, ownerId, groupId, Date.valueOf(today));
        return result != null ? result : BigDecimal.ZERO;
    }

    /**
     * 检查用户当日配额是否有剩余。
     * dailyLimit 来自 subscriptions.daily_limit。
     */
    public boolean checkDailyQuota(Long ownerId, BigDecimal dailyLimit) {
        if (dailyLimit == null || dailyLimit.compareTo(BigDecimal.ZERO) <= 0) {
            return true; // 无每日限制，放行
        }
        LocalDate today = LocalDate.now(ZONE_SHANGHAI);
        BigDecimal usedCost = usageRepository.getTotalUsedCost(ownerId, today);
        return usedCost.compareTo(dailyLimit) < 0;
    }

    /**
     * 累加成本到当日用量记录（用户级别，不区分分组）。
     */
    public void addCost(Long ownerId, BigDecimal cost) {
        if (cost == null || cost.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        LocalDate today = LocalDate.now(ZONE_SHANGHAI);
        usageRepository.addCostByOwner(ownerId, today, cost);
    }

    /**
     * 查询用户当日已用总额。
     */
    public BigDecimal getTodayUsage(Long ownerId) {
        LocalDate today = LocalDate.now(ZONE_SHANGHAI);
        return usageRepository.getTotalUsedCost(ownerId, today);
    }

    /**
     * 返回用户的每日配额摘要（从订阅的 daily_limit 读取）。
     */
    public List<Map<String, Object>> listUserQuotaSummary(Long ownerId) {
        List<SubscriptionItem> subs = subscriptionService.getActiveSubscriptions(ownerId);
        if (subs.isEmpty()) {
            return List.of();
        }

        LocalDate today = LocalDate.now(ZONE_SHANGHAI);

        List<Map<String, Object>> result = new ArrayList<>();
        for (SubscriptionItem sub : subs) {
            if (sub.getDailyLimit() == null || sub.getDailyLimit().isBlank()) {
                continue;
            }
            BigDecimal dailyLimit;
            try {
                dailyLimit = new BigDecimal(sub.getDailyLimit().trim());
            } catch (NumberFormatException e) {
                continue;
            }
            if (dailyLimit.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            // 按该订阅对应分组查当日实际消耗
            BigDecimal groupUsed = getTodayUsageByGroup(ownerId, sub.getGroupId());

            BigDecimal remaining = dailyLimit.subtract(groupUsed).max(BigDecimal.ZERO);
            double percent = dailyLimit.compareTo(BigDecimal.ZERO) > 0
                    ? groupUsed.divide(dailyLimit, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .doubleValue()
                    : 0;

            Map<String, Object> item = new HashMap<>();
            item.put("subscriptionId", sub.getId());
            item.put("groupName", sub.getGroup());
            item.put("dailyLimit", dailyLimit.setScale(2, RoundingMode.HALF_UP).toPlainString());
            item.put("usedCost", groupUsed.setScale(10, RoundingMode.HALF_UP).toPlainString());
            item.put("remaining", remaining.setScale(2, RoundingMode.HALF_UP).toPlainString());
            item.put("percent", Math.min(percent, 100.0));
            item.put("quotaDate", today.toString());
            result.add(item);
        }

        return result;
    }

    /**
     * 查询用户近 N 天的每日用量历史。
     */
    public Map<String, Object> getUserUsageHistory(Long ownerId, int days) {
        LocalDate today = LocalDate.now(ZONE_SHANGHAI);
        List<Map<String, Object>> history = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            BigDecimal cost = usageRepository.getTotalUsedCost(ownerId, date);
            Map<String, Object> item = new HashMap<>();
            item.put("date", date.toString());
            item.put("cost", cost.setScale(10, RoundingMode.HALF_UP).toPlainString());
            history.add(item);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("history", history);
        result.put("days", days);
        return result;
    }
}
