package com.firstapi.backend.service;

import com.firstapi.backend.model.AccountItem;
import com.firstapi.backend.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每天凌晨5点自动探测所有 tempDisabled=true 的账号，
 * 如果账号已恢复（会员续费/解封等），自动解除暂停并恢复调度。
 */
@Service
public class TempDisabledProbeService {

    private static final Logger log = LoggerFactory.getLogger(TempDisabledProbeService.class);
    private static final long PROBE_INTERVAL_MS = 3000;

    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final ConcurrentHashMap<Long, Boolean> probing = new ConcurrentHashMap<>();

    public TempDisabledProbeService(AccountRepository accountRepository, AccountService accountService) {
        this.accountRepository = accountRepository;
        this.accountService = accountService;
    }

    @Scheduled(cron = "0 0 5 * * *")
    public void probeDisabledAccounts() {
        List<AccountItem> disabled = accountRepository.findByTempDisabled(true);
        if (disabled.isEmpty()) {
            log.info("暂停账号探测：无 tempDisabled 账号，跳过");
            return;
        }

        log.info("暂停账号探测：发现 {} 个 tempDisabled 账号，开始逐个探测", disabled.size());
        int recovered = 0;
        int still = 0;

        for (AccountItem account : disabled) {
            if (probing.putIfAbsent(account.getId(), Boolean.TRUE) != null) {
                continue;
            }
            try {
                AccountItem result = accountService.test(account.getId());
                if (!result.isTempDisabled()) {
                    recovered++;
                    log.info("暂停账号探测：账号 {} [{}] 已恢复，重新启用调度",
                            account.getId(), account.getName());
                } else {
                    still++;
                    log.info("暂停账号探测：账号 {} [{}] 仍不可用",
                            account.getId(), account.getName());
                }
            } catch (Exception e) {
                still++;
                log.error("暂停账号探测：账号 {} [{}] 探测异常: {}",
                        account.getId(), account.getName(), e.getMessage());
            } finally {
                probing.remove(account.getId());
            }

            // 间隔3秒避免触发上游限流
            try {
                Thread.sleep(PROBE_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        log.info("暂停账号探测完成：共 {} 个，恢复 {} 个，仍暂停 {} 个",
                disabled.size(), recovered, still);
    }
}
