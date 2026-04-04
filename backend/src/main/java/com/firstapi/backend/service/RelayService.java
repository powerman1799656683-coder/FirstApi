package com.firstapi.backend.service;

import com.firstapi.backend.model.ApiKeyItem;
import com.firstapi.backend.model.GroupItem;
import com.firstapi.backend.model.RelayChatCompletionRequest;
import com.firstapi.backend.model.RelayException;
import com.firstapi.backend.model.RelayResult;
import com.firstapi.backend.model.RelayRoute;
import com.firstapi.backend.repository.GroupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

@Service
public class RelayService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RelayService.class);

    /** Official error type/code values that indicate true quota exhaustion. */
    private static final Set<String> QUOTA_ERROR_TYPES = Set.of(
            "insufficient_quota",       // OpenAI error type/code
            "billing_hard_limit_reached",// OpenAI billing limit
            "quota_exceeded"            // generic
    );

    /** Keywords to match inside error.message only (not full body). */
    private static final List<String> QUOTA_MESSAGE_KEYWORDS = List.of(
            "insufficient_quota",
            "quota_exceeded",
            "quota exceeded",
            "credit balance is too low",
            "billing hard limit has been reached",
            "额度不足",
            "余额不足"
    );

    private final RelayApiKeyAuthService relayApiKeyAuthService;
    private final RelayModelRouter relayModelRouter;
    private final OpenAiRelayAdapter openAiRelayAdapter;
    private final ClaudeRelayAdapter claudeRelayAdapter;
    private final RelayRecordService relayRecordService;
    private final RelayAccountSelector relayAccountSelector;
    private final QuotaStateManager quotaStateManager;
    private final GroupRepository groupRepository;
    private final ObjectMapper objectMapper;
    private final UserService userService;
    private final SubscriptionService subscriptionService;
    private final DailyQuotaService dailyQuotaService;
    private final OAuthTokenRefreshService oauthTokenRefreshService;

    public RelayService(RelayApiKeyAuthService relayApiKeyAuthService,
                        RelayModelRouter relayModelRouter,
                        OpenAiRelayAdapter openAiRelayAdapter,
                        ClaudeRelayAdapter claudeRelayAdapter,
                        RelayRecordService relayRecordService,
                        RelayAccountSelector relayAccountSelector,
                        QuotaStateManager quotaStateManager,
                        GroupRepository groupRepository,
                        ObjectMapper objectMapper,
                        UserService userService,
                        SubscriptionService subscriptionService,
                        DailyQuotaService dailyQuotaService,
                        OAuthTokenRefreshService oauthTokenRefreshService) {
        this.relayApiKeyAuthService = relayApiKeyAuthService;
        this.relayModelRouter = relayModelRouter;
        this.openAiRelayAdapter = openAiRelayAdapter;
        this.claudeRelayAdapter = claudeRelayAdapter;
        this.relayRecordService = relayRecordService;
        this.relayAccountSelector = relayAccountSelector;
        this.quotaStateManager = quotaStateManager;
        this.groupRepository = groupRepository;
        this.objectMapper = objectMapper;
        this.userService = userService;
        this.subscriptionService = subscriptionService;
        this.dailyQuotaService = dailyQuotaService;
        this.oauthTokenRefreshService = oauthTokenRefreshService;
    }

    // ==================== 流式转发入口 ====================

    /**
     * 流式 Responses API：认证/计费后，通过 outputStream 直接管道转发 SSE。
     * 流结束后执行 record + syncQuotaRuntimeState。
     */
    public RelayResult relayResponsesApiStreaming(String authorization, JsonNode requestBody,
                                                   java.io.OutputStream outputStream) {
        String model = requestBody.path("model").asText(null);
        if (model == null || model.trim().isEmpty()) {
            throw new RelayException(HttpStatus.BAD_REQUEST, "Model is required", "invalid_request");
        }

        ApiKeyItem apiKey = relayApiKeyAuthService.authenticate(authorization);
        RelayRoute route = relayModelRouter.route(model);
        GroupItem group = resolveGroup(apiKey);
        checkBilling(apiKey, group);
        validateGroupPlatform(route, group);
        String groupAccountType = resolveGroupAccountType(group);

        RelayExecutionOutcome outcome;
        try {
            if ("openai".equals(route.getProvider())) {
                outcome = executeWithSingleOauthRefreshRetry(() ->
                        openAiRelayAdapter.relayResponsesStreaming(requestBody, route, groupAccountType, outputStream));
            } else {
                throw new RelayException(HttpStatus.BAD_REQUEST, "Unsupported model for responses API", "unsupported_model");
            }
        } finally {
            // 确保无论是否出现异常，都尝试 flush 输出流，避免客户端悬挂
            try { outputStream.flush(); } catch (java.io.IOException ignored) {}
        }

        RelayResult result = outcome.result();
        relayRecordService.record(apiKey, route, result, model, group);
        syncQuotaRuntimeState(result, outcome.oauthRefreshAttempted(), outcome.oauthRefreshSucceeded());
        return result;
    }

    /**
     * 流式 Chat Completions (OpenAI 模型)：管道转发 SSE。
     * Claude 模型仍需缓冲（格式转换），走非流式路径。
     */
    public RelayResult relayChatCompletionStreaming(String authorization, RelayChatCompletionRequest request,
                                                     java.io.OutputStream outputStream) {
        RelayRoute route = validateAndResolveChatCompletionRoute(request);

        ApiKeyItem apiKey = relayApiKeyAuthService.authenticate(authorization);
        GroupItem group = resolveGroup(apiKey);
        checkBilling(apiKey, group);
        validateGroupPlatform(route, group);
        String groupAccountType = resolveGroupAccountType(group);

        RelayExecutionOutcome outcome;
        try {
            if ("openai".equals(route.getProvider())) {
                outcome = executeWithSingleOauthRefreshRetry(() ->
                        openAiRelayAdapter.relayStreaming(request, route, groupAccountType, outputStream));
            } else {
                // Claude 模型：流式需要格式转换，仍走同步缓冲路径
                return relayChatCompletion(authorization, request);
            }
        } finally {
            // 确保无论是否出现异常，都尝试 flush 输出流，避免客户端悬挂
            try { outputStream.flush(); } catch (java.io.IOException ignored) {}
        }

        RelayResult result = outcome.result();
        relayRecordService.record(apiKey, route, result, request.getModel(), group);
        syncQuotaRuntimeState(result, outcome.oauthRefreshAttempted(), outcome.oauthRefreshSucceeded());
        return result;
    }

    /**
     * 流式 Claude /v1/messages：原生格式直转发。
     */
    public RelayResult relayClaudeMessagesStreaming(String xApiKey, String authorization,
                                                     String anthropicVersion, JsonNode requestBody,
                                                     java.io.OutputStream outputStream) {
        validateClaudeMessagesRequest(requestBody);

        String model = requestBody.path("model").asText(null);
        ApiKeyItem apiKey = relayApiKeyAuthService.authenticateFlexible(authorization, xApiKey);
        RelayRoute route = relayModelRouter.route(model);
        if (!"claude".equals(route.getProvider())) {
            throw new RelayException(HttpStatus.BAD_REQUEST, "Unsupported model", "unsupported_model");
        }

        GroupItem group = resolveGroup(apiKey);
        checkBilling(apiKey, group);
        validateGroupPlatform(route, group);
        String groupAccountType = resolveGroupAccountType(group);

        RelayExecutionOutcome outcome;
        try {
            outcome = executeWithSingleOauthRefreshRetry(() ->
                    claudeRelayAdapter.relayMessagesStreaming(
                            requestBody, route, groupAccountType, anthropicVersion, outputStream));
        } finally {
            // 确保无论是否出现异常，都尝试 flush 输出流，避免客户端悬挂
            try { outputStream.flush(); } catch (java.io.IOException ignored) {}
        }

        RelayResult result = outcome.result();
        relayRecordService.record(apiKey, route, result, model, group);
        syncQuotaRuntimeState(result, outcome.oauthRefreshAttempted(), outcome.oauthRefreshSucceeded());
        return result;
    }

    // ==================== 非流式转发入口 ====================

    public RelayResult relayResponsesApi(String authorization, JsonNode requestBody) {
        String model = requestBody.path("model").asText(null);
        if (model == null || model.trim().isEmpty()) {
            throw new RelayException(HttpStatus.BAD_REQUEST, "Model is required", "invalid_request");
        }

        ApiKeyItem apiKey = relayApiKeyAuthService.authenticate(authorization);
        RelayRoute route = relayModelRouter.route(model);
        GroupItem group = resolveGroup(apiKey);
        checkBilling(apiKey, group);
        validateGroupPlatform(route, group);
        String groupAccountType = resolveGroupAccountType(group);

        RelayExecutionOutcome outcome;
        if ("openai".equals(route.getProvider())) {
            outcome = executeWithSingleOauthRefreshRetry(() ->
                    openAiRelayAdapter.relayResponses(requestBody, route, groupAccountType));
        } else {
            throw new RelayException(HttpStatus.BAD_REQUEST, "Unsupported model for responses API", "unsupported_model");
        }

        RelayResult result = outcome.result();
        relayRecordService.record(apiKey, route, result, model, group);
        syncQuotaRuntimeState(result, outcome.oauthRefreshAttempted(), outcome.oauthRefreshSucceeded());
        return result;
    }

    public RelayResult relayChatCompletion(String authorization, RelayChatCompletionRequest request) {
        RelayRoute route = validateAndResolveChatCompletionRoute(request);

        ApiKeyItem apiKey = relayApiKeyAuthService.authenticate(authorization);
        GroupItem group = resolveGroup(apiKey);
        checkBilling(apiKey, group);
        validateGroupPlatform(route, group);
        String groupAccountType = resolveGroupAccountType(group);
        RelayExecutionOutcome outcome;
        if ("openai".equals(route.getProvider())) {
            outcome = executeWithSingleOauthRefreshRetry(() ->
                    openAiRelayAdapter.relay(request, route, groupAccountType));
        } else if ("claude".equals(route.getProvider())) {
            outcome = executeWithSingleOauthRefreshRetry(() ->
                    claudeRelayAdapter.relay(request, route, groupAccountType));
        } else {
            throw new RelayException(HttpStatus.BAD_REQUEST, "Unsupported model", "unsupported_model");
        }
        RelayResult result = outcome.result();
        relayRecordService.record(apiKey, route, result, request.getModel(), group);
        syncQuotaRuntimeState(result, outcome.oauthRefreshAttempted(), outcome.oauthRefreshSucceeded());
        return result;
    }

    public RelayResult relayClaudeMessages(String xApiKey, String authorization, String anthropicVersion, JsonNode requestBody) {
        validateClaudeMessagesRequest(requestBody);

        String model = requestBody.path("model").asText(null);
        ApiKeyItem apiKey = relayApiKeyAuthService.authenticateFlexible(authorization, xApiKey);
        RelayRoute route = relayModelRouter.route(model);
        if (!"claude".equals(route.getProvider())) {
            throw new RelayException(HttpStatus.BAD_REQUEST, "Unsupported model", "unsupported_model");
        }

        GroupItem group = resolveGroup(apiKey);
        checkBilling(apiKey, group);
        validateGroupPlatform(route, group);
        String groupAccountType = resolveGroupAccountType(group);

        RelayExecutionOutcome outcome = executeWithSingleOauthRefreshRetry(() ->
                claudeRelayAdapter.relayMessages(requestBody, route, groupAccountType, anthropicVersion));
        RelayResult result = outcome.result();
        relayRecordService.record(apiKey, route, result, model, group);
        syncQuotaRuntimeState(result, outcome.oauthRefreshAttempted(), outcome.oauthRefreshSucceeded());
        return result;
    }

    /** Default cooldown (seconds) when retry-after header is missing for rate-limit errors. */
    private static final int DEFAULT_RATE_LIMIT_COOLDOWN_SECONDS = 120;
    /** Short cooldown (seconds) for platform overload (529/503). */
    private static final int OVERLOAD_COOLDOWN_SECONDS = 30;

    private RelayExecutionOutcome executeWithSingleOauthRefreshRetry(Supplier<RelayResult> upstreamCall) {
        RelayResult firstResult = upstreamCall.get();
        if (!isRetryableOauthAuthenticationFailure(firstResult)) {
            return new RelayExecutionOutcome(firstResult, false, false);
        }

        boolean refreshed = oauthTokenRefreshService.tryRefreshNow(firstResult.getAccountId());
        if (!refreshed) {
            return new RelayExecutionOutcome(firstResult, true, false);
        }

        LOGGER.info("Account {} OAuth token refreshed successfully after 401, retrying request once, provider={}",
                firstResult.getAccountId(), firstResult.getProvider());
        RelayResult retryResult = upstreamCall.get();
        return new RelayExecutionOutcome(retryResult, true, true);
    }

    private boolean isRetryableOauthAuthenticationFailure(RelayResult result) {
        if (result == null || result.getAccountId() == null) {
            return false;
        }
        if (result.getStatusCode() != 401) {
            return false;
        }
        if (!"OAuth".equalsIgnoreCase(result.getAuthMethod())) {
            return false;
        }
        return isOAuthTokenExpiredResponse(result.getBody());
    }

    /**
     * 判断 401 响应体是否表示 OAuth token 过期/失效。
     * 兼容多种格式：
     * - Anthropic: {"error":{"type":"authentication_error"}}
     * - OpenAI API: {"error":{"type":"authentication_error","code":"invalid_api_key"}}
     * - ChatGPT OAuth: {"error":{"type":null,"code":"token_expired"}}
     * - ChatGPT detail: {"detail":{"code":"token_expired"}}
     */
    private boolean isOAuthTokenExpiredResponse(String body) {
        if (body == null || body.isEmpty()) return true; // 无 body 的 401 也视为 token 过期
        String errorType = extractErrorType(body);
        if ("authentication_error".equalsIgnoreCase(errorType)) {
            return true;
        }
        String errorCode = extractErrorCode(body);
        if ("token_expired".equalsIgnoreCase(errorCode) || "invalid_api_key".equalsIgnoreCase(errorCode)) {
            return true;
        }
        // ChatGPT detail.code 格式
        try {
            JsonNode root = objectMapper.readTree(body);
            String detailCode = root.path("detail").path("code").asText(null);
            if ("token_expired".equalsIgnoreCase(detailCode)) {
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void syncQuotaRuntimeState(RelayResult result) {
        syncQuotaRuntimeState(result, false, false);
    }

    private void syncQuotaRuntimeState(RelayResult result,
                                       boolean oauthRefreshAttempted,
                                       boolean oauthRefreshSucceeded) {
        if (result == null || result.getAccountId() == null) {
            return;
        }

        // --- ChatGPT OAuth usage limit detection (may come on any status code, including 200) ---
        // Only apply to OpenAI provider — Claude/other upstream 429s may contain generic
        // keywords like "too many requests" that would bypass the OAuth protection in handle429.
        if ("openai".equals(result.getProvider())
                && com.firstapi.backend.util.ChatGptUsageLimitDetector.isUsageLimitResponse(result.getBody())) {
            int recoverySecs = com.firstapi.backend.util.ChatGptUsageLimitDetector
                    .parseRecoveryCooldownSeconds(result.getBody());
            if (recoverySecs > 0) {
                // Use the exact recovery time from the response
                quotaStateManager.markQuotaExhaustedWithCooldown(
                        result.getAccountId(), "chatgpt_usage_limit", recoverySecs);
            } else {
                // No parseable recovery time, use default exponential backoff
                markQuotaExhausted(result.getAccountId(), "chatgpt_usage_limit");
            }
            LOGGER.warn("Account {} ChatGPT usage limit detected, cooldown={}s, body snippet: {}",
                    result.getAccountId(), recoverySecs > 0 ? recoverySecs : "exponential",
                    truncate(result.getBody(), 200));
            return;
        }

        if (result.isSuccess()) {
            clearQuotaStateIfRecovered(result.getAccountId());
            return;
        }

        int status = result.getStatusCode();
        String provider = result.getProvider();
        String errorType = extractErrorType(result.getBody());

        // --- 402 billing error (Claude) / payment required ---
        if (status == 402) {
            markQuotaExhausted(result.getAccountId(), "billing_error:" + nullSafe(errorType));
            return;
        }

        // --- 429 handling: provider-aware ---
        if (status == 429) {
            handle429(result, provider, errorType);
            return;
        }

        // --- 529 (Claude overload) / 503 (OpenAI overload) ---
        if (status == 529 || status == 503) {
            // Short in-memory cooldown to reduce pressure on overloaded upstream
            relayAccountSelector.cooldownAccount(result.getAccountId(), OVERLOAD_COOLDOWN_SECONDS, "overload", provider);
            LOGGER.info("Account {} short cooldown {}s due to upstream overload ({})",
                    result.getAccountId(), OVERLOAD_COOLDOWN_SECONDS, status);
            return;
        }

        if (status == 401) {
            handleAuthenticationFailure(result, provider, errorType, oauthRefreshAttempted, oauthRefreshSucceeded);
            return;
        }

        // Other errors: check for quota keywords in error body (existing logic)
        String reason = detectQuotaReason(result);
        if (reason != null) {
            markQuotaExhausted(result.getAccountId(), reason);
        }
    }

    private void handle429(RelayResult result, String provider, String errorType) {
        Long accountId = result.getAccountId();

        if ("openai".equals(provider)) {
            // OpenAI distinguishes insufficient_quota vs rate_limit_exceeded
            boolean isQuotaExhausted = (errorType != null && QUOTA_ERROR_TYPES.contains(errorType.toLowerCase(Locale.ROOT)))
                    || isQuotaErrorCode(result.getBody());
            if (isQuotaExhausted) {
                // Quota exhaustion: long cooldown, needs manual intervention (recharge)
                markQuotaExhausted(accountId, "openai_insufficient_quota");
                return;
            }
            // OpenAI rate limit: use retry-after with fallback
            int cooldownSeconds = parseRetryAfterSeconds(result);
            relayAccountSelector.cooldownAccount(accountId, cooldownSeconds, "openai_rate_limit", "openai");
            LOGGER.info("Account {} OpenAI rate-limited, cooldown {}s", accountId, cooldownSeconds);
            return;
        }

        if ("claude".equals(provider)) {
            // Claude 429: all are rate_limit_error, cannot distinguish quota vs rate limit by type alone.
            // Must inspect message content to detect true quota exhaustion.
            boolean quotaLike = "billing_error".equals(errorType) || isQuotaMessageInBody(result.getBody());
            boolean isOAuth = "OAuth".equalsIgnoreCase(result.getAuthMethod());

            if (quotaLike && !isOAuth) {
                // API Key accounts: quota exhaustion triggers persistent cooldown
                String reason = "billing_error".equals(errorType) ? "claude_billing_error" : "claude_credit_exhausted";
                markQuotaExhausted(accountId, reason);
                return;
            }
            if (quotaLike && isOAuth) {
                // OAuth/subscription accounts don't have credit balance — treat as rate limit
                LOGGER.warn("Account {} Claude OAuth 429 with quota-like message, treating as rate limit. body: {}",
                        accountId, truncate(result.getBody(), 300));
            }
            // Rate limit: use retry-after with fallback
            int cooldownSeconds = parseRetryAfterSeconds(result);
            relayAccountSelector.cooldownAccount(accountId, cooldownSeconds, "claude_rate_limit", "claude");
            LOGGER.info("Account {} Claude rate-limited, cooldown {}s", accountId, cooldownSeconds);
            return;
        }

        // Unknown provider: use retry-after with fallback
        int cooldownSeconds = parseRetryAfterSeconds(result);
        relayAccountSelector.cooldownAccount(accountId, cooldownSeconds, "rate_limit", provider);
        LOGGER.info("Account {} rate-limited (provider={}), cooldown {}s", accountId, provider, cooldownSeconds);
    }

    private int parseRetryAfterSeconds(RelayResult result) {
        return com.firstapi.backend.util.RetryAfterParser.parseSeconds(result);
    }

    private void handleAuthenticationFailure(RelayResult result,
                                             String provider,
                                             String errorType,
                                             boolean oauthRefreshAttempted,
                                             boolean oauthRefreshSucceeded) {
        // 非 OAuth 账号的 401 不做自动处理
        if (!"OAuth".equalsIgnoreCase(result.getAuthMethod())) {
            LOGGER.warn("Account {} received 401, provider={}, errorType={}, authMethod={}, no action taken",
                    result.getAccountId(), provider, errorType, result.getAuthMethod());
            return;
        }

        // OAuth 账号 401 → 尝试刷新 token 或暂停调度
        if (oauthRefreshAttempted) {
            markQuotaExhausted(result.getAccountId(), "oauth_token_expired");
            if (oauthRefreshSucceeded) {
                LOGGER.warn("Account {} still received OAuth 401 after refresh retry, auto-disabled scheduling, provider={}",
                        result.getAccountId(), provider);
            } else {
                LOGGER.warn("Account {} OAuth token expired and refresh failed, auto-disabled scheduling, provider={}",
                        result.getAccountId(), provider);
            }
            return;
        }

        boolean refreshed = oauthTokenRefreshService.tryRefreshNow(result.getAccountId());
        if (refreshed) {
            LOGGER.info("Account {} OAuth token refreshed successfully after 401, provider={}",
                    result.getAccountId(), provider);
        } else {
            // 刷新失败（无 refresh_token 或 token 被吊销）→ 直接暂停调度，避免反复无效请求
            markQuotaExhausted(result.getAccountId(), "oauth_token_expired");
            LOGGER.warn("Account {} OAuth token expired and refresh failed, auto-disabled scheduling, provider={}",
                    result.getAccountId(), provider);
        }
    }

    private String extractErrorType(String body) {
        if (body == null || body.isEmpty()) return null;
        try {
            JsonNode root = objectMapper.readTree(body);
            // Both OpenAI and Claude: {"error":{"type":"..."}}
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

    private boolean isQuotaErrorCode(String body) {
        String code = extractErrorCode(body);
        return code != null && QUOTA_ERROR_TYPES.contains(code.toLowerCase(Locale.ROOT));
    }

    /**
     * Checks whether the upstream error message contains a quota-exhaustion keyword.
     * Used for Claude 429s, where both rate-limit and quota-exhaustion share the same
     * error type ("rate_limit_error") and can only be distinguished via message content.
     */
    private boolean isQuotaMessageInBody(String body) {
        if (body == null || body.isEmpty()) return false;
        try {
            JsonNode root = objectMapper.readTree(body);
            String message = textOrNull(root.path("error"), "message");
            if (message == null) return false;
            String lower = message.toLowerCase(Locale.ROOT);
            return QUOTA_MESSAGE_KEYWORDS.stream().anyMatch(lower::contains);
        } catch (Exception e) {
            return false;
        }
    }

    private static String nullSafe(String value) {
        return value == null ? "unknown" : value;
    }

    private RelayRoute validateAndResolveChatCompletionRoute(RelayChatCompletionRequest request) {
        validateRequest(request);
        RelayRoute route = relayModelRouter.route(request.getModel());
        validateProviderSpecificRequest(route, request);
        return route;
    }

    private void validateRequest(RelayChatCompletionRequest request) {
        if (request == null || request.getModel() == null || request.getModel().trim().isEmpty()) {
            throw new RelayException(HttpStatus.BAD_REQUEST, "Model is required", "invalid_request");
        }
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            throw new RelayException(HttpStatus.BAD_REQUEST, "Messages are required", "invalid_request");
        }
        if (request.hasUnsupportedFields()) {
            throw new RelayException(HttpStatus.BAD_REQUEST, "Unsupported request fields", "invalid_request");
        }
    }

    private void validateProviderSpecificRequest(RelayRoute route, RelayChatCompletionRequest request) {
        if (!request.hasOpenAiToolCallingFields()) {
            return;
        }
        if (!"openai".equals(route.getProvider())) {
            throw new RelayException(HttpStatus.BAD_REQUEST,
                    "OpenAI tool calling is only supported for OpenAI models",
                    "invalid_request");
        }
    }

    private void validateClaudeMessagesRequest(JsonNode requestBody) {
        if (requestBody == null || !requestBody.isObject()) {
            throw new RelayException(HttpStatus.BAD_REQUEST, "Invalid request body", "invalid_request");
        }
        String model = requestBody.path("model").asText(null);
        if (model == null || model.trim().isEmpty()) {
            throw new RelayException(HttpStatus.BAD_REQUEST, "Model is required", "invalid_request");
        }
        JsonNode messages = requestBody.path("messages");
        if (!messages.isArray() || messages.isEmpty()) {
            throw new RelayException(HttpStatus.BAD_REQUEST, "Messages are required", "invalid_request");
        }
    }

    private void checkBilling(ApiKeyItem apiKey, GroupItem group) {
        Long ownerId = apiKey.getOwnerId();
        Long groupId = group != null ? group.getId() : null;

        // 检查当前分组的订阅总配额
        com.firstapi.backend.model.SubscriptionItem activeSub = subscriptionService.getActiveSubscription(ownerId, groupId);
        boolean subscriptionAvailable = subscriptionService.hasQuotaRemaining(ownerId, groupId);

        if (subscriptionAvailable) {
            // 有订阅额度剩余时，再检查每日配额
            if (activeSub != null && activeSub.getDailyLimit() != null && !activeSub.getDailyLimit().isBlank()) {
                try {
                    java.math.BigDecimal dailyLimit = new java.math.BigDecimal(activeSub.getDailyLimit().trim());
                    if (!dailyQuotaService.checkDailyQuota(ownerId, dailyLimit)) {
                        // 每日订阅配额超限 → 降级检查余额
                        if (userService.checkBalanceByAuthUserId(ownerId)) {
                            return;
                        }
                        throw new RelayException(HttpStatus.TOO_MANY_REQUESTS,
                                "\u4eca\u65e5\u914d\u989d\u5df2\u7528\u5c3d\u4e14\u4f59\u989d\u4e0d\u8db3", "daily_quota_exhausted");
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            return;
        }

        // 订阅额度耗尽或无订阅 → 检查余额
        if (userService.checkBalanceByAuthUserId(ownerId)) {
            return;
        }
        throw new RelayException(HttpStatus.TOO_MANY_REQUESTS,
                "\u8ba2\u9605\u989d\u5ea6\u5df2\u7528\u5c3d\u4e14\u4f59\u989d\u4e0d\u8db3", "quota_exhausted");
    }

    private GroupItem resolveGroup(ApiKeyItem apiKey) {
        if (apiKey == null || apiKey.getGroupId() == null) {
            throw new RelayException(HttpStatus.FORBIDDEN, "API key group missing", "invalid_api_key_group");
        }
        GroupItem group = groupRepository.findById(apiKey.getGroupId());
        if (group == null || isDisabledStatus(group.getStatus())) {
            throw new RelayException(HttpStatus.FORBIDDEN, "API key group invalid", "invalid_api_key_group");
        }
        return group;
    }

    private void validateGroupPlatform(RelayRoute route, GroupItem group) {
        if (!PlatformAccountTypeCatalog.providerMatchesPlatform(route.getProvider(), group.getPlatform())) {
            throw new RelayException(HttpStatus.BAD_REQUEST, "Group platform mismatch", "group_platform_mismatch");
        }
    }

    private String resolveGroupAccountType(GroupItem group) {
        String accountType = group.getAccountType();
        if (accountType == null || accountType.trim().isEmpty()) {
            return PlatformAccountTypeCatalog.defaultAccountType(group.getPlatform());
        }
        return accountType.trim();
    }

    /**
     * Detects quota exhaustion by parsing the upstream JSON error structure.
     * Only used as fallback for non-429/402/529/503/401 status codes.
     */
    private String detectQuotaReason(RelayResult result) {
        int statusCode = result.getStatusCode();
        if (statusCode < 400) return null;

        ErrorInfo err = extractErrorInfo(result.getBody());
        if (err != null && err.typeOrCodeMatch) {
            return "error_type:" + err.matchedField;
        }
        return null;
    }

    private record ErrorInfo(String matchedField, boolean typeOrCodeMatch) {}

    private record RelayExecutionOutcome(RelayResult result,
                                         boolean oauthRefreshAttempted,
                                         boolean oauthRefreshSucceeded) {}

    private ErrorInfo extractErrorInfo(String body) {
        if (body == null || body.isEmpty()) return null;
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode errorNode = root.path("error");
            if (errorNode.isMissingNode() || !errorNode.isObject()) return null;
            String errorType = textOrNull(errorNode, "type");
            String errorCode = textOrNull(errorNode, "code");
            String errorMessage = textOrNull(errorNode, "message");

            if (errorType != null && QUOTA_ERROR_TYPES.contains(errorType.toLowerCase(Locale.ROOT))) {
                return new ErrorInfo(errorType, true);
            }
            if (errorCode != null && QUOTA_ERROR_TYPES.contains(errorCode.toLowerCase(Locale.ROOT))) {
                return new ErrorInfo(errorCode, true);
            }
            if (errorMessage != null) {
                String lowerMessage = errorMessage.toLowerCase(Locale.ROOT);
                for (String keyword : QUOTA_MESSAGE_KEYWORDS) {
                    if (lowerMessage.contains(keyword)) {
                        return new ErrorInfo(keyword, false);
                    }
                }
            }
            return null;
        } catch (Exception e) {
            LOGGER.debug("Failed to parse upstream error body for quota detection", e);
            return null;
        }
    }

    private static String textOrNull(JsonNode parent, String field) {
        JsonNode node = parent.path(field);
        return (node.isMissingNode() || node.isNull()) ? null : node.asText();
    }

    private void markQuotaExhausted(Long accountId, String reason) {
        quotaStateManager.markQuotaExhausted(accountId, reason);
    }

    private void clearQuotaStateIfRecovered(Long accountId) {
        quotaStateManager.clearQuotaStateIfRecovered(accountId);
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    private boolean isDisabledStatus(String status) {
        if (status == null) {
            return false;
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("禁用")
                || normalized.equals("disabled")
                || normalized.equals("异常")
                || normalized.equals("error");
    }
}
