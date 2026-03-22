package com.firstapi.backend.config;

import tools.jackson.databind.ObjectMapper;
import com.firstapi.backend.common.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基于 IP 的限流过滤器。
 * - 登录/注册接口：每个 IP 每分钟最多 10 次（防暴力破解）
 * - 一般 API 接口：每个 IP 每分钟最多 120 次（防滥用）
 */
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final int AUTH_LIMIT = 10;
    private static final int API_LIMIT = 120;
    private static final long WINDOW_MS = 60_000L;

    private final ConcurrentHashMap<String, RequestCounter> authCounters = new ConcurrentHashMap<String, RequestCounter>();
    private final ConcurrentHashMap<String, RequestCounter> apiCounters = new ConcurrentHashMap<String, RequestCounter>();
    private final ObjectMapper objectMapper;

    private volatile long lastCleanup = System.currentTimeMillis();

    public RateLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        periodicCleanup();
        String clientIp = resolveClientIp(request);

        if (isAuthEndpoint(path)) {
            if (!checkLimit(clientIp, authCounters, AUTH_LIMIT)) {
                LOGGER.warn("登录/注册限流触发: IP={}", clientIp);
                writeRateLimitResponse(response);
                return;
            }
        }

        if (!checkLimit(clientIp, apiCounters, API_LIMIT)) {
            LOGGER.warn("API 限流触发: IP={}", clientIp);
            writeRateLimitResponse(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAuthEndpoint(String path) {
        return "/api/auth/login".equals(path) || "/api/auth/register".equals(path);
    }

    private boolean checkLimit(String clientIp, ConcurrentHashMap<String, RequestCounter> counters, int limit) {
        long now = System.currentTimeMillis();
        RequestCounter counter = counters.compute(clientIp, (key, existing) -> {
            if (existing == null || existing.isExpired(now)) {
                return new RequestCounter(now);
            }
            return existing;
        });
        return counter.incrementAndCheck(limit);
    }

    /** 可信代理 IP 集合——仅来自这些地址的请求才信任 X-Forwarded-For / X-Real-IP 头 */
    private static final java.util.Set<String> TRUSTED_PROXIES = java.util.Set.of(
            "127.0.0.1", "::1", "0:0:0:0:0:0:0:1"
    );

    private String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (!TRUSTED_PROXIES.contains(remoteAddr)) {
            // 直连请求不信任代理头，防止 IP 伪造绕过限流
            return remoteAddr;
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            String ip = xff.split(",")[0].trim();
            if (!ip.isEmpty()) {
                return ip;
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) {
            return realIp.trim();
        }
        return remoteAddr;
    }

    private void writeRateLimitResponse(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", "60");
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail("请求过于频繁，请稍后再试", null));
    }

    private void periodicCleanup() {
        long now = System.currentTimeMillis();
        if (now - lastCleanup > WINDOW_MS * 2) {
            lastCleanup = now;
            cleanExpired(authCounters, now);
            cleanExpired(apiCounters, now);
        }
    }

    private void cleanExpired(ConcurrentHashMap<String, RequestCounter> counters, long now) {
        counters.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    private static final class RequestCounter {
        private final long windowStart;
        private final AtomicInteger count = new AtomicInteger(0);

        RequestCounter(long windowStart) {
            this.windowStart = windowStart;
        }

        boolean isExpired(long now) {
            return now - windowStart > WINDOW_MS;
        }

        boolean incrementAndCheck(int limit) {
            return count.incrementAndGet() <= limit;
        }
    }
}
