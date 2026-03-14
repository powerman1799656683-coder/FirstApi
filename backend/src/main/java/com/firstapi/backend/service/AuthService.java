package com.firstapi.backend.service;

import com.firstapi.backend.config.AuthProperties;
import com.firstapi.backend.model.AuthLoginRequest;
import com.firstapi.backend.model.AuthUser;
import com.firstapi.backend.model.AuthenticatedUser;
import com.firstapi.backend.repository.AuthUserRepository;
import com.firstapi.backend.util.PasswordHashSupport;
import com.firstapi.backend.util.TimeSupport;
import com.firstapi.backend.util.ValidationSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);

    private final AuthProperties authProperties;
    private final AuthUserRepository authUserRepository;
    private final ConcurrentHashMap<String, SessionRecord> sessions = new ConcurrentHashMap<String, SessionRecord>();

    public AuthService(AuthProperties authProperties, AuthUserRepository authUserRepository) {
        this.authProperties = authProperties;
        this.authUserRepository = authUserRepository;
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
    }

    public AuthenticatedUser login(AuthLoginRequest request) {
        String username = ValidationSupport.requireNotBlank(request.getUsername(), "Username is required");
        String password = ValidationSupport.requireNotBlank(request.getPassword(), "Password is required");
        AuthUser user = authUserRepository.findByUsername(username);
        if (user == null || !user.isEnabled() || !PasswordHashSupport.matches(password, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        authUserRepository.updateLastLogin(user.getId(), TimeSupport.nowDateTime());
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
        user.setDisplayName(ValidationSupport.requireNotBlank(displayName, "Display name is required"));
        user.setEmail(ValidationSupport.requireNotBlank(email, "Email is required"));
        user.setPasswordHash(PasswordHashSupport.hash(password));
        user.setRole(role);
        user.setEnabled(true);
        authUserRepository.save(user);

        if ("change-me-before-public-deploy".equals(password)) {
            LOGGER.warn("Bootstrapped {} user with the default password. Change FIRSTAPI_{}_PASSWORD before public deployment.", username, role);
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
