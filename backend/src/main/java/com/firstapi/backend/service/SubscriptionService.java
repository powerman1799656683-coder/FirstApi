package com.firstapi.backend.service;

import com.firstapi.backend.common.PageResponse;
import com.firstapi.backend.model.AuthUser;
import com.firstapi.backend.model.GroupItem;
import com.firstapi.backend.model.SubscriptionItem;
import com.firstapi.backend.model.SubscriptionPlanItem;
import com.firstapi.backend.repository.AuthUserRepository;
import com.firstapi.backend.repository.GroupRepository;
import com.firstapi.backend.repository.SubscriptionPlanRepository;
import com.firstapi.backend.repository.SubscriptionRepository;
import com.firstapi.backend.util.TimeSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SubscriptionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionService.class);
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+(?:\\.\\d+)?");
    private static final int BALANCE_SCALE = 10;
    private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter EXPIRY_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final SubscriptionRepository repository;
    private final AuthUserRepository authUserRepository;
    private final GroupRepository groupRepository;
    private final SubscriptionPlanRepository planRepository;

    public SubscriptionService(SubscriptionRepository repository,
                               AuthUserRepository authUserRepository,
                               GroupRepository groupRepository,
                               SubscriptionPlanRepository planRepository) {
        this.repository = repository;
        this.authUserRepository = authUserRepository;
        this.groupRepository = groupRepository;
        this.planRepository = planRepository;
    }

    public PageResponse<SubscriptionItem> list(String keyword) {
        List<SubscriptionItem> items = repository.findAll();
        if (!isBlank(keyword)) {
            items = items.stream()
                .filter(i -> contains(i.getUser(), keyword)
                           || contains(i.getGroup(), keyword)
                           || contains(String.valueOf(i.getUid()), keyword))
                .collect(Collectors.toList());
        }
        return new PageResponse<SubscriptionItem>(items);
    }

    public SubscriptionItem get(Long id) {
        SubscriptionItem item = repository.findById(id);
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订阅不存在");
        }
        return item;
    }

    public SubscriptionItem create(SubscriptionItem.Request req) {
        SubscriptionItem item = new SubscriptionItem();
        item.setUser(emptyAsDefault(req.getUser(), "new-user@firstapi.com"));
        item.setUid(resolveUid(req.getUid(), item.getUser()));
        if (req.getGroupId() != null) {
            item.setGroupId(req.getGroupId());
            GroupItem g = groupRepository.findById(req.getGroupId());
            item.setGroup(g != null ? g.getName() : emptyAsDefault(req.getGroup(), "普通会员"));
        } else {
            item.setGroup(emptyAsDefault(req.getGroup(), "普通会员"));
        }

        // 如果指定了 planId，从订阅等级模板读取配额
        if (req.getPlanId() != null) {
            item.setPlanId(req.getPlanId());
            SubscriptionPlanItem plan = planRepository.findById(req.getPlanId());
            if (plan != null) {
                item.setGroup(plan.getName());
                // 用等级的 monthlyQuota 作为配额上限
                item.setUsage(buildUsage(null, plan.getMonthlyQuota()));
                item.setDailyLimit(plan.getDailyLimit());
            } else {
                item.setUsage(buildUsage(req.getUsage(), req.getQuota()));
                String dl = req.getDailyLimit();
                item.setDailyLimit(dl == null || dl.isBlank() ? null : dl);
            }
        } else {
            item.setUsage(buildUsage(req.getUsage(), req.getQuota()));
            String dl = req.getDailyLimit();
            item.setDailyLimit(dl == null || dl.isBlank() ? null : dl);
        }

        item.setProgress(req.getProgress() != null ? req.getProgress() : deriveProgress(item.getUsage()));
        item.setExpiry(emptyAsDefault(req.getExpiry(), TimeSupport.plusMonths(null, 1)));
        item.setStatus(emptyAsDefault(req.getStatus(), "正常"));
        return repository.save(item);
    }

    public SubscriptionItem update(Long id, SubscriptionItem.Request req) {
        SubscriptionItem existing = repository.findById(id);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订阅不存在");
        }
        if (req.getUser() != null) {
            existing.setUser(req.getUser());
            existing.setUid(resolveUid(req.getUid(), existing.getUser()));
        } else if (req.getUid() != null) {
            existing.setUid(req.getUid());
        }
        if (req.getGroupId() != null) {
            existing.setGroupId(req.getGroupId());
            GroupItem g = groupRepository.findById(req.getGroupId());
            if (g != null) existing.setGroup(g.getName());
        } else if (req.getGroup() != null) {
            existing.setGroup(req.getGroup());
        }

        // 如果切换了 planId，更新等级名称和配额
        if (req.getPlanId() != null) {
            existing.setPlanId(req.getPlanId());
            SubscriptionPlanItem plan = planRepository.findById(req.getPlanId());
            if (plan != null) {
                existing.setGroup(plan.getName());
            }
        }

        if (req.getUsage() != null) {
            existing.setUsage(req.getUsage());
        } else if (!isBlank(req.getQuota())) {
            existing.setUsage(replaceQuota(existing.getUsage(), req.getQuota()));
        }
        if (req.getProgress() != null) {
            existing.setProgress(req.getProgress());
        } else if (req.getUsage() != null || !isBlank(req.getQuota())) {
            existing.setProgress(deriveProgress(existing.getUsage()));
        }
        if (req.getExpiry() != null) existing.setExpiry(req.getExpiry());
        if (req.getStatus() != null) existing.setStatus(req.getStatus());
        if (req.getDailyLimit() != null) existing.setDailyLimit(req.getDailyLimit().isBlank() ? null : req.getDailyLimit());
        return repository.update(id, existing);
    }

    public void delete(Long id) {
        SubscriptionItem existing = repository.findById(id);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订阅不存在");
        }
        repository.deleteById(id);
    }

    /**
     * 检查用户是否有活跃订阅且额度剩余（不限分组，向后兼容）。
     */
    public boolean hasQuotaRemaining(Long uid) {
        return hasQuotaRemaining(uid, null);
    }

    /**
     * 检查用户在指定分组下是否有活跃订阅且额度剩余。
     * groupId 为 null 时不限分组（兜底）。
     */
    public boolean hasQuotaRemaining(Long uid, Long groupId) {
        SubscriptionItem sub = repository.findActiveByUidAndGroup(uid, groupId);
        if (sub == null) return false;
        BigDecimal[] parsed = parseUsage(sub.getUsage());
        return parsed != null && parsed[0].compareTo(parsed[1]) < 0;
    }

    /**
     * 从订阅额度中扣减成本（不限分组，向后兼容）。
     */
    public synchronized void deductQuota(Long uid, BigDecimal cost) {
        deductQuota(uid, null, cost);
    }

    /**
     * 从指定分组的订阅额度中扣减成本。
     */
    public synchronized void deductQuota(Long uid, Long groupId, BigDecimal cost) {
        if (cost == null || cost.compareTo(BigDecimal.ZERO) <= 0) return;
        SubscriptionItem sub = repository.findActiveByUidAndGroup(uid, groupId);
        if (sub == null) return;
        BigDecimal[] parsed = parseUsage(sub.getUsage());
        if (parsed == null) return;

        BigDecimal used = parsed[0].add(cost).setScale(BALANCE_SCALE, RoundingMode.HALF_UP);
        BigDecimal total = parsed[1];
        sub.setUsage("\u00a5" + used.toPlainString() + " / \u00a5" + total.setScale(BALANCE_SCALE, RoundingMode.HALF_UP).toPlainString());
        double progress = total.compareTo(BigDecimal.ZERO) > 0
                ? Math.min(100.0, used.divide(total, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue())
                : 0;
        sub.setProgress(Math.round(progress * 10.0) / 10.0);
        repository.update(sub.getId(), sub);
    }

    /**
     * 解析 usage_text 中的 [used, total]。
     * 格式: "¥X.XXXXXXXXXX / ¥Y.XXXXXXXXXX"
     */
    private BigDecimal[] parseUsage(String usage) {
        if (isBlank(usage)) return null;
        Matcher matcher = NUMBER_PATTERN.matcher(usage);
        if (!matcher.find()) return null;
        BigDecimal used = new BigDecimal(matcher.group());
        if (!matcher.find()) return null;
        BigDecimal total = new BigDecimal(matcher.group());
        return new BigDecimal[]{used, total};
    }

    /**
     * 获取用户的活跃订阅（不限分组，向后兼容）。
     */
    public SubscriptionItem getActiveSubscription(Long uid) {
        return getActiveSubscription(uid, null);
    }

    /**
     * 获取用户在指定分组下的活跃订阅。
     */
    public SubscriptionItem getActiveSubscription(Long uid, Long groupId) {
        return repository.findActiveByUidAndGroup(uid, groupId);
    }

    /**
     * 按 uid 获取用户的所有活跃订阅。
     */
    public List<SubscriptionItem> getActiveSubscriptions(Long uid) {
        return repository.findActiveListByUid(uid);
    }

    private String buildUsage(String usage, String quota) {
        if (!isBlank(usage)) {
            return usage;
        }
        if (!isBlank(quota)) {
            try {
                BigDecimal total = new BigDecimal(quota).setScale(BALANCE_SCALE, RoundingMode.HALF_UP);
                return "\u00a5" + BigDecimal.ZERO.setScale(BALANCE_SCALE).toPlainString() + " / \u00a5" + total.toPlainString();
            } catch (NumberFormatException e) {
                return "\u00a50.0000000000 / \u00a5" + quota;
            }
        }
        return "\u00a50.0000000000 / \u00a50.0000000000";
    }

    private String replaceQuota(String usage, String quota) {
        String used = "\u00a5" + BigDecimal.ZERO.setScale(BALANCE_SCALE).toPlainString();
        if (!isBlank(usage) && usage.contains("/")) {
            used = usage.split("/")[0].trim();
        }
        try {
            BigDecimal total = new BigDecimal(quota).setScale(BALANCE_SCALE, RoundingMode.HALF_UP);
            return used + " / \u00a5" + total.toPlainString();
        } catch (NumberFormatException e) {
            return used + " / \u00a5" + quota;
        }
    }

    private Double deriveProgress(String usage) {
        BigDecimal[] parsed = parseUsage(usage);
        if (parsed == null || parsed[1].compareTo(BigDecimal.ZERO) <= 0) {
            return 0.0;
        }
        double progress = parsed[0].divide(parsed[1], 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).doubleValue();
        return Math.min(100.0, Math.round(progress * 10.0) / 10.0);
    }

    /**
     * 根据 user_name（email/username）自动解析 auth_users.id 作为 uid_value。
     * 优先使用显式传入的 uid，若为 null 或 0 则尝试从 auth_users 查找。
     */
    private Long resolveUid(Long explicitUid, String userName) {
        if (explicitUid != null && explicitUid > 0) {
            return explicitUid;
        }
        if (isBlank(userName)) {
            return 0L;
        }
        // 先按 email 查
        AuthUser authUser = authUserRepository.findByEmail(userName);
        if (authUser != null) {
            LOGGER.info("订阅自动解析 uid: user_name={} -> auth_users.id={}", userName, authUser.getId());
            return authUser.getId();
        }
        // 再按 username 查
        authUser = authUserRepository.findByUsername(userName);
        if (authUser != null) {
            LOGGER.info("订阅自动解析 uid: user_name={} -> auth_users.id={}", userName, authUser.getId());
            return authUser.getId();
        }
        LOGGER.warn("订阅无法解析 uid: user_name={} 在 auth_users 中未找到", userName);
        return 0L;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private boolean contains(String value, String keyword) {
        if (value == null || keyword == null) return false;
        return value.toLowerCase().contains(keyword.toLowerCase());
    }

    private String emptyAsDefault(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }

    /**
     * 每日凌晨检查到期订阅，自动续费：
     * - 已用额度归零
     * - 配额上限恢复为等级设定值
     * - 到期时间延长一个月
     * 只处理有 plan_id 且等级状态为"正常"的订阅。
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void refreshExpiredSubscriptions() {
        LocalDate today = LocalDate.now(ZONE_SHANGHAI);
        List<SubscriptionItem> allSubs = repository.findAll();
        int refreshed = 0;

        for (SubscriptionItem sub : allSubs) {
            // 只处理状态"正常"且关联了等级的订阅
            if (!"正常".equals(sub.getStatus()) || sub.getPlanId() == null) {
                continue;
            }

            // 解析到期日期
            LocalDate expiryDate = parseExpiryDate(sub.getExpiry());
            if (expiryDate == null || expiryDate.isAfter(today)) {
                continue; // 未到期或日期格式无效，跳过
            }

            // 查找关联的等级
            SubscriptionPlanItem plan = planRepository.findById(sub.getPlanId());
            if (plan == null || !"正常".equals(plan.getStatus())) {
                LOGGER.info("订阅 id={} 关联的等级 planId={} 不存在或已禁用，跳过自动续费", sub.getId(), sub.getPlanId());
                continue;
            }

            // 重置额度：已用归零，配额上限恢复为等级的 monthlyQuota
            BigDecimal monthlyQuota;
            try {
                monthlyQuota = new BigDecimal(plan.getMonthlyQuota()).setScale(BALANCE_SCALE, RoundingMode.HALF_UP);
            } catch (NumberFormatException e) {
                LOGGER.warn("订阅 id={} 等级 monthlyQuota 格式错误: {}", sub.getId(), plan.getMonthlyQuota());
                continue;
            }
            sub.setUsage("\u00a5" + BigDecimal.ZERO.setScale(BALANCE_SCALE).toPlainString()
                    + " / \u00a5" + monthlyQuota.toPlainString());
            sub.setProgress(0.0);

            // 更新每日配额为等级设定值
            sub.setDailyLimit(plan.getDailyLimit());

            // 到期时间延长一个月
            sub.setExpiry(expiryDate.plusMonths(1).format(EXPIRY_FORMAT));

            repository.update(sub.getId(), sub);
            refreshed++;
            LOGGER.info("订阅自动续费: id={}, user={}, plan={}, 新到期={}", sub.getId(), sub.getUser(), plan.getName(), sub.getExpiry());
        }

        if (refreshed > 0) {
            LOGGER.info("订阅自动续费完成，共刷新 {} 条", refreshed);
        }
    }

    /**
     * 解析 expiry_label 为 LocalDate，支持 yyyy/MM/dd 格式。
     */
    private LocalDate parseExpiryDate(String expiry) {
        if (isBlank(expiry)) return null;
        try {
            return LocalDate.parse(expiry.trim(), EXPIRY_FORMAT);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 更新指定订阅的每日配额
     */
    public boolean updateDailyLimit(Long subscriptionId, BigDecimal dailyLimit) {
        SubscriptionItem existing = repository.findById(subscriptionId);
        if (existing == null) {
            return false;
        }

        String dailyLimitStr = dailyLimit == null ? null : dailyLimit.toPlainString();
        existing.setDailyLimit(dailyLimitStr);

        try {
            repository.update(subscriptionId, existing);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
