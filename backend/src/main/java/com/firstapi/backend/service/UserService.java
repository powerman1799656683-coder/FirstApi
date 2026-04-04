package com.firstapi.backend.service;

import com.firstapi.backend.common.PageResponse;
import com.firstapi.backend.model.AuthUser;
import com.firstapi.backend.model.UserItem;
import com.firstapi.backend.repository.AuthUserRepository;
import com.firstapi.backend.repository.UserRepository;
import com.firstapi.backend.util.PasswordHashSupport;
import com.firstapi.backend.util.TimeSupport;
import com.firstapi.backend.util.ValidationSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserService.class);
    /** 余额保留 10 位小数，与 CostCalculationService 一致 */
    private static final int BALANCE_SCALE = 10;

    private final UserRepository repository;
    private final AuthUserRepository authUserRepository;
    private final SettingsService settingsService;

    public UserService(UserRepository repository,
                       AuthUserRepository authUserRepository,
                       SettingsService settingsService) {
        this.repository = repository;
        this.authUserRepository = authUserRepository;
        this.settingsService = settingsService;
    }

    public PageResponse<UserItem> list(String keyword) {
        List<UserItem> items = repository.findAll();
        if (!isBlank(keyword)) {
            items = items.stream()
                .filter(i -> contains(i.getEmail(), keyword)
                           || contains(i.getUsername(), keyword)
                           || contains(String.valueOf(i.getId()), keyword))
                .collect(Collectors.toList());
        }
        return new PageResponse<UserItem>(items);
    }

    public UserItem get(Long id) {
        UserItem item = repository.findById(id);
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        return item;
    }

    public UserItem create(UserItem.Request req) {
        String username = ValidationSupport.requireNotBlank(req.getUsername(), "用户名不能为空");
        String password = ValidationSupport.requireNotBlank(req.getPassword(), "密码不能为空");

        if (password.length() < 10) {
            throw new IllegalArgumentException("密码长度不能少于10位");
        }

        String email = req.getEmail();
        if (!isBlank(email) && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("邮箱格式无效");
        }

        if (authUserRepository.findByUsername(username) != null) {
            throw new IllegalArgumentException("该用户名已被注册");
        }

        // 初始余额
        String balance = formatBalance(BigDecimal.ZERO);
        if (!isBlank(req.getBalance())) {
            try {
                BigDecimal val = parseBalance(req.getBalance());
                balance = formatBalance(val);
            } catch (Exception ignored) {}
        }

        UserItem item = new UserItem();
        item.setEmail(isBlank(email) ? "" : email);
        item.setUsername(username);
        item.setBalance(balance);
        String defaultGroup = settingsService.getSettings().defaultGroup;
        item.setGroup(emptyAsDefault(req.getGroup(), defaultGroup != null ? defaultGroup : "默认组"));
        item.setRole(emptyAsDefault(req.getRole(), "用户"));
        item.setStatus(emptyAsDefault(req.getStatus(), "正常"));
        item.setTime(emptyAsDefault(req.getTime(), TimeSupport.today()));
        // 先创建 auth_users 记录（含密码哈希），再保存 users 记录，
        // 避免 auth_users 创建失败时 users 表出现孤立记录
        AuthUser authUser = new AuthUser();
        authUser.setUsername(username);
        authUser.setEmail(isBlank(email) ? "" : email);
        authUser.setDisplayName(username);
        authUser.setPasswordHash(PasswordHashSupport.hash(password));
        authUser.setRole(mapRole(req.getRole()));
        authUser.setEnabled(true);
        authUserRepository.save(authUser);

        UserItem saved = repository.save(item);
        return saved;
    }

    public UserItem update(Long id, UserItem.Request req) {
        UserItem existing = repository.findById(id);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        if (req.getEmail() != null) {
            String email = ValidationSupport.requireNotBlank(req.getEmail(), "邮箱不能为空");
            if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                throw new IllegalArgumentException("邮箱格式无效");
            }
            existing.setEmail(email);
        }
        if (req.getUsername() != null) existing.setUsername(ValidationSupport.requireNotBlank(req.getUsername(), "用户名不能为空"));
        if (req.getBalance() != null) existing.setBalance(req.getBalance());
        if (req.getGroup() != null && !req.getGroup().trim().isEmpty()) existing.setGroup(req.getGroup());
        if (req.getRole() != null) existing.setRole(req.getRole());
        if (req.getStatus() != null) existing.setStatus(req.getStatus());
        if (req.getTime() != null) existing.setTime(req.getTime());
        UserItem updated = repository.update(id, existing);

        // Sync auth_users: update password if provided, update email/role
        AuthUser authUser = authUserRepository.findByUsername(existing.getUsername());
        if (authUser == null && !isBlank(req.getPassword())) {
            // 旧数据迁移：users 表有记录但 auth_users 没有，补充创建 auth_users 记录
            AuthUser newAuthUser = new AuthUser();
            newAuthUser.setUsername(existing.getUsername());
            newAuthUser.setEmail(isBlank(existing.getEmail()) ? "" : existing.getEmail());
            newAuthUser.setDisplayName(existing.getUsername());
            newAuthUser.setPasswordHash(PasswordHashSupport.hash(req.getPassword()));
            newAuthUser.setRole(mapRole(existing.getRole()));
            newAuthUser.setEnabled(true);
            authUserRepository.save(newAuthUser);
            LOGGER.info("已为旧用户 {} 补充创建 auth_users 记录", existing.getUsername());
        } else if (authUser != null) {
            if (!isBlank(req.getPassword())) {
                authUserRepository.updatePasswordHash(authUser.getId(), PasswordHashSupport.hash(req.getPassword()));
            }
            authUserRepository.updateByUsername(existing.getUsername(), existing.getEmail(), existing.getUsername(), mapRole(existing.getRole()));
        }

        return updated;
    }

    /**
     * 管理员手动充值/退款。amount 为正数表示充值，负数表示退款。
     */
    public UserItem adjustBalance(Long id, double amount) {
        UserItem existing = repository.findById(id);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        BigDecimal current = parseBalance(existing.getBalance());
        BigDecimal updated = current.add(BigDecimal.valueOf(amount)).setScale(BALANCE_SCALE, RoundingMode.HALF_UP);
        if (updated.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("余额不足，当前余额: " + existing.getBalance());
        }
        existing.setBalance(formatBalance(updated));
        return repository.update(id, existing);
    }

    /**
     * 通过 auth_users.id (即 api_keys.owner_id) 查找对应的 users 记录并扣减余额。
     * 全程使用 BigDecimal，保留 10 位小数精度，不做 double 转换。
     * 扣费失败抛异常，由调用方决定处理方式。
     */
    public synchronized void deductByAuthUserId(Long authUserId, BigDecimal cost) {
        if (cost == null || cost.compareTo(BigDecimal.ZERO) <= 0) return;
        UserItem user = resolveUserByAuthId(authUserId);
        if (user == null) {
            LOGGER.warn("扣费时找不到用户记录: authUserId={}", authUserId);
            return;
        }
        BigDecimal current = parseBalance(user.getBalance());
        BigDecimal updated = current.subtract(cost).setScale(BALANCE_SCALE, RoundingMode.HALF_UP);
        user.setBalance(formatBalance(updated));
        repository.update(user.getId(), user);
    }

    /**
     * 检查用户余额是否 > 0（通过 auth_users.id 查找）。
     * 找不到用户记录时返回 false（拒绝请求，避免逃费）。
     */
    public boolean checkBalanceByAuthUserId(Long authUserId) {
        UserItem user = resolveUserByAuthId(authUserId);
        if (user == null) return false;
        return parseBalance(user.getBalance()).compareTo(BigDecimal.ZERO) > 0;
    }

    private UserItem resolveUserByAuthId(Long authUserId) {
        AuthUser authUser = authUserRepository.findById(authUserId);
        if (authUser == null) return null;
        return repository.findByUsername(authUser.getUsername());
    }

    /**
     * 解析余额字符串为 BigDecimal。
     * 支持 "¥123.45"、"$123.45"、"123.45" 等格式。
     */
    private BigDecimal parseBalance(String balanceStr) {
        if (balanceStr == null || balanceStr.isEmpty()) return BigDecimal.ZERO;
        String cleaned = balanceStr.replaceAll("[^\\d.\\-]", "");
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 格式化余额为统一的人民币格式：¥xxx.xxxxxxxxxx（10 位小数）。
     */
    private String formatBalance(BigDecimal value) {
        return "¥" + value.setScale(BALANCE_SCALE, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * 登录成功后更新用户的登录 IP 和位置信息。
     */
    public void updateLoginInfo(String username, String ip, String location) {
        UserItem user = repository.findByUsername(username);
        if (user != null) {
            user.setLoginIp(ip != null ? ip : "");
            user.setLoginLocation(location != null ? location : "");
            user.setTime(com.firstapi.backend.util.TimeSupport.today());
            repository.update(user.getId(), user);
        }
    }

    public void delete(Long id) {
        UserItem existing = repository.findById(id);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        authUserRepository.deleteByUsername(existing.getUsername());
        repository.deleteById(id);
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

    private String mapRole(String chineseRole) {
        if ("管理员".equals(chineseRole)) return "ADMIN";
        return "USER";
    }
}
