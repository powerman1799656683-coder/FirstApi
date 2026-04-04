package com.firstapi.backend.service;

import com.firstapi.backend.config.RelayProperties;
import com.firstapi.backend.model.AccountItem;
import com.firstapi.backend.model.CooldownEntry;
import com.firstapi.backend.model.IpItem;
import com.firstapi.backend.model.RelayResult;
import com.firstapi.backend.repository.AccountRepository;
import com.firstapi.backend.repository.IpRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scheduled service that probes accounts in cooldown to detect early recovery.
 * <p>
 * Two probe paths:
 * <ul>
 *   <li><b>Memory cooldown</b> (rate limit / overload): probes at {@code entry.probeAt},
 *       clears in-memory cooldown on success.</li>
 *   <li><b>Persistent cooldown</b> (quota exhausted): probes at {@code quotaNextRetryAt - 10s},
 *       clears DB quota state on success.</li>
 * </ul>
 */
@Service
public class CooldownProbeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CooldownProbeService.class);
    private static final DateTimeFormatter QUOTA_RETRY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter QUOTA_RETRY_FALLBACK = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    private static final DateTimeFormatter EXPIRY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private static final Set<String> QUOTA_ERROR_TYPES = Set.of(
            "insufficient_quota",
            "billing_hard_limit_reached",
            "quota_exceeded"
    );

    /** Tracks which persistent-cooldown accounts are currently being probed. */
    private final ConcurrentHashMap<Long, Boolean> persistentProbing = new ConcurrentHashMap<>();

    private final RelayAccountSelector relayAccountSelector;
    private final AccountRepository accountRepository;
    private final QuotaStateManager quotaStateManager;
    private final SensitiveDataService sensitiveDataService;
    private final UpstreamHttpClient upstreamHttpClient;
    private final RelayProperties relayProperties;
    private final IpRepository ipRepository;
    private final ObjectMapper objectMapper;
    private final OpenAiRelayAdapter openAiRelayAdapter;
    private final ClaudeRelayAdapter claudeRelayAdapter;

    public CooldownProbeService(RelayAccountSelector relayAccountSelector,
                                AccountRepository accountRepository,
                                QuotaStateManager quotaStateManager,
                                SensitiveDataService sensitiveDataService,
                                UpstreamHttpClient upstreamHttpClient,
                                RelayProperties relayProperties,
                                IpRepository ipRepository,
                                ObjectMapper objectMapper,
                                OpenAiRelayAdapter openAiRelayAdapter,
                                ClaudeRelayAdapter claudeRelayAdapter) {
        this.relayAccountSelector = relayAccountSelector;
        this.accountRepository = accountRepository;
        this.quotaStateManager = quotaStateManager;
        this.sensitiveDataService = sensitiveDataService;
        this.upstreamHttpClient = upstreamHttpClient;
        this.relayProperties = relayProperties;
        this.ipRepository = ipRepository;
        this.objectMapper = objectMapper;
        this.openAiRelayAdapter = openAiRelayAdapter;
        this.claudeRelayAdapter = claudeRelayAdapter;
    }

    @Scheduled(fixedDelayString = "${app.relay.probe-interval-ms:10000}", initialDelay = 30000)
    public void probeCooldownAccounts() {
        if (!relayProperties.isProbeEnabled()) {
            return;
        }
        probeMemoryCooldowns();
        probePersistentCooldowns();
    }

    // ── Memory cooldown probes ─────────────────────────────────────────

    private void probeMemoryCooldowns() {
        Instant now = Instant.now();
        ConcurrentHashMap<Long, CooldownEntry> entries = relayAccountSelector.getCooldownEntries();

        for (Map.Entry<Long, CooldownEntry> mapEntry : entries.entrySet()) {
            Long accountId = mapEntry.getKey();
            CooldownEntry entry = mapEntry.getValue();

            // Skip if not yet time to probe, or already expired, or already probing
            if (entry.getProbeAt() == null || now.isBefore(entry.getProbeAt())) {
                continue;
            }
            if (now.isAfter(entry.getUntil())) {
                // Already expired, will be cleaned up by getCooldownUntil()
                continue;
            }
            if (!entry.tryStartProbing()) {
                continue;
            }

            try {
                AccountItem account = accountRepository.findById(accountId);
                if (account == null) {
                    relayAccountSelector.removeCooldown(accountId);
                    continue;
                }

                String provider = resolveProvider(entry, account);
                RelayResult probeResult = sendProbe(account, provider);
                handleMemoryProbeResult(accountId, account, probeResult, provider);
            } catch (Exception e) {
                LOGGER.debug("Memory probe failed for account {}: {}", accountId, e.getMessage());
            } finally {
                entry.finishProbing();
            }
        }
    }

    private void handleMemoryProbeResult(Long accountId, AccountItem account, RelayResult result, String provider) {
        if (result.isSuccess()) {
            relayAccountSelector.removeCooldown(accountId);
            LOGGER.info("Account {} memory probe succeeded, cooldown cleared", accountId);
            return;
        }

        int status = result.getStatusCode();
        String errorType = extractErrorType(result.getBody());

        if (status == 429 && isQuotaError(errorType, result)) {
            // OAuth/subscription accounts don't have credit balance — skip persistent cooldown
            if ("OAuth".equalsIgnoreCase(account.getAuthMethod())) {
                LOGGER.warn("Account {} OAuth probe 429 with quota-like message, keeping as memory cooldown", accountId);
            } else {
                // Quota exhaustion discovered — escalate to persistent cooldown
                relayAccountSelector.removeCooldown(accountId);
                markQuotaExhausted(accountId, provider + "_insufficient_quota");
                LOGGER.warn("Account {} probe revealed quota exhaustion, moved to persistent cooldown", accountId);
                return;
            }
        }

        if (status == 429) {
            // Still rate-limited — recalculate cooldown with new retry-after
            int seconds = parseRetryAfterSeconds(result);
            relayAccountSelector.cooldownAccount(accountId, seconds, "rate_limit", provider);
            LOGGER.info("Account {} probe still rate-limited, re-cooldown {}s", accountId, seconds);
            return;
        }

        if (status == 401 || status == 403) {
            // Keep cooldown active — credential is invalid, needs admin re-authentication
            relayAccountSelector.cooldownAccount(accountId, 600, "auth_error_" + status, provider);
            LOGGER.warn("Account {} probe returned {}, re-cooldown 600s, needs admin review", accountId, status);
            return;
        }

        // 503/500/other: keep current cooldown, will auto-expire at recover_at
        LOGGER.debug("Account {} probe returned {}, keeping cooldown", accountId, status);
    }

    // ── Persistent cooldown probes ─────────────────────────────────────

    private void probePersistentCooldowns() {
        Instant now = Instant.now();
        for (AccountItem account : accountRepository.findByQuotaExhausted(true)) {
            LocalDateTime retryAt = parseQuotaRetryAt(account.getQuotaNextRetryAt());
            if (retryAt == null) {
                continue; // No retry time — stays locked until admin
            }
            // Probe 10 seconds before retry time
            Instant probeAt = toInstant(retryAt).minusSeconds(10);
            if (now.isBefore(probeAt)) {
                continue;
            }

            Long accountId = account.getId();
            if (persistentProbing.putIfAbsent(accountId, Boolean.TRUE) != null) {
                continue; // Another thread is already probing this account
            }

            try {
                String provider = resolveProviderFromPlatform(account.getPlatform());
                RelayResult probeResult = sendProbe(account, provider);
                handlePersistentProbeResult(accountId, account, probeResult, provider);
            } catch (Exception e) {
                LOGGER.debug("Persistent probe failed for account {}: {}", accountId, e.getMessage());
            } finally {
                persistentProbing.remove(accountId);
            }
        }
    }

    private void handlePersistentProbeResult(Long accountId, AccountItem account, RelayResult result, String provider) {
        if (result.isSuccess()) {
            clearQuotaState(accountId, account);
            LOGGER.info("Account {} persistent probe succeeded, quota cooldown cleared", accountId);
            return;
        }

        int status = result.getStatusCode();
        String errorType = extractErrorType(result.getBody());

        if (status == 429 && isQuotaError(errorType, result)) {
            // Still exhausted — increment fail count and extend backoff
            markQuotaExhausted(accountId, provider + "_insufficient_quota");
            LOGGER.info("Account {} probe still quota-exhausted, backoff extended", accountId);
            return;
        }

        if (status == 429) {
            // Rate-limited but NOT quota error — quota has recovered!
            clearQuotaState(accountId, account);
            int seconds = parseRetryAfterSeconds(result);
            relayAccountSelector.cooldownAccount(accountId, seconds, "rate_limit", provider);
            LOGGER.info("Account {} quota recovered but rate-limited, switched to memory cooldown {}s", accountId, seconds);
            return;
        }

        if (status == 401 || status == 403) {
            // Don't clear quota state — credential is still invalid
            LOGGER.warn("Account {} persistent probe returned {}, keeping quota-exhausted, needs admin review", accountId, status);
            return;
        }

        // 503/500/other: don't change state, wait for next probe cycle
        LOGGER.debug("Account {} persistent probe returned {}, keeping quota cooldown", accountId, status);
    }

    // ── Probe request construction ─────────────────────────────────────

    private RelayResult sendProbe(AccountItem account, String provider) {
        IpItem proxy = resolveProxySafe(account);
        // 复用 Adapter 的 probeAccount()，确保与正常中转路径完全一致
        // （含 OAuth 账号的正确端点、模型、请求头）
        if ("claude".equals(provider)) {
            return claudeRelayAdapter.probeAccount(account, proxy);
        } else if ("openai".equals(provider)) {
            return openAiRelayAdapter.probeAccount(account, proxy);
        } else {
            // 其他平台：通用 chat/completions
            String credential = sensitiveDataService.reveal(account.getCredential());
            String baseUrl = relayAccountSelector.resolveBaseUrl(account, provider);
            int readTimeout = relayProperties.getProbeReadTimeoutMs();
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put(HttpHeaders.AUTHORIZATION, "Bearer " + credential);
            return upstreamHttpClient.postJson(baseUrl + "/v1/chat/completions", headers,
                    buildOpenAiProbePayload(), proxy, readTimeout);
        }
    }

    private JsonNode buildOpenAiProbePayload() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", relayProperties.getProbeOpenaiModel());
        root.put("max_tokens", 1);
        ArrayNode messages = root.putArray("messages");
        ObjectNode msg = messages.addObject();
        msg.put("role", "user");
        msg.put("content", "hi");
        return root;
    }

    private void markQuotaExhausted(Long accountId, String reason) {
        quotaStateManager.markQuotaExhausted(accountId, reason);
    }

    private void clearQuotaState(Long accountId, AccountItem account) {
        quotaStateManager.clearQuotaState(accountId, account);
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private boolean isQuotaError(String errorType, RelayResult result) {
        if (errorType != null && QUOTA_ERROR_TYPES.contains(errorType.toLowerCase(Locale.ROOT))) {
            return true;
        }
        String errorCode = extractErrorCode(result.getBody());
        return errorCode != null && QUOTA_ERROR_TYPES.contains(errorCode.toLowerCase(Locale.ROOT));
    }

    private String extractErrorType(String body) {
        if (body == null || body.isEmpty()) return null;
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode errorType = root.path("error").path("type");
            return errorType.isMissingNode() || errorType.isNull() ? null : errorType.asText();
        } catch (Exception e) {
            return null;
        }
    }

    private String extractErrorCode(String body) {
        if (body == null || body.isEmpty()) return null;
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode code = root.path("error").path("code");
            return code.isMissingNode() || code.isNull() ? null : code.asText();
        } catch (Exception e) {
            return null;
        }
    }

    private int parseRetryAfterSeconds(RelayResult result) {
        return com.firstapi.backend.util.RetryAfterParser.parseSeconds(result);
    }

    private String resolveProvider(CooldownEntry entry, AccountItem account) {
        if (entry.getProvider() != null) {
            return entry.getProvider();
        }
        return resolveProviderFromPlatform(account.getPlatform());
    }

    private String resolveProviderFromPlatform(String platform) {
        if (platform == null) return "openai";
        String normalized = platform.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("claude") || normalized.contains("anthropic")) {
            return "claude";
        }
        return "openai";
    }

    private IpItem resolveProxySafe(AccountItem account) {
        Long proxyId = account == null ? null : account.getProxyId();
        if (proxyId == null || proxyId <= 0L) {
            return null;
        }
        try {
            IpItem proxy = ipRepository.findById(proxyId);
            if (proxy == null) return null;
            String status = proxy.getStatus();
            if (status != null) {
                String normalized = status.trim().toLowerCase(Locale.ROOT);
                if ("disabled".equals(normalized) || "error".equals(normalized)
                        || "offline".equals(normalized)
                        || "\u7981\u7528".equals(normalized)
                        || "\u5f02\u5e38".equals(normalized)) {
                    return null;
                }
            }
            return proxy;
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDateTime parseQuotaRetryAt(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String normalized = value.trim();
        try {
            return LocalDateTime.parse(normalized, QUOTA_RETRY_FORMAT);
        } catch (DateTimeParseException ignored) {}
        try {
            return LocalDateTime.parse(normalized, QUOTA_RETRY_FALLBACK);
        } catch (DateTimeParseException ignored) {}
        try {
            return LocalDateTime.parse(normalized, EXPIRY_FORMAT);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private Instant toInstant(LocalDateTime ldt) {
        return ldt.atZone(java.time.ZoneId.systemDefault()).toInstant();
    }
}
