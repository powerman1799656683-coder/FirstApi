package com.firstapi.backend.service;

import com.firstapi.backend.model.AccountItem;
import com.firstapi.backend.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Shared component for managing persistent quota-exhausted state in the DB.
 * Used by both {@link RelayService} (main request flow) and {@link CooldownProbeService} (probe flow).
 */
@Component
public class QuotaStateManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuotaStateManager.class);
    private static final DateTimeFormatter QUOTA_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AccountRepository accountRepository;

    public QuotaStateManager(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * Mark an account as quota-exhausted with exponential backoff.
     */
    public void markQuotaExhausted(Long accountId, String reason) {
        AccountItem account = accountRepository.findById(accountId);
        if (account == null) {
            return;
        }
        int nextFailCount = Math.max(0, account.getQuotaFailCount()) + 1;
        int backoffMinutes = backoffMinutes(nextFailCount);
        LocalDateTime now = LocalDateTime.now();
        account.setQuotaExhausted(true);
        account.setQuotaFailCount(nextFailCount);
        account.setQuotaLastReason(reason);
        account.setQuotaNextRetryAt(now.plusMinutes(backoffMinutes).format(QUOTA_TIME_FORMAT));
        account.setQuotaUpdatedAt(now.format(QUOTA_TIME_FORMAT));
        accountRepository.update(accountId, account);
        LOGGER.warn("Account {} enters quota cooldown, reason={}, retryIn={}m", accountId, reason, backoffMinutes);
    }

    /**
     * Mark an account as quota-exhausted with a specific cooldown duration in seconds.
     * Used when the upstream response provides an explicit recovery time
     * (e.g., ChatGPT "try again at Mar 27th, 2026 9:18 AM").
     */
    public void markQuotaExhaustedWithCooldown(Long accountId, String reason, int cooldownSeconds) {
        AccountItem account = accountRepository.findById(accountId);
        if (account == null) {
            return;
        }
        int nextFailCount = Math.max(0, account.getQuotaFailCount()) + 1;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime retryAt = now.plusSeconds(cooldownSeconds);
        account.setQuotaExhausted(true);
        account.setQuotaFailCount(nextFailCount);
        account.setQuotaLastReason(reason);
        account.setQuotaNextRetryAt(retryAt.format(QUOTA_TIME_FORMAT));
        account.setQuotaUpdatedAt(now.format(QUOTA_TIME_FORMAT));
        accountRepository.update(accountId, account);
        LOGGER.warn("Account {} enters quota cooldown, reason={}, retryAt={}", accountId, reason,
                retryAt.format(QUOTA_TIME_FORMAT));
    }

    /**
     * Clear quota-exhausted state if the account is currently exhausted.
     */
    public void clearQuotaStateIfRecovered(Long accountId) {
        AccountItem account = accountRepository.findById(accountId);
        if (account == null || !account.isQuotaExhausted()) {
            return;
        }
        clearQuotaState(accountId, account);
        LOGGER.info("Account {} recovered from quota cooldown", accountId);
    }

    /**
     * Clear quota-exhausted state on the given (already loaded) account object.
     */
    public void clearQuotaState(Long accountId, AccountItem account) {
        LocalDateTime now = LocalDateTime.now();
        account.setQuotaExhausted(false);
        account.setQuotaNextRetryAt(null);
        account.setQuotaFailCount(0);
        account.setQuotaLastReason(null);
        account.setQuotaUpdatedAt(now.format(QUOTA_TIME_FORMAT));
        accountRepository.update(accountId, account);
    }

    /**
     * Exponential backoff: 30m → 2h → 6h → 24h.
     */
    public static int backoffMinutes(int failCount) {
        if (failCount <= 1) return 30;
        if (failCount == 2) return 120;
        if (failCount == 3) return 360;
        return 1440;
    }
}
