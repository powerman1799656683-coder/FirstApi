package com.firstapi.backend.service;

import com.firstapi.backend.common.PageResponse;
import com.firstapi.backend.config.RelayProperties;
import com.firstapi.backend.model.AccountItem;
import com.firstapi.backend.model.GroupItem;
import com.firstapi.backend.model.IpItem;
import com.firstapi.backend.model.RelayException;
import com.firstapi.backend.model.RelayRecordItem;
import com.firstapi.backend.model.RelayResult;
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
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter RECORD_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    private static final DateTimeFormatter EXPIRY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

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

    private AccountService service;

    @BeforeEach
    void setUp() {
        service = new AccountService(
                accountRepository,
                sensitiveDataService,
                ipRepository,
                relayRecordRepository,
                groupRepository,
                accountGroupBindingRepository,
                upstreamHttpClient,
                relayProperties,
                accountOAuthService,
                relayAccountSelector
        );
        lenient().when(accountGroupBindingRepository.findAllGroupings()).thenReturn(Map.of());
    }

    @Test
    void listEnrichesAccountsWithRuntimeMetrics() {
        AccountItem account = new AccountItem();
        account.setId(1L);
        account.setName("gpt_pool_a");
        account.setPlatform("OpenAI");
        account.setType("OpenAI API");
        account.setAccountType("ChatGPT Plus");
        account.setStatus("normal");
        account.setPriorityValue(1);
        account.setConcurrency(50);

        LocalDateTime now = LocalDateTime.now(ZONE);
        List<RelayRecordItem> records = List.of(
                record(1L, true, 200, 1_200, now.minusMinutes(12)),
                record(1L, false, 500, 800, now.minusMinutes(4)),
                record(1L, true, 200, 500, now.minusDays(8))
        );

        when(accountRepository.findAll()).thenReturn(List.of(account));
        when(relayRecordRepository.findAll()).thenReturn(records);
        when(groupRepository.findAll()).thenReturn(List.of(
                group("codex", "OpenAI", "ChatGPT Plus", "normal", "1.5"),
                group("codex-pro", "OpenAI", "ChatGPT Pro", "normal", "1.2"),
                group("legacy", "OpenAI", "ChatGPT Plus", "disabled", "1.0"),
                group("claude", "Anthropic", "Claude Code", "normal", "1.3")
        ));

        PageResponse<AccountItem> response = service.list(null);
        AccountItem enriched = response.getItems().get(0);

        assertThat(enriched.getTodayRequests()).isEqualTo(2L);
        assertThat(enriched.getTodayTokens()).isEqualTo(2_000L);
        assertThat(enriched.getTodayAccountCost()).startsWith("¥");
        assertThat(enriched.getTodayUserCost()).startsWith("¥");
        assertThat(enriched.getGroups()).containsExactly("codex");
        assertThat(enriched.getUsageWindows()).hasSize(3);
        assertThat(enriched.getUsageWindows().get(0).getKey()).isEqualTo("3h");
        assertThat(enriched.getCapacityLimit()).isEqualTo(50);
        assertThat(enriched.getRecentUsedText()).isNotBlank();
        assertThat(enriched.getScheduleEnabled()).isTrue();
    }

    @Test
    void listMarksExpiredAccountAsUnschedulable() {
        AccountItem account = new AccountItem();
        account.setId(2L);
        account.setName("claude_expired");
        account.setPlatform("Anthropic");
        account.setType("Claude Code");
        account.setStatus("normal");
        account.setPriorityValue(1);
        account.setAutoSuspendExpiry(true);
        account.setExpiryTime(LocalDateTime.now(ZONE).minusHours(1).format(EXPIRY_FORMAT));

        when(accountRepository.findAll()).thenReturn(List.of(account));
        when(relayRecordRepository.findAll()).thenReturn(List.of());
        when(groupRepository.findAll()).thenReturn(List.of());

        AccountItem enriched = service.list(null).getItems().get(0);

        assertThat(enriched.getExpired()).isTrue();
        assertThat(enriched.getScheduleEnabled()).isFalse();
        assertThat(enriched.getEffectiveStatus()).isEqualTo("已过期");
    }

    @Test
    void createPersistsProxyIdWhenProxyExists() {
        AccountItem.Request request = new AccountItem.Request();
        request.setName("proxy-account");
        request.setPlatform("OpenAI");
        request.setProxyId(12L);

        IpItem proxy = new IpItem();
        proxy.setId(12L);
        when(ipRepository.findById(12L)).thenReturn(proxy);
        when(accountRepository.save(any(AccountItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccountItem created = service.create(request);

        assertThat(created.getProxyId()).isEqualTo(12L);
    }

    @Test
    void createRejectsUnknownProxyId() {
        AccountItem.Request request = new AccountItem.Request();
        request.setName("proxy-account");
        request.setPlatform("OpenAI");
        request.setProxyId(99L);

        when(ipRepository.findById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testMarksNormalWhenProbeSuccess() {
        AccountItem account = new AccountItem();
        account.setId(21L);
        account.setPlatform("OpenAI");
        account.setCredential("encrypted");
        account.setErrors(3);

        when(accountRepository.findById(21L)).thenReturn(account);
        when(accountRepository.update(eq(21L), any(AccountItem.class))).thenAnswer(invocation -> invocation.getArgument(1));
        when(relayRecordRepository.findAll()).thenReturn(List.of());
        when(groupRepository.findAll()).thenReturn(List.of());
        when(sensitiveDataService.reveal("encrypted")).thenReturn("sk-test");
        when(relayProperties.getOpenaiBaseUrl()).thenReturn("https://api.openai.com");

        RelayResult probe = new RelayResult();
        probe.setStatusCode(200);
        probe.setSuccess(true);
        when(upstreamHttpClient.get(eq("https://api.openai.com/v1/models"), any(Map.class), eq((IpItem) null)))
                .thenReturn(probe);

        AccountItem tested = service.test(21L);

        assertThat(tested.getStatus()).isEqualTo("normal");
        assertThat(tested.getErrors()).isEqualTo(0);
        assertThat(tested.getLastCheck()).isNotBlank();
    }

    @Test
    void testMarksErrorWhenProbeThrows() {
        AccountItem account = new AccountItem();
        account.setId(22L);
        account.setPlatform("OpenAI");
        account.setCredential("encrypted");
        account.setErrors(1);

        when(accountRepository.findById(22L)).thenReturn(account);
        when(accountRepository.update(eq(22L), any(AccountItem.class))).thenAnswer(invocation -> invocation.getArgument(1));
        when(relayRecordRepository.findAll()).thenReturn(List.of());
        when(groupRepository.findAll()).thenReturn(List.of());
        when(sensitiveDataService.reveal("encrypted")).thenReturn("sk-test");
        when(relayProperties.getOpenaiBaseUrl()).thenReturn("https://api.openai.com");
        when(upstreamHttpClient.get(eq("https://api.openai.com/v1/models"), any(Map.class), eq((IpItem) null)))
                .thenThrow(new RelayException(HttpStatus.BAD_GATEWAY, "probe failed", "upstream_error"));

        AccountItem tested = service.test(22L);

        assertThat(tested.getStatus()).isEqualTo("error");
        assertThat(tested.getErrors()).isEqualTo(2);
        assertThat(tested.getLastCheck()).isNotBlank();
    }

    @Test
    void testUsesClaudeMessagesProbeForAnthropicAccounts() {
        AccountItem account = new AccountItem();
        account.setId(23L);
        account.setPlatform("Anthropic");
        account.setCredential("encrypted");
        account.setErrors(2);

        when(accountRepository.findById(23L)).thenReturn(account);
        when(accountRepository.update(eq(23L), any(AccountItem.class))).thenAnswer(invocation -> invocation.getArgument(1));
        when(relayRecordRepository.findAll()).thenReturn(List.of());
        when(groupRepository.findAll()).thenReturn(List.of());
        when(sensitiveDataService.reveal("encrypted")).thenReturn("sk-ant-test");
        when(relayProperties.getClaudeBaseUrl()).thenReturn("https://api.anthropic.com");
        when(relayProperties.getAnthropicVersion()).thenReturn("2023-06-01");

        RelayResult probe = new RelayResult();
        probe.setStatusCode(200);
        probe.setSuccess(true);
        when(upstreamHttpClient.postJson(
                eq("https://api.anthropic.com/v1/messages"),
                any(Map.class),
                any(),
                eq((IpItem) null),
                anyInt()))
                .thenReturn(probe);

        AccountItem tested = service.test(23L);

        assertThat(tested.getStatus()).isEqualTo("normal");
        assertThat(tested.getErrors()).isEqualTo(0);
        assertThat(tested.getLastCheck()).isNotBlank();
    }

    @Test
    void fetchUpstreamModelsUsesChatGptEndpointForOpenAiOAuthAccounts() {
        AccountItem account = new AccountItem();
        account.setId(31L);
        account.setPlatform("OpenAI");
        account.setAuthMethod("OAuth");
        account.setStatus("normal");
        account.setCredential("encrypted");

        when(accountRepository.findAll()).thenReturn(List.of(account));
        when(sensitiveDataService.reveal("encrypted")).thenReturn("oauth-access-token");

        RelayResult result = new RelayResult();
        result.setStatusCode(200);
        result.setSuccess(true);
        result.setBody("""
                {
                  "models": [
                    { "slug": "gpt-5" },
                    { "slug": "gpt-5-mini" }
                  ]
                }
                """);
        when(upstreamHttpClient.get(
                eq("https://chatgpt.com/backend-api/models?history_and_training_disabled=false"),
                any(Map.class),
                eq((IpItem) null)))
                .thenReturn(result);

        List<String> models = service.fetchUpstreamModels();

        assertThat(models).containsExactly("gpt-5", "gpt-5-mini");
    }

    @Test
    void fetchUpstreamModelsKeepsApiKeyAccountsOnV1ModelsEndpoint() {
        AccountItem account = new AccountItem();
        account.setId(32L);
        account.setPlatform("OpenAI");
        account.setAuthMethod("API Key");
        account.setStatus("normal");
        account.setCredential("encrypted");

        when(accountRepository.findAll()).thenReturn(List.of(account));
        when(sensitiveDataService.reveal("encrypted")).thenReturn("sk-test");
        when(relayProperties.getOpenaiBaseUrl()).thenReturn("https://api.openai.com");

        RelayResult result = new RelayResult();
        result.setStatusCode(200);
        result.setSuccess(true);
        result.setBody("""
                {
                  "data": [
                    { "id": "gpt-4o" },
                    { "id": "gpt-4o-mini" }
                  ]
                }
                """);
        when(upstreamHttpClient.get(
                eq("https://api.openai.com/v1/models"),
                any(Map.class),
                eq((IpItem) null)))
                .thenReturn(result);

        List<String> models = service.fetchUpstreamModels();

        assertThat(models).containsExactly("gpt-4o", "gpt-4o-mini");
    }

    private static RelayRecordItem record(Long accountId,
                                          boolean success,
                                          int statusCode,
                                          int totalTokens,
                                          LocalDateTime createdAt) {
        RelayRecordItem item = new RelayRecordItem();
        item.setAccountId(accountId);
        item.setSuccess(success);
        item.setStatusCode(statusCode);
        item.setTotalTokens(totalTokens);
        item.setCreatedAt(createdAt.format(RECORD_TIME_FORMAT));
        return item;
    }

    private static GroupItem group(String name, String platform, String accountType, String status, String rate) {
        GroupItem item = new GroupItem();
        item.setName(name);
        item.setPlatform(platform);
        item.setAccountType(accountType);
        item.setStatus(status);
        item.setRate(rate);
        return item;
    }
}
