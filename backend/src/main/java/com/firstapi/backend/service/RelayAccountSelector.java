package com.firstapi.backend.service;

import com.firstapi.backend.config.RelayProperties;
import com.firstapi.backend.model.AccountItem;
import com.firstapi.backend.model.CooldownEntry;
import com.firstapi.backend.model.IpItem;
import com.firstapi.backend.model.RelayException;
import com.firstapi.backend.repository.AccountRepository;
import com.firstapi.backend.repository.IpRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class RelayAccountSelector {

    private final AccountRepository accountRepository;
    private final IpRepository ipRepository;
    private final RelayProperties relayProperties;
    private final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, AtomicInteger> inFlight = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, CooldownEntry> cooldowns = new ConcurrentHashMap<>();
    private static final int DEFAULT_COOLDOWN_MINUTES = 60;
    private static final DateTimeFormatter EXPIRY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final DateTimeFormatter QUOTA_RETRY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter QUOTA_RETRY_FALLBACK = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    private static final Map<String, List<String>> TIER_HIERARCHY = Map.of(
            "claude", List.of("pro", "max5", "max20"),
            "openai", List.of("plus", "pro"),
            "gemini", List.of("standard"),
            "antigravity", List.of("standard")
    );

    public RelayAccountSelector(AccountRepository accountRepository,
                                IpRepository ipRepository,
                                RelayProperties relayProperties) {
        this.accountRepository = accountRepository;
        this.ipRepository = ipRepository;
        this.relayProperties = relayProperties;
    }

    public AccountItem selectAccount(String provider) {
        return selectAccount(provider, null);
    }

    public AccountItem selectAccount(String provider, String accountType) {
        List<AccountItem> eligible = findEligible(provider, accountType);
        if (eligible.isEmpty()) {
            throw new RelayException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No eligible upstream account", "no_upstream_account");
        }

        // Tier-based scheduling: walk the tier hierarchy, try each tier in order.
        List<String> tierChain = TIER_HIERARCHY.getOrDefault(provider, List.of());
        for (String tierName : tierChain) {
            List<AccountItem> tierAccounts = eligible.stream()
                    .filter(a -> accountMatchesTier(a, tierName))
                    .sorted(Comparator.comparingInt(AccountItem::getPriorityValue))
                    .collect(Collectors.toList());
            AccountItem selected = pickFromPriorityGroups(provider + ":" + tierName, tierAccounts);
            if (selected != null) {
                return selected;
            }
        }

        // Final fallback: accounts with no tiers set (universal) or all remaining eligible.
        List<AccountItem> universal = eligible.stream()
                .filter(a -> a.getTierList().isEmpty())
                .sorted(Comparator.comparingInt(AccountItem::getPriorityValue))
                .collect(Collectors.toList());
        AccountItem fallback = pickFromPriorityGroups(provider + ":universal", universal);
        if (fallback != null) {
            return fallback;
        }

        // Last resort: try absolutely everything regardless of tier.
        eligible.sort(Comparator.comparingInt(AccountItem::getPriorityValue));
        AccountItem lastResort = pickFromPriorityGroups(provider, eligible);
        if (lastResort != null) {
            return lastResort;
        }

        throw new RelayException(HttpStatus.SERVICE_UNAVAILABLE,
                "No eligible upstream account", "no_upstream_account");
    }

    private boolean accountMatchesTier(AccountItem account, String tierName) {
        List<String> accountTiers = account.getTierList();
        if (accountTiers.isEmpty()) {
            return false; // universal accounts handled separately in fallback
        }
        return accountTiers.contains(tierName.toLowerCase(Locale.ROOT));
    }

    private AccountItem pickFromPriorityGroups(String key, List<AccountItem> accounts) {
        if (accounts.isEmpty()) return null;
        int index = 0;
        while (index < accounts.size()) {
            int priority = accounts.get(index).getPriorityValue();
            List<AccountItem> group = new ArrayList<>();
            while (index < accounts.size() && accounts.get(index).getPriorityValue() == priority) {
                group.add(accounts.get(index));
                index++;
            }
            AccountItem selected = pickFromTier(key, priority, group);
            if (selected != null) {
                return selected;
            }
        }
        return null;
    }

    public void releaseAccount(AccountItem account) {
        Long accountId = account == null ? null : account.getId();
        if (accountId == null) {
            return;
        }
        AtomicInteger current = inFlight.get(accountId);
        if (current == null) {
            return;
        }
        while (true) {
            int value = current.get();
            if (value <= 0) {
                inFlight.remove(accountId, current);
                return;
            }
            if (current.compareAndSet(value, value - 1)) {
                if (value - 1 <= 0) {
                    inFlight.remove(accountId, current);
                }
                return;
            }
        }
    }

    public String resolveBaseUrl(AccountItem account, String provider) {
        String baseUrl = account.getBaseUrl();
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            baseUrl = "openai".equals(provider)
                    ? relayProperties.getOpenaiBaseUrl()
                    : relayProperties.getClaudeBaseUrl();
        }
        return stripTrailingSlash(baseUrl);
    }

    public IpItem resolveProxy(AccountItem account) {
        Long proxyId = account == null ? null : account.getProxyId();
        if (proxyId == null || proxyId.longValue() <= 0L) {
            return null;
        }
        IpItem proxy = ipRepository.findById(proxyId);
        if (proxy == null) {
            throw new RelayException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Configured proxy not found", "proxy_not_found");
        }
        if (!isProxyHealthy(proxy.getStatus())) {
            throw new RelayException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Configured proxy unavailable", "proxy_unavailable");
        }
        return proxy;
    }

    /**
     * Cooldown an account for the given number of seconds.
     * Thread-safe: if a longer cooldown already exists, it is preserved.
     */
    public void cooldownAccount(Long accountId, int seconds) {
        cooldownAccount(accountId, seconds, null, null);
    }

    public void cooldownAccount(Long accountId, int seconds, String reason, String provider) {
        if (accountId == null || seconds <= 0) {
            return;
        }
        Instant now = Instant.now();
        Instant until = now.plusSeconds(seconds);
        // Probe at max(until - 10s, now + seconds/2) so short cooldowns don't probe too early
        Instant probeEarly = until.minusSeconds(10);
        Instant probeHalf = now.plusSeconds(Math.max(seconds / 2, 1));
        Instant probeAt = probeEarly.isAfter(probeHalf) ? probeEarly : probeHalf;
        cooldowns.compute(accountId, (id, existing) -> {
            if (existing != null && existing.getUntil().isAfter(until)) {
                return existing; // keep the longer cooldown
            }
            return new CooldownEntry(until, probeAt, reason, provider);
        });
    }

    public void cooldownAccount(Long accountId) {
        cooldownAccount(accountId, DEFAULT_COOLDOWN_MINUTES * 60);
    }

    public Instant getCooldownUntil(Long accountId) {
        if (accountId == null) {
            return null;
        }
        CooldownEntry entry = cooldowns.get(accountId);
        if (entry == null) {
            return null;
        }
        if (Instant.now().isAfter(entry.getUntil())) {
            cooldowns.remove(accountId);
            return null;
        }
        return entry.getUntil();
    }

    /**
     * Returns the in-memory cooldown map for probe service iteration.
     */
    public ConcurrentHashMap<Long, CooldownEntry> getCooldownEntries() {
        return cooldowns;
    }

    /**
     * Remove a cooldown entry (e.g. after successful probe).
     */
    public void removeCooldown(Long accountId) {
        if (accountId != null) {
            cooldowns.remove(accountId);
        }
    }

    public boolean isInCooldown(Long accountId) {
        if (getCooldownUntil(accountId) != null) {
            return true;
        }
        if (accountId == null) {
            return false;
        }
        return isQuotaCooling(accountRepository.findById(accountId));
    }

    public boolean isInCooldown(AccountItem account) {
        if (account == null) {
            return false;
        }
        if (getCooldownUntil(account.getId()) != null) {
            return true;
        }
        return isQuotaCooling(account);
    }

    private List<AccountItem> findEligible(String provider, String accountType) {
        List<AccountItem> all = accountRepository.findAll();
        List<AccountItem> eligible = new ArrayList<>();
        for (AccountItem item : all) {
            if (matchesProvider(item.getPlatform(), provider)
                    && matchesAccountType(item, accountType)
                    && isHealthy(item.getStatus())
                    && !item.isTempDisabled()
                    && !isExpired(item)
                    && !isInCooldown(item)) {
                eligible.add(item);
            }
        }
        return eligible;
    }

    private boolean matchesAccountType(AccountItem item, String accountType) {
        if (accountType == null || accountType.trim().isEmpty()) {
            return true;
        }
        String configured = item.getAccountType();
        // Accounts with no accountType set are universal — they match any requested type
        if (configured == null || configured.trim().isEmpty()) {
            return true;
        }
        return configured.trim().equalsIgnoreCase(accountType.trim());
    }

    private boolean matchesProvider(String platform, String provider) {
        if (platform == null) {
            return false;
        }
        String normalized = platform.trim().toLowerCase(Locale.ROOT);
        if ("claude".equals(provider)) {
            return normalized.contains("claude") || normalized.contains("anthropic");
        }
        return normalized.contains(provider);
    }

    private boolean isHealthy(String status) {
        if (status == null) {
            return true;
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        return !normalized.equals("\u7981\u7528")
                && !normalized.equals("\u5f02\u5e38")
                && !normalized.equals("disabled")
                && !normalized.equals("error");
    }

    private boolean isProxyHealthy(String status) {
        if (status == null) {
            return true;
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        return !normalized.equals("\u7981\u7528")
                && !normalized.equals("\u5f02\u5e38")
                && !normalized.equals("disabled")
                && !normalized.equals("error")
                && !normalized.equals("offline");
    }

    private boolean isExpired(AccountItem item) {
        if (!item.isAutoSuspendExpiry()) {
            return false;
        }
        String expiry = item.getExpiryTime();
        if (expiry == null || expiry.trim().isEmpty()) {
            return false;
        }
        try {
            LocalDateTime expiryTime = LocalDateTime.parse(expiry.trim(), EXPIRY_FORMAT);
            return LocalDateTime.now().isAfter(expiryTime);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean tryAcquire(AccountItem account) {
        Long accountId = account.getId();
        if (accountId == null) {
            return true;
        }
        int limit = resolveConcurrencyLimit(account);
        AtomicInteger current = inFlight.computeIfAbsent(accountId, id -> new AtomicInteger(0));
        while (true) {
            int value = current.get();
            if (value >= limit) {
                return false;
            }
            if (current.compareAndSet(value, value + 1)) {
                return true;
            }
        }
    }

    private AccountItem pickFromTier(String provider, int priority, List<AccountItem> tier) {
        AtomicLong counter = counters.computeIfAbsent(provider + ":" + priority, k -> new AtomicLong(0));
        int size = tier.size();
        for (int i = 0; i < size; i++) {
            long index = counter.getAndIncrement();
            int slot = (int) Math.floorMod(index, size);
            AccountItem candidate = tier.get(slot);
            if (tryAcquire(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private int resolveConcurrencyLimit(AccountItem account) {
        int configured = account.getConcurrency();
        return configured > 0 ? configured : 10;
    }

    private boolean isQuotaCooling(AccountItem account) {
        if (account == null || !account.isQuotaExhausted()) {
            return false;
        }
        LocalDateTime retryAt = parseQuotaRetryAt(account.getQuotaNextRetryAt());
        if (retryAt == null) {
            // No retry time set — stay in cooldown until admin or successful request clears it
            return true;
        }
        if (LocalDateTime.now().isBefore(retryAt)) {
            return true; // still cooling
        }
        // Cooldown expired — lazily clear DB state so UI stays consistent.
        // Concurrent requests may both clear; this is idempotent and harmless.
        account.setQuotaExhausted(false);
        account.setQuotaNextRetryAt(null);
        account.setQuotaFailCount(0);
        account.setQuotaLastReason(null);
        account.setQuotaUpdatedAt(LocalDateTime.now().format(QUOTA_RETRY_FORMAT));
        accountRepository.update(account.getId(), account);
        return false;
    }

    private LocalDateTime parseQuotaRetryAt(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim();
        try {
            return LocalDateTime.parse(normalized, QUOTA_RETRY_FORMAT);
        } catch (DateTimeParseException ignored) {
            // Try legacy formats.
        }
        try {
            return LocalDateTime.parse(normalized, QUOTA_RETRY_FALLBACK);
        } catch (DateTimeParseException ignored) {
            // Try fallback ISO-ish format.
        }
        try {
            return LocalDateTime.parse(normalized, EXPIRY_FORMAT);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private String stripTrailingSlash(String value) {
        String normalized = value == null ? "" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
