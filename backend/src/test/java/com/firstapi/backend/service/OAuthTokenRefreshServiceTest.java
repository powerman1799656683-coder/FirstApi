package com.firstapi.backend.service;

import com.firstapi.backend.model.AccountItem;
import com.firstapi.backend.repository.AccountRepository;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthTokenRefreshServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private SensitiveDataService sensitiveDataService;

    @Mock
    private QuotaStateManager quotaStateManager;

    private OAuthTokenRefreshService service;
    private MockWebServer tokenServer;

    @BeforeEach
    void setUp() throws Exception {
        service = new OAuthTokenRefreshService(
                accountRepository,
                sensitiveDataService,
                quotaStateManager,
                new ObjectMapper()
        );
        tokenServer = new MockWebServer();
        tokenServer.start();
        ReflectionTestUtils.setField(service, "openaiClientId", "");
        ReflectionTestUtils.setField(service, "openaiTokenUrl", tokenServer.url("/oauth/token").toString());
    }

    @AfterEach
    void tearDown() throws Exception {
        tokenServer.shutdown();
    }

    @Test
    void returnsFalseWithoutCallingUpstreamWhenOpenAiClientIdMissing() throws Exception {
        AccountItem account = new AccountItem();
        account.setId(26L);
        account.setAuthMethod("OAuth");
        account.setPlatform("OpenAI");
        account.setEncryptedRefreshToken("encrypted-refresh");

        tokenServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"access_token\":\"new-token\"}"));
        when(accountRepository.findById(26L)).thenReturn(account);
        when(sensitiveDataService.reveal("encrypted-refresh")).thenReturn("refresh-token");

        boolean refreshed = service.tryRefreshNow(26L);

        assertThat(refreshed).isFalse();
        assertThat(tokenServer.getRequestCount()).isZero();
        verify(accountRepository, never()).update(anyLong(), any(AccountItem.class));
        verify(quotaStateManager, never()).clearQuotaStateIfRecovered(anyLong());
        verify(sensitiveDataService, never()).protect(any());
    }

    @Test
    void refreshOpenAiTokenUsesFormPayloadAndUpdatesAccount() throws Exception {
        ReflectionTestUtils.setField(service, "openaiClientId", "app_EMoamEEZ73f0CkXaXp7hrann");

        AccountItem account = new AccountItem();
        account.setId(27L);
        account.setAuthMethod("OAuth");
        account.setPlatform("OpenAI");
        account.setEncryptedRefreshToken("encrypted-refresh");
        account.setCredential("old-credential");

        tokenServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"access_token\":\"new-token\",\"refresh_token\":\"rotated-refresh\",\"expires_in\":7200}"));
        when(accountRepository.findById(27L)).thenReturn(account);
        when(sensitiveDataService.reveal("encrypted-refresh")).thenReturn("refresh-token");
        when(sensitiveDataService.protect("new-token")).thenReturn("encrypted-new-token");
        when(sensitiveDataService.protect("rotated-refresh")).thenReturn("encrypted-rotated-refresh");

        boolean refreshed = service.tryRefreshNow(27L);

        assertThat(refreshed).isTrue();

        RecordedRequest request = tokenServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getHeader("Content-Type")).isEqualTo("application/x-www-form-urlencoded");
        assertThat(request.getBody().readUtf8()).contains(
                "grant_type=refresh_token",
                "refresh_token=refresh-token",
                "client_id=app_EMoamEEZ73f0CkXaXp7hrann"
        );

        assertThat(account.getCredential()).isEqualTo("encrypted-new-token");
        assertThat(account.getEncryptedRefreshToken()).isEqualTo("encrypted-rotated-refresh");
        assertThat(account.getOauthTokenExpiresAt()).isNotBlank();
        verify(accountRepository).update(27L, account);
        verify(quotaStateManager).clearQuotaStateIfRecovered(27L);
    }
}
