package com.firstapi.backend.integration;

import com.firstapi.backend.config.AuthFilter;
import com.firstapi.backend.model.ApiKeyItem;
import com.firstapi.backend.repository.MyApiKeysRepository;
import com.firstapi.backend.service.AccountOAuthService;
import com.firstapi.backend.service.SensitiveDataService;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.Cookie;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AccountOAuthIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private AuthFilter authFilter;

    @Autowired
    private AccountOAuthService accountOAuthService;

    @Autowired
    private MyApiKeysRepository apiKeysRepository;

    @Autowired
    private SensitiveDataService sensitiveDataService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;
    private Cookie adminCookie;
    private MockWebServer tokenServer;
    private MockWebServer relayServer;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(authFilter)
                .build();

        tokenServer = new MockWebServer();
        tokenServer.start();

        relayServer = new MockWebServer();
        relayServer.start();

        ReflectionTestUtils.setField(accountOAuthService, "anthropicTokenUrl",
                tokenServer.url("/v1/oauth/token").toString());
        ReflectionTestUtils.setField(accountOAuthService, "anthropicAuthUrl",
                "https://platform.claude.com/oauth/authorize");
        ReflectionTestUtils.setField(accountOAuthService, "anthropicClientId",
                "9d1c250a-e61b-44d9-88ed-5944d1962f5e");
        ReflectionTestUtils.setField(accountOAuthService, "anthropicRedirectUri",
                "https://platform.claude.com/oauth/code/callback");
        ReflectionTestUtils.setField(accountOAuthService, "anthropicScope",
                "org:create_api_key user:profile");
        ReflectionTestUtils.setField(accountOAuthService, "anthropicBetaHeader",
                "oauth-2025-04-20");

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"AdminPass123!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        adminCookie = login.getResponse().getCookie("FIRSTAPI_SESSION");
        assertThat(adminCookie).isNotNull();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (tokenServer != null) {
            tokenServer.shutdown();
        }
        if (relayServer != null) {
            relayServer.shutdown();
        }
    }

    @Test
    @DisplayName("OAuth full flow: start -> exchange -> create by credentialRef -> reject reused credentialRef")
    void oauthFullFlowWorksEndToEnd() throws Exception {
        MvcResult startResult = mockMvc.perform(post("/api/admin/accounts/oauth/start")
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "platform":"Anthropic",
                                  "accountType":"Claude Code",
                                  "authMethod":"OAuth"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sessionId").isString())
                .andExpect(jsonPath("$.data.state").isString())
                .andExpect(jsonPath("$.data.authorizationUrl").isString())
                .andReturn();

        JsonNode startData = objectMapper.readTree(startResult.getResponse().getContentAsString()).get("data");
        String sessionId = startData.get("sessionId").asText();
        String state = startData.get("state").asText();
        String authorizationUrl = startData.get("authorizationUrl").asText();

        assertThat(authorizationUrl).contains("response_type=code");
        assertThat(authorizationUrl).contains("client_id=9d1c250a-e61b-44d9-88ed-5944d1962f5e");
        assertThat(authorizationUrl).contains("code_challenge=");
        assertThat(authorizationUrl).contains("code_challenge_method=S256");
        assertThat(authorizationUrl).contains("redirect_uri=https%3A%2F%2Fplatform.claude.com%2Foauth%2Fcode%2Fcallback");

        tokenServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"access_token\":\"sk-ant-flow-token\",\"organization_id\":\"org_flow\"}"));

        MvcResult exchangeResult = mockMvc.perform(post("/api/admin/accounts/oauth/exchange")
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId":"%s",
                                  "state":"%s",
                                  "code":"oauth_flow_code"
                                }
                                """.formatted(sessionId, state)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.credentialRef").value(sessionId))
                .andExpect(jsonPath("$.data.providerAccount.provider").value("Anthropic"))
                .andReturn();

        JsonNode exchangeData = objectMapper.readTree(exchangeResult.getResponse().getContentAsString()).get("data");
        String credentialRef = exchangeData.get("credentialRef").asText();
        assertThat(exchangeData.get("credentialMask").asText()).contains("****");

        RecordedRequest tokenRequest = tokenServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(tokenRequest).isNotNull();
        assertThat(tokenRequest.getPath()).isEqualTo("/v1/oauth/token");
        assertThat(tokenRequest.getHeader("Content-Type")).isEqualTo("application/json");
        assertThat(tokenRequest.getHeader("anthropic-beta")).isEqualTo("oauth-2025-04-20");
        String tokenBody = tokenRequest.getBody().readUtf8();
        assertThat(tokenBody).contains("\"grant_type\":\"authorization_code\"");
        assertThat(tokenBody).contains("\"code\":\"oauth_flow_code\"");
        assertThat(tokenBody).contains("\"client_id\":\"9d1c250a-e61b-44d9-88ed-5944d1962f5e\"");
        assertThat(tokenBody).contains("\"state\":\"" + state + "\"");
        assertThat(tokenBody).contains("\"redirect_uri\":\"https://platform.claude.com/oauth/code/callback\"");
        assertThat(tokenBody).contains("\"code_verifier\":");

        String accountName = "oauth-flow-" + System.currentTimeMillis();
        MvcResult createResult = mockMvc.perform(post("/api/admin/accounts")
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"%s",
                                  "platform":"Anthropic",
                                  "accountType":"Claude Code",
                                  "authMethod":"OAuth",
                                  "credentialRef":"%s"
                                }
                                """.formatted(accountName, credentialRef)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value(accountName))
                .andExpect(jsonPath("$.data.authMethod").value("OAuth"))
                .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data");
        long accountId = created.get("id").asLong();

        mockMvc.perform(get("/api/admin/accounts/" + accountId).cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(accountId))
                .andExpect(jsonPath("$.data.name").value(accountName));

        mockMvc.perform(post("/api/admin/accounts")
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"%s-reuse",
                                  "platform":"Anthropic",
                                  "accountType":"Claude Code",
                                  "authMethod":"OAuth",
                                  "credentialRef":"%s"
                                }
                                """.formatted(accountName, credentialRef)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("already consumed")));
    }

    @Test
    @DisplayName("Anthropic OAuth access_token accounts relay /v1/messages with x-api-key using exchanged credential")
    void oauthAccessTokenAccountRelaysClaudeMessagesWithExchangedCredential() throws Exception {
        MvcResult startResult = mockMvc.perform(post("/api/admin/accounts/oauth/start")
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "platform":"Anthropic",
                                  "accountType":"Claude Code",
                                  "authMethod":"OAuth"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode startData = objectMapper.readTree(startResult.getResponse().getContentAsString()).get("data");
        String sessionId = startData.get("sessionId").asText();
        String state = startData.get("state").asText();

        tokenServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"access_token\":\"anth-access-token-only\",\"organization_id\":\"org_flow\"}"));

        MvcResult exchangeResult = mockMvc.perform(post("/api/admin/accounts/oauth/exchange")
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId":"%s",
                                  "state":"%s",
                                  "code":"oauth_flow_code"
                                }
                                """.formatted(sessionId, state)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.credentialRef").value(sessionId))
                .andReturn();

        String credentialRef = objectMapper.readTree(exchangeResult.getResponse().getContentAsString())
                .path("data")
                .path("credentialRef")
                .asText();

        String accountName = "oauth-relay-" + System.currentTimeMillis();
        MvcResult createResult = mockMvc.perform(post("/api/admin/accounts")
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"%s",
                                  "platform":"Anthropic",
                                  "accountType":"Claude Code",
                                  "authMethod":"OAuth",
                                  "credentialRef":"%s"
                                }
                                """.formatted(accountName, credentialRef)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(accountName))
                .andReturn();

        long accountId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data")
                .path("id")
                .asLong();

        jdbcTemplate.update("update `accounts` set `base_url` = ? where `id` = ?",
                relayServer.url("/").toString().replaceAll("/$", ""),
                accountId);
        jdbcTemplate.update("update `accounts` set `temp_disabled` = 1 where `id` <> ?", accountId);

        String relayApiKey = saveApiKey("sk-firstapi-oauth-relay", 2L);

        relayServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""
                        {
                          "id":"msg_oauth_1",
                          "type":"message",
                          "role":"assistant",
                          "model":"claude-3-5-sonnet",
                          "content":[{"type":"text","text":"hello oauth relay"}],
                          "stop_reason":"end_turn",
                          "usage":{"input_tokens":3,"output_tokens":5}
                        }
                        """));

        mockMvc.perform(post("/v1/messages")
                        .header("x-api-key", relayApiKey)
                        .header("anthropic-version", "2023-06-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model":"claude-3-5-sonnet",
                                  "max_tokens":32,
                                  "messages":[{"role":"user","content":"hi"}]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].text").value("hello oauth relay"));

        RecordedRequest request = relayServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getPath()).isEqualTo("/v1/messages");
        assertThat(request.getHeader("x-api-key")).isEqualTo("anth-access-token-only");
        assertThat(request.getHeader("anthropic-version")).isEqualTo("2023-06-01");
    }

    private String saveApiKey(String plainTextKey, Long groupId) {
        ApiKeyItem item = new ApiKeyItem();
        item.setOwnerId(1L);
        item.setGroupId(groupId);
        item.setName("relay");
        item.setKey(sensitiveDataService.protect(plainTextKey));
        item.setCreated("2026/03/22 00:00:00");
        item.setStatus("正常");
        item.setLastUsed("-");
        apiKeysRepository.save(item);
        return plainTextKey;
    }
}
