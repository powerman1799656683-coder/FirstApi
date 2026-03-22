package com.firstapi.backend.service;

import com.firstapi.backend.common.PageResponse;
import com.firstapi.backend.config.RelayProperties;
import com.firstapi.backend.model.AccountItem;
import com.firstapi.backend.model.AccountItem.UsageWindow;
import com.firstapi.backend.model.GroupItem;
import com.firstapi.backend.model.IpItem;
import com.firstapi.backend.model.RelayResult;
import com.firstapi.backend.model.RelayRecordItem;
import com.firstapi.backend.repository.AccountGroupBindingRepository;
import com.firstapi.backend.repository.AccountRepository;
import com.firstapi.backend.repository.GroupRepository;
import com.firstapi.backend.repository.IpRepository;
import com.firstapi.backend.repository.RelayRecordRepository;
import com.firstapi.backend.util.TimeSupport;
import com.firstapi.backend.util.ValidationSupport;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AccountService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter RELAY_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    private static final DateTimeFormatter RELAY_TIME_MINUTE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
    private static final DateTimeFormatter EXPIRY_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final DateTimeFormatter QUOTA_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Map<String, Long> PLATFORM_TOKEN_LIMIT_24H = new HashMap<>();

    static {
        PLATFORM_TOKEN_LIMIT_24H.put("openai", 50_000_000L);
        PLATFORM_TOKEN_LIMIT_24H.put("anthropic", 20_000_000L);
        PLATFORM_TOKEN_LIMIT_24H.put("gemini", 30_000_000L);
        PLATFORM_TOKEN_LIMIT_24H.put("antigravity", 15_000_000L);
    }

    private final AccountRepository repository;
    private final SensitiveDataService sensitiveDataService;
    private final IpRepository ipRepository;
    private final RelayRecordRepository relayRecordRepository;
    private final GroupRepository groupRepository;
    private final AccountGroupBindingRepository accountGroupBindingRepository;
    private final UpstreamHttpClient upstreamHttpClient;
    private final RelayProperties relayProperties;
    private final AccountOAuthService accountOAuthService;
    private final RelayAccountSelector relayAccountSelector;

    public AccountService(AccountRepository repository,
                          SensitiveDataService sensitiveDataService,
                          IpRepository ipRepository,
                          RelayRecordRepository relayRecordRepository,
                          GroupRepository groupRepository,
                          AccountGroupBindingRepository accountGroupBindingRepository,
                          UpstreamHttpClient upstreamHttpClient,
                          RelayProperties relayProperties,
                          AccountOAuthService accountOAuthService,
                          RelayAccountSelector relayAccountSelector) {
        this.repository = repository;
        this.sensitiveDataService = sensitiveDataService;
        this.ipRepository = ipRepository;
        this.relayRecordRepository = relayRecordRepository;
        this.groupRepository = groupRepository;
        this.accountGroupBindingRepository = accountGroupBindingRepository;
        this.upstreamHttpClient = upstreamHttpClient;
        this.relayProperties = relayProperties;
        this.accountOAuthService = accountOAuthService;
        this.relayAccountSelector = relayAccountSelector;
    }

    public PageResponse<AccountItem> list(String keyword) {
        return list(keyword, null, null, null, null, null, 1, 20, "priorityValue", "asc");
    }

    public PageResponse<AccountItem> list(String keyword, String platform, String status,
                                          String authMethod, String scheduleEnabled, Long groupId,
                                          int page, int size, String sortBy, String sortOrder) {
        List<AccountItem> items = repository.findAll();

        Map<Long, List<Long>> allGroupBindings = accountGroupBindingRepository.findAllGroupings();

        // Apply filters
        if (!isBlank(keyword)) {
            items = items.stream()
                    .filter(i -> contains(i.getName(), keyword)
                            || contains(i.getPlatform(), keyword)
                            || contains(i.getType(), keyword)
                            || contains(i.getAccountType(), keyword)
                            || contains(i.getStatus(), keyword)
                            || contains(i.getNotes(), keyword)
                            || contains(String.valueOf(i.getId()), keyword))
                    .collect(Collectors.toList());
        }
        if (!isBlank(platform)) {
            items = items.stream()
                    .filter(i -> platform.equalsIgnoreCase(i.getPlatform()))
                    .collect(Collectors.toList());
        }
        if (!isBlank(authMethod)) {
            items = items.stream()
                    .filter(i -> authMethod.equalsIgnoreCase(i.getAuthMethod()))
                    .collect(Collectors.toList());
        }
        if (groupId != null) {
            items = items.stream()
                    .filter(i -> {
                        List<Long> bindings = allGroupBindings.get(i.getId());
                        return bindings != null && bindings.contains(groupId);
                    })
                    .collect(Collectors.toList());
        }

        // Enrich runtime fields before status/schedule filtering
        enrichRuntimeFields(items, allGroupBindings);

        // Status filter (needs enriched effectiveStatus)
        if (!isBlank(status)) {
            items = items.stream()
                    .filter(i -> {
                        String es = i.getEffectiveStatus();
                        if ("normal".equalsIgnoreCase(status) || "正常".equals(status)) {
                            return "正常".equals(es);
                        }
                        if ("error".equalsIgnoreCase(status) || "异常".equals(status)) {
                            return "异常".equals(es) || "高风险".equals(es) || "额度冷却".equals(es);
                        }
                        if ("paused".equalsIgnoreCase(status) || "已暂停".equals(status)) {
                            return "已暂停".equals(es);
                        }
                        if ("expired".equalsIgnoreCase(status) || "已过期".equals(status)) {
                            return "已过期".equals(es);
                        }
                        return es != null && es.contains(status);
                    })
                    .collect(Collectors.toList());
        }

        // Schedule filter
        if (!isBlank(scheduleEnabled)) {
            boolean enabled = "true".equalsIgnoreCase(scheduleEnabled);
            items = items.stream()
                    .filter(i -> Boolean.TRUE.equals(i.getScheduleEnabled()) == enabled)
                    .collect(Collectors.toList());
        }

        // Sort
        Comparator<AccountItem> comparator = buildComparator(sortBy, sortOrder);
        items.sort(comparator);

        long total = items.size();

        // Paginate
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(100, size));
        int start = (safePage - 1) * safeSize;
        int end = Math.min(start + safeSize, items.size());
        List<AccountItem> paged = start >= items.size() ? Collections.emptyList() : items.subList(start, end);

        return new PageResponse<>(paged, total, safePage, safeSize);
    }

    public AccountItem get(Long id) {
        AccountItem item = repository.findById(id);
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "账户不存在");
        }
        Map<Long, List<Long>> allGroupBindings = accountGroupBindingRepository.findAllGroupings();
        enrichRuntimeFields(Collections.singletonList(item), allGroupBindings);
        return item;
    }

    public AccountItem create(AccountItem.Request req) {
        AccountItem item = new AccountItem();
        item.setName(ValidationSupport.requireNotBlank(req.getName(), "账户名称不能为空"));
        item.setPlatform(ValidationSupport.requireNotBlank(req.getPlatform(), "平台不能为空"));
        item.setType(emptyAsDefault(req.getType(), "API 密钥"));
        item.setUsage(emptyAsDefault(req.getUsage(), "¥0.00"));
        item.setStatus(emptyAsDefault(req.getStatus(), "正常"));
        item.setErrors(req.getErrors() != null ? req.getErrors() : 0);
        item.setLastCheck(emptyAsDefault(req.getLastCheck(), TimeSupport.nowDateTime()));
        item.setBaseUrl(ValidationSupport.trimToNull(req.getBaseUrl()));
        item.setNotes(req.getNotes());
        // Validate platform-accountType match BEFORE deriving auth method
        if (!isBlank(req.getAccountType())) {
            String resolved = PlatformAccountTypeCatalog.resolveAllowedAccountType(item.getPlatform(), req.getAccountType());
            if (resolved == null) {
                List<String> allowed = PlatformAccountTypeCatalog.allowedAccountTypes(item.getPlatform());
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "accountType 非法，平台 " + item.getPlatform() + " 仅支持: " + String.join(", ", allowed));
            }
            item.setAccountType(resolved);
        } else {
            item.setAccountType(req.getAccountType());
        }
        item.setAuthMethod(deriveAuthMethod(req.getAuthMethod(), req.getType(), item.getAccountType()));
        validateOAuthCredentialContractForCreate(item.getAuthMethod(), req);

        // Handle credential via credentialRef or direct credential
        resolveCredential(item, req);

        // Check credential uniqueness
        checkCredentialUniqueness(item.getCredential(), null);

        item.setTempDisabled(req.getTempDisabled() != null && req.getTempDisabled());
        item.setQuotaExhausted(req.getQuotaExhausted() != null && req.getQuotaExhausted());
        item.setQuotaNextRetryAt(req.getQuotaNextRetryAt());
        item.setQuotaFailCount(req.getQuotaFailCount() != null ? req.getQuotaFailCount() : 0);
        item.setQuotaLastReason(req.getQuotaLastReason());
        item.setQuotaUpdatedAt(req.getQuotaUpdatedAt());
        item.setPriorityValue(req.getPriorityValue() != null ? req.getPriorityValue() : 1);
        item.setExpiryTime(req.getExpiryTime());
        item.setAutoSuspendExpiry(req.getAutoSuspendExpiry() == null || req.getAutoSuspendExpiry());
        item.setConcurrency(resolveConcurrency(req.getConcurrency()));
        item.setBillingRate(resolveBillingRate(req.getBillingRate()));
        item.setProxyId(resolveProxyId(req.getProxyId()));
        item.setModels(req.getModels());
        item.setTiers(req.getTiers());
        item.setBalance(req.getBalance() != null ? req.getBalance() : java.math.BigDecimal.ZERO);
        item.setWeight(req.getWeight() != null ? req.getWeight() : 1);

        // Advanced controls
        applyAdvancedControls(item, req);

        AccountItem saved = repository.save(item);

        // Group bindings
        if (req.getGroupIds() != null) {
            accountGroupBindingRepository.replaceBindings(saved.getId(), req.getGroupIds());
        }

        return saved;
    }

    public AccountItem update(Long id, AccountItem.Request req) {
        AccountItem existing = repository.findById(id);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "账户不存在");
        }
        if (req.getName() != null) {
            existing.setName(ValidationSupport.requireNotBlank(req.getName(), "账户名称不能为空"));
        }
        if (req.getPlatform() != null) {
            existing.setPlatform(ValidationSupport.requireNotBlank(req.getPlatform(), "平台不能为空"));
        }
        if (req.getType() != null) {
            existing.setType(req.getType());
        }
        if (req.getUsage() != null) {
            existing.setUsage(req.getUsage());
        }
        if (req.getStatus() != null) {
            existing.setStatus(req.getStatus());
        }
        if (req.getErrors() != null) {
            existing.setErrors(req.getErrors());
        }
        if (req.getLastCheck() != null) {
            existing.setLastCheck(req.getLastCheck());
        }
        if (req.getBaseUrl() != null) {
            existing.setBaseUrl(ValidationSupport.trimToNull(req.getBaseUrl()));
        }
        if (req.getNotes() != null) {
            existing.setNotes(req.getNotes());
        }
        // Validate platform-accountType match BEFORE deriving auth method
        if (req.getAccountType() != null) {
            String resolved = PlatformAccountTypeCatalog.resolveAllowedAccountType(existing.getPlatform(), req.getAccountType());
            if (resolved == null) {
                List<String> allowed = PlatformAccountTypeCatalog.allowedAccountTypes(existing.getPlatform());
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "accountType 非法，平台 " + existing.getPlatform() + " 仅支持: " + String.join(", ", allowed));
            }
            existing.setAccountType(resolved);
        }
        if (req.getAuthMethod() != null || req.getType() != null || req.getAccountType() != null) {
            existing.setAuthMethod(deriveAuthMethod(req.getAuthMethod(), existing.getType(), existing.getAccountType()));
        }
        validateOAuthCredentialContractForUpdate(existing.getAuthMethod(), req, existing.getCredential());

        // Handle credential via credentialRef or direct credential
        resolveCredentialForUpdate(existing, req);

        // Check credential uniqueness when credential is being changed
        if (!ValidationSupport.isBlank(req.getCredential()) || !isBlank(req.getCredentialRef())) {
            checkCredentialUniqueness(existing.getCredential(), id);
        }

        if (req.getTempDisabled() != null) {
            existing.setTempDisabled(req.getTempDisabled());
        }
        if (req.getQuotaExhausted() != null) {
            existing.setQuotaExhausted(req.getQuotaExhausted());
        }
        if (req.getQuotaNextRetryAt() != null) {
            existing.setQuotaNextRetryAt(req.getQuotaNextRetryAt());
        }
        if (req.getQuotaFailCount() != null) {
            existing.setQuotaFailCount(Math.max(0, req.getQuotaFailCount()));
        }
        if (req.getQuotaLastReason() != null) {
            existing.setQuotaLastReason(req.getQuotaLastReason());
        }
        if (req.getQuotaUpdatedAt() != null) {
            existing.setQuotaUpdatedAt(req.getQuotaUpdatedAt());
        }
        if (req.getPriorityValue() != null) {
            existing.setPriorityValue(req.getPriorityValue());
        }
        if (req.getExpiryTime() != null) {
            existing.setExpiryTime(req.getExpiryTime());
        }
        if (req.getAutoSuspendExpiry() != null) {
            existing.setAutoSuspendExpiry(req.getAutoSuspendExpiry());
        }
        if (req.getConcurrency() != null) {
            existing.setConcurrency(resolveConcurrency(req.getConcurrency()));
        }
        if (req.getBillingRate() != null) {
            existing.setBillingRate(resolveBillingRate(req.getBillingRate()));
        }
        if (req.getProxyId() != null) {
            existing.setProxyId(resolveProxyId(req.getProxyId()));
        }
        if (req.getModels() != null) {
            existing.setModels(req.getModels());
        }
        if (req.getTiers() != null) {
            existing.setTiers(req.getTiers());
        }
        if (req.getBalance() != null) {
            existing.setBalance(req.getBalance());
        }
        if (req.getWeight() != null) {
            existing.setWeight(req.getWeight());
        }

        // Advanced controls (update only if provided)
        applyAdvancedControlsForUpdate(existing, req);

        AccountItem updated = repository.update(id, existing);

        // Group bindings
        if (req.getGroupIds() != null) {
            accountGroupBindingRepository.replaceBindings(id, req.getGroupIds());
        }

        return updated;
    }

    public void delete(Long id) {
        AccountItem existing = repository.findById(id);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "账户不存在");
        }
        repository.deleteById(id);
        accountGroupBindingRepository.deleteByAccountId(id);
    }

    public AccountItem recoverQuota(Long id) {
        AccountItem existing = repository.findById(id);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "账户不存在");
        }
        existing.setQuotaExhausted(false);
        existing.setQuotaNextRetryAt(null);
        existing.setQuotaFailCount(0);
        existing.setQuotaLastReason(null);
        existing.setQuotaUpdatedAt(LocalDateTime.now(ZONE).format(QUOTA_TIME_FORMAT));
        AccountItem updated = repository.update(id, existing);
        Map<Long, List<Long>> allGroupBindings = accountGroupBindingRepository.findAllGroupings();
        enrichRuntimeFields(Collections.singletonList(updated), allGroupBindings);
        return updated;
    }

    public AccountItem test(Long id) {
        AccountItem existing = repository.findById(id);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        }

        int previousErrors = existing.getErrors() == null ? 0 : existing.getErrors().intValue();
        int nextErrors = previousErrors + 1;
        String nextStatus = "error";
        try {
            String provider = normalizePlatform(existing.getPlatform());
            String baseUrl = resolveProbeBaseUrl(existing, provider);
            String credential = sensitiveDataService.reveal(existing.getCredential());
            if (ValidationSupport.isBlank(credential)) {
                throw new IllegalArgumentException("Empty credential");
            }

            Map<String, String> headers = new HashMap<>();
            IpItem proxy = resolveTestProxy(existing);
            RelayResult probe;
            if ("anthropic".equals(provider)) {
                if ("OAuth".equalsIgnoreCase(existing.getAuthMethod())) {
                    headers.put("Authorization", "Bearer " + credential);
                    headers.put("anthropic-beta", relayProperties.getOauthBetaHeader());
                } else {
                    headers.put("x-api-key", credential);
                }
                headers.put("anthropic-version", relayProperties.getAnthropicVersion());
                probe = upstreamHttpClient.postJson(
                        baseUrl + "/v1/messages",
                        headers,
                        buildClaudeProbePayload(),
                        proxy,
                        relayProperties.getReadTimeoutMs()
                );
            } else {
                headers.put("Authorization", "Bearer " + credential);
                probe = upstreamHttpClient.get(baseUrl + "/v1/models", headers, proxy);
            }

            if (probe.getStatusCode() >= 200 && probe.getStatusCode() < 300) {
                nextStatus = "normal";
                nextErrors = 0;
            }
        } catch (Exception ignored) {
            nextStatus = "error";
        }

        existing.setStatus(nextStatus);
        existing.setLastCheck(TimeSupport.nowDateTime());
        existing.setErrors(nextErrors);
        AccountItem updated = repository.update(id, existing);
        Map<Long, List<Long>> allGroupBindings = accountGroupBindingRepository.findAllGroupings();
        enrichRuntimeFields(Collections.singletonList(updated), allGroupBindings);
        return updated;
    }

    private JsonNode buildClaudeProbePayload() {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("model", relayProperties.getProbeClaudeModel());
        root.put("max_tokens", 1);
        ArrayNode messages = root.putArray("messages");
        ObjectNode message = messages.addObject();
        message.put("role", "user");
        message.put("content", "hi");
        return root;
    }

    /**
     * 从上游官方 API 查询可用模型列表。
     * 按平台分组，每个平台取一个正常账号调 GET /v1/models，合并去重后返回。
     */
    public List<String> fetchUpstreamModels() {
        List<AccountItem> allAccounts = repository.findAll();
        Map<String, AccountItem> bestPerPlatform = new HashMap<>();

        for (AccountItem acc : allAccounts) {
            if (acc == null || acc.isTempDisabled()) continue;
            String platform = normalizePlatform(acc.getPlatform());
            if (platform.isEmpty()) continue;
            AccountItem current = bestPerPlatform.get(platform);
            if (current == null || "normal".equals(acc.getStatus())) {
                bestPerPlatform.put(platform, acc);
            }
        }

        List<String> models = new ArrayList<>();
        for (Map.Entry<String, AccountItem> entry : bestPerPlatform.entrySet()) {
            String provider = entry.getKey();
            AccountItem acc = entry.getValue();
            try {
                fetchUpstreamModelsForAccount(provider, acc, models);
            } catch (Exception ignored) {
                // 跳过异常账号
            }
        }
        return models.stream().distinct().sorted().collect(Collectors.toList());
    }

    private void fetchUpstreamModelsForAccount(String provider, AccountItem account, List<String> out) {
        String credential = sensitiveDataService.reveal(account.getCredential());
        if (ValidationSupport.isBlank(credential)) {
            return;
        }

        IpItem proxy = resolveTestProxy(account);
        if ("openai".equals(provider) && "OAuth".equalsIgnoreCase(account.getAuthMethod())) {
            int openAiOAuthMatches = fetchOpenAiOAuthModels(credential, proxy, out);
            if (openAiOAuthMatches > 0) {
                return;
            }
        }

        String baseUrl = resolveProbeBaseUrl(account, provider);
        Map<String, String> headers = new HashMap<>();
        if ("anthropic".equals(provider)) {
            headers.put("x-api-key", credential);
            headers.put("anthropic-version", relayProperties.getAnthropicVersion());
        } else {
            headers.put("Authorization", "Bearer " + credential);
        }

        RelayResult result = upstreamHttpClient.get(baseUrl + "/v1/models", headers, proxy);
        if (result.isSuccess() && result.getBody() != null) {
            parseModelIds(result.getBody(), out);
        }
    }

    private int fetchOpenAiOAuthModels(String credential, IpItem proxy, List<String> out) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + credential);
        headers.put("User-Agent", "codex_cli_rs/0.80.0 (Windows 15.7.2; x86_64) Terminal");
        headers.put("Origin", "https://chatgpt.com");
        headers.put("Referer", "https://chatgpt.com/");
        headers.put("Oai-Language", "en-US");
        headers.put("Oai-Device-Id", UUID.randomUUID().toString());

        String[] candidates = new String[]{
                "https://chatgpt.com/backend-api/models?history_and_training_disabled=false",
                "https://chatgpt.com/backend-api/models"
        };

        for (String candidate : candidates) {
            try {
                RelayResult result = upstreamHttpClient.get(candidate, headers, proxy);
                if (result.isSuccess() && result.getBody() != null) {
                    int count = parseModelIds(result.getBody(), out);
                    if (count > 0) {
                        return count;
                    }
                }
            } catch (Exception ignored) {
                // Fallback to the next candidate or to the API-style endpoint.
            }
        }
        return 0;
    }

    private int parseModelIds(String responseBody, List<String> out) {
        try {
            int before = out.size();
            tools.jackson.databind.ObjectMapper om = new tools.jackson.databind.ObjectMapper();
            tools.jackson.databind.JsonNode root = om.readTree(responseBody);
            collectModelIds(root, out);
            return out.size() - before;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private void collectModelIds(tools.jackson.databind.JsonNode node, List<String> out) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (tools.jackson.databind.JsonNode child : node) {
                collectModelIds(child, out);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }

        addModelId(node, "id", out);
        addModelId(node, "slug", out);
        addModelId(node, "model_slug", out);

        collectModelIds(node.get("data"), out);
        collectModelIds(node.get("models"), out);
        collectModelIds(node.get("items"), out);
        collectModelIds(node.get("categories"), out);
    }

    private void addModelId(tools.jackson.databind.JsonNode node, String fieldName, List<String> out) {
        tools.jackson.databind.JsonNode valueNode = node.get(fieldName);
        if (valueNode == null || valueNode.isNull() || !valueNode.isTextual()) {
            return;
        }
        String modelId = valueNode.asText().trim();
        if (!modelId.isEmpty()) {
            out.add(modelId);
        }
    }

    // Batch operations
    public int batchToggleSchedule(List<Long> ids, boolean enable) {
        int count = 0;
        for (Long id : ids) {
            AccountItem item = repository.findById(id);
            if (item != null) {
                item.setTempDisabled(!enable);
                repository.update(id, item);
                count++;
            }
        }
        return count;
    }

    public int batchDelete(List<Long> ids) {
        int count = 0;
        for (Long id : ids) {
            AccountItem item = repository.findById(id);
            if (item != null) {
                repository.deleteById(id);
                accountGroupBindingRepository.deleteByAccountId(id);
                count++;
            }
        }
        return count;
    }

    public List<AccountItem> batchTest(List<Long> ids) {
        List<AccountItem> results = new ArrayList<>();
        for (Long id : ids) {
            try {
                results.add(test(id));
            } catch (Exception ignored) {
                // Skip failed tests
            }
        }
        return results;
    }

    private void resolveCredential(AccountItem item, AccountItem.Request req) {
        if (!isBlank(req.getCredentialRef())) {
            // Use OAuth credential reference
            String encrypted = accountOAuthService.consumeCredentialRef(req.getCredentialRef());
            if (encrypted != null) {
                item.setCredential(encrypted);
            }
        } else if (!ValidationSupport.isBlank(req.getCredential())) {
            item.setCredential(sensitiveDataService.protect(req.getCredential().trim()));
        }
    }

    private void resolveCredentialForUpdate(AccountItem existing, AccountItem.Request req) {
        if (!isBlank(req.getCredentialRef())) {
            String encrypted = accountOAuthService.consumeCredentialRef(req.getCredentialRef());
            if (encrypted != null) {
                existing.setCredential(encrypted);
            }
        } else if (!ValidationSupport.isBlank(req.getCredential())) {
            existing.setCredential(sensitiveDataService.protect(req.getCredential().trim()));
        }
    }

    private void checkCredentialUniqueness(String encryptedCredential, Long excludeId) {
        if (isBlank(encryptedCredential)) {
            return;
        }
        String plain;
        try {
            plain = sensitiveDataService.reveal(encryptedCredential);
        } catch (Exception e) {
            return;
        }
        if (isBlank(plain)) {
            return;
        }
        for (AccountItem existing : repository.findAll()) {
            if (existing == null || isBlank(existing.getCredential())) {
                continue;
            }
            if (excludeId != null && excludeId.equals(existing.getId())) {
                continue;
            }
            try {
                String existingPlain = sensitiveDataService.reveal(existing.getCredential());
                if (plain.equals(existingPlain)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该凭证已被其他账号使用");
                }
            } catch (ResponseStatusException e) {
                throw e;
            } catch (Exception ignored) {
            }
        }
    }

    private void validateOAuthCredentialContractForCreate(String authMethod, AccountItem.Request req) {
        if (!"OAuth".equalsIgnoreCase(authMethod)) {
            return;
        }
        if (!ValidationSupport.isBlank(req.getCredential())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "OAuth account creation does not accept plain credential");
        }
        if (isBlank(req.getCredentialRef())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "OAuth account creation requires credentialRef");
        }
    }

    private void validateOAuthCredentialContractForUpdate(String authMethod,
                                                          AccountItem.Request req,
                                                          String existingEncryptedCredential) {
        if (!"OAuth".equalsIgnoreCase(authMethod)) {
            return;
        }
        if (!ValidationSupport.isBlank(req.getCredential())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "OAuth account update does not accept plain credential");
        }
        if (isBlank(req.getCredentialRef()) && isBlank(existingEncryptedCredential)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "OAuth account update requires credentialRef when no credential is stored");
        }
    }

    private void applyAdvancedControls(AccountItem item, AccountItem.Request req) {
        item.setInterceptWarmupRequest(req.getInterceptWarmupRequest() != null && req.getInterceptWarmupRequest());
        item.setWindow5hCostControlEnabled(req.getWindow5hCostControlEnabled() != null && req.getWindow5hCostControlEnabled());
        item.setWindow5hCostLimitUsd(req.getWindow5hCostLimitUsd());
        item.setSessionCountControlEnabled(req.getSessionCountControlEnabled() != null && req.getSessionCountControlEnabled());
        item.setSessionCountLimit(req.getSessionCountLimit());
        item.setTlsFingerprintMode(req.getTlsFingerprintMode() != null ? req.getTlsFingerprintMode() : "NONE");
        item.setSessionIdMasqueradeEnabled(req.getSessionIdMasqueradeEnabled() != null && req.getSessionIdMasqueradeEnabled());
        item.setSessionIdMasqueradeTtlMinutes(req.getSessionIdMasqueradeTtlMinutes() != null ? req.getSessionIdMasqueradeTtlMinutes() : 15);
    }

    private void applyAdvancedControlsForUpdate(AccountItem existing, AccountItem.Request req) {
        if (req.getInterceptWarmupRequest() != null) {
            existing.setInterceptWarmupRequest(req.getInterceptWarmupRequest());
        }
        if (req.getWindow5hCostControlEnabled() != null) {
            existing.setWindow5hCostControlEnabled(req.getWindow5hCostControlEnabled());
        }
        if (req.getWindow5hCostLimitUsd() != null) {
            existing.setWindow5hCostLimitUsd(req.getWindow5hCostLimitUsd());
        }
        if (req.getSessionCountControlEnabled() != null) {
            existing.setSessionCountControlEnabled(req.getSessionCountControlEnabled());
        }
        if (req.getSessionCountLimit() != null) {
            existing.setSessionCountLimit(req.getSessionCountLimit());
        }
        if (req.getTlsFingerprintMode() != null) {
            existing.setTlsFingerprintMode(req.getTlsFingerprintMode());
        }
        if (req.getSessionIdMasqueradeEnabled() != null) {
            existing.setSessionIdMasqueradeEnabled(req.getSessionIdMasqueradeEnabled());
        }
        if (req.getSessionIdMasqueradeTtlMinutes() != null) {
            existing.setSessionIdMasqueradeTtlMinutes(req.getSessionIdMasqueradeTtlMinutes());
        }
    }

    private Comparator<AccountItem> buildComparator(String sortBy, String sortOrder) {
        boolean desc = "desc".equalsIgnoreCase(sortOrder);
        Comparator<AccountItem> cmp;
        switch (sortBy == null ? "priorityValue" : sortBy) {
            case "name":
                cmp = Comparator.comparing(a -> a.getName() == null ? "" : a.getName().toLowerCase(), String::compareTo);
                break;
            case "platform":
                cmp = Comparator.comparing(a -> a.getPlatform() == null ? "" : a.getPlatform().toLowerCase(), String::compareTo);
                break;
            case "status":
            case "effectiveStatus":
                cmp = Comparator.comparing(a -> a.getEffectiveStatus() == null ? "" : a.getEffectiveStatus(), String::compareTo);
                break;
            case "weight":
                cmp = Comparator.comparingInt(AccountItem::getWeight);
                break;
            case "concurrency":
                cmp = Comparator.comparingInt(AccountItem::getConcurrency);
                break;
            case "todayRequests":
                cmp = Comparator.comparingLong(a -> a.getTodayRequests() == null ? 0L : a.getTodayRequests());
                break;
            case "todayTokens":
                cmp = Comparator.comparingLong(a -> a.getTodayTokens() == null ? 0L : a.getTodayTokens());
                break;
            case "id":
                cmp = Comparator.comparingLong(a -> a.getId() == null ? 0L : a.getId());
                break;
            case "priorityValue":
            default:
                cmp = Comparator.comparingInt(AccountItem::getPriorityValue);
                break;
        }
        return desc ? cmp.reversed() : cmp;
    }

    private IpItem resolveTestProxy(AccountItem account) {
        if (account == null || account.getProxyId() == null || account.getProxyId() <= 0L) {
            return null;
        }
        IpItem proxy = ipRepository.findById(account.getProxyId());
        if (proxy == null) {
            throw new IllegalArgumentException("Proxy not found");
        }
        if (isDisabledStatus(proxy.getStatus())) {
            throw new IllegalArgumentException("Proxy unavailable");
        }
        return proxy;
    }

    private String resolveProbeBaseUrl(AccountItem account, String provider) {
        String configured = account == null ? null : account.getBaseUrl();
        String base = isBlank(configured)
                ? ("anthropic".equals(provider) ? relayProperties.getClaudeBaseUrl() : relayProperties.getOpenaiBaseUrl())
                : configured.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    private void enrichRuntimeFields(List<AccountItem> items, Map<Long, List<Long>> allGroupBindings) {
        if (items == null || items.isEmpty()) {
            return;
        }

        Map<Long, List<RecordSnapshot>> recordsByAccount = buildRecordMap(relayRecordRepository.findAll());
        Map<String, List<GroupItem>> groupsByPlatform = buildGroupsByPlatform(groupRepository.findAll());
        List<GroupItem> allGroups = groupRepository.findAll();
        Map<Long, String> groupNameMap = new HashMap<>();
        for (GroupItem g : allGroups) {
            if (g != null && g.getId() != null) {
                groupNameMap.put(g.getId(), g.getName());
            }
        }

        LocalDateTime now = LocalDateTime.now(ZONE);
        LocalDate today = now.toLocalDate();

        for (AccountItem item : items) {
            if (item == null) {
                continue;
            }
            List<RecordSnapshot> accountRecords = recordsByAccount.getOrDefault(item.getId(), Collections.emptyList());
            int capacityLimit = resolveCapacityLimit(item);
            int capacityUsed = countRecordsSince(accountRecords, now.minusMinutes(1));
            long todayRequests = countRecordsOnDate(accountRecords, today);
            long todayTokens = sumTokensOnDate(accountRecords, today);

            BigDecimal accountCost = sumCostOnDate(accountRecords, today);
            BigDecimal platformRate = resolvePlatformBillingRate(item.getPlatform(), groupsByPlatform);
            BigDecimal userCost = accountCost
                    .multiply(resolveBillingRate(item.getBillingRate()))
                    .multiply(platformRate);

            item.setCapacityLimit(capacityLimit);
            item.setCapacityUsed(capacityUsed);
            item.setTodayRequests(todayRequests);
            item.setTodayTokens(todayTokens);
            item.setTodayAccountCost(formatCny(accountCost));
            item.setTodayUserCost(formatCny(userCost));

            // Resolve groups from explicit bindings, fallback to platform-based
            List<Long> boundGroupIds = allGroupBindings != null ? allGroupBindings.getOrDefault(item.getId(), Collections.emptyList()) : Collections.emptyList();
            item.setGroupIds(boundGroupIds);
            if (!boundGroupIds.isEmpty()) {
                List<String> names = new ArrayList<>();
                for (Long gid : boundGroupIds) {
                    String name = groupNameMap.get(gid);
                    if (name != null) {
                        names.add(name);
                    }
                }
                item.setGroups(names.isEmpty() ? Collections.singletonList("未分组") : names);
            } else {
                item.setGroups(resolveAccountGroups(item.getPlatform(), groupsByPlatform));
            }

            item.setUsageWindows(buildUsageWindows(accountRecords, item, now, capacityLimit));

            RecordSnapshot latest = latestRecord(accountRecords);
            if (latest == null) {
                item.setRecentUsedAt("-");
                item.setRecentUsedText("-");
            } else {
                item.setRecentUsedAt(latest.createdAt.format(RELAY_TIME_FORMAT));
                item.setRecentUsedText(toRelativeText(latest.createdAt, now));
            }

            boolean expired = isExpired(item, now);
            item.setExpired(expired);
            item.setExpiryLabel(buildExpiryLabel(item.getExpiryTime(), expired));
            item.setScheduleEnabled(!item.isTempDisabled() && !expired && !relayAccountSelector.isInCooldown(item));
            item.setEffectiveStatus(deriveEffectiveStatus(item, accountRecords, now, expired));

            if (isBlank(item.getAuthMethod())) {
                item.setAuthMethod(deriveAuthMethod(null, item.getType(), item.getAccountType()));
            }
            if (isBlank(item.getUsage())) {
                item.setUsage(item.getTodayUserCost());
            }

            item.setCredentialMask(maskCredential(item.getCredential()));
        }
    }

    private List<UsageWindow> buildUsageWindows(List<RecordSnapshot> accountRecords,
                                                AccountItem item,
                                                LocalDateTime now,
                                                int capacityLimit) {
        long req3h = countRecordsSince(accountRecords, now.minusHours(3));
        long req3hLimit = Math.max(120L, (long) capacityLimit * 60L);

        long token24h = sumTokensSince(accountRecords, now.minusHours(24));
        long token24hLimit = resolveTokenLimit24h(item.getPlatform(), capacityLimit);

        long err7d = countErrorsSince(accountRecords, now.minusDays(7));
        long err7dLimit = Math.max(20L, (long) capacityLimit * 4L);

        List<UsageWindow> windows = new ArrayList<>();
        windows.add(buildWindow("3h", "3h", req3h, req3hLimit));
        windows.add(buildWindow("24h", "24h", token24h, token24hLimit));
        windows.add(buildWindow("7d", "7d", err7d, err7dLimit));
        return windows;
    }

    private UsageWindow buildWindow(String key, String label, long used, long limit) {
        long safeLimit = Math.max(1L, limit);
        int percentage = (int) Math.min(100L, Math.round((double) used * 100.0 / safeLimit));
        return new UsageWindow(
                key,
                label,
                used,
                safeLimit,
                percentage,
                used + " / " + safeLimit,
                toneForPercentage(percentage)
        );
    }

    private String toneForPercentage(int percentage) {
        if (percentage >= 90) {
            return "critical";
        }
        if (percentage >= 70) {
            return "warning";
        }
        if (percentage >= 40) {
            return "normal";
        }
        return "low";
    }

    private String deriveEffectiveStatus(AccountItem item,
                                         List<RecordSnapshot> accountRecords,
                                         LocalDateTime now,
                                         boolean expired) {
        if (item.isTempDisabled()) {
            return "已暂停";
        }
        if (relayAccountSelector.isInCooldown(item)) {
            return "额度冷却";
        }
        if (expired) {
            return "已过期";
        }

        List<RecordSnapshot> recent = accountRecords.stream()
                .filter(r -> !r.createdAt.isBefore(now.minusMinutes(30)))
                .collect(Collectors.toList());
        if (recent.size() >= 4) {
            long errors = recent.stream().filter(r -> isError(r.record)).count();
            double ratio = (double) errors / recent.size();
            if (ratio >= 0.5d) {
                return "高风险";
            }
        }

        String status = item.getStatus();
        if (isBlank(status)) {
            return "正常";
        }
        if (isDisabledStatus(status)) {
            return "异常";
        }
        return status;
    }

    private boolean isDisabledStatus(String status) {
        if (isBlank(status)) {
            return false;
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("禁用")
                || normalized.contains("停用")
                || normalized.contains("异常")
                || normalized.contains("error")
                || normalized.contains("disabled");
    }

    private boolean isError(RelayRecordItem record) {
        if (record == null) {
            return false;
        }
        if (Boolean.FALSE.equals(record.getSuccess())) {
            return true;
        }
        return record.getStatusCode() != null && record.getStatusCode() >= 400;
    }

    private Map<Long, List<RecordSnapshot>> buildRecordMap(List<RelayRecordItem> allRecords) {
        Map<Long, List<RecordSnapshot>> map = new HashMap<>();
        if (allRecords == null) {
            return map;
        }
        for (RelayRecordItem record : allRecords) {
            if (record == null || record.getAccountId() == null) {
                continue;
            }
            LocalDateTime createdAt = parseRelayTime(record.getCreatedAt());
            if (createdAt == null) {
                continue;
            }
            map.computeIfAbsent(record.getAccountId(), ignored -> new ArrayList<>())
                    .add(new RecordSnapshot(record, createdAt));
        }
        for (List<RecordSnapshot> snapshots : map.values()) {
            snapshots.sort(Comparator.comparing((RecordSnapshot r) -> r.createdAt).reversed());
        }
        return map;
    }

    private Map<String, List<GroupItem>> buildGroupsByPlatform(List<GroupItem> groups) {
        Map<String, List<GroupItem>> map = new HashMap<>();
        if (groups == null) {
            return map;
        }
        for (GroupItem group : groups) {
            if (group == null || isBlank(group.getPlatform()) || isDisabledStatus(group.getStatus())) {
                continue;
            }
            String key = normalizePlatform(group.getPlatform());
            map.computeIfAbsent(key, ignored -> new ArrayList<>()).add(group);
        }
        return map;
    }

    private List<String> resolveAccountGroups(String platform, Map<String, List<GroupItem>> groupsByPlatform) {
        List<GroupItem> groups = groupsByPlatform.getOrDefault(normalizePlatform(platform), Collections.emptyList());
        List<String> names = groups.stream()
                .map(GroupItem::getName)
                .filter(name -> !isBlank(name))
                .distinct()
                .limit(4)
                .collect(Collectors.toList());
        if (names.isEmpty()) {
            return Collections.singletonList("未分组");
        }
        return names;
    }

    private BigDecimal resolvePlatformBillingRate(String platform, Map<String, List<GroupItem>> groupsByPlatform) {
        List<GroupItem> groups = groupsByPlatform.getOrDefault(normalizePlatform(platform), Collections.emptyList());
        if (groups.isEmpty()) {
            return BigDecimal.ONE;
        }
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        for (GroupItem group : groups) {
            BigDecimal rate = parsePositiveDecimal(group.getRate());
            if (rate == null) {
                continue;
            }
            total = total.add(rate);
            count++;
        }
        if (count == 0) {
            return BigDecimal.ONE;
        }
        return total.divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal parsePositiveDecimal(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            BigDecimal decimal = new BigDecimal(value.trim());
            if (decimal.compareTo(BigDecimal.ZERO) < 0) {
                return null;
            }
            return decimal;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String formatCny(BigDecimal value) {
        BigDecimal safe = value == null ? BigDecimal.ZERO : value;
        return "¥" + safe.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private long resolveTokenLimit24h(String platform, int capacityLimit) {
        long base = PLATFORM_TOKEN_LIMIT_24H.getOrDefault(normalizePlatform(platform), 15_000_000L);
        long capacityFactor = Math.max(1L, capacityLimit / 10L);
        return base * capacityFactor;
    }

    private int resolveCapacityLimit(AccountItem item) {
        int configured = item.getConcurrency();
        int fallback = defaultConcurrencyForType(item.getType(), item.getAccountType());
        if (configured <= 0) {
            return fallback;
        }
        return Math.max(configured, fallback);
    }

    private int defaultConcurrencyForType(String type, String accountType) {
        String normalized = (type == null ? "" : type).toLowerCase(Locale.ROOT)
                + " "
                + (accountType == null ? "" : accountType).toLowerCase(Locale.ROOT);
        if (normalized.contains("claude code")) {
            return 10;
        }
        if (normalized.contains("plus")) {
            return 50;
        }
        if (normalized.contains("api")) {
            return 50;
        }
        return 20;
    }

    private String deriveAuthMethod(String explicit, String type, String accountType) {
        if (!isBlank(explicit)) {
            return explicit.trim();
        }
        String normalized = (type == null ? "" : type).toLowerCase(Locale.ROOT)
                + " "
                + (accountType == null ? "" : accountType).toLowerCase(Locale.ROOT);
        if (normalized.contains("oauth") || normalized.contains("plus") || normalized.contains("code")) {
            return "OAuth";
        }
        return "API Key";
    }

    private boolean isExpired(AccountItem item, LocalDateTime now) {
        if (item == null || !item.isAutoSuspendExpiry()) {
            return false;
        }
        LocalDateTime expiry = parseExpiryTime(item.getExpiryTime());
        return expiry != null && now.isAfter(expiry);
    }

    private String buildExpiryLabel(String expiryTime, boolean expired) {
        if (isBlank(expiryTime)) {
            return "-";
        }
        return expired ? expiryTime + " (已过期)" : expiryTime;
    }

    private LocalDateTime parseExpiryTime(String expiryTime) {
        if (isBlank(expiryTime)) {
            return null;
        }
        try {
            return LocalDateTime.parse(expiryTime.trim(), EXPIRY_TIME_FORMAT);
        } catch (Exception ignored) {
            return null;
        }
    }

    private LocalDateTime parseRelayTime(String createdAt) {
        if (isBlank(createdAt)) {
            return null;
        }
        try {
            return LocalDateTime.parse(createdAt.trim(), RELAY_TIME_FORMAT);
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(createdAt.trim(), RELAY_TIME_MINUTE_FORMAT);
            } catch (Exception ignoredAgain) {
                return null;
            }
        }
    }

    private RecordSnapshot latestRecord(List<RecordSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return null;
        }
        return snapshots.get(0);
    }

    private String toRelativeText(LocalDateTime target, LocalDateTime now) {
        if (target == null) {
            return "-";
        }
        long minutes = Math.max(0L, Duration.between(target, now).toMinutes());
        if (minutes < 1L) {
            return "刚刚";
        }
        if (minutes < 60L) {
            return minutes + "分钟前";
        }
        long hours = minutes / 60L;
        if (hours < 24L) {
            return hours + "小时前";
        }
        long days = hours / 24L;
        return days + "天前";
    }

    private int countRecordsSince(List<RecordSnapshot> snapshots, LocalDateTime since) {
        if (snapshots == null || snapshots.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (RecordSnapshot snapshot : snapshots) {
            if (!snapshot.createdAt.isBefore(since)) {
                count++;
            }
        }
        return count;
    }

    private long countRecordsOnDate(List<RecordSnapshot> snapshots, LocalDate date) {
        if (snapshots == null || snapshots.isEmpty()) {
            return 0L;
        }
        return snapshots.stream()
                .filter(snapshot -> snapshot.createdAt.toLocalDate().equals(date))
                .count();
    }

    private long sumTokensOnDate(List<RecordSnapshot> snapshots, LocalDate date) {
        if (snapshots == null || snapshots.isEmpty()) {
            return 0L;
        }
        long sum = 0L;
        for (RecordSnapshot snapshot : snapshots) {
            if (snapshot.createdAt.toLocalDate().equals(date)) {
                sum += safeToken(snapshot.record.getTotalTokens());
            }
        }
        return sum;
    }

    private BigDecimal sumCostOnDate(List<RecordSnapshot> snapshots, LocalDate date) {
        if (snapshots == null || snapshots.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (RecordSnapshot snapshot : snapshots) {
            if (snapshot.createdAt.toLocalDate().equals(date) && snapshot.record.getCost() != null) {
                sum = sum.add(snapshot.record.getCost());
            }
        }
        return sum;
    }

    private long sumTokensSince(List<RecordSnapshot> snapshots, LocalDateTime since) {
        if (snapshots == null || snapshots.isEmpty()) {
            return 0L;
        }
        long sum = 0L;
        for (RecordSnapshot snapshot : snapshots) {
            if (!snapshot.createdAt.isBefore(since)) {
                sum += safeToken(snapshot.record.getTotalTokens());
            }
        }
        return sum;
    }

    private long countErrorsSince(List<RecordSnapshot> snapshots, LocalDateTime since) {
        if (snapshots == null || snapshots.isEmpty()) {
            return 0L;
        }
        long count = 0L;
        for (RecordSnapshot snapshot : snapshots) {
            if (!snapshot.createdAt.isBefore(since) && isError(snapshot.record)) {
                count++;
            }
        }
        return count;
    }

    private long safeToken(Integer tokens) {
        return tokens == null ? 0L : Math.max(0, tokens.longValue());
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private boolean contains(String value, String keyword) {
        if (value == null || keyword == null) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private String emptyAsDefault(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }

    private int resolveConcurrency(Integer concurrency) {
        if (concurrency == null || concurrency <= 0) {
            return 10;
        }
        return concurrency;
    }

    private BigDecimal resolveBillingRate(BigDecimal billingRate) {
        if (billingRate == null) {
            return BigDecimal.ONE;
        }
        if (billingRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("账号计费倍率必须 >= 0");
        }
        return billingRate;
    }

    private Long resolveProxyId(Long proxyId) {
        if (proxyId == null || proxyId <= 0L) {
            return null;
        }
        if (ipRepository.findById(proxyId) == null) {
            throw new IllegalArgumentException("代理节点不存在");
        }
        return proxyId;
    }

    private String maskCredential(String encrypted) {
        if (isBlank(encrypted)) {
            return "-";
        }
        try {
            String plain = sensitiveDataService.reveal(encrypted);
            if (isBlank(plain)) {
                return "-";
            }
            if (plain.length() <= 8) {
                return plain.substring(0, 2) + "****";
            }
            return plain.substring(0, 4) + "****" + plain.substring(plain.length() - 4);
        } catch (Exception ignored) {
            return "sk-****";
        }
    }

    private String normalizePlatform(String platform) {
        if (isBlank(platform)) {
            return "";
        }
        String normalized = platform.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("anthropic") || normalized.contains("claude")) {
            return "anthropic";
        }
        if (normalized.contains("openai") || normalized.contains("gpt")) {
            return "openai";
        }
        if (normalized.contains("gemini")) {
            return "gemini";
        }
        if (normalized.contains("antigravity")) {
            return "antigravity";
        }
        return normalized;
    }

    private static final class RecordSnapshot {
        private final RelayRecordItem record;
        private final LocalDateTime createdAt;

        private RecordSnapshot(RelayRecordItem record, LocalDateTime createdAt) {
            this.record = record;
            this.createdAt = createdAt;
        }
    }
}
