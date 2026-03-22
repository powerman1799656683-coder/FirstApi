package com.firstapi.backend.service;

import com.firstapi.backend.common.CurrentSessionHolder;
import com.firstapi.backend.model.AccountOAuthSession;
import com.firstapi.backend.model.AuthenticatedUser;
import com.firstapi.backend.repository.AccountOAuthSessionRepository;
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

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountOAuthServiceTest {

    @Mock
    private AccountOAuthSessionRepository oauthSessionRepository;

    @Mock
    private SensitiveDataService sensitiveDataService;

    private AccountOAuthService service;
    private AccountOAuthSession savedSession;
    private MockWebServer tokenServer;

    @BeforeEach
    void setUp() throws Exception {
        service = new AccountOAuthService(oauthSessionRepository, sensitiveDataService);

        ReflectionTestUtils.setField(service, "anthropicAuthUrl", "https://console.anthropic.com/oauth/authorize");
        ReflectionTestUtils.setField(service, "anthropicClientId", "00000000-0000-4000-8000-000000000001");
        ReflectionTestUtils.setField(service, "anthropicClientSecret", "test-secret");
        ReflectionTestUtils.setField(service, "anthropicRedirectUri", "https://console.anthropic.com/oauth/callback");
        ReflectionTestUtils.setField(service, "anthropicScope", "org:create_api_key");
        ReflectionTestUtils.setField(service, "openaiAuthUrl", "https://auth.openai.com/oauth/authorize");
        ReflectionTestUtils.setField(service, "openaiClientId", "app_EMoamEEZ73f0CkXaXp7hrann");
        ReflectionTestUtils.setField(service, "openaiRedirectUri", "http://localhost:1455/auth/callback");
        ReflectionTestUtils.setField(service, "openaiScope", "openid profile email offline_access");
        ReflectionTestUtils.setField(service, "openaiSimplifiedFlow", true);
        ReflectionTestUtils.setField(service, "openaiIdTokenAddOrganizations", true);

        tokenServer = new MockWebServer();
        tokenServer.start();
        ReflectionTestUtils.setField(service, "anthropicTokenUrl", tokenServer.url("/oauth/token").toString());
        ReflectionTestUtils.setField(service, "openaiTokenUrl", tokenServer.url("/openai/oauth/token").toString());

        lenient().when(oauthSessionRepository.save(any(AccountOAuthSession.class))).thenAnswer(invocation -> {
            AccountOAuthSession session = invocation.getArgument(0);
            session.setId(100L);
            savedSession = session;
            return session;
        });

        AuthenticatedUser user = new AuthenticatedUser();
        user.setId(7L);
        user.setUsername("tester");
        user.setRole("ADMIN");
        CurrentSessionHolder.set(user);
    }

    @AfterEach
    void tearDown() throws Exception {
        CurrentSessionHolder.clear();
        if (tokenServer != null) {
            tokenServer.shutdown();
        }
    }

    @Test
    void startBuildsPkceAuthorizationUrl() {
        Map<String, Object> result = service.start(Map.of(
                "platform", "Anthropic",
                "accountType", "Claude Code",
                "authMethod", "OAuth"
        ));

        String authorizationUrl = String.valueOf(result.get("authorizationUrl"));
        assertThat(authorizationUrl).contains("response_type=code");
        assertThat(authorizationUrl).contains("code_challenge=");
        assertThat(authorizationUrl).contains("code_challenge_method=S256");
    }

    @Test
    void startBuildsClaudeAuthorizeUrlWithCodeFlagAndDefaultScopes() {
        ReflectionTestUtils.setField(service, "anthropicAuthUrl", "https://claude.ai/oauth/authorize");
        ReflectionTestUtils.setField(service, "anthropicScope", "");

        Map<String, Object> result = service.start(Map.of(
                "platform", "Anthropic",
                "accountType", "Claude Code",
                "authMethod", "OAuth"
        ));

        String authorizationUrl = String.valueOf(result.get("authorizationUrl"));
        assertThat(authorizationUrl).contains("code=true");
        assertThat(authorizationUrl).contains(
                "scope=org%3Acreate_api_key+user%3Aprofile+user%3Ainference+user%3Asessions%3Aclaude_code+user%3Amcp_servers"
        );
    }

    @Test
    void exchangeSendsCodeVerifierToTokenEndpoint() throws Exception {
        when(oauthSessionRepository.findBySessionId(anyString())).thenAnswer(invocation -> {
            String sessionId = invocation.getArgument(0);
            if (savedSession != null && sessionId.equals(savedSession.getSessionId())) {
                return savedSession;
            }
            return null;
        });

        Map<String, Object> startResult = service.start(Map.of(
                "platform", "Anthropic",
                "accountType", "Claude Code",
                "authMethod", "OAuth"
        ));
        String sessionId = String.valueOf(startResult.get("sessionId"));
        String state = String.valueOf(startResult.get("state"));

        tokenServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"access_token\":\"sk-ant-test-token\",\"organization_id\":\"org_test\"}"));
        when(sensitiveDataService.protect("sk-ant-test-token")).thenReturn("encrypted-token");

        Map<String, Object> exchangeResult = service.exchange(Map.of(
                "sessionId", sessionId,
                "state", state,
                "code", "oauth_test_code"
        ));

        assertThat(exchangeResult.get("credentialRef")).isEqualTo(sessionId);

        RecordedRequest request = tokenServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getHeader("Content-Type")).isEqualTo("application/json");
        String body = request.getBody().readUtf8();
        assertThat(body).contains("\"grant_type\":\"authorization_code\"");
        assertThat(body).contains("\"code\":\"oauth_test_code\"");
        assertThat(body).contains("\"state\":\"" + state + "\"");
        assertThat(body).contains("\"code_verifier\":");
    }

    @Test
    void exchangeDoesNotRequireClientSecretForPkce() throws Exception {
        ReflectionTestUtils.setField(service, "anthropicClientSecret", "");
        when(oauthSessionRepository.findBySessionId(anyString())).thenAnswer(invocation -> {
            String sessionId = invocation.getArgument(0);
            if (savedSession != null && sessionId.equals(savedSession.getSessionId())) {
                return savedSession;
            }
            return null;
        });

        Map<String, Object> startResult = service.start(Map.of(
                "platform", "Anthropic",
                "accountType", "Claude Code",
                "authMethod", "OAuth"
        ));
        String sessionId = String.valueOf(startResult.get("sessionId"));
        String state = String.valueOf(startResult.get("state"));

        tokenServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"access_token\":\"sk-ant-test-token\",\"organization_id\":\"org_test\"}"));
        when(sensitiveDataService.protect("sk-ant-test-token")).thenReturn("encrypted-token");

        Map<String, Object> exchangeResult = service.exchange(Map.of(
                "sessionId", sessionId,
                "state", state,
                "code", "oauth_test_code"
        ));
        assertThat(exchangeResult.get("credentialRef")).isEqualTo(sessionId);

        RecordedRequest request = tokenServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getHeader("Content-Type")).isEqualTo("application/json");
        String body = request.getBody().readUtf8();
        assertThat(body).contains("\"grant_type\":\"authorization_code\"");
        assertThat(body).contains("\"code_verifier\":");
        assertThat(body).doesNotContain("\"client_secret\":");
    }

    @Test
    void startRejectsNonUuidAnthropicClientId() {
        ReflectionTestUtils.setField(service, "anthropicClientId", "claude-code-proxy");

        assertThatThrownBy(() -> service.start(Map.of(
                "platform", "Anthropic",
                "accountType", "Claude Code",
                "authMethod", "OAuth"
        )))
                .hasMessageContaining("FIRSTAPI_OAUTH_ANTHROPIC_CLIENT_ID")
                .hasMessageContaining("UUID");
    }

    @Test
    void startBuildsOpenAiAuthorizationUrl() {
        Map<String, Object> result = service.start(Map.of(
                "platform", "OpenAI",
                "accountType", "ChatGPT Plus",
                "authMethod", "OAuth"
        ));

        String authorizationUrl = String.valueOf(result.get("authorizationUrl"));
        assertThat(authorizationUrl).startsWith("https://auth.openai.com/oauth/authorize?");
        assertThat(authorizationUrl).contains("client_id=app_EMoamEEZ73f0CkXaXp7hrann");
        assertThat(authorizationUrl).contains("response_type=code");
        assertThat(authorizationUrl).contains("scope=openid+profile+email+offline_access");
        assertThat(authorizationUrl).contains("redirect_uri=http%3A%2F%2Flocalhost%3A1455%2Fauth%2Fcallback");
        assertThat(authorizationUrl).contains("codex_cli_simplified_flow=true");
        assertThat(authorizationUrl).contains("id_token_add_organizations=true");
        assertThat(authorizationUrl).contains("code_challenge=");
        assertThat(authorizationUrl).contains("code_challenge_method=S256");
    }

    @Test
    void exchangeOpenAiCodeUsesFormPayloadWithPkce() throws Exception {
        when(oauthSessionRepository.findBySessionId(anyString())).thenAnswer(invocation -> {
            String sessionId = invocation.getArgument(0);
            if (savedSession != null && sessionId.equals(savedSession.getSessionId())) {
                return savedSession;
            }
            return null;
        });

        Map<String, Object> startResult = service.start(Map.of(
                "platform", "OpenAI",
                "accountType", "ChatGPT Plus",
                "authMethod", "OAuth"
        ));
        String sessionId = String.valueOf(startResult.get("sessionId"));
        String state = String.valueOf(startResult.get("state"));

        tokenServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"access_token\":\"sk-openai-oauth-token\",\"sub\":\"user_123\"}"));
        when(sensitiveDataService.protect("sk-openai-oauth-token")).thenReturn("encrypted-openai-token");

        Map<String, Object> exchangeResult = service.exchange(Map.of(
                "sessionId", sessionId,
                "state", state,
                "code", "oauth_openai_code"
        ));

        assertThat(exchangeResult.get("credentialRef")).isEqualTo(sessionId);

        RecordedRequest request = tokenServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getPath()).isEqualTo("/openai/oauth/token");
        assertThat(request.getHeader("anthropic-beta")).isNull();
        String body = request.getBody().readUtf8();
        assertThat(body).contains("grant_type=authorization_code");
        assertThat(body).contains("code=oauth_openai_code");
        assertThat(body).contains("client_id=app_EMoamEEZ73f0CkXaXp7hrann");
        assertThat(body).contains("redirect_uri=http%3A%2F%2Flocalhost%3A1455%2Fauth%2Fcallback");
        assertThat(body).contains("code_verifier=");
    }

    @Test
    void exchangeOpenAiCodeHandlesLargeIdTokenPayload() {
        when(oauthSessionRepository.findBySessionId(anyString())).thenAnswer(invocation -> {
            String sessionId = invocation.getArgument(0);
            if (savedSession != null && sessionId.equals(savedSession.getSessionId())) {
                return savedSession;
            }
            return null;
        });

        Map<String, Object> startResult = service.start(Map.of(
                "platform", "OpenAI",
                "accountType", "ChatGPT Plus",
                "authMethod", "OAuth"
        ));
        String sessionId = String.valueOf(startResult.get("sessionId"));
        String state = String.valueOf(startResult.get("state"));

        String largeIdToken = "header." + "a".repeat(24000) + ".signature";
        tokenServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"access_token\":\"sk-openai-large-token\",\"sub\":\"user_456\",\"id_token\":\""
                        + largeIdToken + "\"}"));
        when(sensitiveDataService.protect("sk-openai-large-token")).thenReturn("encrypted-large-token");

        Map<String, Object> exchangeResult = service.exchange(Map.of(
                "sessionId", sessionId,
                "state", state,
                "code", "oauth_openai_large_code"
        ));

        assertThat(exchangeResult.get("credentialRef")).isEqualTo(sessionId);
        assertThat(exchangeResult.get("credentialMask")).isEqualTo("sk-o****oken");
    }

}
