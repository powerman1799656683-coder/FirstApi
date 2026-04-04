package com.firstapi.backend.service;

import com.firstapi.backend.config.RelayProperties;
import com.firstapi.backend.model.AccountItem;
import com.firstapi.backend.repository.AccountGroupBindingRepository;
import com.firstapi.backend.repository.AccountRepository;
import com.firstapi.backend.repository.GroupRepository;
import com.firstapi.backend.repository.IpRepository;
import com.firstapi.backend.repository.RelayRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountQuotaRecoveryTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private SensitiveDataService sensitiveDataService;
    @Mock
    private IpRepository ipRepository;
    @Mock
    private RelayRecordRepository relayRecordRepository;
    @Mock
    private GroupRepository groupRepository;
    @Mock
    private AccountGroupBindingRepository accountGroupBindingRepository;
    @Mock
    private UpstreamHttpClient upstreamHttpClient;
    @Mock
    private RelayProperties relayProperties;
    @Mock
    private AccountOAuthService accountOAuthService;
    @Mock
    private RelayAccountSelector relayAccountSelector;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(
                accountRepository,
                sensitiveDataService,
                ipRepository,
                relayRecordRepository,
                groupRepository,
                accountGroupBindingRepository,
                upstreamHttpClient,
                relayProperties,
                accountOAuthService,
                relayAccountSelector,
                null,
                null,
                null
        );
        when(accountGroupBindingRepository.findAllGroupings()).thenReturn(java.util.Map.of());
    }

    @Test
    void recoverQuotaClearsCooldownFields() {
        AccountItem account = new AccountItem();
        account.setId(1L);
        account.setName("openai-plus");
        account.setPlatform("OpenAI");
        account.setType("OpenAI API");
        account.setStatus("normal");
        account.setQuotaExhausted(true);
        account.setQuotaFailCount(3);
        account.setQuotaNextRetryAt("2099-01-01 00:00:00");
        account.setQuotaLastReason("insufficient_quota");

        when(accountRepository.findById(1L)).thenReturn(account);
        when(accountRepository.update(eq(1L), any(AccountItem.class))).thenAnswer(invocation -> invocation.getArgument(1));
        when(relayRecordRepository.findAll()).thenReturn(List.of());
        when(groupRepository.findAll()).thenReturn(List.of());

        AccountItem recovered = accountService.recoverQuota(1L);

        assertThat(recovered.isQuotaExhausted()).isFalse();
        assertThat(recovered.getQuotaFailCount()).isZero();
        assertThat(recovered.getQuotaNextRetryAt()).isNull();
        assertThat(recovered.getQuotaLastReason()).isNull();
        assertThat(recovered.getQuotaUpdatedAt()).isNotBlank();
    }
}
