package com.firstapi.backend.service;

import com.firstapi.backend.model.AccountItem;
import com.firstapi.backend.model.IpItem;
import com.firstapi.backend.model.RelayChatCompletionRequest;
import com.firstapi.backend.model.RelayResult;
import com.firstapi.backend.model.RelayRoute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenAiRelayAdapterTest {

    @Mock
    private RelayAccountSelector relayAccountSelector;

    @Mock
    private SensitiveDataService sensitiveDataService;

    @Mock
    private UpstreamHttpClient upstreamHttpClient;

    private OpenAiRelayAdapter adapter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        adapter = new OpenAiRelayAdapter(
                relayAccountSelector,
                sensitiveDataService,
                upstreamHttpClient,
                objectMapper
        );
    }

    @Test
    void relayPassesResolvedProxyToUpstreamClient() {
        AccountItem account = new AccountItem();
        account.setId(7L);
        account.setCredential("enc-credential");

        IpItem proxy = new IpItem();
        proxy.setId(3L);
        proxy.setProtocol("SOCKS5");
        proxy.setAddress("127.0.0.1:7890");

        when(relayAccountSelector.selectAccount("openai", "ChatGPT Plus")).thenReturn(account);
        when(relayAccountSelector.resolveBaseUrl(account, "openai")).thenReturn("https://api.openai.com");
        when(relayAccountSelector.resolveProxy(account)).thenReturn(proxy);
        when(sensitiveDataService.reveal("enc-credential")).thenReturn("sk-test");

        RelayResult upstream = new RelayResult();
        upstream.setSuccess(true);
        when(upstreamHttpClient.postJson(
                eq("https://api.openai.com/v1/chat/completions"),
                anyMap(),
                any(JsonNode.class),
                same(proxy)
        )).thenReturn(upstream);

        RelayChatCompletionRequest request = RelayChatCompletionRequest.builder()
                .model("gpt-4o-mini")
                .addMessage("user", "hello")
                .build();

        RelayResult result = adapter.relay(request, new RelayRoute("openai"), "ChatGPT Plus");

        assertThat(result).isSameAs(upstream);
        assertThat(result.getAccountId()).isEqualTo(7L);
        verify(upstreamHttpClient).postJson(
                eq("https://api.openai.com/v1/chat/completions"),
                anyMap(),
                any(JsonNode.class),
                same(proxy)
        );
        verify(relayAccountSelector).releaseAccount(account);
    }

    @Test
    void relayResponsesConvertsSseBodyIntoJsonForNonStreamRequests() throws Exception {
        AccountItem account = new AccountItem();
        account.setId(9L);
        account.setCredential("enc-responses");

        when(relayAccountSelector.selectAccount("openai", "ChatGPT Plus")).thenReturn(account);
        when(relayAccountSelector.resolveBaseUrl(account, "openai")).thenReturn("https://api.openai.com");
        when(relayAccountSelector.resolveProxy(account)).thenReturn(null);
        when(sensitiveDataService.reveal("enc-responses")).thenReturn("sk-responses");

        RelayResult upstream = new RelayResult();
        upstream.setStatusCode(200);
        upstream.setContentType("application/json");
        upstream.setSuccess(true);
        upstream.setBody("event: response.created\n"
                + "data: {\"type\":\"response.created\",\"response\":{\"id\":\"resp_test\",\"object\":\"response\",\"status\":\"in_progress\"}}\n\n"
                + "event: response.output_item.done\n"
                + "data: {\"type\":\"response.output_item.done\",\"item\":{\"id\":\"msg_test\",\"type\":\"message\",\"status\":\"completed\",\"content\":[{\"type\":\"output_text\",\"text\":\"pong\"}],\"role\":\"assistant\"},\"output_index\":0}\n\n"
                + "event: response.completed\n"
                + "data: {\"type\":\"response.completed\",\"response\":{\"id\":\"resp_test\",\"object\":\"response\",\"status\":\"completed\",\"model\":\"gpt-5.4\",\"output\":[{\"id\":\"msg_test\",\"type\":\"message\",\"status\":\"completed\",\"content\":[{\"type\":\"output_text\",\"text\":\"pong\"}],\"role\":\"assistant\"}],\"usage\":{\"input_tokens\":11,\"output_tokens\":7,\"total_tokens\":18}}}\n\n");
        when(upstreamHttpClient.postJson(
                eq("https://api.openai.com/v1/responses"),
                anyMap(),
                any(JsonNode.class),
                isNull()
        )).thenReturn(upstream);

        RelayResult result = adapter.relayResponses(
                objectMapper.readTree("{\"model\":\"gpt-5.4\",\"input\":\"reply pong\",\"stream\":false}"),
                new RelayRoute("openai"),
                "ChatGPT Plus"
        );

        assertThat(result.getBody()).startsWith("{");
        JsonNode response = objectMapper.readTree(result.getBody());
        assertThat(response.path("object").asText()).isEqualTo("response");
        assertThat(response.path("status").asText()).isEqualTo("completed");
        assertThat(response.path("output").path(0).path("content").path(0).path("text").asText()).isEqualTo("pong");
        assertThat(response.path("usage").path("total_tokens").asInt()).isEqualTo(18);

        verify(relayAccountSelector).releaseAccount(account);
    }

    @Test
    void relayForwardsOpenAiToolCallingFieldsToUpstream() throws Exception {
        AccountItem account = new AccountItem();
        account.setId(11L);
        account.setCredential("enc-tools");

        when(relayAccountSelector.selectAccount("openai", "ChatGPT Plus")).thenReturn(account);
        when(relayAccountSelector.resolveBaseUrl(account, "openai")).thenReturn("https://api.openai.com");
        when(relayAccountSelector.resolveProxy(account)).thenReturn(null);
        when(sensitiveDataService.reveal("enc-tools")).thenReturn("sk-tools");

        RelayResult upstream = new RelayResult();
        upstream.setSuccess(true);
        when(upstreamHttpClient.postJson(
                eq("https://api.openai.com/v1/chat/completions"),
                anyMap(),
                any(JsonNode.class),
                isNull()
        )).thenReturn(upstream);

        RelayChatCompletionRequest request = objectMapper.readValue("""
                {
                  "model": "gpt-4o-mini",
                  "tool_choice": "auto",
                  "parallel_tool_calls": true,
                  "tools": [
                    {
                      "type": "function",
                      "function": {
                        "name": "read_file",
                        "description": "Read a file",
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
                    },
                    {
                      "role": "assistant",
                      "content": null,
                      "tool_calls": [
                        {
                          "id": "call_list_files",
                          "type": "function",
                          "function": {
                            "name": "list_files",
                            "arguments": "{}"
                          }
                        }
                      ]
                    },
                    {
                      "role": "tool",
                      "tool_call_id": "call_list_files",
                      "content": "[\\"README.md\\"]"
                    }
                  ]
                }
                """, RelayChatCompletionRequest.class);

        adapter.relay(request, new RelayRoute("openai"), "ChatGPT Plus");

        ArgumentCaptor<JsonNode> payloadCaptor = ArgumentCaptor.forClass(JsonNode.class);
        verify(upstreamHttpClient).postJson(
                eq("https://api.openai.com/v1/chat/completions"),
                anyMap(),
                payloadCaptor.capture(),
                isNull()
        );

        JsonNode payload = payloadCaptor.getValue();
        assertThat(payload.path("tool_choice").asText()).isEqualTo("auto");
        assertThat(payload.path("parallel_tool_calls").asBoolean()).isTrue();
        assertThat(payload.path("tools").isArray()).isTrue();
        assertThat(payload.path("tools").path(0).path("function").path("name").asText()).isEqualTo("read_file");
        assertThat(payload.path("messages").path(1).path("tool_calls").isArray()).isTrue();
        assertThat(payload.path("messages").path(2).path("tool_call_id").asText()).isEqualTo("call_list_files");
    }

    @Test
    void relaySetsAuthMethodOnResult() {
        AccountItem account = new AccountItem();
        account.setId(40L);
        account.setCredential("enc-auth");
        account.setAuthMethod("OAuth");

        when(relayAccountSelector.selectAccount("openai", "plus")).thenReturn(account);
        when(relayAccountSelector.resolveBaseUrl(account, "openai")).thenReturn("https://api.openai.com");
        when(relayAccountSelector.resolveProxy(account)).thenReturn(null);
        when(sensitiveDataService.reveal("enc-auth")).thenReturn("sk-relay");

        RelayResult upstream = new RelayResult();
        upstream.setStatusCode(401);
        when(upstreamHttpClient.postJson(
                eq("https://api.openai.com/v1/chat/completions"),
                anyMap(), any(JsonNode.class), isNull()
        )).thenReturn(upstream);

        RelayChatCompletionRequest request = RelayChatCompletionRequest.builder()
                .model("gpt-4o-mini").addMessage("user", "hi").build();

        RelayResult result = adapter.relay(request, new RelayRoute("openai"), "plus");
        assertThat(result.getAuthMethod()).isEqualTo("OAuth");
    }

    @Test
    void relayStreamingSetsAuthMethodOnResult() {
        AccountItem account = new AccountItem();
        account.setId(41L);
        account.setCredential("enc-auth-s");
        account.setAuthMethod("OAuth");

        when(relayAccountSelector.selectAccount("openai", "plus")).thenReturn(account);
        when(relayAccountSelector.resolveBaseUrl(account, "openai")).thenReturn("https://api.openai.com");
        when(relayAccountSelector.resolveProxy(account)).thenReturn(null);
        when(sensitiveDataService.reveal("enc-auth-s")).thenReturn("sk-relay-s");

        RelayResult upstream = new RelayResult();
        upstream.setStatusCode(401);
        when(upstreamHttpClient.postJsonStreaming(
                eq("https://api.openai.com/v1/chat/completions"),
                anyMap(), any(JsonNode.class), isNull(), any()
        )).thenReturn(upstream);

        RelayChatCompletionRequest request = RelayChatCompletionRequest.builder()
                .model("gpt-4o-mini").addMessage("user", "hi").build();

        RelayResult result = adapter.relayStreaming(request, new RelayRoute("openai"), "plus",
                new ByteArrayOutputStream());
        assertThat(result.getAuthMethod()).isEqualTo("OAuth");
    }

    @Test
    void relayResponsesSetsAuthMethodOnResult() throws Exception {
        AccountItem account = new AccountItem();
        account.setId(42L);
        account.setCredential("enc-auth-r");
        account.setAuthMethod("OAuth");

        when(relayAccountSelector.selectAccount("openai", "plus")).thenReturn(account);
        when(relayAccountSelector.resolveProxy(account)).thenReturn(null);
        when(sensitiveDataService.reveal("enc-auth-r")).thenReturn("sk-relay-r");

        // OAuth 账号走 chatgpt.com codex endpoint
        RelayResult upstream = new RelayResult();
        upstream.setStatusCode(401);
        upstream.setBody("{\"error\":{\"code\":\"token_expired\"}}");
        when(upstreamHttpClient.postJson(
                eq("https://chatgpt.com/backend-api/codex/responses"),
                anyMap(), any(JsonNode.class), isNull()
        )).thenReturn(upstream);

        RelayResult result = adapter.relayResponses(
                objectMapper.readTree("{\"model\":\"gpt-5.4\",\"input\":\"hi\"}"),
                new RelayRoute("openai"), "plus");
        assertThat(result.getAuthMethod()).isEqualTo("OAuth");
    }

    @Test
    void relayStreamingForwardsOpenAiToolCallingFieldsToUpstream() throws Exception {
        AccountItem account = new AccountItem();
        account.setId(12L);
        account.setCredential("enc-tools-stream");

        when(relayAccountSelector.selectAccount("openai", "ChatGPT Plus")).thenReturn(account);
        when(relayAccountSelector.resolveBaseUrl(account, "openai")).thenReturn("https://api.openai.com");
        when(relayAccountSelector.resolveProxy(account)).thenReturn(null);
        when(sensitiveDataService.reveal("enc-tools-stream")).thenReturn("sk-tools-stream");

        RelayResult upstream = new RelayResult();
        upstream.setSuccess(true);
        when(upstreamHttpClient.postJsonStreaming(
                eq("https://api.openai.com/v1/chat/completions"),
                anyMap(),
                any(JsonNode.class),
                isNull(),
                any()
        )).thenReturn(upstream);

        RelayChatCompletionRequest request = objectMapper.readValue("""
                {
                  "model": "gpt-4o-mini",
                  "stream": true,
                  "tool_choice": "auto",
                  "parallel_tool_calls": true,
                  "tools": [
                    {
                      "type": "function",
                      "function": {
                        "name": "read_file",
                        "description": "Read a file",
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
                """, RelayChatCompletionRequest.class);

        adapter.relayStreaming(request, new RelayRoute("openai"), "ChatGPT Plus", new ByteArrayOutputStream());

        ArgumentCaptor<JsonNode> payloadCaptor = ArgumentCaptor.forClass(JsonNode.class);
        verify(upstreamHttpClient).postJsonStreaming(
                eq("https://api.openai.com/v1/chat/completions"),
                anyMap(),
                payloadCaptor.capture(),
                isNull(),
                any()
        );

        JsonNode payload = payloadCaptor.getValue();
        assertThat(payload.path("stream").asBoolean()).isTrue();
        assertThat(payload.path("tool_choice").asText()).isEqualTo("auto");
        assertThat(payload.path("parallel_tool_calls").asBoolean()).isTrue();
        assertThat(payload.path("tools").isArray()).isTrue();
        assertThat(payload.path("tools").path(0).path("function").path("name").asText()).isEqualTo("read_file");
    }
}
