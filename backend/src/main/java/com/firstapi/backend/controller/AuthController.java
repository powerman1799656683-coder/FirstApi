package com.firstapi.backend.controller;

import com.firstapi.backend.common.ApiResponse;
import com.firstapi.backend.common.CurrentSessionHolder;
import com.firstapi.backend.model.AuthLoginRequest;
import com.firstapi.backend.model.AuthRegisterRequest;
import com.firstapi.backend.model.AuthenticatedUser;
import com.firstapi.backend.service.AuthService;
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

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<AuthenticatedUser> login(@RequestBody AuthLoginRequest request, HttpServletResponse response) {
        AuthenticatedUser user = authService.login(request);
        response.addHeader(HttpHeaders.SET_COOKIE, authService.buildSessionCookie(authService.openSession(user)).toString());
        return ApiResponse.ok("登录成功", user);
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
        return ApiResponse.ok(CurrentSessionHolder.require());
    }
}
