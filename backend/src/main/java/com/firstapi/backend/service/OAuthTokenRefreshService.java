package com.firstapi.backend.service;

import com.firstapi.backend.model.AccountItem;
import com.firstapi.backend.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 定时检测 OAuth 账号 token 过期并用 refresh_token 自动刷新。
 * 也提供 {@link #tryRefreshNow(Long)} 方法供 401 时即时调用。
 */
@Service
public class OAuthTokenRefreshService {

    private static final Logger log = LoggerFactory.getLogger(OAuthTokenRefreshService.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 防止并发刷新同一个账号 */
    private final ConcurrentHashMap<Long, Boolean> refreshingAccounts = new ConcurrentHashMap<>();

    private final AccountRepository accountRepository;
    private final SensitiveDataService sensitiveDataService;
    private final QuotaStateManager quotaStateManager;
    private final ObjectMapper objectMapper;

    @Value("${FIRSTAPI_OAUTH_ANTHROPIC_TOKEN_URL:https://platform.claude.com/v1/oauth/token}")
    private String anthropicTokenUrl;

    @Value("${FIRSTAPI_OAUTH_ANTHROPIC_CLIENT_ID:9d1c250a-e61b-44d9-88ed-5944d1962f5e}")
    private String anthropicClientId;

    @Value("${FIRSTAPI_OAUTH_ANTHROPIC_CLIENT_SECRET:}")
    private String anthropicClientSecret;

    @Value("${FIRSTAPI_OAUTH_ANTHROPIC_BETA_HEADER:oauth-2025-04-20}")
    private String anthropicBetaHeader;

    @Value("${FIRSTAPI_OAUTH_OPENAI_TOKEN_URL:https://auth.openai.com/oauth/token}")
    private String openaiTokenUrl;

    @Value("${FIRSTAPI_OAUTH_OPENAI_CLIENT_ID:app_EMoamEEZ73f0CkXaXp7hrann}")
    private String openaiClientId;

    @Value("${FIRSTAPI_OAUTH_OPENAI_CLIENT_SECRET:}")
    private String openaiClientSecret;

    public OAuthTokenRefreshService(AccountRepository accountRepository,
                                    SensitiveDataService sensitiveDataService,
                                    QuotaStateManager quotaStateManager,
                                    ObjectMapper objectMapper) {
        this.accountRepository = accountRepository;
        this.sensitiveDataService = sensitiveDataService;
        this.quotaStateManager = quotaStateManager;
        this.objectMapper = objectMapper;
    }

    /**
     * 定时扫描：每 30 分钟检查一次，刷新即将在 1 小时内过期的 OAuth token。
     */
    @Scheduled(fixedDelayString = "${FIRSTAPI_OAUTH_REFRESH_CHECK_DELAY_MS:1800000}")
    public void scheduledRefreshCheck() {
        LocalDateTime now = LocalDateTime.now(ZONE);
        LocalDateTime threshold = now.plusHours(1);

        for (AccountItem account : accountRepository.findAll()) {
            if (!"OAuth".equalsIgnoreCase(account.getAuthMethod())) continue;
            if (isBlank(account.getEncryptedRefreshToken())) continue;
            if (isBlank(account.getOauthTokenExpiresAt())) continue;

            try {
                LocalDateTime expiresAt = LocalDateTime.parse(account.getOauthTokenExpiresAt().trim(), DT_FORMAT);
                if (expiresAt.isBefore(threshold)) {
                    log.info("Account {} OAuth token expires at {}, attempting scheduled refresh", account.getId(), account.getOauthTokenExpiresAt());
                    tryRefreshNow(account.getId());
                }
            } catch (Exception e) {
                log.warn("Account {} failed to parse oauth_token_expires_at: {}", account.getId(), account.getOauthTokenExpiresAt());
            }
        }
    }

    /**
     * 即时尝试刷新指定账号的 OAuth token。
     * @return true 如果刷新成功
     */
    public boolean tryRefreshNow(Long accountId) {
        if (accountId == null) return false;

        // 防止并发刷新
        if (refreshingAccounts.putIfAbsent(accountId, Boolean.TRUE) != null) {
            log.info("Account {} refresh already in progress, skipping", accountId);
            return false;
        }

        try {
            AccountItem account = accountRepository.findById(accountId);
            if (account == null) return false;
            if (!"OAuth".equalsIgnoreCase(account.getAuthMethod())) return false;
            if (isBlank(account.getEncryptedRefreshToken())) {
                log.warn("Account {} has no refresh_token, cannot auto-refresh", accountId);
                return false;
            }

            String refreshToken = sensitiveDataService.reveal(account.getEncryptedRefreshToken());
            if (isBlank(refreshToken)) return false;

            String platform = account.getPlatform();
            if (platform != null && (platform.toLowerCase().contains("claude") || platform.toLowerCase().contains("anthropic"))) {
                return refreshAnthropicToken(account, refreshToken);
            }
            if (platform != null && (platform.toLowerCase().contains("openai") || platform.toLowerCase().contains("gpt"))) {
                return refreshOpenAiToken(account, refreshToken);
            }

            log.warn("Account {} unsupported platform for OAuth refresh: {}", accountId, platform);
            return false;
        } catch (Exception e) {
            log.error("Account {} OAuth refresh failed: {}", accountId, e.getMessage(), e);
            return false;
        } finally {
            refreshingAccounts.remove(accountId);
        }
    }

    private boolean refreshAnthropicToken(AccountItem account, String refreshToken) {
        Map<String, Object> requestPayload = new LinkedHashMap<>();
        requestPayload.put("grant_type", "refresh_token");
        requestPayload.put("refresh_token", refreshToken);
        requestPayload.put("client_id", resolveAnthropicClientId());
        if (!isBlank(anthropicClientSecret)) {
            requestPayload.put("client_secret", anthropicClientSecret.trim());
        }

        return executeTokenRefresh(account, anthropicTokenUrl, requestPayload, true);
    }

    private boolean refreshOpenAiToken(AccountItem account, String refreshToken) {
        String tokenUrl = openaiTokenUrl == null ? null : openaiTokenUrl.trim();
        if (isBlank(tokenUrl)) {
            log.error("Account {} OpenAI OAuth refresh skipped: FIRSTAPI_OAUTH_OPENAI_TOKEN_URL is blank", account.getId());
            return false;
        }

        String clientId = resolveOpenAiClientId();
        if (isBlank(clientId)) {
            log.error("Account {} OpenAI OAuth refresh skipped: FIRSTAPI_OAUTH_OPENAI_CLIENT_ID is blank", account.getId());
            return false;
        }

        StringBuilder formBody = new StringBuilder();
        appendFormField(formBody, "grant_type", "refresh_token");
        appendFormField(formBody, "refresh_token", refreshToken);
        appendFormField(formBody, "client_id", clientId);
        if (!isBlank(openaiClientSecret)) {
            appendFormField(formBody, "client_secret", openaiClientSecret.trim());
        }

        return executeTokenRefresh(account, tokenUrl, formBody.toString(), "application/x-www-form-urlencoded", false);
    }

    private boolean executeTokenRefresh(AccountItem account, String tokenUrl, Map<String, Object> requestPayload, boolean isAnthropic) {
        try {
            return executeTokenRefresh(account, tokenUrl, objectMapper.writeValueAsString(requestPayload), "application/json", isAnthropic);
        } catch (Exception e) {
            log.error("Account {} OAuth refresh request failed: {}", account.getId(), e.getMessage(), e);
            return false;
        }
    }

    private boolean executeTokenRefresh(AccountItem account, String tokenUrl, String requestBody, String contentType, boolean isAnthropic) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(tokenUrl).toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", contentType);
            connection.setRequestProperty("Accept", "application/json");
            if (isAnthropic && !isBlank(anthropicBetaHeader)) {
                connection.setRequestProperty("anthropic-beta", anthropicBetaHeader.trim());
            }

            try (OutputStream os = connection.getOutputStream()) {
                os.write(requestBody.getBytes(StandardCharsets.UTF_8));
            }

            int status = connection.getResponseCode();
            String responseBody = readText(status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream());

            log.info("Account {} OAuth refresh response: HTTP {}", account.getId(), status);

            if (status < 200 || status >= 300) {
                log.error("Account {} OAuth refresh failed: HTTP {}, body: {}", account.getId(), status, truncate(responseBody, 300));
                // 刷新失败 → 关闭调度（会员过期 / refresh_token 失效等）
                disableAccount(account, status, responseBody);
                return false;
            }

            JsonNode root = objectMapper.readTree(responseBody);

            // Extract new access token
            String newAccessToken = firstField(root, "access_token", "token", "api_key", "credential");
            if (isBlank(newAccessToken)) {
                log.error("Account {} OAuth refresh response missing access_token", account.getId());
                return false;
            }

            // Extract new refresh token (may be rotated)
            String newRefreshToken = firstField(root, "refresh_token");

            // Extract new expiry
            String newExpiresAt = resolveExpiresAt(root);

            // Update account
            account.setCredential(sensitiveDataService.protect(newAccessToken));
            if (!isBlank(newRefreshToken)) {
                account.setEncryptedRefreshToken(sensitiveDataService.protect(newRefreshToken));
            }
            if (!isBlank(newExpiresAt)) {
                account.setOauthTokenExpiresAt(newExpiresAt);
            }
            accountRepository.update(account.getId(), account);

            // Clear any existing quota exhaustion from oauth_token_expired
            quotaStateManager.clearQuotaStateIfRecovered(account.getId());

            log.info("Account {} OAuth token refreshed successfully, new expiry: {}", account.getId(), newExpiresAt);
            return true;
        } catch (Exception e) {
            log.error("Account {} OAuth refresh request failed: {}", account.getId(), e.getMessage(), e);
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String resolveExpiresAt(JsonNode root) {
        JsonNode expiresAt = root.path("expires_at");
        if (!expiresAt.isMissingNode() && !expiresAt.isNull()) {
            return expiresAt.asText();
        }
        JsonNode expiresIn = root.path("expires_in");
        if (!expiresIn.isMissingNode() && expiresIn.isNumber()) {
            return LocalDateTime.now(ZONE).plusSeconds(expiresIn.asLong()).format(DT_FORMAT);
        }
        return null;
    }

    private String firstField(JsonNode root, String... fieldNames) {
        for (String name : fieldNames) {
            JsonNode node = root.path(name);
            if (!node.isMissingNode() && !node.isNull() && !node.asText().isBlank()) {
                return node.asText();
            }
        }
        return null;
    }

    private String resolveAnthropicClientId() {
        String normalized = anthropicClientId.trim();
        if (normalized.toLowerCase().startsWith("urn:uuid:")) {
            return normalized;
        }
        return normalized;
    }

    private String resolveOpenAiClientId() {
        if (openaiClientId == null) {
            return null;
        }
        return openaiClientId.trim();
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void appendFormField(StringBuilder formBody, String key, String value) {
        if (formBody.length() > 0) {
            formBody.append("&");
        }
        formBody.append(urlEncode(key)).append("=").append(urlEncode(value));
    }

    private static String readText(java.io.InputStream inputStream) {
        if (inputStream == null) return "";
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    /**
     * OAuth token 刷新失败时，自动暂停账号调度。
     * 常见原因：会员过期、refresh_token 被吊销、账号被封。
     */
    private void disableAccount(AccountItem account, int httpStatus, String responseBody) {
        try {
            account.setTempDisabled(true);
            account.setStatus("error");
            accountRepository.update(account.getId(), account);
            log.warn("Account {} OAuth refresh failed (HTTP {}), auto-disabled scheduling. body: {}",
                    account.getId(), httpStatus, truncate(responseBody, 200));
        } catch (Exception e) {
            log.error("Account {} failed to auto-disable after OAuth refresh failure: {}",
                    account.getId(), e.getMessage());
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
