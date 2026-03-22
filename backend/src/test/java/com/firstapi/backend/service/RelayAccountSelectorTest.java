package com.firstapi.backend.service;

import com.firstapi.backend.config.RelayProperties;
import com.firstapi.backend.model.AccountItem;
import com.firstapi.backend.model.IpItem;
import com.firstapi.backend.model.RelayException;
import com.firstapi.backend.repository.AccountRepository;
import com.firstapi.backend.repository.IpRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelayAccountSelectorTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private IpRepository ipRepository;

    private RelayAccountSelector selector;

    @BeforeEach
    void setUp() {
        selector = new RelayAccountSelector(
                accountRepository,
                ipRepository,
                new RelayProperties()
        );
    }

    @Test
    void roundRobinsAcrossHealthyAccounts() {
        List<AccountItem> accounts = Arrays.asList(
                account(1L, "A", "OpenAI", "normal"),
                account(2L, "B", "OpenAI", "normal"),
                account(3L, "C", "OpenAI", "normal")
        );
        when(accountRepository.findAll()).thenReturn(accounts);

        assertThat(selector.selectAccount("openai").getId()).isEqualTo(1L);
        assertThat(selector.selectAccount("openai").getId()).isEqualTo(2L);
        assertThat(selector.selectAccount("openai").getId()).isEqualTo(3L);
        assertThat(selector.selectAccount("openai").getId()).isEqualTo(1L);
        assertThat(selector.selectAccount("openai").getId()).isEqualTo(2L);
        assertThat(selector.selectAccount("openai").getId()).isEqualTo(3L);
    }

    @Test
    void skipsUnhealthyAccounts() {
        List<AccountItem> accounts = Arrays.asList(
                account(1L, "A", "OpenAI", "normal"),
                account(2L, "B", "OpenAI", "disabled"),
                account(3L, "C", "OpenAI", "normal")
        );
        when(accountRepository.findAll()).thenReturn(accounts);

        assertThat(selector.selectAccount("openai").getId()).isEqualTo(1L);
        assertThat(selector.selectAccount("openai").getId()).isEqualTo(3L);
        assertThat(selector.selectAccount("openai").getId()).isEqualTo(1L);
    }

    @Test
    void throwsWhenNoEligibleAccounts() {
        List<AccountItem> accounts = Collections.singletonList(
                account(1L, "A", "OpenAI", "disabled")
        );
        when(accountRepository.findAll()).thenReturn(accounts);

        assertThatThrownBy(() -> selector.selectAccount("openai"))
                .isInstanceOf(RelayException.class)
                .hasMessageContaining("No eligible upstream account");
    }

    @Test
    void claudeProviderMatchesAnthropicPlatform() {
        List<AccountItem> accounts = Arrays.asList(
                account(1L, "A", "Anthropic", "normal"),
                account(2L, "B", "OpenAI", "normal")
        );
        when(accountRepository.findAll()).thenReturn(accounts);

        assertThat(selector.selectAccount("claude").getId()).isEqualTo(1L);
        assertThat(selector.selectAccount("claude").getId()).isEqualTo(1L);
    }

    @Test
    void separateCountersPerProvider() {
        List<AccountItem> accounts = Arrays.asList(
                account(1L, "OA-1", "OpenAI", "normal"),
                account(2L, "OA-2", "OpenAI", "normal"),
                account(3L, "CL-1", "Anthropic", "normal"),
                account(4L, "CL-2", "Anthropic", "normal")
        );
        when(accountRepository.findAll()).thenReturn(accounts);

        assertThat(selector.selectAccount("openai").getId()).isEqualTo(1L);
        assertThat(selector.selectAccount("claude").getId()).isEqualTo(3L);
        assertThat(selector.selectAccount("openai").getId()).isEqualTo(2L);
        assertThat(selector.selectAccount("claude").getId()).isEqualTo(4L);
        assertThat(selector.selectAccount("openai").getId()).isEqualTo(1L);
        assertThat(selector.selectAccount("claude").getId()).isEqualTo(3L);
    }

    @Test
    void prefersLowerPriorityUntilTierSaturated() {
        AccountItem highPriority = account(1L, "high", "OpenAI", "ok");
        highPriority.setPriorityValue(1);
        highPriority.setConcurrency(1);
        AccountItem lowPriority = account(2L, "low", "OpenAI", "ok");
        lowPriority.setPriorityValue(5);
        lowPriority.setConcurrency(1);
        when(accountRepository.findAll()).thenReturn(Arrays.asList(highPriority, lowPriority));

        AccountItem first = selector.selectAccount("openai");
        assertThat(first.getId()).isEqualTo(1L);

        AccountItem second = selector.selectAccount("openai");
        assertThat(second.getId()).isEqualTo(2L);

        selector.releaseAccount(first);
        assertThat(selector.selectAccount("openai").getId()).isEqualTo(1L);
    }

    @Test
    void respectsConcurrencyLimitAndRecoversAfterRelease() {
        AccountItem first = account(1L, "A", "OpenAI", "ok");
        first.setConcurrency(1);
        AccountItem second = account(2L, "B", "OpenAI", "ok");
        second.setConcurrency(1);
        when(accountRepository.findAll()).thenReturn(Arrays.asList(first, second));

        AccountItem firstSelected = selector.selectAccount("openai");
        AccountItem secondSelected = selector.selectAccount("openai");
        assertThat(firstSelected.getId()).isEqualTo(1L);
        assertThat(secondSelected.getId()).isEqualTo(2L);

        assertThatThrownBy(() -> selector.selectAccount("openai"))
                .isInstanceOf(RelayException.class)
                .hasMessageContaining("No eligible upstream account");

        selector.releaseAccount(firstSelected);
        assertThat(selector.selectAccount("openai").getId()).isEqualTo(1L);
    }

    @Test
    void resolveBaseUrlUsesAccountUrlWhenPresent() {
        AccountItem account = account(1L, "A", "OpenAI", "normal");
        account.setBaseUrl("https://custom.openai.com/");

        assertThat(selector.resolveBaseUrl(account, "openai"))
                .isEqualTo("https://custom.openai.com");
    }

    @Test
    void resolveBaseUrlFallsBackToDefaultForOpenai() {
        AccountItem account = account(1L, "A", "OpenAI", "normal");

        assertThat(selector.resolveBaseUrl(account, "openai"))
                .isEqualTo("https://api.openai.com");
    }

    @Test
    void resolveBaseUrlFallsBackToDefaultForClaude() {
        AccountItem account = account(1L, "A", "Anthropic", "normal");

        assertThat(selector.resolveBaseUrl(account, "claude"))
                .isEqualTo("https://api.anthropic.com");
    }

    @Test
    void resolveProxyReturnsConfiguredIpWhenPresent() {
        AccountItem account = account(1L, "A", "OpenAI", "ok");
        account.setProxyId(12L);
        IpItem proxy = new IpItem();
        proxy.setId(12L);
        proxy.setProtocol("SOCKS5");
        proxy.setAddress("127.0.0.1:7890");
        when(ipRepository.findById(12L)).thenReturn(proxy);

        assertThat(selector.resolveProxy(account)).isSameAs(proxy);
    }

    @Test
    void resolveProxyReturnsNullWhenNoProxyConfigured() {
        AccountItem account = account(1L, "A", "OpenAI", "ok");
        assertThat(selector.resolveProxy(account)).isNull();
    }

    @Test
    void resolveProxyThrowsWhenConfiguredProxyMissing() {
        AccountItem account = account(1L, "A", "OpenAI", "ok");
        account.setProxyId(99L);
        when(ipRepository.findById(99L)).thenReturn(null);

        assertThatThrownBy(() -> selector.resolveProxy(account))
                .isInstanceOf(RelayException.class)
                .hasMessageContaining("Configured proxy not found");
    }

    @Test
    void resolveProxyThrowsWhenConfiguredProxyIsUnavailable() {
        AccountItem account = account(1L, "A", "OpenAI", "ok");
        account.setProxyId(100L);
        IpItem proxy = new IpItem();
        proxy.setId(100L);
        proxy.setProtocol("SOCKS5");
        proxy.setAddress("127.0.0.1:7890");
        proxy.setStatus("error");
        when(ipRepository.findById(100L)).thenReturn(proxy);

        assertThatThrownBy(() -> selector.resolveProxy(account))
                .isInstanceOf(RelayException.class)
                .hasMessageContaining("Configured proxy unavailable");
    }

    @Test
    void tierBasedSchedulingFallsThroughHierarchy() {
        AccountItem proAccount = account(1L, "pro-acc", "Anthropic", "normal");
        proAccount.setConcurrency(1);
        proAccount.setTiers("pro");
        AccountItem max5Account = account(2L, "max5-acc", "Anthropic", "normal");
        max5Account.setConcurrency(1);
        max5Account.setTiers("pro,max5");
        AccountItem max20Account = account(3L, "max20-acc", "Anthropic", "normal");
        max20Account.setConcurrency(1);
        max20Account.setTiers("pro,max5,max20");
        when(accountRepository.findAll()).thenReturn(Arrays.asList(proAccount, max5Account, max20Account));

        // First request: picks from pro tier (account 1)
        AccountItem first = selector.selectAccount("claude");
        assertThat(first.getId()).isEqualTo(1L);
        // Second request: pro tier saturated for acc 1, picks acc 2 (also in pro tier)
        AccountItem second = selector.selectAccount("claude");
        assertThat(second.getId()).isEqualTo(2L);
        // Third request: both pro-tier slots taken, picks acc 3 (also in pro tier)
        AccountItem third = selector.selectAccount("claude");
        assertThat(third.getId()).isEqualTo(3L);
    }

    @Test
    void universalAccountsUsedAsFallback() {
        AccountItem tiered = account(1L, "tiered", "OpenAI", "normal");
        tiered.setConcurrency(1);
        tiered.setTiers("plus");
        AccountItem universal = account(2L, "universal", "OpenAI", "normal");
        universal.setConcurrency(1);
        // No tiers set - universal account
        when(accountRepository.findAll()).thenReturn(Arrays.asList(tiered, universal));

        AccountItem first = selector.selectAccount("openai");
        assertThat(first.getId()).isEqualTo(1L);
        // Tiered account saturated, falls back to universal
        AccountItem second = selector.selectAccount("openai");
        assertThat(second.getId()).isEqualTo(2L);
    }

    @Test
    void filtersByAccountTypeWhenGroupTypeProvided() {
        AccountItem plus = account(1L, "plus", "OpenAI", "normal");
        plus.setAccountType("ChatGPT Plus");
        AccountItem pro = account(2L, "pro", "OpenAI", "normal");
        pro.setAccountType("ChatGPT Pro");
        when(accountRepository.findAll()).thenReturn(Arrays.asList(plus, pro));

        assertThat(selector.selectAccount("openai", "ChatGPT Pro").getId()).isEqualTo(2L);
        assertThat(selector.selectAccount("openai", "ChatGPT Plus").getId()).isEqualTo(1L);
    }

    @Test
    void allowsQuotaExhaustedAccountAfterRetryTime() {
        AccountItem cooling = account(1L, "cooling", "OpenAI", "normal");
        cooling.setAccountType("ChatGPT Plus");
        cooling.setQuotaExhausted(true);
        cooling.setQuotaNextRetryAt("2000-01-01 00:00:00");
        when(accountRepository.findAll()).thenReturn(List.of(cooling));

        assertThat(selector.selectAccount("openai", "ChatGPT Plus").getId()).isEqualTo(1L);
    }

    @Test
    void skipsQuotaExhaustedAccountBeforeRetryTime() {
        AccountItem cooling = account(1L, "cooling", "OpenAI", "normal");
        cooling.setAccountType("ChatGPT Plus");
        cooling.setQuotaExhausted(true);
        cooling.setQuotaNextRetryAt("2999-01-01 00:00:00");
        when(accountRepository.findAll()).thenReturn(List.of(cooling));

        assertThatThrownBy(() -> selector.selectAccount("openai", "ChatGPT Plus"))
                .isInstanceOf(RelayException.class)
                .hasMessageContaining("No eligible upstream account");
    }

    private static AccountItem account(Long id, String name, String platform, String status) {
        AccountItem item = new AccountItem();
        item.setId(id);
        item.setName(name);
        item.setPlatform(platform);
        item.setStatus(status);
        return item;
    }

}
