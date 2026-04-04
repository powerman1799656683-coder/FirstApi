package com.firstapi.backend.controller;

import com.firstapi.backend.common.ApiResponse;
import com.firstapi.backend.common.CurrentSessionHolder;
import com.firstapi.backend.model.AuthLoginRequest;
import com.firstapi.backend.model.AuthRegisterRequest;
import com.firstapi.backend.model.AuthenticatedUser;
import com.firstapi.backend.service.AuthService;
import com.firstapi.backend.service.IpGeoService;
import com.firstapi.backend.service.UserService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final IpGeoService ipGeoService;
    private final JdbcTemplate jdbcTemplate;

    public AuthController(AuthService authService, UserService userService, IpGeoService ipGeoService, JdbcTemplate jdbcTemplate) {
        this.authService = authService;
        this.userService = userService;
        this.ipGeoService = ipGeoService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/login")
    public ApiResponse<AuthenticatedUser> login(@RequestBody AuthLoginRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
        AuthenticatedUser user = authService.login(request);
        response.addHeader(HttpHeaders.SET_COOKIE, authService.buildSessionCookie(authService.openSession(user)).toString());
        fillBalance(user);
        String clientIp = resolveClientIp(httpRequest);
        String location = ipGeoService.lookup(clientIp);
        userService.updateLoginInfo(user.getUsername(), clientIp, location);
        return ApiResponse.ok("登录成功", user);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty()) {
            ip = ip.split(",")[0].trim();
        }
        if (ip == null || ip.isEmpty()) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    @PostMapping("/register")
    public ApiResponse<AuthenticatedUser> register(@RequestBody AuthRegisterRequest request, HttpServletResponse response) {
        AuthenticatedUser user = authService.register(request);
        response.addHeader(HttpHeaders.SET_COOKIE, authService.buildSessionCookie(authService.openSession(user)).toString());
        return ApiResponse.ok("注册成功", user);
    }

    @PostMapping("/logout")
    public ApiResponse<Boolean> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request);
        response.addHeader(HttpHeaders.SET_COOKIE, authService.clearSessionCookie().toString());
        return ApiResponse.ok("退出登录成功", true);
    }

    @GetMapping("/session")
    public ApiResponse<AuthenticatedUser> session() {
        AuthenticatedUser authed = CurrentSessionHolder.require();
        fillBalance(authed);
        return ApiResponse.ok(authed);
    }

    private void fillBalance(AuthenticatedUser user) {
        try {
            String balance = jdbcTemplate.queryForObject(
                    "SELECT `balance` FROM `users` WHERE `username` = ?",
                    String.class,
                    user.getUsername()
            );
            user.setBalance(balance != null ? balance : "¥0.00");
        } catch (Exception e) {
            user.setBalance("¥0.00");
        }
    }
}
