package com.firstapi.backend.service;

import com.firstapi.backend.config.AuthProperties;
import com.firstapi.backend.model.AuthLoginRequest;
import com.firstapi.backend.model.AuthRegisterRequest;
import com.firstapi.backend.model.AuthUser;
import com.firstapi.backend.model.AuthenticatedUser;
import com.firstapi.backend.repository.AuthUserRepository;
import com.firstapi.backend.util.PasswordHashSupport;
import com.firstapi.backend.util.TimeSupport;
import com.firstapi.backend.util.ValidationSupport;
import com.firstapi.backend.model.SettingsData;
import com.firstapi.backend.model.UserItem;
import com.firstapi.backend.repository.UserRepository;
import com.firstapi.backend.service.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
public class AuthService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$");

    private final AuthProperties authProperties;
    private final AuthUserRepository authUserRepository;
    private final UserRepository userRepository;
    private final SettingsService settingsService;
    private final ConcurrentHashMap<String, SessionRecord> sessions = new ConcurrentHashMap<String, SessionRecord>();

    public AuthService(AuthProperties authProperties, 
                       AuthUserRepository authUserRepository,
                       UserRepository userRepository,
                       SettingsService settingsService) {
        this.authProperties = authProperties;
        this.authUserRepository = authUserRepository;
        this.userRepository = userRepository;
        this.settingsService = settingsService;
    }

    @PostConstruct
    public void init() {
        bootstrapUser(
                authProperties.getAdminUsername(),
                authProperties.getAdminDisplayName(),
                authProperties.getAdminEmail(),
                authProperties.getAdminPassword(),
                "ADMIN"
        );

        if (authProperties.isUserEnabled() && !ValidationSupport.isBlank(authProperties.getUserPassword())) {
            bootstrapUser(
                    authProperties.getUserUsername(),
                    authProperties.getUserDisplayName(),
                    authProperties.getUserEmail(),
                    authProperties.getUserPassword(),
                    "USER"
            );
        }

        // 确保所有 auth_users 在 users 业务表中都有对应记录
        syncAuthUserToBusinessTable(authProperties.getAdminUsername(), authProperties.getAdminEmail(), "ADMIN");
        if (authProperties.isUserEnabled() && !ValidationSupport.isBlank(authProperties.getUserPassword())) {
            syncAuthUserToBusinessTable(authProperties.getUserUsername(), authProperties.getUserEmail(), "USER");
        }
    }

    private void syncAuthUserToBusinessTable(String username, String email, String role) {
        if (ValidationSupport.isBlank(username)) return;
        if (userRepository.findByUsername(username) != null) return;

        UserItem businessUser = new UserItem();
        businessUser.setUsername(username);
        businessUser.setEmail(email);
        businessUser.setBalance("¥0.00");
        SettingsData settings = settingsService.getSettings();
        businessUser.setGroup(settings.defaultGroup != null ? settings.defaultGroup : "默认组");
        businessUser.setRole("ADMIN".equals(role) ? "管理员" : "用户");
        businessUser.setStatus("正常");
        businessUser.setTime(TimeSupport.today());
        userRepository.save(businessUser);
        LOGGER.info("已同步用户 {} 到业务表", username);
    }

    public AuthenticatedUser login(AuthLoginRequest request) {
        String username = ValidationSupport.requireNotBlank(request.getUsername(), "用户名不能为空");
        String password = ValidationSupport.requireNotBlank(request.getPassword(), "密码不能为空");
        AuthUser user = authUserRepository.findByUsername(username);
        boolean passwordMatch = false;
        if (user != null && user.isEnabled()) {
            try {
                passwordMatch = PasswordHashSupport.matches(password, user.getPasswordHash());
            } catch (IllegalArgumentException ignored) {
                // password too short — treat as mismatch, don't leak validation details
            }
        }
        if (!passwordMatch) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }

        authUserRepository.updateLastLogin(user.getId(), TimeSupport.nowDateTime());
        return toAuthenticatedUser(user);
    }

    public AuthenticatedUser register(AuthRegisterRequest request) {
        String username = ValidationSupport.requireNotBlank(request.getUsername(), "用户名不能为空");
        String password = ValidationSupport.requireNotBlank(request.getPassword(), "密码不能为空");
        String confirmPassword = ValidationSupport.requireNotBlank(request.getConfirmPassword(), "确认密码不能为空");
        String displayName = ValidationSupport.isBlank(request.getDisplayName()) ? username : request.getDisplayName().trim();
        String email = request.getEmail() == null ? "" : request.getEmail().trim();

        if (!ValidationSupport.isBlank(email) && !EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("邮箱格式不正确");
        }
        if (password.length() < 10) {
            throw new IllegalArgumentException("密码长度不能少于10位");
        }
        if (!password.equals(confirmPassword)) {
            throw new IllegalArgumentException("两次输入的密码不一致");
        }
        if (authUserRepository.findByUsername(username) != null) {
            throw new IllegalArgumentException("该用户名已被注册");
        }

        SettingsData settings = settingsService.getSettings();
        if (Boolean.FALSE.equals(settings.registrationOpen)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "系统已关闭注册");
        }

        AuthUser user = new AuthUser();
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setEmail(email);
        user.setPasswordHash(PasswordHashSupport.hash(password));
        user.setRole("USER");
        user.setEnabled(true);
        authUserRepository.save(user);

        // Link to business user table
        UserItem businessUser = new UserItem();
        businessUser.setUsername(username);
        businessUser.setEmail(email);
        businessUser.setBalance("¥0.00");
        businessUser.setGroup(settings.defaultGroup != null ? settings.defaultGroup : "默认组");
        businessUser.setRole("用户");
        businessUser.setStatus("正常");
        businessUser.setTime(TimeSupport.today());
        userRepository.save(businessUser);

        return toAuthenticatedUser(user);
    }

    public String openSession(AuthenticatedUser user) {
        purgeExpiredSessions();
        String token = UUID.randomUUID().toString().replace("-", "");
        sessions.put(token, new SessionRecord(user.getId(), expiresAt()));
        return token;
    }

    public AuthenticatedUser resolveAuthenticatedUser(HttpServletRequest request) {
        String token = readSessionToken(request);
        if (token == null) {
            return null;
        }

        SessionRecord record = sessions.get(token);
        if (record == null || record.isExpired()) {
            sessions.remove(token);
            return null;
        }

        AuthUser user = authUserRepository.findById(record.userId);
        if (user == null || !user.isEnabled()) {
            sessions.remove(token);
            return null;
        }

        record.extend(expiresAt());
        return toAuthenticatedUser(user);
    }

    public void logout(HttpServletRequest request) {
        String token = readSessionToken(request);
        if (token != null) {
            sessions.remove(token);
        }
    }

    public ResponseCookie buildSessionCookie(String token) {
        return ResponseCookie.from(authProperties.getCookieName(), token)
                .httpOnly(true)
                .secure(authProperties.isSecureCookie())
                .sameSite(authProperties.getSameSite())
                .path("/")
                .maxAge(Duration.ofMinutes(authProperties.getSessionTtlMinutes()))
                .build();
    }

    public ResponseCookie clearSessionCookie() {
        return ResponseCookie.from(authProperties.getCookieName(), "")
                .httpOnly(true)
                .secure(authProperties.isSecureCookie())
                .sameSite(authProperties.getSameSite())
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
    }

    private void bootstrapUser(String username, String displayName, String email, String password, String role) {
        if (ValidationSupport.isBlank(username) || ValidationSupport.isBlank(password)) {
            return;
        }

        if (authUserRepository.findByUsername(username) != null) {
            return;
        }

        AuthUser user = new AuthUser();
        user.setUsername(username.trim());
        user.setDisplayName(ValidationSupport.requireNotBlank(displayName, "显示名称不能为空"));
        user.setEmail(ValidationSupport.requireNotBlank(email, "邮箱不能为空"));
        user.setPasswordHash(PasswordHashSupport.hash(password));
        user.setRole(role);
        user.setEnabled(true);
        authUserRepository.save(user);

        if ("change-me-before-public-deploy".equals(password)) {
            LOGGER.warn("用户 {} 仍在使用默认密码。公网部署前请修改 FIRSTAPI_{}_PASSWORD。", username, role);
        }
    }

    private AuthenticatedUser toAuthenticatedUser(AuthUser user) {
        return new AuthenticatedUser(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getRole()
        );
    }

    private String readSessionToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (authProperties.getCookieName().equals(cookie.getName()) && !ValidationSupport.isBlank(cookie.getValue())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private long expiresAt() {
        return System.currentTimeMillis() + (authProperties.getSessionTtlMinutes() * 60L * 1000L);
    }

    private void purgeExpiredSessions() {
        for (String token : sessions.keySet()) {
            SessionRecord record = sessions.get(token);
            if (record != null && record.isExpired()) {
                sessions.remove(token);
            }
        }
    }

    private static final class SessionRecord {
        private final Long userId;
        private volatile long expiresAt;

        private SessionRecord(Long userId, long expiresAt) {
            this.userId = userId;
            this.expiresAt = expiresAt;
        }

        private boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }

        private void extend(long nextExpiry) {
            this.expiresAt = nextExpiry;
        }
    }
}
