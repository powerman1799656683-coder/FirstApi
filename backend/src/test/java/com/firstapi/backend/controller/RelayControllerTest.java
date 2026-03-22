package com.firstapi.backend.controller;

import com.firstapi.backend.model.ApiKeyItem;
import com.firstapi.backend.repository.MyApiKeysRepository;
import com.firstapi.backend.service.SensitiveDataService;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.sql.init.schema-locations=classpath:schema-test.sql",
        "app.security.data-secret=test-secret-key-for-testing"
})
class RelayControllerTest {

    private static final MockWebServer OPENAI_SERVER = new MockWebServer();

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private MyApiKeysRepository apiKeysRepository;

    @Autowired
    private SensitiveDataService sensitiveDataService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeAll
    static void startServer() throws IOException {
        OPENAI_SERVER.start();
    }

    @AfterAll
    static void stopServer() throws IOException {
        OPENAI_SERVER.shutdown();
    }

    @BeforeEach
    void clearRecordedRequests() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        while (OPENAI_SERVER.takeRequest(10, TimeUnit.MILLISECONDS) != null) {
            // Drain requests left by previous tests so each assertion sees its own upstream call.
        }
        jdbcTemplate.update("update `accounts` set `base_url` = null");
    }

    @DynamicPropertySource
    static void registerRelayProperties(DynamicPropertyRegistry registry) {
        registry.add("app.relay.openai-base-url", () -> OPENAI_SERVER.url("/").toString().replaceAll("/$", ""));
        registry.add("app.relay.claude-base-url", () -> OPENAI_SERVER.url("/").toString().replaceAll("/$", ""));
    }

    @Test
    void relaysOpenAiChatCompletions() throws Exception {
        OPENAI_SERVER.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"id\":\"chatcmpl-test\",\"object\":\"chat.completion\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"hello\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}"));

        String key = saveApiKey("sk-firstapi-live", "openai-default");

        mockMvc.perform(post("/v1/chat/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"gpt-4o-mini\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.choices[0].message.content").value("hello"));

        RecordedRequest request = OPENAI_SERVER.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getPath()).isEqualTo("/v1/chat/completions");
    }

    @Test
    void usesAccountSpecificOpenAiBaseUrlWhenPresent() throws Exception {
        OPENAI_SERVER.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"id\":\"chatcmpl-test\",\"object\":\"chat.completion\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"hello\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}"));

        setAccountBaseUrl(1L, OPENAI_SERVER.url("/openai-account").toString().replaceAll("/$", ""));
        String key = saveApiKey("sk-firstapi-openai-account-url", "openai-default");

        mockMvc.perform(post("/v1/chat/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"gpt-4o-mini\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"))
                .andExpect(status().isOk());

        RecordedRequest request = OPENAI_SERVER.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getPath()).isEqualTo("/openai-account/v1/chat/completions");
    }

    @Test
    void returnsOpenAiStyleErrorForBadKey() throws Exception {
        mockMvc.perform(post("/v1/chat/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer sk-firstapi-bad")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"gpt-4o-mini\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("invalid_api_key"));
    }

    @Test
    void relaysOpenAiStreamChunks() throws Exception {
        OPENAI_SERVER.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
                .setBody("data: {\"id\":\"chatcmpl-stream\",\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}\n\n"
                        + "data: [DONE]\n\n"));

        String key = saveApiKey("sk-firstapi-stream", "openai-default");

        MvcResult result = mockMvc.perform(post("/v1/chat/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"gpt-4o-mini\",\"stream\":true,\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith(MediaType.TEXT_EVENT_STREAM_VALUE)))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("data: {\"id\":\"chatcmpl-stream\"");
        assertThat(body).contains("data: [DONE]");
    }

    @Test
    void relaysClaudeModelThroughOpenAiCompatibleEndpoint() throws Exception {
        OPENAI_SERVER.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"id\":\"msg_123\",\"type\":\"message\",\"role\":\"assistant\",\"content\":[{\"type\":\"text\",\"text\":\"hello from claude\"}],\"model\":\"claude-3-5-sonnet\",\"stop_reason\":\"end_turn\",\"usage\":{\"input_tokens\":3,\"output_tokens\":4}}"));

        String key = saveApiKey("sk-firstapi-claude", "claude-default");

        mockMvc.perform(post("/v1/chat/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"claude-3-5-sonnet\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.choices[0].message.content").value("hello from claude"));

        RecordedRequest request = OPENAI_SERVER.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getPath()).isEqualTo("/v1/messages");
    }

    @Test
    void relaysClaudeStreamChunksThroughOpenAiCompatibleEndpoint() throws Exception {
        OPENAI_SERVER.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
                .addHeader("request-id", "req_claude_stream")
                .setBody("event: message_start\n"
                        + "data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_123\",\"role\":\"assistant\",\"model\":\"claude-3-5-sonnet\"}}\n\n"
                        + "event: content_block_delta\n"
                        + "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"hello from claude\"}}\n\n"
                        + "event: message_delta\n"
                        + "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"input_tokens\":3,\"output_tokens\":4}}\n\n"
                        + "event: message_stop\n"
                        + "data: {\"type\":\"message_stop\"}\n\n"));

        String key = saveApiKey("sk-firstapi-claude-stream", "claude-default");

        MvcResult result = mockMvc.perform(post("/v1/chat/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"claude-3-5-sonnet\",\"stream\":true,\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith(MediaType.TEXT_EVENT_STREAM_VALUE)))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"object\":\"chat.completion.chunk\"");
        assertThat(body).contains("\"content\":\"hello from claude\"");
        assertThat(body).contains("data: [DONE]");

        RecordedRequest request = OPENAI_SERVER.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getPath()).isEqualTo("/v1/messages");
        assertThat(request.getBody().readUtf8()).contains("\"stream\":true");
    }

    @Test
    void preservesClaudeUpstreamErrors() throws Exception {
        OPENAI_SERVER.enqueue(new MockResponse()
                .setResponseCode(429)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"type\":\"error\",\"error\":{\"type\":\"rate_limit_error\",\"message\":\"slow down\"}}"));

        String key = saveApiKey("sk-firstapi-claude-error", "claude-default");

        mockMvc.perform(post("/v1/chat/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"claude-3-5-sonnet\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().string(containsString("slow down")));
    }

    @Test
    void usesAccountSpecificClaudeBaseUrlWhenPresent() throws Exception {
        OPENAI_SERVER.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"id\":\"msg_123\",\"type\":\"message\",\"role\":\"assistant\",\"content\":[{\"type\":\"text\",\"text\":\"hello from claude\"}],\"model\":\"claude-3-5-sonnet\",\"stop_reason\":\"end_turn\",\"usage\":{\"input_tokens\":3,\"output_tokens\":4}}"));

        setAccountBaseUrl(2L, OPENAI_SERVER.url("/claude-account").toString().replaceAll("/$", ""));
        String key = saveApiKey("sk-firstapi-claude-account-url", "claude-default");

        mockMvc.perform(post("/v1/chat/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"claude-3-5-sonnet\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.choices[0].message.content").value("hello from claude"));

        RecordedRequest request = OPENAI_SERVER.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getPath()).isEqualTo("/claude-account/v1/messages");
    }

    private String saveApiKey(String plainTextKey, String group) {
        Long groupId = "openai-default".equals(group) ? 1L : "claude-default".equals(group) ? 2L : null;
        ApiKeyItem item = new ApiKeyItem();
        item.setOwnerId(7L);
        item.setName("relay");
        item.setGroupId(groupId);
        item.setKey(sensitiveDataService.protect(plainTextKey));
        item.setCreated("2026/03/16 12:20:00");
        item.setStatus("\u6b63\u5e38");
        item.setLastUsed("-");
        apiKeysRepository.save(item);
        return plainTextKey;
    }

    private void setAccountBaseUrl(Long accountId, String baseUrl) {
        jdbcTemplate.update("update `accounts` set `base_url` = ? where `id` = ?", baseUrl, accountId);
    }
}
