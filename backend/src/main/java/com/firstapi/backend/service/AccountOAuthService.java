package com.firstapi.backend.service;

import com.firstapi.backend.common.CurrentSessionHolder;
import com.firstapi.backend.model.AccountOAuthSession;
import com.firstapi.backend.repository.AccountOAuthSessionRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class AccountOAuthService {
    private static final Logger log = LoggerFactory.getLogger(AccountOAuthService.class);

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter DB_DT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int SESSION_TTL_MINUTES = 10;
    private static final int PKCE_VERIFIER_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String DEFAULT_ANTHROPIC_SCOPE =
            "org:create_api_key user:profile user:inference user:sessions:claude_code user:mcp_servers";

    private final AccountOAuthSessionRepository oauthSessionRepository;
    private final SensitiveDataService sensitiveDataService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${FIRSTAPI_OAUTH_ANTHROPIC_AUTH_URL:https://claude.ai/oauth/authorize}")
    private String anthropicAuthUrl;

    @Value("${FIRSTAPI_OAUTH_ANTHROPIC_TOKEN_URL:https://platform.claude.com/v1/oauth/token}")
    private String anthropicTokenUrl;

    @Value("${FIRSTAPI_OAUTH_ANTHROPIC_CLIENT_ID:9d1c250a-e61b-44d9-88ed-5944d1962f5e}")
    private String anthropicClientId;

    @Value("${FIRSTAPI_OAUTH_ANTHROPIC_CLIENT_SECRET:}")
    private String anthropicClientSecret;

    @Value("${FIRSTAPI_OAUTH_ANTHROPIC_REDIRECT_URI:https://platform.claude.com/oauth/code/callback}")
    private String anthropicRedirectUri;

    @Value("${FIRSTAPI_OAUTH_ANTHROPIC_SCOPE:org:create_api_key user:profile user:inference user:sessions:claude_code user:mcp_servers}")
    private String anthropicScope;

    @Value("${FIRSTAPI_OAUTH_ANTHROPIC_BETA_HEADER:oauth-2025-04-20}")
    private String anthropicBetaHeader;

    @Value("${FIRSTAPI_OAUTH_OPENAI_AUTH_URL:https://auth.openai.com/oauth/authorize}")
    private String openaiAuthUrl;

    @Value("${FIRSTAPI_OAUTH_OPENAI_TOKEN_URL:https://auth.openai.com/oauth/token}")
    private String openaiTokenUrl;

    @Value("${FIRSTAPI_OAUTH_OPENAI_CLIENT_ID:app_EMoamEEZ73f0CkXaXp7hrann}")
    private String openaiClientId;

    @Value("${FIRSTAPI_OAUTH_OPENAI_CLIENT_SECRET:}")
    private String openaiClientSecret;

    @Value("${FIRSTAPI_OAUTH_OPENAI_REDIRECT_URI:http://localhost:1455/auth/callback}")
    private String openaiRedirectUri;

    @Value("${FIRSTAPI_OAUTH_OPENAI_SCOPE:openid profile email offline_access}")
    private String openaiScope;

    @Value("${FIRSTAPI_OAUTH_OPENAI_SIMPLIFIED_FLOW:true}")
    private boolean openaiSimplifiedFlow;

    @Value("${FIRSTAPI_OAUTH_OPENAI_ID_TOKEN_ADD_ORGANIZATIONS:true}")
    private boolean openaiIdTokenAddOrganizations;

    public AccountOAuthService(AccountOAuthSessionRepository oauthSessionRepository,
                               SensitiveDataService sensitiveDataService) {
        this.oauthSessionRepository = oauthSessionRepository;
        this.sensitiveDataService = sensitiveDataService;
    }

    public Map<String, Object> start(Map<String, String> req) {
        String platform = requireParam(req, "platform");
        String accountType = requireParam(req, "accountType");
        String authMethod = requireParam(req, "authMethod");
        if (!"OAuth".equalsIgnoreCase(authMethod)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "oauth/start only accepts authMethod=OAuth");
        }

        String sessionId = "oauth_sess_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String state = generateStateValue();
        String codeVerifier = generatePkceCodeVerifier();
        String codeChallenge = buildCodeChallenge(codeVerifier);
        LocalDateTime now = LocalDateTime.now(ZONE);
        LocalDateTime expiresAt = now.plusMinutes(SESSION_TTL_MINUTES);

        String authorizationUrl = buildAuthorizationUrl(platform, state, codeChallenge);

        AccountOAuthSession session = new AccountOAuthSession();
        session.setSessionId(sessionId);
        session.setStateValue(state);
        session.setPlatform(platform);
        session.setAccountType(accountType);
        session.setAuthMethod(authMethod);
        session.setCodeVerifier(codeVerifier);
        session.setStatusName("PENDING");
        session.setExpiresAt(expiresAt.format(DT_FORMAT));
        session.setCreatedBy(CurrentSessionHolder.require().getId());
        oauthSessionRepository.save(session);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sessionId);
        result.put("state", state);
        result.put("authorizationUrl", authorizationUrl);
        result.put("expiresAt", expiresAt.format(DT_FORMAT));
        return result;
    }

    public Map<String, Object> exchange(Map<String, String> req) {
        String sessionId = requireParam(req, "sessionId");
        String state = requireParam(req, "state");
        String code = requireParam(req, "code");
        Long currentUserId = CurrentSessionHolder.require().getId();

        AccountOAuthSession session = oauthSessionRepository.findBySessionId(sessionId);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "OAuth session not found");
        }
        if (!Objects.equals(session.getCreatedBy(), currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "OAuth session does not belong to current user");
        }
        if (!"PENDING".equals(session.getStatusName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OAuth session status is invalid: " + session.getStatusName());
        }
        if (!state.equals(session.getStateValue())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OAuth state mismatch");
        }

        LocalDateTime expiresAt = parseDateTimeFlexible(session.getExpiresAt(), "OAuth session expiry is invalid");
        if (LocalDateTime.now(ZONE).isAfter(expiresAt)) {
            session.setStatusName("EXPIRED");
            oauthSessionRepository.update(session);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OAuth session expired");
        }

        try {
            OAuthExchangePayload exchangePayload = exchangeCodeForCredential(
                    session.getPlatform(),
                    state,
                    code,
                    session.getCodeVerifier()
            );
            String credential = exchangePayload.credential;
            String encryptedCredential = sensitiveDataService.protect(credential);
            String credentialMask = maskCredential(credential);

            session.setEncryptedCredential(encryptedCredential);
            session.setCredentialMask(credentialMask);
            session.setProviderSubject(exchangePayload.providerSubject);
            session.setStatusName("EXCHANGED");
            session.setExchangedAt(LocalDateTime.now(ZONE).format(DT_FORMAT));
            oauthSessionRepository.update(session);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("credentialRef", sessionId);
            result.put("credentialMask", credentialMask);
            result.put("authMethod", "OAuth");
            Map<String, String> providerAccount = new LinkedHashMap<>();
            providerAccount.put("provider", session.getPlatform());
            providerAccount.put("subject", session.getProviderSubject());
            result.put("providerAccount", providerAccount);
            result.put("expiresAt", exchangePayload.expiresAt);
            return result;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            session.setStatusName("FAILED");
            session.setErrorText(ex.getMessage());
            oauthSessionRepository.update(session);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OAuth code exchange failed: " + ex.getMessage());
        }
    }

    public String consumeCredentialRef(String credentialRef) {
        if (credentialRef == null || credentialRef.trim().isEmpty()) {
            return null;
        }
        Long currentUserId = CurrentSessionHolder.require().getId();
        AccountOAuthSession session = oauthSessionRepository.findBySessionId(credentialRef);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "credentialRef is invalid");
        }
        if (!Objects.equals(session.getCreatedBy(), currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "credentialRef does not belong to current user");
        }
        if (!"EXCHANGED".equals(session.getStatusName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OAuth session has not been exchanged or is already consumed");
        }
        String encrypted = session.getEncryptedCredential();
        session.setStatusName("CONSUMED");
        session.setConsumedAt(LocalDateTime.now(ZONE).format(DT_FORMAT));
        oauthSessionRepository.update(session);
        return encrypted;
    }

    @Scheduled(fixedDelayString = "${FIRSTAPI_OAUTH_SESSION_CLEANUP_DELAY_MS:300000}")
    public void cleanupExpiredSessions() {
        oauthSessionRepository.deleteExpired();
    }

    private OAuthExchangePayload exchangeCodeForCredential(String platform, String state, String code, String codeVerifier) {
        if (isBlank(code)) {
            throw new IllegalArgumentException("OAuth code must not be empty");
        }
        if (isBlank(state)) {
            throw new IllegalArgumentException("OAuth state must not be empty");
        }
        if (isBlank(codeVerifier)) {
            throw new IllegalStateException("OAuth session is missing code verifier");
        }
        if (isAnthropicPlatform(platform)) {
            return exchangeAnthropicCode(state, code.trim(), codeVerifier.trim());
        }
        if (isOpenAiPlatform(platform)) {
            return exchangeOpenAiCode(code.trim(), codeVerifier.trim());
        }
        throw new IllegalArgumentException("OAuth exchange is not supported for platform: " + platform);
    }

    private OAuthExchangePayload exchangeAnthropicCode(String state, String code, String codeVerifier) {
        String tokenUrl = requiredConfig("FIRSTAPI_OAUTH_ANTHROPIC_TOKEN_URL", anthropicTokenUrl);
        String clientId = requireAnthropicClientId();
        String redirectUri = requiredConfig("FIRSTAPI_OAUTH_ANTHROPIC_REDIRECT_URI", anthropicRedirectUri);

        Map<String, Object> requestPayload = new LinkedHashMap<>();
        requestPayload.put("grant_type", "authorization_code");
        requestPayload.put("code", code);
        requestPayload.put("client_id", clientId);
        if (!isBlank(anthropicClientSecret)) {
            requestPayload.put("client_secret", anthropicClientSecret.trim());
        }
        requestPayload.put("redirect_uri", redirectUri);
        requestPayload.put("code_verifier", codeVerifier);
        requestPayload.put("state", state.trim());

        HttpURLConnection connection = null;
        try {
            String requestBody = objectMapper.writeValueAsString(requestPayload);
            connection = (HttpURLConnection) URI.create(tokenUrl).toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            if (!isBlank(anthropicBetaHeader)) {
                connection.setRequestProperty("anthropic-beta", anthropicBetaHeader.trim());
            }

            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(requestBody.getBytes(StandardCharsets.UTF_8));
            }

            int status = connection.getResponseCode();
            String responseBody = readText(status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream());
            Map<String, Object> payload = parseJson(responseBody);

            // Log token endpoint response details for debugging (never log credential values)
            log.info("Anthropic OAuth token response keys: {}, HTTP status: {}", payload.keySet(), status);
            log.info("Anthropic OAuth token_type={}, scope={}, expires_in={}, organization={}, account={}",
                    payload.get("token_type"), payload.get("scope"), payload.get("expires_in"),
                    payload.get("organization"), payload.get("account"));

            if (status < 200 || status >= 300) {
                String providerError = firstNonBlank(payload,
                        "error_description",
                        "error",
                        "message");
                if (isBlank(providerError)) {
                    throw new IllegalStateException("OAuth token endpoint rejected the code (HTTP " + status + ")");
                }
                throw new IllegalStateException("OAuth token endpoint rejected the code (HTTP "
                        + status + "): " + providerError);
            }

            String credential = firstNonBlank(payload,
                    "access_token",
                    "token",
                    "api_key",
                    "setup_token",
                    "credential");
            if (isBlank(credential)) {
                throw new IllegalStateException("OAuth exchange response does not contain a credential token");
            }

            OAuthExchangePayload exchangePayload = new OAuthExchangePayload();
            exchangePayload.credential = credential;
            exchangePayload.providerSubject = firstNonBlank(payload,
                    "provider_subject",
                    "subject",
                    "sub",
                    "organization_id",
                    "org_id");
            exchangePayload.expiresAt = resolveExpiresAt(payload);
            return exchangePayload;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("OAuth token exchange request failed: " + ex.getMessage(), ex);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private OAuthExchangePayload exchangeOpenAiCode(String code, String codeVerifier) {
        String tokenUrl = requiredConfig("FIRSTAPI_OAUTH_OPENAI_TOKEN_URL", openaiTokenUrl);
        String clientId = requiredConfig("FIRSTAPI_OAUTH_OPENAI_CLIENT_ID", openaiClientId);
        String redirectUri = requiredConfig("FIRSTAPI_OAUTH_OPENAI_REDIRECT_URI", openaiRedirectUri);

        StringBuilder formBody = new StringBuilder();
        appendFormField(formBody, "grant_type", "authorization_code");
        appendFormField(formBody, "code", code);
        appendFormField(formBody, "client_id", clientId);
        if (!isBlank(openaiClientSecret)) {
            appendFormField(formBody, "client_secret", openaiClientSecret.trim());
        }
        appendFormField(formBody, "redirect_uri", redirectUri);
        appendFormField(formBody, "code_verifier", codeVerifier);

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(tokenUrl).toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("Accept", "application/json");

            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(formBody.toString().getBytes(StandardCharsets.UTF_8));
            }

            int status = connection.getResponseCode();
            String responseBody = readText(status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream());
            Map<String, Object> payload = parseJson(responseBody);

            if (status < 200 || status >= 300) {
                String providerError = firstNonBlank(payload,
                        "error_description",
                        "error",
                        "message");
                if (isBlank(providerError)) {
                    throw new IllegalStateException("OAuth token endpoint rejected the code (HTTP " + status + ")");
                }
                throw new IllegalStateException("OAuth token endpoint rejected the code (HTTP "
                        + status + "): " + providerError);
            }

            String credential = firstNonBlank(payload,
                    "access_token",
                    "token",
                    "api_key",
                    "credential");
            if (isBlank(credential)) {
                throw new IllegalStateException("OAuth exchange response does not contain a credential token");
            }

            OAuthExchangePayload exchangePayload = new OAuthExchangePayload();
            exchangePayload.credential = credential;
            exchangePayload.providerSubject = firstNonBlank(payload,
                    "sub",
                    "subject",
                    "user_id",
                    "organization_id",
                    "org_id");
            exchangePayload.expiresAt = resolveExpiresAt(payload);
            return exchangePayload;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("OAuth token exchange request failed: " + ex.getMessage(), ex);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String buildAuthorizationUrl(String platform, String state, String codeChallenge) {
        if (isAnthropicPlatform(platform)) {
            String clientId = requireAnthropicClientId();
            String redirectUri = requiredConfig("FIRSTAPI_OAUTH_ANTHROPIC_REDIRECT_URI", anthropicRedirectUri);
            String scope = isBlank(anthropicScope) ? DEFAULT_ANTHROPIC_SCOPE : anthropicScope.trim();

            StringBuilder url = new StringBuilder(anthropicAuthUrl);
            appendQuery(url, "code", "true");
            appendQuery(url, "response_type", "code");
            appendQuery(url, "client_id", clientId);
            appendQuery(url, "redirect_uri", redirectUri);
            appendQuery(url, "scope", scope);
            appendQuery(url, "state", state);
            appendQuery(url, "code_challenge", codeChallenge);
            appendQuery(url, "code_challenge_method", "S256");
            return url.toString();
        }
        if (isOpenAiPlatform(platform)) {
            String authUrl = requiredConfig("FIRSTAPI_OAUTH_OPENAI_AUTH_URL", openaiAuthUrl);
            String clientId = requiredConfig("FIRSTAPI_OAUTH_OPENAI_CLIENT_ID", openaiClientId);
            String redirectUri = requiredConfig("FIRSTAPI_OAUTH_OPENAI_REDIRECT_URI", openaiRedirectUri);
            String scope = isBlank(openaiScope) ? "openid profile email offline_access" : openaiScope.trim();

            StringBuilder url = new StringBuilder(authUrl);
            appendQuery(url, "client_id", clientId);
            appendQuery(url, "code_challenge", codeChallenge);
            appendQuery(url, "code_challenge_method", "S256");
            if (openaiSimplifiedFlow) {
                appendQuery(url, "codex_cli_simplified_flow", "true");
            }
            if (openaiIdTokenAddOrganizations) {
                appendQuery(url, "id_token_add_organizations", "true");
            }
            appendQuery(url, "redirect_uri", redirectUri);
            appendQuery(url, "response_type", "code");
            appendQuery(url, "scope", scope);
            appendQuery(url, "state", state);
            return url.toString();
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OAuth start is not supported for platform: " + platform);
    }

    private String requireAnthropicClientId() {
        String configured = requiredConfig("FIRSTAPI_OAUTH_ANTHROPIC_CLIENT_ID", anthropicClientId);
        String normalized = configured.trim();
        String value = normalized.toLowerCase(Locale.ROOT).startsWith("urn:uuid:")
                ? normalized.substring("urn:uuid:".length())
                : normalized;
        try {
            UUID.fromString(value);
            return normalized;
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("FIRSTAPI_OAUTH_ANTHROPIC_CLIENT_ID must be a valid UUID");
        }
    }

    private String generatePkceCodeVerifier() {
        byte[] randomBytes = new byte[PKCE_VERIFIER_BYTES];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String generateStateValue() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        StringBuilder state = new StringBuilder(randomBytes.length * 2);
        for (byte randomByte : randomBytes) {
            state.append(Character.forDigit((randomByte >>> 4) & 0x0F, 16));
            state.append(Character.forDigit(randomByte & 0x0F, 16));
        }
        return state.toString();
    }

    private String buildCodeChallenge(String codeVerifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to generate PKCE code challenge");
        }
    }

    private String maskCredential(String plain) {
        if (plain == null || plain.trim().isEmpty()) {
            return "-";
        }
        if (plain.length() <= 8) {
            return plain.substring(0, 2) + "****";
        }
        return plain.substring(0, 4) + "****" + plain.substring(plain.length() - 4);
    }

    private String requireParam(Map<String, String> params, String key) {
        String value = params.get(key);
        if (value == null || value.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parameter " + key + " is required");
        }
        return value.trim();
    }

    private boolean isAnthropicPlatform(String platform) {
        if (isBlank(platform)) {
            return false;
        }
        String normalized = platform.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("anthropic") || normalized.contains("claude");
    }

    private boolean isOpenAiPlatform(String platform) {
        if (isBlank(platform)) {
            return false;
        }
        String normalized = platform.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("openai") || normalized.contains("chatgpt") || normalized.contains("gpt");
    }

    private String requiredConfig(String envName, String value) {
        if (!isBlank(value)) {
            return value.trim();
        }
        throw new IllegalArgumentException("Missing OAuth configuration: " + envName);
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void appendQuery(StringBuilder url, String key, String value) {
        if (url.indexOf("?") < 0) {
            url.append("?");
        } else if (!url.toString().endsWith("?") && !url.toString().endsWith("&")) {
            url.append("&");
        }
        url.append(urlEncode(key)).append("=").append(urlEncode(value));
    }

    private void appendFormField(StringBuilder formBody, String key, String value) {
        if (formBody.length() > 0) {
            formBody.append("&");
        }
        formBody.append(urlEncode(key)).append("=").append(urlEncode(value));
    }

    private String readText(InputStream inputStream) {
        if (inputStream == null) {
            return "";
        }
        try (InputStream source = inputStream; ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] data = new byte[1024];
            int read;
            while ((read = source.read(data)) != -1) {
                buffer.write(data, 0, read);
            }
            return buffer.toString(StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return "";
        }
    }

    private Map<String, Object> parseJson(String body) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (isBlank(body)) {
            return result;
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            if (root == null || !root.isObject()) {
                return result;
            }
            root.properties().forEach(entry -> {
                JsonNode value = entry.getValue();
                if (value == null || value.isNull()) {
                    result.put(entry.getKey(), null);
                } else if (value.isTextual()) {
                    result.put(entry.getKey(), value.textValue());
                } else if (value.isBoolean()) {
                    result.put(entry.getKey(), value.booleanValue());
                } else if (value.isIntegralNumber()) {
                    result.put(entry.getKey(), value.longValue());
                } else if (value.isFloatingPointNumber()) {
                    result.put(entry.getKey(), value.doubleValue());
                } else {
                    // Preserve non-scalar JSON payloads as compact JSON strings.
                    result.put(entry.getKey(), value.toString());
                }
            });
        } catch (Exception ignored) {
            // Fall back to an empty map when response body is not valid JSON.
        }
        return result;
    }

    private String firstNonBlank(Map<String, Object> payload, String... keys) {
        if (payload == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            Object value = payload.get(key);
            if (value instanceof String text && !isBlank(text)) {
                // If the value looks like a JSON object, try to extract a nested message
                String trimmed = text.trim();
                if (trimmed.startsWith("{")) {
                    Map<String, Object> nested = parseJson(trimmed);
                    String nestedMsg = nested.containsKey("message") && nested.get("message") instanceof String m && !isBlank(m)
                            ? m.trim() : null;
                    if (nestedMsg != null) {
                        return nestedMsg;
                    }
                }
                return trimmed;
            }
        }
        return null;
    }

    private String resolveExpiresAt(Map<String, Object> payload) {
        String expiresAt = firstNonBlank(payload, "expires_at");
        if (!isBlank(expiresAt)) {
            return expiresAt;
        }
        Object expiresIn = payload.get("expires_in");
        if (expiresIn instanceof Number number) {
            return LocalDateTime.now(ZONE).plusSeconds(number.longValue()).format(DT_FORMAT);
        }
        if (expiresIn instanceof String text) {
            try {
                long seconds = Long.parseLong(text.trim());
                return LocalDateTime.now(ZONE).plusSeconds(seconds).format(DT_FORMAT);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private LocalDateTime parseDateTimeFlexible(String text, String errorMessage) {
        if (isBlank(text)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
        }
        String trimmed = text.trim();
        DateTimeFormatter[] formatters = new DateTimeFormatter[]{
                DT_FORMAT,
                DB_DT_FORMAT,
                DateTimeFormatter.ISO_LOCAL_DATE_TIME
        };
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDateTime.parse(trimmed, formatter);
            } catch (Exception ignored) {
                // Try next formatter.
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
    }

    private String escapeJsonValue(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class OAuthExchangePayload {
        private String credential;
        private String providerSubject;
        private String expiresAt;
    }
}
