package com.firstapi.backend.service;

import com.firstapi.backend.common.PageResponse;
import com.firstapi.backend.model.AuthUser;
import com.firstapi.backend.model.UserItem;
import com.firstapi.backend.repository.AuthUserRepository;
import com.firstapi.backend.repository.UserRepository;
import com.firstapi.backend.util.PasswordHashSupport;
import com.firstapi.backend.util.TimeSupport;
import com.firstapi.backend.util.ValidationSupport;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {

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

        String email = req.getEmail();
        if (!isBlank(email) && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("邮箱格式无效");
        }

        if (authUserRepository.findByUsername(username) != null) {
            throw new IllegalArgumentException("该用户名已被注册");
        }

        // Format balance from numeric input
        String balance = "¥0.00";
        if (!isBlank(req.getBalance())) {
            try {
                double val = Double.parseDouble(req.getBalance().replaceAll("[^\\d.\\-]", ""));
                balance = String.format("$%.2f", val);
            } catch (NumberFormatException ignored) {}
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
        UserItem saved = repository.save(item);

        AuthUser authUser = new AuthUser();
        authUser.setUsername(username);
        authUser.setEmail(isBlank(email) ? "" : email);
        authUser.setDisplayName(username);
        authUser.setPasswordHash(PasswordHashSupport.hash(password));
        authUser.setRole(mapRole(req.getRole()));
        authUser.setEnabled(true);
        authUserRepository.save(authUser);

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
        if (authUser != null) {
            if (!isBlank(req.getPassword())) {
                authUserRepository.updatePasswordHash(authUser.getId(), PasswordHashSupport.hash(req.getPassword()));
            }
            authUserRepository.updateByUsername(existing.getUsername(), existing.getEmail(), existing.getUsername(), mapRole(existing.getRole()));
        }

        return updated;
    }

    public UserItem adjustBalance(Long id, double amount) {
        UserItem existing = repository.findById(id);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        double current = parseBalance(existing.getBalance());
        double updated = current + amount;
        if (updated < 0) {
            throw new IllegalArgumentException("余额不足，当前余额: " + existing.getBalance());
        }
        existing.setBalance(String.format("$%.2f", updated));
        return repository.update(id, existing);
    }

    private double parseBalance(String balanceStr) {
        if (balanceStr == null || balanceStr.isEmpty()) return 0;
        String cleaned = balanceStr.replaceAll("[^\\d.\\-]", "");
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return 0;
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