package com.firstapi.backend.controller;

import com.firstapi.backend.model.RelayResult;
import com.firstapi.backend.service.RelayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RelayControllerStandaloneTest {

    @Mock
    private RelayService relayService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new RelayController(relayService))
                .setControllerAdvice(new RelayExceptionHandler(), new ApiExceptionHandler())
                .build();
    }

    @Test
    void acceptsPostForModelsEndpointToSupportGatewayChecks() throws Exception {
        mockMvc.perform(post("/v1/models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.object").value("list"))
                .andExpect(jsonPath("$.data[0].id").value("gpt-4o"));
    }

    @Test
    void delegatesAnthropicCompatibleMessagesEndpoint() throws Exception {
        RelayResult result = new RelayResult();
        result.setStatusCode(200);
        result.setContentType(MediaType.APPLICATION_JSON_VALUE);
        result.setBody("{\"type\":\"message\",\"content\":[{\"type\":\"text\",\"text\":\"hello from claude\"}]}");
        when(relayService.relayClaudeMessages(eq("sk-firstapi"), isNull(), eq("2023-06-01"), any()))
                .thenReturn(result);

        mockMvc.perform(post("/v1/messages")
                        .header("x-api-key", "sk-firstapi")
                        .header("anthropic-version", "2023-06-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"claude-3-5-sonnet\",\"max_tokens\":16,\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.type").value("message"))
                .andExpect(jsonPath("$.content[0].text").value("hello from claude"));

        verify(relayService).relayClaudeMessages(eq("sk-firstapi"), isNull(), eq("2023-06-01"), any());
    }
}
