package com.firstapi.backend.service;

import com.firstapi.backend.config.RelayProperties;
import com.firstapi.backend.model.RelayChatCompletionRequest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeRelayAdapterTest {

    private final ClaudeRelayAdapter adapter = new ClaudeRelayAdapter(new ObjectMapper(), new RelayProperties());

    @Test
    void convertsOpenAiMessagesIntoClaudePayload() {
        RelayChatCompletionRequest request = RelayChatCompletionRequest.builder()
                .model("claude-3-5-sonnet")
                .addMessage("user", "hello")
                .maxTokens(128)
                .build();

        ObjectNode payload = adapter.toClaudeRequest(request);

        assertThat(payload.get("model").asText()).isEqualTo("claude-3-5-sonnet");
        assertThat(payload.get("messages").get(0).get("role").asText()).isEqualTo("user");
        assertThat(payload.get("messages").get(0).get("content").asText()).isEqualTo("hello");
        assertThat(payload.get("max_tokens").asInt()).isEqualTo(128);
    }

    @Test
    void carriesStreamAndMappedSamplingFieldsIntoClaudePayload() {
        RelayChatCompletionRequest request = RelayChatCompletionRequest.builder()
                .model("claude-3-5-sonnet")
                .addMessage("user", "hello")
                .build();
        request.setStream(true);
        request.setTemperature(0.2);
        request.setTopP(0.8);
        request.setStop(Collections.singletonList("done"));

        ObjectNode payload = adapter.toClaudeRequest(request);

        assertThat(payload.get("stream").asBoolean()).isTrue();
        assertThat(payload.get("temperature").asDouble()).isEqualTo(0.2);
        assertThat(payload.get("top_p").asDouble()).isEqualTo(0.8);
        assertThat(payload.get("stop_sequences").get(0).asText()).isEqualTo("done");
    }
}
