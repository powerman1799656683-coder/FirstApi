package com.firstapi.backend.service;

import com.firstapi.backend.model.ApiKeyItem;
import com.firstapi.backend.model.GroupItem;
import com.firstapi.backend.model.RelayChatCompletionRequest;
import com.firstapi.backend.model.RelayException;
import com.firstapi.backend.model.RelayResult;
import com.firstapi.backend.model.RelayRoute;
import com.firstapi.backend.repository.GroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelayServiceTest {

    @Mock
    private RelayApiKeyAuthService relayApiKeyAuthService;

    @Mock
    private RelayModelRouter relayModelRouter;

    @Mock
    private OpenAiRelayAdapter openAiRelayAdapter;

    @Mock
    private ClaudeRelayAdapter claudeRelayAdapter;

    @Mock
    private RelayRecordService relayRecordService;

    @Mock
    private RelayAccountSelector relayAccountSelector;

    @Mock
    private QuotaStateManager quotaStateManager;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private UserService userService;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private DailyQuotaService dailyQuotaService;

    @Mock
    private OAuthTokenRefreshService oauthTokenRefreshService;

    private RelayService relayService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        relayService = new RelayService(
                relayApiKeyAuthService,
                relayModelRouter,
                openAiRelayAdapter,
                claudeRelayAdapter,
                relayRecordService,
                relayAccountSelector,
                quotaStateManager,
                groupRepository,
                objectMapper,
                userService,
                subscriptionService,
                dailyQuotaService,
                oauthTokenRefreshService
        );
    }

    @Test
    void rejectsWhenApiKeyGroupMissing() {
        ApiKeyItem apiKey = new ApiKeyItem();
        apiKey.setGroupId(null);
        when(relayApiKeyAuthService.authenticate("Bearer sk-test")).thenReturn(apiKey);

        assertThatThrownBy(() -> relayService.relayChatCompletion("Bearer sk-test", validRequest("gpt-4o-mini")))
                .isInstanceOf(RelayException.class)
                .matches(ex -> ((RelayException) ex).getStatus().equals(HttpStatus.FORBIDDEN))
                .hasMessageContaining("group");

        verify(openAiRelayAdapter, never()).relay(any(), any(), any());
        verify(claudeRelayAdapter, never()).relay(any(), any(), any());
    }

    @Test
    void rejectsWhenGroupPlatformMismatch() {
        ApiKeyItem apiKey = new ApiKeyItem();
        apiKey.setGroupId(1L);
        when(relayApiKeyAuthService.authenticate("Bearer sk-test")).thenReturn(apiKey);
        when(userService.checkBalanceByAuthUserId(any())).thenReturn(true);
        when(relayModelRouter.route("gpt-4o-mini")).thenReturn(new RelayRoute("openai"));
        when(groupRepository.findById(1L)).thenReturn(group("claude-default", "Anthropic", "Claude Code", "正常"));

        assertThatThrownBy(() -> relayService.relayChatCompletion("Bearer sk-test", validRequest("gpt-4o-mini")))
                .isInstanceOf(RelayException.class)
                .matches(ex -> ((RelayException) ex).getStatus().equals(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("platform");
    }

    @Test
    void marksQuotaCooldownWhenQuotaErrorDetected() {
        ApiKeyItem apiKey = new ApiKeyItem();
        apiKey.setId(9L);
        apiKey.setOwnerId(99L);
        apiKey.setGroupId(2L);
        when(relayApiKeyAuthService.authenticate("Bearer sk-test")).thenReturn(apiKey);
        when(userService.checkBalanceByAuthUserId(99L)).thenReturn(true);
        when(relayModelRouter.route("gpt-4o-mini")).thenReturn(new RelayRoute("openai"));
        when(groupRepository.findById(2L)).thenReturn(group("openai-default", "OpenAI", "ChatGPT Plus", "正常"));

        RelayResult result = new RelayResult();
        result.setAccountId(1L);
        result.setSuccess(false);
        result.setStatusCode(402);
        when(openAiRelayAdapter.relay(any(), any(), eq("ChatGPT Plus"))).thenReturn(result);

        relayService.relayChatCompletion("Bearer sk-test", validRequest("gpt-4o-mini"));

        verify(quotaStateManager).markQuotaExhausted(eq(1L), any());
    }

    @Test
    void clearsQuotaStateAfterSuccessfulProbe() {
        ApiKeyItem apiKey = new ApiKeyItem();
        apiKey.setId(9L);
        apiKey.setOwnerId(99L);
        apiKey.setGroupId(2L);
        when(relayApiKeyAuthService.authenticate("Bearer sk-test")).thenReturn(apiKey);
        when(userService.checkBalanceByAuthUserId(99L)).thenReturn(true);
        when(relayModelRouter.route("gpt-4o-mini")).thenReturn(new RelayRoute("openai"));
        when(groupRepository.findById(2L)).thenReturn(group("openai-default", "OpenAI", "ChatGPT Plus", "正常"));

        RelayResult result = new RelayResult();
        result.setAccountId(1L);
        result.setSuccess(true);
        result.setStatusCode(200);
        when(openAiRelayAdapter.relay(any(), any(), eq("ChatGPT Plus"))).thenReturn(result);

        relayService.relayChatCompletion("Bearer sk-test", validRequest("gpt-4o-mini"));

        verify(quotaStateManager).clearQuotaStateIfRecovered(eq(1L));
    }

    @Test
    void allowsOpenAiToolCallingFieldsForOpenAiModels() throws Exception {
        ApiKeyItem apiKey = new ApiKeyItem();
        apiKey.setId(9L);
        apiKey.setOwnerId(99L);
        apiKey.setGroupId(2L);
        when(relayApiKeyAuthService.authenticate("Bearer sk-test")).thenReturn(apiKey);
        when(userService.checkBalanceByAuthUserId(99L)).thenReturn(true);
        when(relayModelRouter.route("gpt-4o-mini")).thenReturn(new RelayRoute("openai"));
        when(groupRepository.findById(2L)).thenReturn(group("openai-default", "OpenAI", "ChatGPT Plus", "姝ｅ父"));

        RelayResult result = new RelayResult();
        result.setAccountId(1L);
        result.setSuccess(true);
        result.setStatusCode(200);
        when(openAiRelayAdapter.relay(any(), any(), eq("ChatGPT Plus"))).thenReturn(result);

        RelayChatCompletionRequest request = requestFromJson("""
                {
                  "model": "gpt-4o-mini",
                  "tool_choice": "auto",
                  "parallel_tool_calls": true,
                  "tools": [
                    {
                      "type": "function",
                      "function": {
                        "name": "read_file",
                        "parameters": {
                          "type": "object",
                          "properties": {
                            "path": {
                              "type": "string"
                            }
                          }
                        }
                      }
                    }
                  ],
                  "messages": [
                    {
                      "role": "user",
                      "content": "Read the README"
                    }
                  ]
                }
                """);

        RelayResult relayed = relayService.relayChatCompletion("Bearer sk-test", request);

        assertThat(relayed).isSameAs(result);
        verify(openAiRelayAdapter).relay(eq(request), any(), eq("ChatGPT Plus"));
    }

    @Test
    void rejectsOpenAiToolCallingFieldsForClaudeModels() throws Exception {
        when(relayModelRouter.route("claude-3-5-sonnet")).thenReturn(new RelayRoute("claude"));

        RelayChatCompletionRequest request = requestFromJson("""
                {
                  "model": "claude-3-5-sonnet",
                  "tool_choice": "auto",
                  "tools": [
                    {
                      "type": "function",
                      "function": {
                        "name": "read_file",
                        "parameters": {
                          "type": "object",
                          "properties": {}
                        }
                      }
                    }
                  ],
                  "messages": [
                    {
                      "role": "user",
                      "content": "Read the README"
                    }
                  ]
                }
                """);

        assertThatThrownBy(() -> relayService.relayChatCompletion("Bearer sk-test", request))
                .isInstanceOf(RelayException.class)
                .matches(ex -> ((RelayException) ex).getStatus().equals(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("tool calling is only supported for OpenAI models");

        verify(relayApiKeyAuthService, never()).authenticate(any());
        verify(openAiRelayAdapter, never()).relay(any(), any(), any());
        verify(claudeRelayAdapter, never()).relay(any(), any(), any());
    }

    @Test
    void relaysClaudeMessagesUsingAnthropicCompatibleAuth() throws Exception {
        JsonNode requestBody = new ObjectMapper().readTree("""
                {
                  "model": "claude-3-5-sonnet",
                  "max_tokens": 16,
                  "messages": [
                    { "role": "user", "content": "hello" }
                  ]
                }
                """);

        ApiKeyItem apiKey = new ApiKeyItem();
        apiKey.setId(9L);
        apiKey.setOwnerId(99L);
        apiKey.setGroupId(2L);
        when(relayApiKeyAuthService.authenticateFlexible(null, "sk-anthropic")).thenReturn(apiKey);
        when(userService.checkBalanceByAuthUserId(99L)).thenReturn(true);
        when(relayModelRouter.route("claude-3-5-sonnet")).thenReturn(new RelayRoute("claude"));
        when(groupRepository.findById(2L)).thenReturn(group("claude-default", "Anthropic", "Claude Code", "姝ｅ父"));

        RelayResult result = new RelayResult();
        result.setAccountId(1L);
        result.setSuccess(true);
        result.setStatusCode(200);
        when(claudeRelayAdapter.relayMessages(eq(requestBody), any(), eq("Claude Code"), eq("2023-06-01")))
                .thenReturn(result);

        RelayResult relayed = relayService.relayClaudeMessages("sk-anthropic", null, "2023-06-01", requestBody);

        assertThat(relayed).isSameAs(result);
        verify(relayApiKeyAuthService).authenticateFlexible(null, "sk-anthropic");
        verify(claudeRelayAdapter).relayMessages(eq(requestBody), any(), eq("Claude Code"), eq("2023-06-01"));
        verify(relayRecordService).record(eq(apiKey), any(), eq(result), eq("claude-3-5-sonnet"), any());
    }

    @Test
    void retriesResponsesApiOnceAfterRefreshingOauthTokenOn401() throws Exception {
        ApiKeyItem apiKey = apiKey(99L, 2L);
        JsonNode requestBody = objectMapper.readTree("""
                {
                  "model": "gpt-4o-mini",
                  "input": "hello"
                }
                """);
        RelayResult expiredToken = oauthAuthenticationError(1L);
        RelayResult success = successResult(1L);

        when(relayApiKeyAuthService.authenticate("Bearer sk-test")).thenReturn(apiKey);
        when(userService.checkBalanceByAuthUserId(99L)).thenReturn(true);
        when(relayModelRouter.route("gpt-4o-mini")).thenReturn(new RelayRoute("openai"));
        when(groupRepository.findById(2L)).thenReturn(group("openai-default", "OpenAI", "ChatGPT Plus", "active"));
        when(openAiRelayAdapter.relayResponses(eq(requestBody), any(), eq("ChatGPT Plus")))
                .thenReturn(expiredToken, success);
        when(oauthTokenRefreshService.tryRefreshNow(1L)).thenReturn(true);

        RelayResult relayed = relayService.relayResponsesApi("Bearer sk-test", requestBody);

        assertThat(relayed).isSameAs(success);
        verify(openAiRelayAdapter, times(2)).relayResponses(eq(requestBody), any(), eq("ChatGPT Plus"));
        verify(oauthTokenRefreshService).tryRefreshNow(1L);
        verify(quotaStateManager).clearQuotaStateIfRecovered(1L);
        verify(relayRecordService).record(eq(apiKey), any(), eq(success), eq("gpt-4o-mini"), any());
    }

    @Test
    void retriesResponsesApiStreamingOnceAfterRefreshingOauthTokenOn401() throws Exception {
        ApiKeyItem apiKey = apiKey(99L, 2L);
        JsonNode requestBody = objectMapper.readTree("""
                {
                  "model": "gpt-4o-mini",
                  "input": "hello"
                }
                """);
        RelayResult expiredToken = oauthAuthenticationError(1L);
        RelayResult success = successResult(1L);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        when(relayApiKeyAuthService.authenticate("Bearer sk-test")).thenReturn(apiKey);
        when(userService.checkBalanceByAuthUserId(99L)).thenReturn(true);
        when(relayModelRouter.route("gpt-4o-mini")).thenReturn(new RelayRoute("openai"));
        when(groupRepository.findById(2L)).thenReturn(group("openai-default", "OpenAI", "ChatGPT Plus", "active"));
        when(openAiRelayAdapter.relayResponsesStreaming(eq(requestBody), any(), eq("ChatGPT Plus"), eq(outputStream)))
                .thenReturn(expiredToken, success);
        when(oauthTokenRefreshService.tryRefreshNow(1L)).thenReturn(true);

        RelayResult relayed = relayService.relayResponsesApiStreaming("Bearer sk-test", requestBody, outputStream);

        assertThat(relayed).isSameAs(success);
        verify(openAiRelayAdapter, times(2)).relayResponsesStreaming(eq(requestBody), any(), eq("ChatGPT Plus"), eq(outputStream));
        verify(oauthTokenRefreshService).tryRefreshNow(1L);
        verify(quotaStateManager).clearQuotaStateIfRecovered(1L);
    }

    @Test
    void retriesChatCompletionOnceAfterRefreshingOauthTokenOn401() {
        ApiKeyItem apiKey = apiKey(99L, 2L);
        RelayChatCompletionRequest request = validRequest("gpt-4o-mini");
        RelayResult expiredToken = oauthAuthenticationError(1L);
        RelayResult success = successResult(1L);

        when(relayApiKeyAuthService.authenticate("Bearer sk-test")).thenReturn(apiKey);
        when(userService.checkBalanceByAuthUserId(99L)).thenReturn(true);
        when(relayModelRouter.route("gpt-4o-mini")).thenReturn(new RelayRoute("openai"));
        when(groupRepository.findById(2L)).thenReturn(group("openai-default", "OpenAI", "ChatGPT Plus", "active"));
        when(openAiRelayAdapter.relay(eq(request), any(), eq("ChatGPT Plus")))
                .thenReturn(expiredToken, success);
        when(oauthTokenRefreshService.tryRefreshNow(1L)).thenReturn(true);

        RelayResult relayed = relayService.relayChatCompletion("Bearer sk-test", request);

        assertThat(relayed).isSameAs(success);
        verify(openAiRelayAdapter, times(2)).relay(eq(request), any(), eq("ChatGPT Plus"));
        verify(oauthTokenRefreshService).tryRefreshNow(1L);
        verify(quotaStateManager).clearQuotaStateIfRecovered(1L);
    }

    @Test
    void retriesChatCompletionStreamingOnceAfterRefreshingOauthTokenOn401() {
        ApiKeyItem apiKey = apiKey(99L, 2L);
        RelayChatCompletionRequest request = validRequest("gpt-4o-mini");
        RelayResult expiredToken = oauthAuthenticationError(1L);
        RelayResult success = successResult(1L);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        when(relayApiKeyAuthService.authenticate("Bearer sk-test")).thenReturn(apiKey);
        when(userService.checkBalanceByAuthUserId(99L)).thenReturn(true);
        when(relayModelRouter.route("gpt-4o-mini")).thenReturn(new RelayRoute("openai"));
        when(groupRepository.findById(2L)).thenReturn(group("openai-default", "OpenAI", "ChatGPT Plus", "active"));
        when(openAiRelayAdapter.relayStreaming(eq(request), any(), eq("ChatGPT Plus"), eq(outputStream)))
                .thenReturn(expiredToken, success);
        when(oauthTokenRefreshService.tryRefreshNow(1L)).thenReturn(true);

        RelayResult relayed = relayService.relayChatCompletionStreaming("Bearer sk-test", request, outputStream);

        assertThat(relayed).isSameAs(success);
        verify(openAiRelayAdapter, times(2)).relayStreaming(eq(request), any(), eq("ChatGPT Plus"), eq(outputStream));
        verify(oauthTokenRefreshService).tryRefreshNow(1L);
        verify(quotaStateManager).clearQuotaStateIfRecovered(1L);
    }

    @Test
    void retriesOauth401OnlyOnceWhenAuthenticationErrorPersists() throws Exception {
        ApiKeyItem apiKey = apiKey(99L, 2L);
        JsonNode requestBody = objectMapper.readTree("""
                {
                  "model": "gpt-4o-mini",
                  "input": "hello"
                }
                """);
        RelayResult first401 = oauthAuthenticationError(1L);
        RelayResult second401 = oauthAuthenticationError(1L);

        when(relayApiKeyAuthService.authenticate("Bearer sk-test")).thenReturn(apiKey);
        when(userService.checkBalanceByAuthUserId(99L)).thenReturn(true);
        when(relayModelRouter.route("gpt-4o-mini")).thenReturn(new RelayRoute("openai"));
        when(groupRepository.findById(2L)).thenReturn(group("openai-default", "OpenAI", "ChatGPT Plus", "active"));
        when(openAiRelayAdapter.relayResponses(eq(requestBody), any(), eq("ChatGPT Plus")))
                .thenReturn(first401, second401);
        when(oauthTokenRefreshService.tryRefreshNow(1L)).thenReturn(true);

        RelayResult relayed = relayService.relayResponsesApi("Bearer sk-test", requestBody);

        assertThat(relayed).isSameAs(second401);
        verify(openAiRelayAdapter, times(2)).relayResponses(eq(requestBody), any(), eq("ChatGPT Plus"));
        verify(oauthTokenRefreshService).tryRefreshNow(1L);
        verify(quotaStateManager).markQuotaExhausted(1L, "oauth_token_expired");
    }

    @Test
    void retriesClaudeChatCompletionOnceAfterRefreshingOauthTokenOn401() {
        ApiKeyItem apiKey = apiKey(99L, 2L);
        RelayChatCompletionRequest request = validRequest("claude-3-5-sonnet");
        RelayResult expiredToken = oauthAuthenticationError(1L, "claude");
        RelayResult success = successResult(1L, "claude");

        when(relayApiKeyAuthService.authenticate("Bearer sk-test")).thenReturn(apiKey);
        when(userService.checkBalanceByAuthUserId(99L)).thenReturn(true);
        when(relayModelRouter.route("claude-3-5-sonnet")).thenReturn(new RelayRoute("claude"));
        when(groupRepository.findById(2L)).thenReturn(group("claude-default", "Anthropic", "Claude Code", "active"));
        when(claudeRelayAdapter.relay(eq(request), any(), eq("Claude Code")))
                .thenReturn(expiredToken, success);
        when(oauthTokenRefreshService.tryRefreshNow(1L)).thenReturn(true);

        RelayResult relayed = relayService.relayChatCompletion("Bearer sk-test", request);

        assertThat(relayed).isSameAs(success);
        verify(claudeRelayAdapter, times(2)).relay(eq(request), any(), eq("Claude Code"));
        verify(oauthTokenRefreshService).tryRefreshNow(1L);
        verify(quotaStateManager).clearQuotaStateIfRecovered(1L);
    }

    @Test
    void retriesClaudeMessagesOnceAfterRefreshingOauthTokenOn401() throws Exception {
        ApiKeyItem apiKey = apiKey(99L, 2L);
        JsonNode requestBody = objectMapper.readTree("""
                {
                  "model": "claude-3-5-sonnet",
                  "messages": [
                    { "role": "user", "content": "hello" }
                  ]
                }
                """);
        RelayResult expiredToken = oauthAuthenticationError(1L, "claude");
        RelayResult success = successResult(1L, "claude");

        when(relayApiKeyAuthService.authenticateFlexible(null, "sk-anthropic")).thenReturn(apiKey);
        when(userService.checkBalanceByAuthUserId(99L)).thenReturn(true);
        when(relayModelRouter.route("claude-3-5-sonnet")).thenReturn(new RelayRoute("claude"));
        when(groupRepository.findById(2L)).thenReturn(group("claude-default", "Anthropic", "Claude Code", "active"));
        when(claudeRelayAdapter.relayMessages(eq(requestBody), any(), eq("Claude Code"), eq("2023-06-01")))
                .thenReturn(expiredToken, success);
        when(oauthTokenRefreshService.tryRefreshNow(1L)).thenReturn(true);

        RelayResult relayed = relayService.relayClaudeMessages("sk-anthropic", null, "2023-06-01", requestBody);

        assertThat(relayed).isSameAs(success);
        verify(claudeRelayAdapter, times(2)).relayMessages(eq(requestBody), any(), eq("Claude Code"), eq("2023-06-01"));
        verify(oauthTokenRefreshService).tryRefreshNow(1L);
        verify(quotaStateManager).clearQuotaStateIfRecovered(1L);
    }

    @Test
    void retriesClaudeMessagesStreamingOnceAfterRefreshingOauthTokenOn401() throws Exception {
        ApiKeyItem apiKey = apiKey(99L, 2L);
        JsonNode requestBody = objectMapper.readTree("""
                {
                  "model": "claude-3-5-sonnet",
                  "messages": [
                    { "role": "user", "content": "hello" }
                  ]
                }
                """);
        RelayResult expiredToken = oauthAuthenticationError(1L, "claude");
        RelayResult success = successResult(1L, "claude");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        when(relayApiKeyAuthService.authenticateFlexible(null, "sk-anthropic")).thenReturn(apiKey);
        when(userService.checkBalanceByAuthUserId(99L)).thenReturn(true);
        when(relayModelRouter.route("claude-3-5-sonnet")).thenReturn(new RelayRoute("claude"));
        when(groupRepository.findById(2L)).thenReturn(group("claude-default", "Anthropic", "Claude Code", "active"));
        when(claudeRelayAdapter.relayMessagesStreaming(eq(requestBody), any(), eq("Claude Code"), eq("2023-06-01"), eq(outputStream)))
                .thenReturn(expiredToken, success);
        when(oauthTokenRefreshService.tryRefreshNow(1L)).thenReturn(true);

        RelayResult relayed = relayService.relayClaudeMessagesStreaming("sk-anthropic", null, "2023-06-01", requestBody, outputStream);

        assertThat(relayed).isSameAs(success);
        verify(claudeRelayAdapter, times(2)).relayMessagesStreaming(eq(requestBody), any(), eq("Claude Code"), eq("2023-06-01"), eq(outputStream));
        verify(oauthTokenRefreshService).tryRefreshNow(1L);
        verify(quotaStateManager).clearQuotaStateIfRecovered(1L);
    }

    private GroupItem group(String name, String platform, String accountType, String status) {
        GroupItem item = new GroupItem();
        item.setName(name);
        item.setPlatform(platform);
        item.setAccountType(accountType);
        item.setStatus(status);
        return item;
    }

    private RelayChatCompletionRequest validRequest(String model) {
        return RelayChatCompletionRequest.builder()
                .model(model)
                .addMessage("user", "hello")
                .build();
    }

    private ApiKeyItem apiKey(Long ownerId, Long groupId) {
        ApiKeyItem item = new ApiKeyItem();
        item.setId(9L);
        item.setOwnerId(ownerId);
        item.setGroupId(groupId);
        return item;
    }

    private RelayResult oauthAuthenticationError(Long accountId) {
        return oauthAuthenticationError(accountId, "openai");
    }

    private RelayResult oauthAuthenticationError(Long accountId, String provider) {
        RelayResult result = new RelayResult();
        result.setAccountId(accountId);
        result.setSuccess(false);
        result.setStatusCode(401);
        result.setProvider(provider);
        result.setAuthMethod("OAuth");
        result.setBody("""
                {
                  "error": {
                    "type": "authentication_error",
                    "message": "token expired"
                  }
                }
                """);
        return result;
    }

    private RelayResult successResult(Long accountId) {
        return successResult(accountId, "openai");
    }

    private RelayResult successResult(Long accountId, String provider) {
        RelayResult result = new RelayResult();
        result.setAccountId(accountId);
        result.setSuccess(true);
        result.setStatusCode(200);
        result.setProvider(provider);
        result.setAuthMethod("OAuth");
        result.setBody("""
                {
                  "id": "resp_123"
                }
                """);
        return result;
    }

    private RelayChatCompletionRequest requestFromJson(String json) throws Exception {
        return objectMapper.readValue(json, RelayChatCompletionRequest.class);
    }
}
