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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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

    private RelayService relayService;

    @BeforeEach
    void setUp() {
        relayService = new RelayService(
                relayApiKeyAuthService,
                relayModelRouter,
                openAiRelayAdapter,
                claudeRelayAdapter,
                relayRecordService,
                relayAccountSelector,
                quotaStateManager,
                groupRepository,
                new tools.jackson.databind.ObjectMapper()
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
}
