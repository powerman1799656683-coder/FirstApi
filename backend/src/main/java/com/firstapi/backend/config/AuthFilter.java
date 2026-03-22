package com.firstapi.backend.config;

import tools.jackson.databind.ObjectMapper;
import com.firstapi.backend.common.ApiResponse;
import com.firstapi.backend.common.CurrentSessionHolder;
import com.firstapi.backend.model.AuthenticatedUser;
import com.firstapi.backend.service.AuthService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class AuthFilter extends OncePerRequestFilter {

    private final AuthService authService;
    private final ObjectMapper objectMapper;

    public AuthFilter(AuthService authService, ObjectMapper objectMapper) {
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        applySecurityHeaders(response);

        String path = request.getRequestURI();
        if (!path.startsWith("/api/") || isPublicApi(path) || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        AuthenticatedUser user = authService.resolveAuthenticatedUser(request);
        if (user == null) {
            writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "请先登录");
            return;
        }

        if (path.startsWith("/api/admin/") && !user.isAdmin()) {
            writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "需要管理员权限");
            return;
        }

        CurrentSessionHolder.set(user);
        try {
            filterChain.doFilter(request, response);
        } finally {
            CurrentSessionHolder.clear();
        }
    }

    private boolean isPublicApi(String path) {
        return path.startsWith("/api/public/")
                || "/api/auth/login".equals(path)
                || "/api/auth/register".equals(path)
                || "/api/auth/logout".equals(path);
    }

    private void applySecurityHeaders(HttpServletResponse response) {
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-XSS-Protection", "1; mode=block");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=()");
        response.setHeader("Content-Security-Policy",
                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                + "img-src 'self' data:; font-src 'self'; connect-src 'self'; "
                + "frame-ancestors 'none'; base-uri 'self'; form-action 'self'");
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        response.setHeader("Cache-Control", "no-store");
    }

    private void writeJsonError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(message, null));
    }
}
